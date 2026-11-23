package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patch P65 (docs/PATCHES.md): 26.1's lightmap. {@code EntityRenderer.updateLightmap(float)} starts with
 * {@code if (PresentationHooks.modernLightmap(this, partialTicks)) return;}, ahead of its
 * {@code if (this.lightmapUpdateNeeded)} (1.12.2's and OptiFine's method alike begin with that check).
 */
public final class LightmapTransformer implements IClassTransformer {

    static final String PATCH = "P65";
    private static final String WHAT = "26.1's lightmap (EntityRenderer.updateLightmap)";
    private static final String TARGET = "net.minecraft.client.renderer.EntityRenderer";
    private static final String OWNER = "net/minecraft/client/renderer/EntityRenderer";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode method = Asm.method(node, "updateLightmap", "func_78472_g", "(F)V");
        if (method == null) {
            Asm.skipped(PATCH, WHAT, "updateLightmap not found");
            return basicClass;
        }
        // The shape: aload 0; getfield lightmapUpdateNeeded Z; then ifeq past the body (MCP's recompile, OptiFine) or
        // ifne over a return (the game as Mojang compiled it).
        AbstractInsnNode first = real(method.instructions.getFirst());
        AbstractInsnNode field = first == null ? null : real(first.getNext());
        AbstractInsnNode jump = field == null ? null : real(field.getNext());
        boolean shape = first != null && first.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) first).var == 0
                && field != null && field.getOpcode() == Opcodes.GETFIELD && OWNER.equals(((FieldInsnNode) field).owner)
                && Asm.named(((FieldInsnNode) field).name, "lightmapUpdateNeeded", "field_78536_aa")
                && jump != null && (jump.getOpcode() == Opcodes.IFEQ || jump.getOpcode() == Opcodes.IFNE);
        for (AbstractInsnNode insn = method.instructions.getFirst(); shape && insn != first; insn = insn.getNext()) {
            // A frame at the head would make the inserted F_SAME a second frame at one offset.
            shape = !(insn instanceof FrameNode);
        }
        if (!shape) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (updateLightmap does not start with its update check)");
            return basicClass;
        }
        // One branch past a return; at its target the locals are the parameters and the stack is empty (F_SAME).
        LabelNode vanilla = new LabelNode();
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new VarInsnNode(Opcodes.FLOAD, 1));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "modernLightmap",
                "(L" + OWNER + ";F)Z", false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        head.add(new InsnNode(Opcodes.RETURN));
        head.add(vanilla);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(head);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }

    /** @return this instruction or the next that is not a label, line number or frame */
    private static AbstractInsnNode real(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) {
            insn = insn.getNext();
        }
        return insn;
    }
}
