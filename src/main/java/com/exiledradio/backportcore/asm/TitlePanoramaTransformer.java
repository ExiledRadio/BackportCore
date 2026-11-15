package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Patch P6 (docs/PATCHES.md): the title screen draws the newest installed version's panorama the way 1.13 and
 * later draw it (sharp, no blur, a slow full turn), and 1.13's overlay in place of 1.12.2's two gradients.
 *
 * <p>Three call sites in {@code GuiMainMenu.drawScreen} change owner, nothing else: its one
 * {@code renderSkybox(mouseX, mouseY, partialTicks)} call and its two {@code drawGradientRect} calls become
 * static calls into {@code presentation.client.PresentationHooks}, with the menu as the first argument. The
 * hooks call vanilla's own methods when no backport is installed, so the title screen is 1.12.2's.
 */
public final class TitlePanoramaTransformer implements IClassTransformer {

    static final String PATCH = "P6";
    private static final String WHAT = "title screen panorama (GuiMainMenu.drawScreen)";
    private static final String TARGET = "net.minecraft.client.gui.GuiMainMenu";
    private static final String MENU = "net/minecraft/client/gui/GuiMainMenu";

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
        List<MethodInsnNode> skybox = Asm.privateCalls(draw, MENU, "renderSkybox", "func_73971_c", "(IIF)V");
        List<MethodInsnNode> gradients = Asm.calls(draw, Opcodes.INVOKEVIRTUAL, null, "drawGradientRect", "func_73733_a", "(IIIIII)V");
        if (skybox.size() != 1 || gradients.size() != 2) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + skybox.size() + " renderSkybox, " + gradients.size() + " drawGradientRect calls)");
            return basicClass;
        }
        // Same arguments on the stack, the menu included; the hook takes the menu as its first parameter.
        draw.instructions.set(skybox.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "renderSkybox",
                "(L" + MENU + ";IIF)V", false));
        for (MethodInsnNode g : gradients) {
            draw.instructions.set(g, new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "drawGradient",
                    "(L" + MENU + ";IIIIII)V", false));
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
