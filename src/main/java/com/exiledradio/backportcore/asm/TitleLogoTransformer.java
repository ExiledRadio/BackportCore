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
 * Patch P29 (docs/PATCHES.md): the 1.20 title logo layout. In {@code GuiMainMenu.drawScreen} the logo's seven
 * {@code drawTexturedModalRect} calls become {@code PresentationHooks.titleLogoRect(menu, ...)}, the edition's
 * {@code drawModalRectWithCustomSizedTexture} becomes {@code titleEdition(...)}, and the splash's place (the
 * {@code 90} added to half the width and the {@code 70.0F}) goes through {@code splashX(I)I} and {@code splashY(F)F}.
 */
public final class TitleLogoTransformer implements IClassTransformer {

    static final String PATCH = "P29";
    private static final String WHAT = "title logo layout (GuiMainMenu.drawScreen)";
    private static final String TARGET = "net.minecraft.client.gui.GuiMainMenu";
    private static final String GUI = "net/minecraft/client/gui/Gui";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode draw = Asm.method(node, "drawScreen", "func_73863_a", "(IIF)V");
        if (draw == null) {
            Asm.skipped(PATCH, WHAT, "drawScreen(IIF)V not found");
            return basicClass;
        }
        List<MethodInsnNode> rects = Asm.calls(draw, Opcodes.INVOKEVIRTUAL, null, "drawTexturedModalRect", "func_73729_b", "(IIIIII)V");
        List<MethodInsnNode> edition = Asm.calls(draw, Opcodes.INVOKESTATIC, null, "drawModalRectWithCustomSizedTexture", "func_146110_a",
                "(IIFFIIFF)V");
        List<AbstractInsnNode> ninety = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : draw.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == 90 && insn.getNext() != null
                    && insn.getNext().getOpcode() == Opcodes.IADD) {
                ninety.add(insn);
            }
        }
        int seventy = Asm.constants(draw, 70.0F).size();
        if (rects.size() != 7 || edition.size() != 1 || ninety.size() != 1 || seventy != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + rects.size() + " logo pieces, " + edition.size() + " edition, "
                    + ninety.size() + "/" + seventy + " splash constants)");
            return basicClass;
        }
        for (MethodInsnNode call : rects) {
            draw.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "titleLogoRect", "(L" + GUI + ";IIIIII)V", false));
        }
        draw.instructions.set(edition.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "titleEdition", "(IIFFIIFF)V", false));
        draw.instructions.insert(ninety.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "splashX", "(I)I", false));
        Asm.wrapConstants(draw, 70.0F, "splashY", "(F)F");
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
