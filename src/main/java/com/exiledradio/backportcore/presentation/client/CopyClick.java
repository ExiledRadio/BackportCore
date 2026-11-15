package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.command.SeedCommand;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 1.15's {@code COPY_TO_CLIPBOARD} click action, which 1.12.2 lacks: a chat component's click that "runs"
 * {@link SeedCommand#COPY} puts the rest on the clipboard instead of sending anything (Forge raises its client chat
 * event before a clicked command is run or sent).
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
}
