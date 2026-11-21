package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.presentation.GraphicsPreset;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.client.DebugBindings;
import com.exiledradio.backportcore.presentation.client.DebugEntries;
import com.exiledradio.backportcore.presentation.client.DebugOptionsScreen;
import com.exiledradio.backportcore.presentation.client.OptionWidgets;
import com.exiledradio.backportcore.presentation.client.OptionsListScreen;
import com.exiledradio.backportcore.presentation.client.RenderOptions;
import com.exiledradio.backportcore.presentation.client.TitleLogo;
import com.exiledradio.backportcore.presentation.client.VideoLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionButton;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * Core 1.2.0's 1.18 to 1.21.11 look and feel in the dev client: the screens it builds, opened in code (the dummy
 * versions stop at 1.16, so each piece is driven directly), the preset applied and put back, and the hooks' math.
 */
@SideOnly(Side.CLIENT)
final class ClientLookFeelTests {

    private ClientLookFeelTests() {
    }

    static void title(Minecraft mc) throws Exception {
        GuiScreen menu = mc.currentScreen;
        for (String p : new String[] {"P26", "P27", "P28", "P29", "P30"}) {
            check("client: " + p + " applied", "applied".equals(com.exiledradio.backportcore.asm.Asm.state(p)),
                    com.exiledradio.backportcore.asm.Asm.state(p));
        }
        video(mc, menu);
        preset(mc);
        controls(mc, menu);
        debugOptions(mc);
        check("client: the title logo stays 1.12.2's with no 1.20 textures", TitleLogo.layout() == VanillaRevisions.TitleLogo.CLASSIC
                && TitleLogo.splashX(90) == 90 && TitleLogo.frames == 0);
        check("client: chunk fade's fog: linear, half way, half visible", Math.abs(RenderOptions.fogCoordinate(GL11.GL_LINEAR, 0, 100, 0, 50, 0.5) - 75.0F) < 1e-3,
                RenderOptions.fogCoordinate(GL11.GL_LINEAR, 0, 100, 0, 50, 0.5));
        check("client: chunk fade's fog: exp keeps the distance when fully visible",
                Math.abs(RenderOptions.fogCoordinate(GL11.GL_EXP, 0, 0, 0.1F, 10, 1.0) - 10.0F) < 1e-3);
        check("client: no debug binds before 1.21.11, keys pass through", !DebugBindings.active() && DebugBindings.translate(30) == 30);
        check("client: Cloud Distance and the weather keep 1.12.2's below 1.21.6", com.exiledradio.backportcore.presentation.client.PresentationHooks.cloudFast(256) == 256
                && com.exiledradio.backportcore.presentation.client.PresentationHooks.cloudFancy(-3) == -3
                && com.exiledradio.backportcore.presentation.client.PresentationHooks.weatherRadius(10) == 10);
        check("client: P31 and P32 applied", "applied".equals(com.exiledradio.backportcore.asm.Asm.state("P31"))
                && "applied".equals(com.exiledradio.backportcore.asm.Asm.state("P32")));
        check("client: before 1.20.5 the screens keep 1.12.2's dirt (no menu background, no 1.20.5 lists)",
                !com.exiledradio.backportcore.presentation.client.MenuBackground.active()
                        && com.exiledradio.backportcore.presentation.client.MenuBackground.menuFrames == 0
                        && com.exiledradio.backportcore.presentation.client.MenuBackground.listFrames == 0,
                com.exiledradio.backportcore.presentation.client.MenuBackground.menuFrames + "/"
                        + com.exiledradio.backportcore.presentation.client.MenuBackground.listFrames);
        check("client: before 1.20.2 Ctrl + B is always the narrator's", com.exiledradio.backportcore.presentation.client.PresentationHooks.narratorHotkeyOn());
        float[] a = com.exiledradio.backportcore.presentation.client.PanoramaRenderer.lastAngles();
        check("client: before 1.20.5 the panorama nods between 20 and 30 degrees", a[0] >= 20.0F && a[0] <= 30.0F, a[0]);
        check("client: 1.20.6's panorama time: ticks, half a tick past seven", com.exiledradio.backportcore.presentation.client.PanoramaRenderer.advance(1100, 1000) == 2.0F
                && com.exiledradio.backportcore.presentation.client.PanoramaRenderer.advance(5000, 0) == 0.5F
                && com.exiledradio.backportcore.presentation.client.PanoramaRenderer.advanceSpin(359.9F, 2.0F, 1.0F) < 1.0F);
        ClientGdLookTests.title(mc, menu);
        mc.displayGuiScreen(menu);
    }

