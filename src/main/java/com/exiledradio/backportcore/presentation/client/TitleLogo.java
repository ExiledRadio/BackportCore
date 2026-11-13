package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * The title screen's logo in the layout of the version that drew its textures ({@link VanillaRevisions.TitleLogo}),
 * through patch P29. 1.20's layout is drawn when the newest installed version that replaces {@code minecraft.png}
 * is 1.20 or later and it replaces {@code edition.png} too (its textures are then in 1.20's format), and no resource
 * pack of the player's replaces {@code minecraft.png} over Core's (a pack is drawn the 1.12.2 way, as it would be).
 * Minceraft, one start in ten thousand, is that version's {@code minceraft.png} when it has one.
 */
@SideOnly(Side.CLIENT)
public final class TitleLogo {

    static final String LOGO = "textures/gui/title/minecraft.png";
    static final String EDITION = "textures/gui/title/edition.png";
    static final String MINCERAFT = "textures/gui/title/minceraft.png";

    /** For the client self-test: frames drawn in 1.20's layout. */
    public static volatile int frames;

    @Nullable
    private static GuiMainMenu menu;
    private static VanillaRevisions.TitleLogo cached;

    private TitleLogo() {
    }

    /** @return the version whose contribution replaces vanilla asset {@code path} (the newest), or null */
    @Nullable
    static GameVersion sourceVersion(String path) {
        GameVersion found = null;
        for (Contribution c : Presentation.installed()) {
            if (c.replacedAssets().containsKey(path)) {
                found = c.version();
            }
        }
        return found;
    }

    /** @return the layout to draw now (worked out again after each resource load) */
    public static VanillaRevisions.TitleLogo layout() {
        if (cached == null) {
            cached = compute(Minecraft.getMinecraft());
        }
        return cached;
    }

    /** A resource load ended: the player's packs may have changed. */
    static void invalidate() {
        cached = null;
    }

    private static VanillaRevisions.TitleLogo compute(Minecraft mc) {
        GameVersion logo = sourceVersion(LOGO);
        GameVersion edition = sourceVersion(EDITION);
        if (logo == null || edition == null || VanillaRevisions.TitleLogo.forVersion(logo) != VanillaRevisions.TitleLogo.LOGO_1_20
                || VanillaRevisions.TitleLogo.forVersion(edition) != VanillaRevisions.TitleLogo.LOGO_1_20) {
            return VanillaRevisions.TitleLogo.CLASSIC;
        }
        try (IResource r = mc.getResourceManager().getResource(new ResourceLocation(LOGO))) {
            if (!(com.exiledradio.backportcore.BackportCore.MOD_ID + ":vanilla_by_version").equals(r.getResourcePackName())) {
                return VanillaRevisions.TitleLogo.CLASSIC;
            }
        } catch (Exception e) {
            return VanillaRevisions.TitleLogo.CLASSIC;
        }
        return VanillaRevisions.TitleLogo.LOGO_1_20;
    }

    private static boolean modern() {
        return layout() == VanillaRevisions.TitleLogo.LOGO_1_20;
    }

    /** P29: one of the logo's pieces. In 1.20's layout the first draws the whole logo and the others nothing. */
    static void rect(Gui gui, int x, int y, int u, int v, int w, int h) {
        if (!(gui instanceof GuiMainMenu) || !modern()) {
            gui.drawTexturedModalRect(x, y, u, v, w, h);
            return;
        }
        GuiMainMenu m = (GuiMainMenu) gui;
        if (u != 0 || v != 0) {
            return;
        }
        // The first piece: 99 wide when this start rolled Minceraft (1.12.2's roll stands in for 1.20's own).
        menu = m;
        Minecraft mc = Minecraft.getMinecraft();
        boolean minceraft = w == 99 && sourceVersion(MINCERAFT) != null;
        mc.getTextureManager().bindTexture(new ResourceLocation(minceraft ? MINCERAFT : LOGO));
        Gui.drawModalRectWithCustomSizedTexture(m.width / 2 - 128, y, 0.0F, 0.0F, 256, 44, 256.0F, 64.0F);
        frames++;
    }

    /** P29: "Java Edition". In 1.20's layout, {@code edition.png}'s 128 by 14 at width / 2 - 64, 37 below the logo's top. */
    static void edition(int x, int y, float u, float v, int w, int h, float tw, float th) {
        if (menu == null || !modern()) {
            Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, w, h, tw, th);
            return;
        }
        Gui.drawModalRectWithCustomSizedTexture(menu.width / 2 - 64, 30 + 44 - 7, 0.0F, 0.0F, 128, 14, 128.0F, 16.0F);
    }

    /** P29: the splash's x is half the width plus 90 in 1.12.2, plus 123 in 1.20. */
    public static int splashX(int vanilla) {
        return modern() ? 123 : vanilla;
    }

    /** P29: the splash's y is 70 in 1.12.2, 69 in 1.20. */
    public static float splashY(float vanilla) {
        return modern() ? 69.0F : vanilla;
    }
}
