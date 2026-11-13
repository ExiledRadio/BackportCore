package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch P27 (docs/PATCHES.md): 1.21.11's Weather Effect Radius. {@code EntityRenderer.renderRainSnow} sets its
 * radius to 5, then to 10 with Fancy graphics; both constants go through {@code PresentationHooks.weatherRadius(I)I}.
 */
public final class WeatherRadiusTransformer implements IClassTransformer {

    static final String PATCH = "P27";
    private static final String WHAT = "weather effect radius (EntityRenderer.renderRainSnow)";
    private static final String TARGET = "net.minecraft.client.renderer.EntityRenderer";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode rain = Asm.method(node, "renderRainSnow", "func_78474_d", "(F)V");
        if (rain == null) {
            Asm.skipped(PATCH, WHAT, "renderRainSnow(F)V not found");
            return basicClass;
        }
        // "int radius = 5;" and "radius = 10;": the two constants stored into the same local.
        List<AbstractInsnNode> five = new ArrayList<AbstractInsnNode>();
        List<AbstractInsnNode> ten = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : rain.instructions.toArray()) {
            AbstractInsnNode next = insn.getNext();
            if (next == null || next.getOpcode() != Opcodes.ISTORE) {
                continue;
            }
            if (insn.getOpcode() == Opcodes.ICONST_5) {
                five.add(insn);
            } else if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == 10) {
                ten.add(insn);
            }
        }
        if (five.size() != 1 || ten.size() != 1 || ((VarInsnNode) five.get(0).getNext()).var != ((VarInsnNode) ten.get(0).getNext()).var) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + five.size() + " fives, " + ten.size() + " tens)");
            return basicClass;
        }
        for (AbstractInsnNode insn : new AbstractInsnNode[] {five.get(0), ten.get(0)}) {
            rain.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "weatherRadius", "(I)I", false));
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
