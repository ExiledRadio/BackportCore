package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patch P42 (docs/PATCHES.md): 26.1's borderless fullscreen. {@code Minecraft.toggleFullscreen} starts with
 * {@code if (BorderlessFullscreen.borderlessToggle(this)) return;} (one branch, an {@code F_SAME} frame at the head,
 * where the only local is {@code this}). Applied only to 1.12.2's own method, recognized by its single
 * {@code Display.setFullscreen(Z)V} call; any other shape (a launcher's or Cleanroom's own) is left alone.
 */
public final class FullscreenTransformer implements IClassTransformer {

    static final String PATCH = "P42";
    private static final String WHAT = "borderless fullscreen (Minecraft.toggleFullscreen)";
    private static final String TARGET = "net.minecraft.client.Minecraft";
    private static final String HOOK = "com/exiledradio/backportcore/presentation/client/BorderlessFullscreen";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode m = Asm.method(node, "toggleFullscreen", "func_71352_k", "()V");
        if (m == null) {
            Asm.skipped(PATCH, WHAT, "toggleFullscreen()V not found");
            return basicClass;
        }
        int setFullscreen = 0;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if ("org/lwjgl/opengl/Display".equals(call.owner) && "setFullscreen".equals(call.name) && "(Z)V".equals(call.desc)) {
                    setFullscreen++;
                }
            }
        }
        if (setFullscreen != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + setFullscreen + " Display.setFullscreen calls)");
            return basicClass;
        }
        LabelNode vanilla = new LabelNode();
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "borderlessToggle", "(Lnet/minecraft/client/Minecraft;)Z", false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        head.add(new InsnNode(Opcodes.RETURN));
        head.add(vanilla);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(head);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
