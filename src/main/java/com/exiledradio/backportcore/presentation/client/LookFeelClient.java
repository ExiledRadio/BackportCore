package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The Look &amp; Feel page's switches taking effect at once. Most parts ask their version every frame
 * ({@link Presentation#newest(LookArea)}); what was read once is read again here: the panorama choice, and the
 * resources (vanilla's replaced textures, its wording, the modern textures) when a part that serves them changed.
 */
@SideOnly(Side.CLIENT)
public final class LookFeelClient {

    /** For the self-tests: how many resource reloads a look change asked for. */
    public static volatile int reloads;

    private String state = state();
    private String resources = resources();

    /** @return every switch and the newest look, as one string */
    static String state() {
        StringBuilder b = new StringBuilder();
        b.append(CoreConfig.lookAndFeel.newerLook).append(' ').append(CoreConfig.lookAndFeel.newestLook);
        for (LookArea a : LookArea.values()) {
            b.append(' ').append(a.switchOn());
        }
        return b.toString();
    }

    /** @return what the resource packs serve: the versions of the parts that replace vanilla's files */
    static String resources() {
        StringBuilder b = new StringBuilder();
        for (LookArea a : new LookArea[] {LookArea.STARTUP, LookArea.TITLE, LookArea.MENUS, LookArea.TOASTS, LookArea.INVENTORY,
                LookArea.WORDING, LookArea.TEXTURES}) {
            b.append(Presentation.newest(a)).append(' ');
        }
        return b.append(CoreConfig.textures.modernTextures).toString();
    }

    // Post: Core's own handler syncs the fields on the On event.
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.PostConfigChangedEvent event) {
        if (!BackportCore.MOD_ID.equals(event.getModID())) {
            return;
        }
        String now = state();
        if (now.equals(this.state)) {
            return;
        }
        this.state = now;
        BackportCore.LOGGER.info("Look and feel: now {} ({})", Presentation.newest(), now);
        PanoramaRenderer.resetChoice();
        com.exiledradio.backportcore.client.ModernTextures.sync();
        String res = resources();
        if (!res.equals(this.resources)) {
            this.resources = res;
            reloads++;
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(mc::refreshResources);
        }
    }
}
