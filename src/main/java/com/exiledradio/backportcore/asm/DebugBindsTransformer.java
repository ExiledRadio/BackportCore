package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch P30 (docs/PATCHES.md): 1.21.11's Debug key binds. {@code Minecraft.runTickKeyboard} names F3 (61) and C (46)
 * by number: {@code Keyboard.isKeyDown(61)} (the modifier, three times), {@code key == 61} (the overlay, on its
 * release) and {@code Keyboard.isKeyDown(46)} (the crash, twice). Each number goes through
 * {@code PresentationHooks.debugModifierKey(I)I}, {@code debugOverlayKey(I)I} or {@code debugCrashKey(I)I}.
 */
public final class DebugBindsTransformer implements IClassTransformer {

    static final String PATCH = "P30";
    private static final String WHAT = "debug key binds (Minecraft.runTickKeyboard)";
    private static final String TARGET = "net.minecraft.client.Minecraft";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode keyboard = Asm.method(node, "runTickKeyboard", "func_184118_az", "()V");
        if (keyboard == null) {
            Asm.skipped(PATCH, WHAT, "runTickKeyboard()V not found");
            return basicClass;
        }
        List<AbstractInsnNode> modifier = new ArrayList<AbstractInsnNode>();
        List<AbstractInsnNode> overlay = new ArrayList<AbstractInsnNode>();
        List<AbstractInsnNode> crash = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : keyboard.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.BIPUSH) {
                continue;
            }
            int n = ((IntInsnNode) insn).operand;
            AbstractInsnNode next = insn.getNext();
            boolean isKeyDown = next instanceof MethodInsnNode && "org/lwjgl/input/Keyboard".equals(((MethodInsnNode) next).owner)
                    && "isKeyDown".equals(((MethodInsnNode) next).name);
            if (n == 61 && isKeyDown) {
                modifier.add(insn);
            } else if (n == 61 && next != null && (next.getOpcode() == Opcodes.IF_ICMPNE || next.getOpcode() == Opcodes.IF_ICMPEQ)) {
                overlay.add(insn);
            } else if (n == 46 && isKeyDown) {
                crash.add(insn);
            }
        }
        if (modifier.size() != 3 || overlay.size() != 1 || crash.size() != 2) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + modifier.size() + " modifier, " + overlay.size() + " overlay, " + crash.size() + " crash)");
            return basicClass;
        }
        for (AbstractInsnNode insn : modifier) {
            keyboard.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "debugModifierKey", "(I)I", false));
        }
        keyboard.instructions.insert(overlay.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "debugOverlayKey", "(I)I", false));
        for (AbstractInsnNode insn : crash) {
            keyboard.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "debugCrashKey", "(I)I", false));
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