    private static void video(Minecraft mc, GuiScreen menu) {
        GuiVideoSettings video = new GuiVideoSettings(menu, mc.gameSettings);
        mc.displayGuiScreen(video);
        List<GuiButton> entries = new ArrayList<GuiButton>();
        List<String> keys = Arrays.asList("FRAMERATE_LIMIT", "GRAPHICS", "backportcore:graphics_preset", "RENDER_DISTANCE", "ATTACK_INDICATOR");
        for (String k : keys) {
            entries.add(new GuiButton(0, 0, 0, 150, 20, k));
        }
        List<GuiOptionsRowList.Row> rows = VideoLayout.rows(video, new ArrayList<GuiButton>(entries), new ArrayList<String>(keys));
        List<String> kinds = new ArrayList<String>();
        for (GuiOptionsRowList.Row r : rows) {
            kinds.add(r.getClass().getSimpleName());
        }
        check("client: 1.21.11's Video Settings rows: header, Display, header, Preset, Quality, header, Preferences",
                kinds.equals(Arrays.asList("HeaderRow", "Row", "HeaderRow", "Row", "Row", "HeaderRow", "Row")), kinds);
        try {
            Field a = ReflectionHelper.findField(GuiOptionsRowList.Row.class, "buttonA", "field_148323_b");
            GuiButton preset = (GuiButton) a.get(rows.get(3));
            check("client: the Preset is a 310-wide slider at the left edge", preset instanceof VideoLayout.PresetSlider && preset.width == 310
                    && preset.x == video.width / 2 - 155, preset == null ? "none" : preset.getClass().getSimpleName() + " " + preset.width);
        } catch (Exception e) {
            check("client: the Preset row is readable", false, e.toString());
        }
    }

    private static void preset(Minecraft mc) {
        GameSettings s = mc.gameSettings;
        int distance = s.renderDistanceChunks;
        int ao = s.ambientOcclusion;
        int clouds = s.clouds;
        int particles = s.particleSetting;
        int mip = s.mipmapLevels;
        boolean shadows = s.entityShadows;
        int before = VideoLayout.applied;
        VideoLayout.apply(mc, GraphicsPreset.FAST);
        check("client: Fast sets 1.12.2's settings", VideoLayout.applied == before + 1 && s.renderDistanceChunks == 8 && s.ambientOcclusion == 0
                && s.clouds == 1 && s.particleSetting == 1 && s.mipmapLevels == 2 && !s.entityShadows,
                s.renderDistanceChunks + " ao" + s.ambientOcclusion + " c" + s.clouds + " p" + s.particleSetting + " m" + s.mipmapLevels);
        check("client: and Core's", Presentation.CLOUD_RANGE.get() == 32.0 && Presentation.WEATHER_RADIUS.get() == 5.0
                && !Presentation.CUTOUT_LEAVES.get() && "none".equals(Presentation.TEXTURE_FILTERING.get()));
        check("client: what Fast set reads as Fast", GraphicsPreset.matches(GraphicsPreset.FAST, VideoLayout.current(mc)), VideoLayout.current(mc));
        VideoLayout.apply(mc, GraphicsPreset.FABULOUS);
        check("client: Fabulous! sets 4x anisotropic filtering", "anisotropic".equals(Presentation.TEXTURE_FILTERING.get())
                && Presentation.MAX_ANISOTROPY.get() == 2.0 && Presentation.CLOUD_RANGE.get() == 128.0);
        s.renderDistanceChunks = distance;
        s.ambientOcclusion = ao;
        s.clouds = clouds;
        s.particleSetting = particles;
        s.entityShadows = shadows;
        if (s.mipmapLevels != mip) {
            s.setOptionFloatValue(GameSettings.Options.MIPMAP_LEVELS, mip);
        }
        s.saveOptions();
        for (com.exiledradio.backportcore.presentation.option.PresentationOption o : new com.exiledradio.backportcore.presentation.option.PresentationOption[] {
                Presentation.CLOUD_RANGE, Presentation.WEATHER_RADIUS, Presentation.CUTOUT_LEAVES, Presentation.TEXTURE_FILTERING,
                Presentation.MAX_ANISOTROPY, Presentation.GRAPHICS_PRESET}) {
            o.reset();
        }
    }

