package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patches P10 and P11 (docs/PATCHES.md): the biome layers' two neighbor tests follow 1.16's rules once a 1.16
 * backport is installed.
 *
 * <ul>
 *   <li>P10, {@code GenLayer.biomesEqualOrMesaPlateau(II)Z} (static; every hills, edge and shore layer asks it):
 *       the body becomes {@code return worldgen.WorldgenHooks.similarBiomes(a, b)}.</li>
 *   <li>P11, {@code GenLayerBiomeEdge.canBiomesBeNeighbors(II)Z} (private; decides mountain edges): the body
 *       becomes {@code return worldgen.WorldgenHooks.canBeNeighbors(a, b)}.</li>
 * </ul>
 * Each hook runs 1.12.2's own code line for line until then. The new bodies are straight-line.
 */
public final class LayerRulesTransformer implements IClassTransformer {

    static final String SIMILAR = "P10";
    static final String NEIGHBORS = "P11";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        if ("net.minecraft.world.gen.layer.GenLayer".equals(transformedName)) {
            return patch(basicClass, SIMILAR, "similar biomes (GenLayer.biomesEqualOrMesaPlateau)",
                    "biomesEqualOrMesaPlateau", "func_151616_a", true, "similarBiomes");
        }
        if ("net.minecraft.world.gen.layer.GenLayerBiomeEdge".equals(transformedName)) {
            return patch(basicClass, NEIGHBORS, "mountain edges (GenLayerBiomeEdge.canBiomesBeNeighbors)",
                    "canBiomesBeNeighbors", "func_151634_b", false, "canBeNeighbors");
        }
        return basicClass;
    }

    private static byte[] patch(byte[] bytes, String patch, String what, String mcp, String srg, boolean isStatic, String hook) {
        ClassNode node = Asm.read(bytes);
        MethodNode method = Asm.method(node, mcp, srg, "(II)Z");
        if (method == null || ((method.access & Opcodes.ACC_STATIC) != 0) != isStatic) {
            Asm.skipped(patch, what, mcp + "(II)Z not found");
            return bytes;
        }
        int first = isStatic ? 0 : 1;
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, first));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, first + 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.WORLDGEN_HOOKS, hook, "(II)Z", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        Asm.applied(patch, what);
        return Asm.write(node);
    }
}
