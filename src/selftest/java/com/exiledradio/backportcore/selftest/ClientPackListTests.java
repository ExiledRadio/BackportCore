package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.client.TexturesPackEntry;
import com.exiledradio.backportcore.presentation.LookArea;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryDefault;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Method;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * Core 1.4.3's "Backport Textures" row in vanilla's Resource Packs screen, last in the client suite: the screen opened in
 * code, the row's column and place checked, the row moved and Done pressed (through the screen's own click handling, no
 * real input), then the setting, the game's packs and the number of resource reloads checked. Twice: off on its own, and
 * on again together with a vanilla pack change, which must still read the resources only once. Screenshots go to
 * {@code screenshots/packs-*.png}.
 */
@SideOnly(Side.CLIENT)
final class ClientPackListTests {

    static boolean done;
    private static int stage;
    private static int ticks;
    private static int reloads;
    private static int reloadsBefore;
    private static int framesBefore;
    private static boolean listening;
    private static Dummy dummy;

    private ClientPackListTests() {
    }

    /** A row of no pack, to have a pack above the Backport Textures row. */
    private static final class Dummy extends ResourcePackListEntry {
        Dummy(GuiScreenResourcePacks screen) {
            super(screen);
        }

        boolean down() {
            return canMoveDown();
        }

        @Override
        protected int getResourcePackFormat() {
            return 3;
        }

        @Override
        protected String getResourcePackDescription() {
            return "self-test";
        }

        @Override
        protected String getResourcePackName() {
            return "Self-test row";
        }

