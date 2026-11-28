package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Patch P78 (docs/PATCHES.md): 1.20.2's player preview in the inventory. The one {@code GuiInventory.drawEntityOnScreen}
 * call in {@code GuiInventory.drawGuiContainerBackgroundLayer} and in {@code GuiContainerCreative.drawGuiContainerBackgroundLayer}
 * goes through {@code PresentationHooks.inventoryPreview} and {@code creativePreview} with the same arguments, which draw
 * the player centered in its box and clipped to it ({@code InventoryPreview}) from 1.20.2 on, and call vanilla's
 * otherwise.
 */
public final class InventoryPreviewTransformer implements IClassTransformer {

    static final String PATCH = "P78";
    private static final String SURVIVAL = "net.minecraft.client.gui.inventory.GuiInventory";
    private static final String CREATIVE = "net.minecraft.client.gui.inventory.GuiContainerCreative";
    private static final String OWNER = "net/minecraft/client/gui/inventory/GuiInventory";
    private static final String DESC = "(IIIFFLnet/minecraft/entity/EntityLivingBase;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        if (SURVIVAL.equals(transformedName)) {
            return patch(basicClass, "player preview (GuiInventory)", "inventoryPreview");
        }
        if (CREATIVE.equals(transformedName)) {
            return patch(basicClass, "player preview (GuiContainerCreative)", "creativePreview");
        }
        return basicClass;
    }

    private static byte[] patch(byte[] basicClass, String what, String hook) {
        ClassNode node = Asm.read(basicClass);
        MethodNode background = Asm.method(node, "drawGuiContainerBackgroundLayer", "func_146976_a", "(FII)V");
        if (background == null) {
            Asm.skipped(PATCH, what, "drawGuiContainerBackgroundLayer(FII)V not found");
            return basicClass;
        }
        int swapped = Asm.swapCalls(background, Opcodes.INVOKESTATIC, OWNER, "drawEntityOnScreen", "func_147046_a", DESC, hook, DESC);
        if (swapped != 1) {
            Asm.skipped(PATCH, what, "unexpected shape (" + swapped + " drawEntityOnScreen calls)");
            return basicClass;
        }
        Asm.applied(PATCH, what);
        return Asm.write(node);
    }
}
