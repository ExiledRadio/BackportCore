package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch P50 (docs/PATCHES.md): 1.19.4's Notification Time. Three places pass a time through
 * {@code presentation.client.NotificationTime}, each a value in and the same type out, so the stack and the frames
 * stay as they were:
 * <ul>
 * <li>{@code GuiToast.ToastInstance.render}: the time handed to {@code IToast.draw(GuiToast, long)}.</li>
 * <li>{@code GuiSubtitleOverlay.renderSubtitles}: its {@code 3000L} (when a subtitle goes) and {@code 3000.0F} (how it
 * fades).</li>
 * <li>{@code GuiIngame.updateTick}: the {@code 40} put into {@code remainingHighlightTicks} (the held item's name).</li>
 * </ul>
 * Each class is all or nothing: a class whose shape differs is left as it is, with one line in the log.
 */
public final class NotificationTimeTransformer implements IClassTransformer {

    static final String PATCH = "P50";
    private static final String HOOK = "com/exiledradio/backportcore/presentation/client/NotificationTime";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if ("net.minecraft.client.gui.toasts.GuiToast$ToastInstance".equals(transformedName)) {
            return toasts(basicClass);
        }
        if ("net.minecraft.client.gui.GuiSubtitleOverlay".equals(transformedName)) {
            return subtitles(basicClass);
        }
        if ("net.minecraft.client.gui.GuiIngame".equals(transformedName)) {
            return highlight(basicClass);
        }
        return basicClass;
    }

    private static byte[] toasts(byte[] bytes) {
        String what = "notification time: toasts (GuiToast.ToastInstance.render)";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "render", "func_193684_a", "(II)Z");
        if (m == null) {
            Asm.skipped(PATCH + "-toasts", what, "method not found");
            return bytes;
        }
        List<MethodInsnNode> calls = Asm.calls(m, Opcodes.INVOKEINTERFACE, "net/minecraft/client/gui/toasts/IToast", "draw", "func_193653_a",
                "(Lnet/minecraft/client/gui/toasts/GuiToast;J)Lnet/minecraft/client/gui/toasts/IToast$Visibility;");
        if (calls.size() != 1) {
            Asm.skipped(PATCH + "-toasts", what, calls.size() + " draw calls");
            return bytes;
        }
        // The long on top of the stack right before the call is the toast's time.
        m.instructions.insertBefore(calls.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "toastTime", "(J)J", false));
        Asm.applied(PATCH + "-toasts", what);
        return Asm.write(node);
    }

    private static byte[] subtitles(byte[] bytes) {
        String what = "notification time: subtitles (GuiSubtitleOverlay.renderSubtitles)";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "renderSubtitles", "func_184068_a", "(Lnet/minecraft/client/gui/ScaledResolution;)V");
        if (m == null) {
            Asm.skipped(PATCH + "-subtitles", what, "method not found");
            return bytes;
        }
        List<LdcInsnNode> longs = Asm.constants(m, 3000L);
        List<LdcInsnNode> floats = Asm.constants(m, 3000.0F);
        if (longs.size() != 1 || floats.size() != 1) {
            Asm.skipped(PATCH + "-subtitles", what, longs.size() + " 3000L and " + floats.size() + " 3000.0F");
            return bytes;
        }
        m.instructions.insert(longs.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "subtitleTime", "(J)J", false));
        m.instructions.insert(floats.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "subtitleFade", "(F)F", false));
        Asm.applied(PATCH + "-subtitles", what);
        return Asm.write(node);
    }

    private static byte[] highlight(byte[] bytes) {
        String what = "notification time: held item name (GuiIngame.updateTick)";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "updateTick", "func_73831_a", "()V");
        if (m == null) {
            Asm.skipped(PATCH + "-highlight", what, "method not found");
            return bytes;
        }
        List<IntInsnNode> found = new ArrayList<IntInsnNode>();
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == 40) {
                AbstractInsnNode next = insn.getNext();
                if (next instanceof FieldInsnNode && next.getOpcode() == Opcodes.PUTFIELD
                        && Asm.named(((FieldInsnNode) next).name, "remainingHighlightTicks", "field_92017_k")) {
                    found.add((IntInsnNode) insn);
                }
            }
        }
        if (found.size() != 1) {
            Asm.skipped(PATCH + "-highlight", what, found.size() + " stores of 40");
            return bytes;
        }
        m.instructions.insert(found.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "highlightTicks", "(I)I", false));
        Asm.applied(PATCH + "-highlight", what);
        return Asm.write(node);
    }
}
