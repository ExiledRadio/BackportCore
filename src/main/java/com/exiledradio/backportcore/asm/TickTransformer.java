package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Patches P38-P40 (docs/PATCHES.md), 1.20.3's {@code /tick}:
 * <ul>
 *   <li>P38, the server loop: {@code MinecraftServer.run}'s four {@code 50L} become {@code TickHooks.msPerTick()}
 *       and its two {@code this.tick()} become {@code TickHooks.runTick(this)} (the tick rate and sprints);
 *       {@code updateTimeLightAndEntities}' {@code FunctionManager.update()} goes through {@code TickHooks.functions}.</li>
 *   <li>P39, freezing, both sides: {@code WorldServer.tick} and {@code World.updateEntityWithOptionalForce} return at
 *       their head when a hook says so; {@code World.updateEntities}' {@code ITickable.update()} goes through
 *       {@code TickHooks.updateTile}.</li>
 *   <li>P40, the client: {@code WorldClient.tick}'s time setters, {@code Minecraft.runTick}'s particle and ambient
 *       particle ticks, and the partial tick at the head of {@code RenderManager.renderEntityStatic} and
 *       {@code TileEntityRendererDispatcher.render(TileEntity, float, int)}.</li>
 * </ul>
 * Calls are swapped for static calls with the same stack; the two early returns add one branch with an {@code F_SAME}
 * frame at the method's head, where the locals are exactly the parameters. Every class here is patched by Forge,
 * so the game runs javac-built bytes.
 */
public final class TickTransformer implements IClassTransformer {

    static final String LOOP = "P38";
    static final String FREEZE = "P39";
    static final String CLIENT = "P40";
    private static final String HOOKS = "com/exiledradio/backportcore/command/tick/TickHooks";
    private static final String CLIENT_HOOKS = "com/exiledradio/backportcore/command/tick/client/ClientTicks";

    private static final String SERVER = "net.minecraft.server.MinecraftServer";
    private static final String WORLD = "net.minecraft.world.World";
    private static final String WORLD_SERVER = "net.minecraft.world.WorldServer";
    private static final String WORLD_CLIENT = "net.minecraft.client.multiplayer.WorldClient";
    private static final String MINECRAFT = "net.minecraft.client.Minecraft";
    private static final String RENDER_MANAGER = "net.minecraft.client.renderer.entity.RenderManager";
    private static final String TILE_DISPATCHER = "net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher";

