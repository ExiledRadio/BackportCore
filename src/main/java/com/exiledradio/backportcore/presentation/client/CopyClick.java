package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.command.SeedCommand;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 1.15's {@code COPY_TO_CLIPBOARD} click action, which 1.12.2 lacks. Two ways in:
 * <ul>
 *   <li>a chat component whose click is the added action ({@code presentation.CopyToClipboard}, from tellraw and the
 *       like): clicking it in the chat screen copies its value, as 1.15's {@code Screen.handleComponentClicked}
 *       (Shift still inserts, as for every click);</li>
 *   <li>Core's own /seed: a click that "runs" {@link SeedCommand#COPY} puts the rest on the clipboard instead of
 *       sending anything (Forge raises its client chat event before a clicked command is run or sent), which works
 *       even where the other side lacks the action.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class CopyClick {

    /** For the client self-test: the last text copied. */
    public static volatile String lastCopied;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChat(ClientChatEvent event) {
        String msg = event.getMessage();
        if (msg != null && msg.startsWith(SeedCommand.COPY)) {
            String text = msg.substring(SeedCommand.COPY.length());
            GuiScreen.setClipboardString(text);
            lastCopied = text;
            event.setMessage("");
        }
    }

    /** A left click on a copy_to_clipboard component in the chat screen, before 1.12.2's handler logs it as unknown. */
    @SubscribeEvent
    public void onMouse(net.minecraftforge.client.event.GuiScreenEvent.MouseInputEvent.Pre event) {
        net.minecraft.util.text.event.ClickEvent.Action copy = com.exiledradio.backportcore.presentation.CopyToClipboard.ACTION;
        if (copy == null || !(event.getGui() instanceof net.minecraft.client.gui.GuiChat) || org.lwjgl.input.Mouse.getEventButton() != 0
                || !org.lwjgl.input.Mouse.getEventButtonState() || GuiScreen.isShiftKeyDown()) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        net.minecraft.util.text.ITextComponent clicked = mc.ingameGUI.getChatGUI().getChatComponent(org.lwjgl.input.Mouse.getX(),
                org.lwjgl.input.Mouse.getY());
        String text = copied(clicked, copy);
        if (text != null) {
            GuiScreen.setClipboardString(text);
            lastCopied = text;
            event.setCanceled(true);
        }
    }

    /** @return the value to copy if the component's click is copy_to_clipboard, else null */
    public static String copied(net.minecraft.util.text.ITextComponent component, net.minecraft.util.text.event.ClickEvent.Action copy) {
        if (component == null || copy == null) {
            return null;
        }
        net.minecraft.util.text.event.ClickEvent click = component.getStyle().getClickEvent();
        return click != null && click.getAction() == copy ? click.getValue() : null;
    }
}
