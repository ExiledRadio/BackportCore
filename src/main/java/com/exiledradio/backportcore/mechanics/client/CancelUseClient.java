package com.exiledradio.backportcore.mechanics.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.mechanics.CancelUse;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 26.4 Pre-Release 1's {@code Minecraft.pauseGame}: opening the pause menu while using an item cancels the use. In
 * 1.12.2 the use key reads as up once the menu closes, so a drawn bow fired its arrow. With a 26.4 backport installed.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID, value = Side.CLIENT)
public final class CancelUseClient {

    private CancelUseClient() {
    }

    @SubscribeEvent
    public static void open(GuiOpenEvent event) {
        if (!(event.getGui() instanceof GuiIngameMenu) || !Presentation.installedNewest().isAtLeast("26.4")) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null && player.isHandActive()) {
            player.resetActiveHand();
            CancelUse.send();
        }
    }
}
