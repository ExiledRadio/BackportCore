package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Patch P90 (docs/PATCHES.md): spawn rules checked before the mob is built, as current Java orders them.
 *
 * <p>In {@code WorldEntitySpawner.findChunksForSpawning}, the result of the one {@code WorldServer.canCreatureTypeSpawnHere
 * (type, entry, pos)} call passes through {@code SpawnPrechecks.allowed(result, world, entry, pos)}. The world, entry and
 * position are the locals the call's arguments were loaded from (the four {@code ALOAD}s just before it); anything else
 * there and the patch is skipped. The call stays in place for other coremods and mixins, and the stack is the same
 * afterwards, so the frames stay valid.
 */
public final class SpawnPrecheckTransformer implements IClassTransformer {

    static final String PATCH = "P90";
    private static final String WHAT = "spawn rules before the mob is built (WorldEntitySpawner.findChunksForSpawning)";
    private static final String TARGET = "net.minecraft.world.WorldEntitySpawner";
    private static final String HOOK = "com/exiledradio/backportcore/worldgen/SpawnPrechecks";
    private static final String DESC = "(Lnet/minecraft/entity/EnumCreatureType;Lnet/minecraft/world/biome/Biome$SpawnListEntry;"
            + "Lnet/minecraft/util/math/BlockPos;)Z";
    private static final String HOOK_DESC = "(ZLnet/minecraft/world/World;Lnet/minecraft/world/biome/Biome$SpawnListEntry;"
            + "Lnet/minecraft/util/math/BlockPos;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode method = Asm.method(node, "findChunksForSpawning", "func_77192_a", "(Lnet/minecraft/world/WorldServer;ZZZ)I");
        if (method == null) {
            Asm.skipped(PATCH, WHAT, "findChunksForSpawning not found");
            return basicClass;
        }
        List<MethodInsnNode> calls = Asm.calls(method, Opcodes.INVOKEVIRTUAL, "net/minecraft/world/WorldServer",
                "canCreatureTypeSpawnHere", "func_175732_a", DESC);
        if (calls.size() != 1) {
            Asm.skipped(PATCH, WHAT, calls.size() + " canCreatureTypeSpawnHere calls, not 1");
            return basicClass;
        }
        MethodInsnNode call = calls.get(0);
        // The arguments as loaded: world, type, entry, pos (nearest the call last).
        int[] locals = new int[4];
        AbstractInsnNode insn = call.getPrevious();
        for (int i = 3; i >= 0; i--) {
            while (insn != null && insn.getOpcode() < 0) {
                insn = insn.getPrevious(); // labels, line numbers, frames
            }
            if (insn == null || insn.getOpcode() != Opcodes.ALOAD) {
                Asm.skipped(PATCH, WHAT, "the call's arguments are not four plain local loads");
                return basicClass;
            }
            locals[i] = ((VarInsnNode) insn).var;
            insn = insn.getPrevious();
        }
        InsnList after = new InsnList();
        after.add(new VarInsnNode(Opcodes.ALOAD, locals[0]));
        after.add(new VarInsnNode(Opcodes.ALOAD, locals[2]));
        after.add(new VarInsnNode(Opcodes.ALOAD, locals[3]));
        after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "allowed", HOOK_DESC, false));
        method.instructions.insert(call, after);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
