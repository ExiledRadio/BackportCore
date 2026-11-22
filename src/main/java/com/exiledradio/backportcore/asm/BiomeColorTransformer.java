package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Patch P44 (docs/PATCHES.md): the grass, foliage and water color blend reads underground biomes.
 * {@code BiomeColorHelper.getColorAtPos} asks {@code blockAccess.getBiome(pos)} for each of the 3 by 3 columns; that
 * one call becomes {@code UndergroundBiomes.colorBiome(blockAccess, pos)} (same stack, same result where no backport
 * registered an underground biome).
 */
public final class BiomeColorTransformer implements IClassTransformer {

    static final String PATCH = "P44";
    private static final String WHAT = "underground biome colors (BiomeColorHelper.getColorAtPos)";
    private static final String TARGET = "net.minecraft.world.biome.BiomeColorHelper";
    private static final String HOOK = "com/exiledradio/backportcore/worldgen/UndergroundBiomes";
    private static final String HOOK_DESC = "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/biome/Biome;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode m = null;
        for (MethodNode candidate : node.methods) {
            if ((candidate.name.equals("getColorAtPos") || candidate.name.equals("func_180285_a"))
                    && candidate.desc.startsWith("(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;")) {
                m = candidate;
            }
        }
        if (m == null) {
            Asm.skipped(PATCH, WHAT, "method not found");
            return basicClass;
        }
        int done = 0;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.INVOKEINTERFACE) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.owner.equals("net/minecraft/world/IBlockAccess")
                        && (call.name.equals("getBiome") || call.name.equals("func_180494_b"))) {
                    m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "colorBiome", HOOK_DESC, false));
                    done++;
                    break;
                }
            }
        }
        if (done == 0) {
            Asm.skipped(PATCH, WHAT, "getBiome call not found");
            return basicClass;
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
