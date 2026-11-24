package com.exiledradio.backportcore.presentation.versions.client;

import com.exiledradio.backportcore.presentation.LegacyCopies;
import com.exiledradio.backportcore.presentation.LoadingRevision;
import com.exiledradio.backportcore.presentation.client.DebugText;
import com.exiledradio.backportcore.presentation.client.FogPipeline;
import com.exiledradio.backportcore.presentation.client.LoadingScreens;
import com.exiledradio.backportcore.presentation.versions.look121.client.AirBubbles;
import com.exiledradio.backportcore.presentation.versions.look121.client.ChatDrafts;
import com.exiledradio.backportcore.presentation.versions.look121.client.Fog;
import com.exiledradio.backportcore.presentation.versions.look121.client.FramerateLimiter;
import com.exiledradio.backportcore.presentation.versions.look121.client.HighContrastOutline;
import com.exiledradio.backportcore.presentation.versions.look121.client.InvertMouseX;
import com.exiledradio.backportcore.presentation.versions.look121.client.ItemRarityTooltips;
import com.exiledradio.backportcore.presentation.versions.look121.client.LoadingScreen1219;
import com.exiledradio.backportcore.presentation.versions.look121.client.SprintWindow;

import net.minecraftforge.common.MinecraftForge;

import java.util.regex.Pattern;

/**
 * The client side of {@code versions.Look121}: 1.21's look and feel that Backport Core owns since 1.4.7 (moved out of
 * Tricky Trials Backport, whose {@code lookfeel.client} had them).
 *
 * <p>Each handler is gated by its own version or option ({@code Presentation.isAtLeast}, {@code isShown()}), and
 * left off while an older Tricky Trials jar that still runs its copy is on the class path ({@link LegacyCopies}). The
 * fog and F3 steps keep Tricky Trials' version and name, so an older jar's copy is left out; its loading screen
 * drawer, registered after this one, replaces it with the same screen.
 */
public final class Look121Client {

    /** Tricky Trials Backport 1.0.1's package of these handlers (gone from 1.0.2). */
    static final String LEGACY = "com.exiledradio.trickytrialsbackport.lookfeel.client.";

    /** 1.12.2's {@code RenderGlobal.getDebugInfoEntities}: "E: 3/93, B: 0, I: 0". */
    private static final Pattern ENTITY_B = Pattern.compile(", B: \\d+");

    private Look121Client() {
    }

    /** From Backport Core's client pre-init, before any backport's client code. */
    static void init() {
        handler("FramerateLimiter", new FramerateLimiter());
        handler("HighContrastOutline", new HighContrastOutline());
        handler("AirBubbles", new AirBubbles());
        handler("ItemRarityTooltips", new ItemRarityTooltips());
        handler("InvertMouseX", new InvertMouseX());
        handler("SprintWindow", new SprintWindow());
        handler("ChatDrafts", new ChatDrafts());
        LoadingScreens.register(LoadingRevision.SPAWN_VIEW_1_21_9, new LoadingScreen1219());
        FogPipeline.add("1.21.6", "fog thickens in rain", Fog.RAIN);
        FogPipeline.add("1.21.11", "nether fog from 10 to 96 blocks", Fog.NETHER);
        FogPipeline.add("1.21.11", "sunrise dimmed by rain and thunder, clouds by world time", Fog.SKY);
        DebugText.add("1.21.2", "no B on the entity line", lines -> {
            for (int i = 0; i < lines.left.size(); i++) {
                String line = lines.left.get(i);
                if (line != null && line.startsWith("E: ")) {
                    lines.left.set(i, ENTITY_B.matcher(line).replaceFirst(""));
                }
            }
        });
    }

    /** Registers Core's copy of a handler unless an older Tricky Trials jar still has its own. */
    private static void handler(String legacyName, Object handler) {
        if (LegacyCopies.coreRuns(LEGACY + legacyName)) {
            MinecraftForge.EVENT_BUS.register(handler);
        }
    }
}
