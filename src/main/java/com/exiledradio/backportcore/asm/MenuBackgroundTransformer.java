package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Patches P31 and P32 (docs/PATCHES.md). Each only changes who answers an existing call (an instance call becomes a
 * static call with the same arguments, the receiver first; a static call another static call), so no local is read and
 * the stack map frames stay as they were.
 * <ul>
 *   <li>P31, 1.20.5's menu backgrounds ({@code presentation.client.MenuBackground}):
 *     <ul>
 *       <li>{@code GuiScreen.drawBackground(I)V}: its one {@code tessellator.draw()} (the dirt) becomes
 *           {@code PresentationHooks.menuDirt(tessellator)};</li>
 *       <li>{@code GuiScreen.drawWorldBackground(I)V}: its one {@code drawGradientRect} (the gradient over the world)
 *           becomes {@code menuWorldGradient(screen, ...)};</li>
 *       <li>{@code GuiSlot.drawScreen(IIF)V}: its one {@code drawContainerBackground(tessellator)}, its two
 *           {@code overlayBackground(...)} and its one {@code getMaxScroll()} become {@code listBackground},
 *           {@code listOverlay} and {@code listMaxScroll}.</li>
 *     </ul>
 *     GuiScreen and GuiSlot are patched apart: either can be skipped and the other still applies.</li>
 *   <li>P32, 1.20.2's Narrator Hotkey: {@code Minecraft.dispatchKeypresses()V}'s one {@code GuiScreen.isCtrlKeyDown()}
 *       (the narrator's Ctrl + B) becomes {@code PresentationHooks.narratorCtrl()}.</li>
 * </ul>
 */
public final class MenuBackgroundTransformer implements IClassTransformer {

    static final String MENU = "P31";
    static final String NARRATOR = "P32";
    private static final String SCREEN = "net/minecraft/client/gui/GuiScreen";
    private static final String SLOT = "net/minecraft/client/gui/GuiSlot";
    private static final String TESSELLATOR = "net/minecraft/client/renderer/Tessellator";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) {
            return basicClass;
        }
        switch (transformedName) {
            case "net.minecraft.client.gui.GuiScreen":
                return screen(basicClass);
            case "net.minecraft.client.gui.GuiSlot":
                return slot(basicClass);
            case "net.minecraft.client.Minecraft":
                return minecraft(basicClass);
            default:
                return basicClass;
        }
    }

    private static byte[] screen(byte[] bytes) {
        String what = "menu backgrounds (GuiScreen.drawBackground, drawWorldBackground)";
        ClassNode node = Asm.read(bytes);
        MethodNode dirt = Asm.method(node, "drawBackground", "func_146278_c", "(I)V");
        MethodNode world = Asm.method(node, "drawWorldBackground", "func_146270_b", "(I)V");
        if (dirt == null || world == null) {
            Asm.skipped(MENU, what, "drawBackground or drawWorldBackground not found");
            return bytes;
        }
        int draws = Asm.calls(dirt, Opcodes.INVOKEVIRTUAL, TESSELLATOR, "draw", "func_78381_a", "()V").size();
        int gradients = Asm.calls(world, Opcodes.INVOKEVIRTUAL, null, "drawGradientRect", "func_73733_a", "(IIIIII)V").size();
        if (draws != 1 || gradients != 1) {
            Asm.skipped(MENU, what, "unexpected shape (" + draws + " tessellator draws, " + gradients + " gradients)");
            return bytes;
        }
        Asm.swapCalls(dirt, Opcodes.INVOKEVIRTUAL, TESSELLATOR, "draw", "func_78381_a", "()V", "menuDirt", "(L" + TESSELLATOR + ";)V");
        Asm.swapCalls(world, Opcodes.INVOKEVIRTUAL, null, "drawGradientRect", "func_73733_a", "(IIIIII)V", "menuWorldGradient",
                "(L" + SCREEN + ";IIIIII)V");
        Asm.applied(MENU, what);
        return Asm.write(node);
    }

    private static byte[] slot(byte[] bytes) {
        String what = "list backgrounds (GuiSlot.drawScreen)";
        ClassNode node = Asm.read(bytes);
        MethodNode draw = Asm.method(node, "drawScreen", "func_148128_a", "(IIF)V");
        if (draw == null) {
            Asm.skipped(MENU + "-list", what, "drawScreen(IIF)V not found");
            return bytes;
        }
        String containerDesc = "(L" + TESSELLATOR + ";)V";
        // Forge's drawContainerBackground keeps its name in production.
        int container = Asm.calls(draw, Opcodes.INVOKEVIRTUAL, SLOT, "drawContainerBackground", "drawContainerBackground", containerDesc).size();
        int overlays = Asm.calls(draw, Opcodes.INVOKEVIRTUAL, SLOT, "overlayBackground", "func_148136_c", "(IIII)V").size();
        int maxScroll = Asm.calls(draw, Opcodes.INVOKEVIRTUAL, SLOT, "getMaxScroll", "func_148135_f", "()I").size();
        if (container != 1 || overlays != 2 || maxScroll != 1) {
            Asm.skipped(MENU + "-list", what, "unexpected shape (" + container + " container backgrounds, " + overlays + " overlays, "
                    + maxScroll + " getMaxScroll calls)");
            return bytes;
        }
        Asm.swapCalls(draw, Opcodes.INVOKEVIRTUAL, SLOT, "drawContainerBackground", "drawContainerBackground", containerDesc,
                "listBackground", "(L" + SLOT + ";L" + TESSELLATOR + ";)V");
        Asm.swapCalls(draw, Opcodes.INVOKEVIRTUAL, SLOT, "overlayBackground", "func_148136_c", "(IIII)V", "listOverlay",
                "(L" + SLOT + ";IIII)V");
        Asm.swapCalls(draw, Opcodes.INVOKEVIRTUAL, SLOT, "getMaxScroll", "func_148135_f", "()I", "listMaxScroll", "(L" + SLOT + ";)I");
        Asm.applied(MENU + "-list", what);
        return Asm.write(node);
    }

    private static byte[] minecraft(byte[] bytes) {
        String what = "Narrator Hotkey (Minecraft.dispatchKeypresses)";
        ClassNode node = Asm.read(bytes);
        MethodNode keys = Asm.method(node, "dispatchKeypresses", "func_152348_aa", "()V");
        if (keys == null) {
            Asm.skipped(NARRATOR, what, "dispatchKeypresses()V not found");
            return bytes;
        }
        int ctrl = Asm.calls(keys, Opcodes.INVOKESTATIC, SCREEN, "isCtrlKeyDown", "func_146271_m", "()Z").size();
        if (ctrl != 1) {
            Asm.skipped(NARRATOR, what, "unexpected shape (" + ctrl + " isCtrlKeyDown calls)");
            return bytes;
        }
        Asm.swapCalls(keys, Opcodes.INVOKESTATIC, SCREEN, "isCtrlKeyDown", "func_146271_m", "()Z", "narratorCtrl", "()Z");
        Asm.applied(NARRATOR, what);
        return Asm.write(node);
    }
}
