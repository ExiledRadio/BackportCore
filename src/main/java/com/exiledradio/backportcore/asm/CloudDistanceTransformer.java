package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch P26 (docs/PATCHES.md): 1.21.6's Cloud Distance. {@code RenderGlobal.renderClouds} starts with a call to
 * {@code PresentationHooks.cloudsBegin()} (the far plane and the clouds' fade), its fast clouds' two {@code -256}
 * and two {@code 256} loop bounds and {@code renderCloudsFancy}'s cell loops ({@code -3} to {@code 4}, both axes) go
 * through {@code cloudFast(I)I} and {@code cloudFancy(I)I}.
 */
public final class CloudDistanceTransformer implements IClassTransformer {

    static final String PATCH = "P26";
    private static final String WHAT = "cloud distance (RenderGlobal.renderClouds, renderCloudsFancy)";
    private static final String TARGET = "net.minecraft.client.renderer.RenderGlobal";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode clouds = Asm.method(node, "renderClouds", "func_180447_b", "(FIDDD)V");
        MethodNode fancy = Asm.method(node, "renderCloudsFancy", "func_180445_c", "(FIDDD)V");
        if (clouds == null || fancy == null) {
            Asm.skipped(PATCH, WHAT, "renderClouds or renderCloudsFancy not found");
            return basicClass;
        }
        List<AbstractInsnNode> fast = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : clouds.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.SIPUSH && Math.abs(((IntInsnNode) insn).operand) == 256) {
                fast.add(insn);
            }
        }
        // The cell loops: "-3" stored as the loop variable, "4" compared with it (if_icmpgt).
        List<AbstractInsnNode> cells = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : fancy.instructions.toArray()) {
            AbstractInsnNode next = insn.getNext();
            if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == -3 && next != null && next.getOpcode() == Opcodes.ISTORE
                    || insn.getOpcode() == Opcodes.ICONST_4 && next != null && next.getOpcode() == Opcodes.IF_ICMPGT) {
                cells.add(insn);
            }
        }
        if (fast.size() != 4 || cells.size() != 4) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + fast.size() + " fast bounds, " + cells.size() + " fancy bounds)");
            return basicClass;
        }
        for (AbstractInsnNode insn : fast) {
            clouds.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "cloudFast", "(I)I", false));
        }
        for (AbstractInsnNode insn : cells) {
            fancy.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "cloudFancy", "(I)I", false));
        }
        // The head: cloudsBegin(); if (modernClouds(partialTicks, x, y, z)) return; (one branch, an F_SAME frame at
        // the head, where the locals are the parameters and the stack is empty).
        LabelNode vanilla = new LabelNode();
        InsnList head = new InsnList();
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "cloudsBegin", "()V", false));
        head.add(new VarInsnNode(Opcodes.FLOAD, 1));
        head.add(new VarInsnNode(Opcodes.DLOAD, 3));
        head.add(new VarInsnNode(Opcodes.DLOAD, 5));
        head.add(new VarInsnNode(Opcodes.DLOAD, 7));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "modernClouds", "(FDDD)Z", false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        head.add(new InsnNode(Opcodes.RETURN));
        head.add(vanilla);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        clouds.instructions.insert(head);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
