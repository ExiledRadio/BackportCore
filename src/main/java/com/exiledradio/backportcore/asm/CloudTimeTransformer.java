package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Patch P25 (docs/PATCHES.md): the clouds' drift follows world time from 1.21.11, the same for every player.
 * 1.12.2 moves them by {@code RenderGlobal.cloudTickCounter}, a count of this client's ticks; each read of it in
 * {@code renderClouds} and {@code renderCloudsFancy} goes through {@code PresentationHooks.cloudTicks(int)}.
 */
public final class CloudTimeTransformer implements IClassTransformer {

    static final String PATCH = "P25";
    private static final String WHAT = "cloud drift by world time (RenderGlobal.renderClouds, renderCloudsFancy)";
    private static final String TARGET = "net.minecraft.client.renderer.RenderGlobal";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        int wrapped = 0;
        for (String[] m : new String[][] {{"renderClouds", "func_180447_b"}, {"renderCloudsFancy", "func_180445_c"}}) {
            MethodNode method = Asm.method(node, m[0], m[1], "(FIDDD)V");
            if (method == null) {
                Asm.skipped(PATCH, WHAT, m[0] + " not found");
                return basicClass;
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.GETFIELD && node.name.equals(((FieldInsnNode) insn).owner)
                        && Asm.named(((FieldInsnNode) insn).name, "cloudTickCounter", "field_72773_u")) {
                    method.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "cloudTicks", "(I)I", false));
                    wrapped++;
                }
            }
        }
        if (wrapped < 2) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + wrapped + " reads)");
            return basicClass;
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
