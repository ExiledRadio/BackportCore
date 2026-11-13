package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Patch P23 (docs/PATCHES.md): 1.21.2's slot highlight. {@code GuiContainer.drawScreen}'s white square over the
 * hovered slot ({@code drawGradientRect}) goes through {@code PresentationHooks.slotHighlight}, which draws 1.21.2's
 * two sprites instead once a backport of 1.21.2 or later supplies them: {@code slot_highlight_back} behind the item
 * (by depth), {@code slot_highlight_front} over it, so the item stays visible.
 */
public final class SlotHighlightTransformer implements IClassTransformer {

    static final String PATCH = "P23";
    private static final String WHAT = "slot highlight (GuiContainer.drawScreen)";
    private static final String TARGET = "net.minecraft.client.gui.inventory.GuiContainer";

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
        int swapped = Asm.swapCalls(draw, Opcodes.INVOKEVIRTUAL, node.name, "drawGradientRect", "func_73733_a", "(IIIIII)V",
                "slotHighlight", "(Lnet/minecraft/client/gui/inventory/GuiContainer;IIIIII)V");
        if (swapped != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + swapped + " gradient calls)");
            return basicClass;
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