    /** The classes of each patch done so far: a patch counts as applied once all of its classes are. */
    private static final Set<String> DONE = ConcurrentHashMap.newKeySet();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        switch (transformedName) {
            case SERVER: return server(basicClass);
            case WORLD: return world(basicClass);
            case WORLD_SERVER: return worldServer(basicClass);
            case WORLD_CLIENT: return worldClient(basicClass);
            case MINECRAFT: return minecraft(basicClass);
            case RENDER_MANAGER: return renderManager(basicClass);
            case TILE_DISPATCHER: return tileDispatcher(basicClass);
            default: return basicClass;
        }
    }

    private static void done(String patch, String what, String cls, String... all) {
        DONE.add(cls);
        for (String c : all) {
            if (!DONE.contains(c)) {
                return;
            }
        }
        Asm.applied(patch, what);
    }

    // ---- P38 -------------------------------------------------------------------------------------------------------

    private static byte[] server(byte[] bytes) {
        String what = "tick rate (MinecraftServer.run)";
        ClassNode node = Asm.read(bytes);
        MethodNode run = Asm.method(node, "run", "run", "()V");
        MethodNode update = Asm.method(node, "updateTimeLightAndEntities", "func_71190_q", "()V");
        if (run == null || update == null) {
            Asm.skipped(LOOP, what, "run or updateTimeLightAndEntities not found");
            return bytes;
        }
        List<LdcInsnNode> fifties = Asm.constants(run, 50L);
        List<MethodInsnNode> ticks = Asm.calls(run, Opcodes.INVOKEVIRTUAL, "net/minecraft/server/MinecraftServer", "tick", "func_71217_p", "()V");
        List<MethodInsnNode> functions = Asm.calls(update, Opcodes.INVOKEVIRTUAL, "net/minecraft/advancements/FunctionManager",
                "update", "func_73660_a", "()V");
        if (fifties.size() != 4 || ticks.size() != 2 || functions.size() != 1) {
            Asm.skipped(LOOP, what, "unexpected shape (" + fifties.size() + " x 50L, " + ticks.size() + " ticks, "
                    + functions.size() + " function updates)");
            return bytes;
        }
        for (LdcInsnNode ldc : fifties) {
            run.instructions.set(ldc, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "msPerTick", "()J", false));
        }
        for (MethodInsnNode call : ticks) {
            run.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "runTick", "(Lnet/minecraft/server/MinecraftServer;)V", false));
        }
        update.instructions.set(functions.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "functions",
                "(Lnet/minecraft/advancements/FunctionManager;)V", false));
        Asm.applied(LOOP, what);
        return Asm.write(node);
    }

    // ---- P39 -------------------------------------------------------------------------------------------------------

    private static final String FREEZE_WHAT = "frozen game (WorldServer.tick, World.updateEntityWithOptionalForce, World.updateEntities)";

    /** {@code if (hook(args)) return;} at the method's head. */
    private static void returnIfAtHead(MethodNode method, InsnList loadArgs, String owner, String hook, String desc) {
        InsnList head = new InsnList();
        LabelNode go = new LabelNode();
        head.add(loadArgs);
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, hook, desc, false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, go));
        head.add(new InsnNode(Opcodes.RETURN));
        head.add(go);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        AbstractInsnNode first = method.instructions.getFirst();
        if (first == null) {
            method.instructions.add(head);
        } else {
            method.instructions.insertBefore(first, head);
        }
    }

    private static byte[] world(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode update = Asm.method(node, "updateEntityWithOptionalForce", "func_72866_a", "(Lnet/minecraft/entity/Entity;Z)V");
        MethodNode entities = Asm.method(node, "updateEntities", "func_72939_s", "()V");
        if (update == null || entities == null) {
            Asm.skipped(FREEZE, FREEZE_WHAT, "updateEntityWithOptionalForce or updateEntities not found");
            return bytes;
        }
        List<MethodInsnNode> tiles = Asm.calls(entities, Opcodes.INVOKEINTERFACE, "net/minecraft/util/ITickable", "update", "func_73660_a", "()V");
        if (tiles.size() != 1) {
            Asm.skipped(FREEZE, FREEZE_WHAT, "unexpected shape (" + tiles.size() + " ITickable.update calls)");
            return bytes;
        }
        entities.instructions.set(tiles.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "updateTile", "(Lnet/minecraft/util/ITickable;)V", false));
        InsnList args = new InsnList();
        args.add(new VarInsnNode(Opcodes.ALOAD, 0));
        args.add(new VarInsnNode(Opcodes.ALOAD, 1));
        returnIfAtHead(update, args, HOOKS, "skipEntity", "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;)Z");
        done(FREEZE, FREEZE_WHAT, WORLD, WORLD, WORLD_SERVER);
        return Asm.write(node);
    }

    private static byte[] worldServer(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode tick = Asm.method(node, "tick", "func_72835_b", "()V");
        if (tick == null) {
            Asm.skipped(FREEZE, FREEZE_WHAT, "WorldServer.tick not found");
            return bytes;
        }
        InsnList args = new InsnList();
        args.add(new VarInsnNode(Opcodes.ALOAD, 0));
        returnIfAtHead(tick, args, HOOKS, "frozenWorldTick", "(Lnet/minecraft/world/WorldServer;)Z");
        done(FREEZE, FREEZE_WHAT, WORLD_SERVER, WORLD, WORLD_SERVER);
        return Asm.write(node);
    }

    // ---- P40 -------------------------------------------------------------------------------------------------------

    private static final String CLIENT_WHAT = "frozen game on the client (WorldClient.tick, Minecraft.runTick, entity and block entity partial ticks)";
    private static final String[] CLIENT_CLASSES = {WORLD_CLIENT, MINECRAFT, RENDER_MANAGER, TILE_DISPATCHER};

    private static byte[] worldClient(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode tick = Asm.method(node, "tick", "func_72835_b", "()V");
        if (tick == null) {
            Asm.skipped(CLIENT, CLIENT_WHAT, "WorldClient.tick not found");
            return bytes;
        }
        List<MethodInsnNode> total = Asm.calls(tick, Opcodes.INVOKEVIRTUAL, null, "setTotalWorldTime", "func_82738_a", "(J)V");
        List<MethodInsnNode> day = Asm.calls(tick, Opcodes.INVOKEVIRTUAL, null, "setWorldTime", "func_72877_b", "(J)V");
        if (total.size() != 1 || day.size() != 1) {
            Asm.skipped(CLIENT, CLIENT_WHAT, "unexpected shape (" + total.size() + "/" + day.size() + " time setters in WorldClient.tick)");
            return bytes;
        }
        tick.instructions.set(total.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_HOOKS, "setTotalWorldTime", "(Lnet/minecraft/world/World;J)V", false));
        tick.instructions.set(day.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_HOOKS, "setWorldTime", "(Lnet/minecraft/world/World;J)V", false));
        done(CLIENT, CLIENT_WHAT, WORLD_CLIENT, CLIENT_CLASSES);
        return Asm.write(node);
    }

    private static byte[] minecraft(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode runTick = Asm.method(node, "runTick", "func_71407_l", "()V");
        if (runTick == null) {
            Asm.skipped(CLIENT, CLIENT_WHAT, "Minecraft.runTick not found");
            return bytes;
        }
        List<MethodInsnNode> particles = Asm.calls(runTick, Opcodes.INVOKEVIRTUAL, "net/minecraft/client/particle/ParticleManager",
                "updateEffects", "func_78868_a", "()V");
        List<MethodInsnNode> ambient = Asm.calls(runTick, Opcodes.INVOKEVIRTUAL, "net/minecraft/client/multiplayer/WorldClient",
                "doVoidFogParticles", "func_73029_E", "(III)V");
        if (particles.size() != 1 || ambient.size() != 1) {
            Asm.skipped(CLIENT, CLIENT_WHAT, "unexpected shape (" + particles.size() + "/" + ambient.size() + " particle ticks in runTick)");
            return bytes;
        }
        runTick.instructions.set(particles.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_HOOKS, "updateParticles",
                "(Lnet/minecraft/client/particle/ParticleManager;)V", false));
        runTick.instructions.set(ambient.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_HOOKS, "animateTick",
                "(Lnet/minecraft/client/multiplayer/WorldClient;III)V", false));
        done(CLIENT, CLIENT_WHAT, MINECRAFT, CLIENT_CLASSES);
        return Asm.write(node);
    }

    /** {@code partialTicks = hook(arg1, partialTicks);} at the head; the parameters are read on the first line. */
    private static void partialAtHead(MethodNode method, String hook, String desc) {
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 1));
        head.add(new VarInsnNode(Opcodes.FLOAD, 2));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_HOOKS, hook, desc, false));
        head.add(new VarInsnNode(Opcodes.FSTORE, 2));
        method.instructions.insertBefore(method.instructions.getFirst(), head);
    }

    private static byte[] renderManager(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode render = Asm.method(node, "renderEntityStatic", "func_188388_a", "(Lnet/minecraft/entity/Entity;FZ)V");
        if (render == null || render.instructions.getFirst() == null) {
            Asm.skipped(CLIENT, CLIENT_WHAT, "RenderManager.renderEntityStatic not found");
            return bytes;
        }
        partialAtHead(render, "entityPartialTicks", "(Lnet/minecraft/entity/Entity;F)F");
        done(CLIENT, CLIENT_WHAT, RENDER_MANAGER, CLIENT_CLASSES);
        return Asm.write(node);
    }

    private static byte[] tileDispatcher(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode render = Asm.method(node, "render", "func_180546_a", "(Lnet/minecraft/tileentity/TileEntity;FI)V");
        if (render == null || render.instructions.getFirst() == null) {
            Asm.skipped(CLIENT, CLIENT_WHAT, "TileEntityRendererDispatcher.render not found");
            return bytes;
        }
        partialAtHead(render, "tilePartialTicks", "(Lnet/minecraft/tileentity/TileEntity;F)F");
        done(CLIENT, CLIENT_WHAT, TILE_DISPATCHER, CLIENT_CLASSES);
        return Asm.write(node);
    }
}