        @Override
        protected void bindResourcePackIcon() {
            Minecraft.getMinecraft().getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_MISSING_TEXTURE);
        }
    }

    private static void shot(Minecraft mc, String name) {
        ScreenShotHelper.saveScreenshot(mc.gameDir, "packs-" + name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    /** From the presentation tests' tick, at the title screen; sets {@link #done} when finished. */
    static void tick(Minecraft mc) {
        ticks++;
        try {
            switch (stage) {
                case 0:
                    if (!listening) {
                        listening = true;
                        ((SimpleReloadableResourceManager) mc.getResourceManager()).registerReloadListener(m -> reloads++);
                    }
                    check("client packs: the textures are on to begin with", CoreConfig.textures.modernTextures && CoreConfig.lookAndFeel.newerLook);
                    mc.displayGuiScreen(new GuiScreenResourcePacks(new GuiMainMenu()));
                    stage = 1;
                    ticks = 0;
                    break;
                case 1:
                    if (ticks >= 10) {
                        GuiScreenResourcePacks screen = (GuiScreenResourcePacks) mc.currentScreen;
                        TexturesPackEntry entry = TexturesPackEntry.on(screen);
                        check("client packs: the screen has the Backport Textures row", entry != null);
                        check("client packs: the setting it stands for is found", TexturesPackEntry.setting() != null);
                        if (entry == null) {
                            finish();
                            return;
                        }
                        shot(mc, "on");
                        check("client packs: on, the row is selected", entry.selected());
                        check("client packs: selected, the row is just above Default", aboveDefault(screen, entry), screen.getSelectedResourcePacks());
                        // A pack above it: it offers no move down, and a swap past it is put back on the next frame.
                        dummy = new Dummy(screen);
                        List<ResourcePackListEntry> selected = screen.getSelectedResourcePacks();
                        selected.add(selected.indexOf(entry), dummy);
                        check("client packs: the pack above it shows no down arrow", !dummy.down());
                        selected.remove(entry);
                        selected.add(selected.indexOf(dummy), entry);
                        framesBefore = TexturesPackEntry.frames;
                        stage = 2;
                        ticks = 0;
                    }
                    break;
                case 2:
                    if (TexturesPackEntry.frames >= framesBefore + 3 || ticks >= 100) {
                        GuiScreenResourcePacks screen = (GuiScreenResourcePacks) mc.currentScreen;
                        TexturesPackEntry entry = TexturesPackEntry.on(screen);
                        check("client packs: moved above another pack, it is back above Default", aboveDefault(screen, entry),
                                (TexturesPackEntry.frames - framesBefore) + " frames, " + screen.getSelectedResourcePacks());
                        screen.getSelectedResourcePacks().remove(dummy);
                        entry.click(true);
                        check("client packs: its left arrow moves it to the available column", !entry.selected()
                                && screen.getAvailableResourcePacks().contains(entry));
                        screen.setWorldAndResolution(mc, screen.width, screen.height);
                        check("client packs: a resize keeps it available, once", !entry.selected() && count(screen, entry) == 1);
                        check("client packs: not saved before Done", CoreConfig.textures.modernTextures);
                        shot(mc, "moved-off");
                        reloadsBefore = reloads;
                        done(mc, screen);
                        stage = 3;
                        ticks = 0;
                    }
                    break;
                case 3:
                    if (ticks >= 30) {
                        check("client packs: Done turned the setting off", !CoreConfig.textures.modernTextures);
                        check("client packs: and saved it", "false".equals(TexturesPackEntry.setting().get()[0]),
                                TexturesPackEntry.setting().get()[0]);
                        check("client packs: the Look & Feel page's Textures switch agrees", !LookArea.TEXTURES.switchOn());
                        check("client packs: the game's packs switched to 1.12.2's textures", !hasPack(mc, "modern") && hasPack(mc, "classic"));
                        check("client packs: the resources were read once", reloads - reloadsBefore == 1, reloads - reloadsBefore);
                        check("client packs: Done went back", mc.currentScreen instanceof GuiMainMenu, mc.currentScreen);
                        mc.displayGuiScreen(new GuiScreenResourcePacks(new GuiMainMenu()));
                        stage = 4;
                        ticks = 0;
                    }
                    break;
                case 4:
                    if (ticks >= 10) {
                        GuiScreenResourcePacks screen = (GuiScreenResourcePacks) mc.currentScreen;
                        TexturesPackEntry entry = TexturesPackEntry.on(screen);
                        check("client packs: off, the row is available", entry != null && !entry.selected()
                                && screen.getAvailableResourcePacks().contains(entry));
                        shot(mc, "off");
                        entry.click(false);
                        check("client packs: its right arrow puts it just above Default", entry.selected() && aboveDefault(screen, entry),
                                screen.getSelectedResourcePacks());
                        // As if a pack moved too: vanilla's Done saves its list and reloads, and that one reload covers both.
                        screen.markChanged();
                        reloadsBefore = reloads;
                        done(mc, screen);
                        stage = 5;
                        ticks = 0;
                    }
                    break;
                case 5:
                    if (ticks >= 30) {
                        check("client packs: Done turned the setting back on", CoreConfig.textures.modernTextures
                                && "true".equals(TexturesPackEntry.setting().get()[0]));
                        check("client packs: the Look & Feel page's Textures switch agrees again", LookArea.TEXTURES.switchOn());
                        check("client packs: the game's packs have the modern textures again", hasPack(mc, "modern") && !hasPack(mc, "classic"));
                        check("client packs: with a vanilla change too, the resources were read once", reloads - reloadsBefore == 1,
                                reloads - reloadsBefore);
                        finish();
                    }
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            SelfTestRunner.fail("client packs: stage " + stage + " threw", t.toString());
            BackportCore.LOGGER.error("client packs stage {}", stage, t);
            CoreConfig.textures.modernTextures = true;
            mc.displayGuiScreen(new GuiMainMenu());
            finish();
        }
    }

    private static void finish() {
        stage = 99;
        done = true;
    }

    private static boolean aboveDefault(GuiScreenResourcePacks screen, TexturesPackEntry entry) {
        List<ResourcePackListEntry> selected = screen.getSelectedResourcePacks();
        int i = selected.indexOf(entry);
        return i >= 0 && i + 1 < selected.size() && selected.get(i + 1) instanceof ResourcePackListEntryDefault;
    }

    private static int count(GuiScreenResourcePacks screen, TexturesPackEntry entry) {
        int n = 0;
        for (ResourcePackListEntry e : screen.getAvailableResourcePacks()) {
            n += e == entry ? 1 : 0;
        }
        for (ResourcePackListEntry e : screen.getSelectedResourcePacks()) {
            n += e == entry ? 1 : 0;
        }
        return n;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasPack(Minecraft mc, String name) throws Exception {
        List<IResourcePack> packs = (List<IResourcePack>) ReflectionHelper.findField(Minecraft.class, "defaultResourcePacks", "field_110449_ao").get(mc);
        for (IResourcePack p : packs) {
            if ((BackportCore.MOD_ID + ":" + name).equals(p.getPackName())) {
                return true;
            }
        }
        return false;
    }

    /** Clicks the screen's Done button through its own mouse handling, so Forge's button events fire as for a player. */
    @SuppressWarnings("unchecked")
    private static void done(Minecraft mc, GuiScreenResourcePacks screen) throws Exception {
        List<GuiButton> buttons = (List<GuiButton>) ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n").get(screen);
        GuiButton doneButton = null;
        for (GuiButton b : buttons) {
            if (b.id == 1) {
                doneButton = b;
            }
        }
        check("client packs: the screen has its Done button", doneButton != null);
        if (doneButton == null) {
            return;
        }
        Method click = ReflectionHelper.findMethod(GuiScreen.class, "mouseClicked", "func_73864_a", int.class, int.class, int.class);
        click.invoke(screen, doneButton.x + 2, doneButton.y + 2, 0);
    }
}
