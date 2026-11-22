package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Patch P46 (docs/PATCHES.md): 1.18's Simulation Distance ({@code simulation/SimulationDistance}). Four calls, each
 * exactly once in its method, or the class is left alone:
 * <ul>
 * <li>{@code World.updateEntityWithOptionalForce}: the result of the private {@code isAreaLoaded(IIIIIIZ)Z} passes
 * through {@code entityArea(Z, World, Entity)} (the call stays; parameters 0 and 1 are loaded after it).</li>
 * <li>{@code World.updateEntities}: {@code isBlockLoaded(BlockPos, Z)} before a block entity's tick becomes
 * {@code tileLoaded(World, BlockPos, Z)}.</li>
 * <li>{@code WorldServer.tickUpdates}: {@code isAreaLoaded(BlockPos, BlockPos)} becomes
 * {@code scheduledTickLoaded(World, BlockPos, BlockPos)}.</li>
 * <li>{@code WorldServer.updateBlocks}: Forge's {@code getPersistentChunkIterable(Iterator)} becomes
 * {@code randomTickChunks(World, Iterator)}.</li>
 * </ul>
 * Every change keeps the stack as it was and adds no branch, so the frames stay valid.
 */
public final class SimulationTransformer implements IClassTransformer {

    static final String PATCH = "P46";
    private static final String WHAT = "simulation distance (World and WorldServer ticking)";
    private static final String WORLD = "net.minecraft.world.World";
    private static final String WORLD_SERVER = "net.minecraft.world.WorldServer";
    private static final String HOOKS = "com/exiledradio/backportcore/simulation/SimulationDistance";
    private static final String POS = "Lnet/minecraft/util/math/BlockPos;";

    private static boolean worldDone;
    private static boolean worldServerDone;
    private static String failure;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        if (WORLD.equals(transformedName)) {
            return world(basicClass);
        }
        if (WORLD_SERVER.equals(transformedName)) {
            return worldServer(basicClass);
        }
        return basicClass;
    }

    private static byte[] world(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode update = Asm.method(node, "updateEntityWithOptionalForce", "func_72866_a", "(Lnet/minecraft/entity/Entity;Z)V");
        MethodNode entities = Asm.method(node, "updateEntities", "func_72939_s", "()V");
        if (update == null || entities == null) {
            return fail("updateEntityWithOptionalForce or updateEntities not found", bytes);
        }
        List<MethodInsnNode> area = Asm.privateCalls(update, null, "isAreaLoaded", "func_175663_a", "(IIIIIIZ)Z");
        List<MethodInsnNode> loaded = Asm.calls(entities, Opcodes.INVOKEVIRTUAL, null, "isBlockLoaded", "func_175668_a", "(" + POS + "Z)Z");
        if (area.size() != 1 || loaded.size() != 1) {
            return fail("unexpected shape (" + area.size() + " area checks, " + loaded.size() + " block entity load checks)", bytes);
        }
        InsnList after = new InsnList();
        after.add(new VarInsnNode(Opcodes.ALOAD, 0));
        after.add(new VarInsnNode(Opcodes.ALOAD, 1));
        after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "entityArea", "(ZLnet/minecraft/world/World;Lnet/minecraft/entity/Entity;)Z", false));
        update.instructions.insert(area.get(0), after);
        entities.instructions.set(loaded.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tileLoaded",
                "(Lnet/minecraft/world/World;" + POS + "Z)Z", false));
        worldDone = true;
        done();
        return Asm.write(node);
    }

    private static byte[] worldServer(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode ticks = Asm.method(node, "tickUpdates", "func_72955_a", "(Z)Z");
        MethodNode blocks = Asm.method(node, "updateBlocks", "func_147456_g", "()V");
        if (ticks == null || blocks == null) {
            return fail("tickUpdates or updateBlocks not found", bytes);
        }
        List<MethodInsnNode> area = Asm.calls(ticks, Opcodes.INVOKEVIRTUAL, null, "isAreaLoaded", "func_175707_a", "(" + POS + POS + ")Z");
        List<MethodInsnNode> persistent = Asm.calls(blocks, Opcodes.INVOKEVIRTUAL, null, "getPersistentChunkIterable", "getPersistentChunkIterable",
                "(Ljava/util/Iterator;)Ljava/util/Iterator;");
        if (area.size() != 1 || persistent.size() != 1) {
            return fail("unexpected shape (" + area.size() + " block tick area checks, " + persistent.size() + " chunk iterables)", bytes);
        }
        ticks.instructions.set(area.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "scheduledTickLoaded",
                "(Lnet/minecraft/world/World;" + POS + POS + ")Z", false));
        blocks.instructions.set(persistent.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "randomTickChunks",
                "(Lnet/minecraft/world/World;Ljava/util/Iterator;)Ljava/util/Iterator;", false));
        worldServerDone = true;
        done();
        return Asm.write(node);
    }

    /** Applied once both classes are; a class that cannot be patched leaves the whole patch skipped. */
    private static void done() {
        if (failure == null && worldDone && worldServerDone) {
            Asm.applied(PATCH, WHAT);
        }
    }

    private static byte[] fail(String why, byte[] bytes) {
        failure = why;
        Asm.skipped(PATCH, WHAT, why);
        return bytes;
    }
}
