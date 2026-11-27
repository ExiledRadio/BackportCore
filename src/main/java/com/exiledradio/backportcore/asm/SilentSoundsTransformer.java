package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patch P77 (docs/PATCHES.md): 26.4 Pre-Release 1's silent mob fixes in vanilla code. Each method stores its mob (or
 * packet) in {@code mechanics.SilentSounds} / {@code mechanics.client.SilentSoundsClient} at its head, and its sound
 * calls become static calls of a hook with the same arguments, the world first: the stack is the same, no local is
 * read after the head, so the frames stay valid. The hooks call the world themselves unless the mob is silent.
 */
public final class SilentSoundsTransformer implements IClassTransformer {

    static final String PATCH = "P77";
    private static final String HOOKS = "com/exiledradio/backportcore/mechanics/SilentSounds";
    private static final String CLIENT_HOOKS = "com/exiledradio/backportcore/mechanics/client/SilentSoundsClient";
    private static final String ENTITY = "Lnet/minecraft/entity/Entity;";
    private static final String WORLD = "Lnet/minecraft/world/World;";
    private static final String PLAYER = "Lnet/minecraft/entity/player/EntityPlayer;";
    private static final String POS = "Lnet/minecraft/util/math/BlockPos;";
    private static final String SOUND = "Lnet/minecraft/util/SoundEvent;Lnet/minecraft/util/SoundCategory;";
    private static final String EVENT = "(I" + POS + "I)V";
    private static final String PLAYER_EVENT = "(" + PLAYER + "I" + POS + "I)V";
    private static final String PLAYER_SOUND = "(" + PLAYER + "DDD" + SOUND + "FF)V";
    private static final String LOCAL_SOUND = "(DDD" + SOUND + "FFZ)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        switch (transformedName) {
            case "net.minecraft.entity.ai.EntityAIBreakDoor":
                return patch(basicClass, "door break (EntityAIBreakDoor.updateTask)", "updateTask", "func_75246_d", "()V",
                        doorHead(), 0, "playEvent", "func_175718_b", EVENT, HOOKS, "doorEvent", "(" + WORLD + "I" + POS + "I)V", 3);
            case "net.minecraft.entity.boss.EntityWither":
                return patch(basicClass, "wither block breaking (EntityWither.updateAITasks)", "updateAITasks", "func_70619_bc", "()V",
                        fixed(head(0, HOOKS, "wither", "(" + ENTITY + ")V")), 0, "playEvent", "func_180498_a", PLAYER_EVENT, HOOKS,
                        "witherEvent", "(" + WORLD + PLAYER + "I" + POS + "I)V", 1);
            case "net.minecraft.item.ItemChorusFruit":
                return patch(basicClass, "chorus fruit teleport (ItemChorusFruit.onItemUseFinish)", "onItemUseFinish", "func_77654_b",
                        "(Lnet/minecraft/item/ItemStack;" + WORLD + "Lnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/item/ItemStack;",
                        fixed(head(3, HOOKS, "chorus", "(" + ENTITY + ")V")), 0, "playSound", "func_184148_a", PLAYER_SOUND, HOOKS,
                        "chorusSound", "(" + WORLD + PLAYER + "DDD" + SOUND + "FF)V", 1);
            case "net.minecraft.client.network.NetHandlerPlayClient":
                return client(basicClass);
            default:
                return basicClass;
        }
    }

    private static InsnList head(int local, String owner, String hook, String desc) {
        InsnList l = new InsnList();
        l.add(new VarInsnNode(Opcodes.ALOAD, local));
        l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, hook, desc, false));
        return l;
    }

    /** {@code SilentSounds.door(this.entity)}, with the field as the method itself reads it (MCP or SRG name). */
    private static java.util.function.Function<MethodNode, InsnList> doorHead() {
        return m -> {
            for (org.objectweb.asm.tree.AbstractInsnNode n : m.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.GETFIELD && "Lnet/minecraft/entity/EntityLiving;".equals(((FieldInsnNode) n).desc)) {
                    FieldInsnNode f = (FieldInsnNode) n;
                    InsnList l = new InsnList();
                    l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    l.add(new FieldInsnNode(Opcodes.GETFIELD, f.owner, f.name, f.desc));
                    l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "door", "(" + ENTITY + ")V", false));
                    return l;
                }
            }
            return null;
        };
    }

    private static java.util.function.Function<MethodNode, InsnList> fixed(InsnList head) {
        return m -> head;
    }

    private static byte[] patch(byte[] bytes, String what, String mcp, String srg, String desc, java.util.function.Function<MethodNode, InsnList> headOf, int unused,
                                String callMcp, String callSrg, String callDesc, String hookOwner, String hook, String hookDesc,
                                int expected) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, mcp, srg, desc);
        if (m == null) {
            Asm.skipped(PATCH, what, "method not found");
            return bytes;
        }
        java.util.List<MethodInsnNode> calls = Asm.calls(m, Opcodes.INVOKEVIRTUAL, null, callMcp, callSrg, callDesc);
        if (calls.size() != expected) {
            Asm.skipped(PATCH, what, calls.size() + " sound calls, expected " + expected);
            return bytes;
        }
        InsnList head = headOf.apply(m);
        if (head == null) {
            Asm.skipped(PATCH, what, "mob field not found");
            return bytes;
        }
        for (MethodInsnNode call : calls) {
            m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, hookOwner, hook, hookDesc, false));
        }
        m.instructions.insert(head);
        Asm.applied(PATCH, what);
        return Asm.write(node);
    }

    private static byte[] client(byte[] bytes) {
        String what = "silent pickup and totem sounds (NetHandlerPlayClient)";
        ClassNode node = Asm.read(bytes);
        MethodNode collect = Asm.method(node, "handleCollectItem", "func_147246_a",
                "(Lnet/minecraft/network/play/server/SPacketCollectItem;)V");
        MethodNode status = Asm.method(node, "handleEntityStatus", "func_147236_a",
                "(Lnet/minecraft/network/play/server/SPacketEntityStatus;)V");
        if (collect == null || status == null) {
            Asm.skipped(PATCH, what, "method not found");
            return bytes;
        }
        java.util.List<MethodInsnNode> collectCalls = Asm.calls(collect, Opcodes.INVOKEVIRTUAL, null, "playSound", "func_184134_a", LOCAL_SOUND);
        java.util.List<MethodInsnNode> statusCalls = Asm.calls(status, Opcodes.INVOKEVIRTUAL, null, "playSound", "func_184134_a", LOCAL_SOUND);
        if (collectCalls.size() != 2 || statusCalls.isEmpty()) {
            Asm.skipped(PATCH, what, collectCalls.size() + " pickup and " + statusCalls.size() + " status sound calls");
            return bytes;
        }
        String hookDesc = "(" + WORLD + "DDD" + SOUND + "FFZ)V";
        for (MethodInsnNode call : collectCalls) {
            collect.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_HOOKS, "collectSound", hookDesc, false));
        }
        for (MethodInsnNode call : statusCalls) {
            status.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_HOOKS, "statusSound", hookDesc, false));
        }
        collect.instructions.insert(head(1, CLIENT_HOOKS, "collect", "(Lnet/minecraft/network/play/server/SPacketCollectItem;)V"));
        status.instructions.insert(head(1, CLIENT_HOOKS, "status", "(Lnet/minecraft/network/play/server/SPacketEntityStatus;)V"));
        Asm.applied(PATCH, what);
        return Asm.write(node);
    }
}