    private static void controls(Minecraft mc, GuiScreen menu) {
        OptionsListScreen controls = OptionsListScreen.controls(menu);
        mc.displayGuiScreen(controls);
        List<GuiButton> b = buttons(controls);
        check("client: Core's Controls: Mouse Settings... and Key Binds... first, then Auto-Jump", b.size() >= 4
                && b.get(0) instanceof OptionWidgets.Action && b.get(1) instanceof OptionWidgets.Action
                && b.get(2) instanceof GuiOptionButton && ((GuiOptionButton) b.get(2)).getOption() == GameSettings.Options.AUTO_JUMP
                && b.get(1).x == controls.width / 2 + 5, describe(b));
    }

    private static void debugOptions(Minecraft mc) throws Exception {
        DebugEntries.loadProfile(DebugEntries.DEFAULT);
        check("client: the default profile: FPS in F3, Biome off", DebugEntries.status(DebugEntries.FPS) == DebugEntries.Status.IN_OVERLAY
                && DebugEntries.status(DebugEntries.BIOME) == DebugEntries.Status.NEVER);
        check("client: F3 + G with F3 hidden turns chunk borders on for good", DebugEntries.toggle(DebugEntries.CHUNK_BORDERS, false)
                && DebugEntries.status(DebugEntries.CHUNK_BORDERS) == DebugEntries.Status.ALWAYS_ON && DebugEntries.usingProfile(DebugEntries.CUSTOM));
        DebugEntries.loadProfile(DebugEntries.DEFAULT);

        List<String> left = Arrays.asList("Minecraft 1.12.2 (1.12.2/forge)", "60 fps (3 chunk updates)", "C: 1/2", "", "XYZ: 1", "Block: 1 2 3",
                "Chunk: 1 2 3 in 0 0 0", "Biome: Plains", "Light: 15", "", "Debug: Pie [shift]: hidden FPS [alt]: hidden", "Mod line");
        List<String> ids = DebugEntries.classify(left, true, "60 fps (3 chunk updates)", null, null);
        check("client: debug lines sort into entries", ids.equals(Arrays.asList(DebugEntries.GAME_VERSION, DebugEntries.FPS, DebugEntries.CHUNK_RENDER_STATS, "",
                DebugEntries.PLAYER_POSITION, DebugEntries.PLAYER_POSITION, DebugEntries.PLAYER_SECTION_POSITION, DebugEntries.BIOME,
                DebugEntries.LIGHT_LEVELS, "", null, null)), ids);
        List<String> shown = DebugEntries.filter(left, ids, true, false);
        check("client: the default profile with F3 shows version, fps and position, and other lines",
                shown.equals(Arrays.asList("Minecraft 1.12.2 (1.12.2/forge)", "60 fps (3 chunk updates)", "", "XYZ: 1", "Block: 1 2 3",
                        "Chunk: 1 2 3 in 0 0 0", "", "Debug: Pie [shift]: hidden FPS [alt]: hidden", "Mod line")), shown);
        check("client: and nothing with F3 hidden", DebugEntries.filter(left, ids, false, false).isEmpty());

        DebugOptionsScreen screen = new DebugOptionsScreen();
        mc.displayGuiScreen(screen);
        check("client: Debug Options lists every entry", screen.listed == DebugEntries.ENTRIES.size(), screen.listed);
        Method typed = ReflectionHelper.findMethod(GuiScreen.class, "keyTyped", "func_73869_a", char.class, int.class);
        for (char c : "chunk".toCharArray()) {
            typed.invoke(screen, c, 0);
        }
        check("client: its search finds the three chunk entries", screen.listed == 3, screen.listed);
        screen.drawScreen(0, 0, 0.0F);
    }

    private static List<GuiButton> buttons(GuiScreen screen) {
        try {
            Field f = ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
            @SuppressWarnings("unchecked")
            List<GuiButton> list = (List<GuiButton>) f.get(screen);
            return list;
        } catch (Exception e) {
            return new ArrayList<GuiButton>();
        }
    }

    private static String describe(List<GuiButton> b) {
        StringBuilder sb = new StringBuilder();
        for (GuiButton x : b) {
            sb.append(x.getClass().getSimpleName()).append('(').append(x.displayString).append(' ').append(x.x).append(',').append(x.y).append(") ");
        }
        return sb.toString();
    }
}
