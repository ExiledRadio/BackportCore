package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Patches P12 to P16 (docs/PATCHES.md): the presentation module's owner swaps on the client's screens and sky.
 * Every one only changes who answers an existing call or constant (an instance call becomes a static call with the
 * same arguments; a constant passes through a static call of the same type), so no local is read and the method's
 * stack map frames stay as Mojang's or javac's wrote them. Each hook answers exactly what vanilla 1.12.2 does until
 * an installed version changes that piece.
 * <ul>
 *   <li>P12 {@code GuiButton.drawButton} and Forge's {@code GuiButtonExt.drawButton}: the label colors
 *       {@code 14737632} (normal) and {@code 16777120} (hovered) pass through {@code buttonTextColor} (1.15: white).</li>
 *   <li>P13 {@code GuiIngame.renderGameOverlay} and Forge's {@code GuiIngameForge.renderChat}: the chat's
 *       {@code drawChat(updateCounter)} call becomes {@code PresentationHooks.drawChat(chat, updateCounter)}.</li>
 *   <li>P14 {@code GuiChat}: both {@code getChatComponent(x, y)} calls (click and hover) go through the hook, and the
 *       input box's color ({@code Integer.MIN_VALUE} in {@code drawScreen}) through {@code chatBoxColor}.</li>
 *   <li>P15 {@code RenderGlobal}: {@code renderSky}'s {@code world.getSkyColor(entity, partialTicks)} and the cloud
 *       passes' two {@code provider.getCloudHeight()} calls go through the fog pipeline's sky and cloud steps.</li>
 *   <li>P16 {@code EntityRenderer}: {@code updateFogColor}'s {@code getSkyColor} call and {@code renderWorldPass}'s two
 *       {@code 128.0D} cloud-layer comparisons (whether the camera is above the clouds) follow the same steps.</li>
 *   <li>P61 {@code GuiTextField.drawTextBox}: the frame's color ({@code -6250336}) passes through
 *       {@code textFieldFrame(field, color)} (1.16: white while focused).</li>
 *   <li>P62 the sliders' {@code mouseDragged} ({@code GuiOptionSlider}, {@code GuiSlider}, the sound screen's
 *       {@code GuiScreenOptionsSounds$Button}, Forge's {@code GuiSlider}): the handle's texture row ({@code 66}) passes
 *       through {@code sliderHandleRow(button, row)} (1.14: the hovered button's row, 86, under the mouse).</li>
 * </ul>
 * P61 and P62 load {@code this} before the constant for their hook; that adds a stack slot, never a local.
 * A class whose shape is not exactly the expected one is left unchanged (logged), never half-patched.
 */
public final class ScreenPatchesTransformer implements IClassTransformer {

    private static final String GUI_NEW_CHAT = "net/minecraft/client/gui/GuiNewChat";
    private static final String COMPONENT = "Lnet/minecraft/util/text/ITextComponent;";
    private static final String SKY_DESC = "(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/math/Vec3d;";
    private static final String SKY_HOOK = "(Lnet/minecraft/util/math/Vec3d;F)Lnet/minecraft/util/math/Vec3d;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) {
            return basicClass;
        }
        switch (transformedName) {
            case "net.minecraft.client.gui.GuiButton":
            case "net.minecraftforge.fml.client.config.GuiButtonExt":
                return buttons(transformedName, basicClass);
            case "net.minecraft.client.gui.GuiIngame":
                return chatRender(basicClass, "renderGameOverlay", "func_175180_a", "(F)V", "GuiIngame.renderGameOverlay");
            case "net.minecraftforge.client.GuiIngameForge":
                return chatRender(basicClass, "renderChat", "renderChat", "(II)V", "GuiIngameForge.renderChat");
            case "net.minecraft.client.gui.GuiChat":
                return chatScreen(basicClass);
            case "net.minecraft.client.renderer.RenderGlobal":
                return renderGlobal(basicClass);
            case "net.minecraft.client.renderer.EntityRenderer":
                return entityRenderer(basicClass);
            case "net.minecraft.client.gui.GuiTextField":
                return textField(basicClass);
            case "net.minecraft.client.gui.GuiOptionSlider":
            case "net.minecraft.client.gui.GuiSlider":
            case "net.minecraft.client.gui.GuiScreenOptionsSounds$Button":
                return slider(transformedName, basicClass, 2);
            case "net.minecraftforge.fml.client.config.GuiSlider":
                return slider(transformedName, basicClass, 1);
            default:
                return basicClass;
        }
    }

    private static byte[] buttons(String cls, byte[] bytes) {
        String what = "button label colors (" + cls.substring(cls.lastIndexOf('.') + 1) + ".drawButton)";
        ClassNode node = Asm.read(bytes);
        MethodNode draw = Asm.method(node, "drawButton", "func_191745_a", "(Lnet/minecraft/client/Minecraft;IIF)V");
        if (draw == null) {
            Asm.skipped("P12", what, "drawButton not found");
            return bytes;
        }
        int normal = Asm.constants(draw, 14737632).size();
        int hovered = Asm.constants(draw, 16777120).size();
        if (normal != 1 || hovered != 1) {
            Asm.skipped("P12", what, "unexpected shape (" + normal + " normal, " + hovered + " hovered color constants)");
            return bytes;
        }
        Asm.wrapConstants(draw, 14737632, "buttonTextColor", "(I)I");
        Asm.wrapConstants(draw, 16777120, "buttonTextColor", "(I)I");
        Asm.applied("P12", what);
        return Asm.write(node);
    }

    private static byte[] chatRender(byte[] bytes, String mcp, String srg, String desc, String what) {
        what = "chat drawing (" + what + ")";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, mcp, srg, desc);
        if (m == null) {
            Asm.skipped("P13", what, "method not found");
            return bytes;
        }
        int n = Asm.calls(m, Opcodes.INVOKEVIRTUAL, GUI_NEW_CHAT, "drawChat", "func_146230_a", "(I)V").size();
        if (n != 1) {
            Asm.skipped("P13", what, "unexpected shape (" + n + " drawChat calls)");
            return bytes;
        }
        Asm.swapCalls(m, Opcodes.INVOKEVIRTUAL, GUI_NEW_CHAT, "drawChat", "func_146230_a", "(I)V", "drawChat", "(L" + GUI_NEW_CHAT + ";I)V");
        Asm.applied("P13", what);
        return Asm.write(node);
    }

    private static byte[] chatScreen(byte[] bytes) {
        String what = "chat screen (GuiChat)";
        ClassNode node = Asm.read(bytes);
        MethodNode draw = Asm.method(node, "drawScreen", "func_73863_a", "(IIF)V");
        MethodNode click = Asm.method(node, "mouseClicked", "func_73864_a", "(III)V");
        if (draw == null || click == null) {
            Asm.skipped("P14", what, "drawScreen or mouseClicked not found");
            return bytes;
        }
        String compDesc = "(II)" + COMPONENT;
        int inDraw = Asm.calls(draw, Opcodes.INVOKEVIRTUAL, GUI_NEW_CHAT, "getChatComponent", "func_146236_a", compDesc).size();
        int inClick = Asm.calls(click, Opcodes.INVOKEVIRTUAL, GUI_NEW_CHAT, "getChatComponent", "func_146236_a", compDesc).size();
        int box = Asm.constants(draw, Integer.MIN_VALUE).size();
        if (inDraw != 1 || inClick != 1 || box != 1) {
            Asm.skipped("P14", what, "unexpected shape (" + inDraw + "/" + inClick + " getChatComponent calls, " + box + " box colors)");
            return bytes;
        }
        String hook = "(L" + GUI_NEW_CHAT + ";II)" + COMPONENT;
        Asm.swapCalls(draw, Opcodes.INVOKEVIRTUAL, GUI_NEW_CHAT, "getChatComponent", "func_146236_a", compDesc, "chatComponent", hook);
        Asm.swapCalls(click, Opcodes.INVOKEVIRTUAL, GUI_NEW_CHAT, "getChatComponent", "func_146236_a", compDesc, "chatComponent", hook);
        Asm.wrapConstants(draw, Integer.MIN_VALUE, "chatBoxColor", "(I)I");
        Asm.applied("P14", what);
        return Asm.write(node);
    }

    private static int skyCalls(MethodNode m) {
        return Asm.calls(m, Opcodes.INVOKEVIRTUAL, null, "getSkyColor", "func_72833_a", SKY_DESC).size();
    }

    private static int swapSky(MethodNode m) {
        // Wrapped after the call (renderSky's and updateFogColor's float partialTicks is local 1), so mixins keep it.
        return Asm.wrapCalls(m, Opcodes.INVOKEVIRTUAL, null, "getSkyColor", "func_72833_a", SKY_DESC, "skyColor", SKY_HOOK, 1);
    }

    private static byte[] renderGlobal(byte[] bytes) {
        String what = "sky color and cloud height (RenderGlobal)";
        ClassNode node = Asm.read(bytes);
        MethodNode sky = Asm.method(node, "renderSky", "func_174976_a", "(FI)V");
        MethodNode clouds = Asm.method(node, "renderClouds", "func_180447_b", "(FIDDD)V");
        MethodNode fancy = Asm.method(node, "renderCloudsFancy", "func_180445_c", "(FIDDD)V");
        if (sky == null || clouds == null || fancy == null) {
            Asm.skipped("P15", what, "renderSky, renderClouds or renderCloudsFancy not found");
            return bytes;
        }
        String provider = "net/minecraft/world/WorldProvider";
        int s = skyCalls(sky);
        int c1 = Asm.calls(clouds, Opcodes.INVOKEVIRTUAL, provider, "getCloudHeight", "func_76571_f", "()F").size();
        int c2 = Asm.calls(fancy, Opcodes.INVOKEVIRTUAL, provider, "getCloudHeight", "func_76571_f", "()F").size();
        if (s != 1 || c1 != 1 || c2 != 1) {
            Asm.skipped("P15", what, "unexpected shape (" + s + " getSkyColor, " + c1 + "+" + c2 + " getCloudHeight calls)");
            return bytes;
        }
        swapSky(sky);
        for (MethodNode m : new MethodNode[] {clouds, fancy}) {
            Asm.wrapCalls(m, Opcodes.INVOKEVIRTUAL, provider, "getCloudHeight", "func_76571_f", "()F", "cloudHeight", "(F)F");
        }
        Asm.applied("P15", what);
        return Asm.write(node);
    }

    private static byte[] entityRenderer(byte[] bytes) {
        String what = "fog's sky color and the cloud layer (EntityRenderer)";
        ClassNode node = Asm.read(bytes);
        MethodNode fog = Asm.method(node, "updateFogColor", "func_78466_h", "(F)V");
        MethodNode pass = Asm.method(node, "renderWorldPass", "func_175068_a", "(IFJ)V");
        if (fog == null || pass == null) {
            Asm.skipped("P16", what, "updateFogColor or renderWorldPass not found");
            return bytes;
        }
        int s = skyCalls(fog);
        int layer = Asm.constants(pass, 128.0D).size();
        if (s != 1 || layer != 2) {
            Asm.skipped("P16", what, "unexpected shape (" + s + " getSkyColor calls, " + layer + " cloud-layer constants)");
            return bytes;
        }
        swapSky(fog);
        Asm.wrapConstants(pass, 128.0D, "cloudLayer", "(D)D");
        Asm.applied("P16", what);
        return Asm.write(node);
    }

    private static final String GUI_BUTTON = "net/minecraft/client/gui/GuiButton";
    private static final String GUI_TEXT_FIELD = "net/minecraft/client/gui/GuiTextField";

    private static byte[] textField(byte[] bytes) {
        String what = "text field frame (GuiTextField.drawTextBox)";
        ClassNode node = Asm.read(bytes);
        MethodNode draw = Asm.method(node, "drawTextBox", "func_146194_f", "()V");
        if (draw == null) {
            Asm.skipped("P61", what, "drawTextBox not found");
            return bytes;
        }
        java.util.List<org.objectweb.asm.tree.LdcInsnNode> frame = Asm.constants(draw, -6250336);
        if (frame.size() != 1) {
            Asm.skipped("P61", what, "unexpected shape (" + frame.size() + " frame colors)");
            return bytes;
        }
        withThis(draw, frame.get(0), "textFieldFrame", "(L" + GUI_TEXT_FIELD + ";I)I");
        Asm.applied("P61", what);
        return Asm.write(node);
    }

    private static byte[] slider(String cls, byte[] bytes, int expected) {
        String what = "slider handle (" + cls.substring(cls.lastIndexOf('.') + 1) + ".mouseDragged)";
        ClassNode node = Asm.read(bytes);
        MethodNode drag = Asm.method(node, "mouseDragged", "func_146119_b", "(Lnet/minecraft/client/Minecraft;II)V");
        if (drag == null) {
            Asm.skipped("P62", what, "mouseDragged not found");
            return bytes;
        }
        java.util.List<org.objectweb.asm.tree.AbstractInsnNode> rows = new java.util.ArrayList<org.objectweb.asm.tree.AbstractInsnNode>();
        for (org.objectweb.asm.tree.AbstractInsnNode insn = drag.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.BIPUSH && ((org.objectweb.asm.tree.IntInsnNode) insn).operand == 66) {
                rows.add(insn);
            }
        }
        if (rows.size() != expected) {
            Asm.skipped("P62", what, "unexpected shape (" + rows.size() + " handle rows)");
            return bytes;
        }
        for (org.objectweb.asm.tree.AbstractInsnNode row : rows) {
            withThis(drag, row, "sliderHandleRow", "(L" + GUI_BUTTON + ";I)I");
        }
        Asm.applied("P62", what);
        return Asm.write(node);
    }

    /** {@code this} before the int constant, the hook after it: the constant becomes {@code hook(this, constant)}. */
    private static void withThis(MethodNode m, org.objectweb.asm.tree.AbstractInsnNode constant, String hook, String desc) {
        m.instructions.insertBefore(constant, new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.insert(constant, new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, hook, desc, false));
    }
}
