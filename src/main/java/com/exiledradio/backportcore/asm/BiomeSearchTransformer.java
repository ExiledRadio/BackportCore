package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patch P8 (docs/PATCHES.md): the biome search behind strongholds and the spawn point picks as 1.13 and later
 * do, once a backport of 1.13 or later is installed.
 *
 * <p>{@code BiomeProvider.findBiomePosition}'s whole body becomes
 * {@code return worldgen.WorldgenHooks.findBiomePosition(this, x, z, range, biomes, random)}, which runs
 * 1.12.2's own loop until then. The new body is straight-line, so it needs no stack map frames; the old body,
 * its frames, locals and handlers go.
 */
public final class BiomeSearchTransformer implements IClassTransformer {

    static final String PATCH = "P8";
    private static final String WHAT = "biome search (BiomeProvider.findBiomePosition)";
    private static final String TARGET = "net.minecraft.world.biome.BiomeProvider";
    private static final String DESC = "(IIILjava/util/List;Ljava/util/Random;)Lnet/minecraft/util/math/BlockPos;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode method = Asm.method(node, "findBiomePosition", "func_180630_a", DESC);
        if (method == null) {
            Asm.skipped(PATCH, WHAT, "findBiomePosition not found");
            return basicClass;
        }
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.WORLDGEN_HOOKS, "findBiomePosition",
                "(Lnet/minecraft/world/biome/BiomeProvider;IIILjava/util/List;Ljava/util/Random;)Lnet/minecraft/util/math/BlockPos;", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
