package com.exiledradio.backportcore.presentation.versions.client;

import com.exiledradio.backportcore.presentation.LegacyCopies;
import com.exiledradio.backportcore.presentation.client.DebugText;
import com.exiledradio.backportcore.presentation.client.FogPipeline;
import com.exiledradio.backportcore.presentation.versions.Look118;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The client side of {@code versions.Look118}: 1.17 and 1.18's look and feel that Backport Core owns since 1.4.7
 * (moved out of Caves &amp; Cliffs Backport): the clouds at 192 and Hide Lightning Flashes (1.18), the fog farther out
 * (1.18.1) and the F3 Block and Chunk rows (1.18.2), each one step of Core's pipelines (which run a step only once the
 * newest installed version is its version or later, and leave out an older jar's step of the same version and name),
 * and 1.18's autosave indicator.
 *
 * <p>The autosave indicator's handlers stay off while an older Caves &amp; Cliffs Backport jar runs its own copy
 * ({@link LegacyCopies}: its {@code lookfeel.client.LookAndFeelClient}).
 */
public final class Look118Client {

    /** Caves &amp; Cliffs Backport's own copy, in its releases before 1.1.6. */
    static final String LEGACY = "com.exiledradio.cavesandcliffsbackport.lookfeel.client.LookAndFeelClient";

    /** For the client self-tests: frames that drew the indicator, sky colors undone, F3 frames edited. */
    public static volatile int indicatorFrames;
    public static volatile int flashesHidden;
    public static volatile int debugEdits;

    private Look118Client() {
    }

    /** From Backport Core's client pre-init, before any backport's client code. */
    static void init() {
        // Core's ModernClouds draws at FogPipeline.cloudHeight too, so the clouds move there as well.
        FogPipeline.add("1.18", "clouds at 192, lightning flashes", new FogPipeline.Step() {
            @Override
            public void clouds(FogPipeline.Clouds clouds) {
                clouds.height = Look118.cloudHeight(clouds.provider.getDimension(), clouds.height);
            }

            @Override
            public void sky(FogPipeline.Sky sky) {
                if (Look118.HIDE_LIGHTNING_FLASHES.isShown() && Look118.HIDE_LIGHTNING_FLASHES.get() && sky.world != null
                        && sky.world.getLastLightningBolt() > 0) {
                    double[] rgb = {sky.red, sky.green, sky.blue};
                    Look118.undoLightningFlash(rgb, sky.world.getLastLightningBolt(), sky.partialTicks);
                    sky.red = rgb[0];
                    sky.green = rgb[1];
                    sky.blue = rgb[2];
                    flashesHidden++;
                }
            }
        });
        FogPipeline.add("1.18.1", "fog starts farther out", new FogPipeline.Step() {
            @Override
            public void distance(FogPipeline.Distance d) {
                // Forge's fog event fires only for 1.12.2's clear-air branch (not in water, lava, blindness or
                // powder snow); its thick-fog case (the nether's, a boss's) and the sky keep vanilla's, as 1.18.1's.
                if (d.fogMode == -1 || !Float.isNaN(d.start()) || thickFog(d.entity)) {
                    return;
                }
                d.set(Look118.terrainFogStart(d.far), Look118.terrainFogEnd(d.far));
            }
        });
        DebugText.add("1.18.2", "block and chunk rows", Look118Client::debugText);
        if (LegacyCopies.coreRuns(LEGACY)) {
            MinecraftForge.EVENT_BUS.register(new AutosaveIndicator());
        }
    }

    private static boolean thickFog(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.world == null || entity == null || mc.world.provider.doesXZShowFog((int) entity.posX, (int) entity.posZ)
                || mc.ingameGUI.getBossOverlay().shouldCreateFog();
    }

    /** 1.18.2's {@code DebugScreenOverlay}: "Block: x y z [x y z in chunk]", "Chunk: x y z [x z in r.X.Z.mca]". */
    static void debugText(DebugText.Lines lines) {
        Entity e = lines.mc.getRenderViewEntity();
        if (e == null) {
            return;
        }
        BlockPos pos = new BlockPos(e.posX, e.getEntityBoundingBox().minY, e.posZ);
        int block = DebugText.Lines.find(lines.left, "Block: ");
        int chunk = DebugText.Lines.find(lines.left, "Chunk: ");
        if (block >= 0) {
            lines.left.set(block, Look118.blockRow(pos.getX(), pos.getY(), pos.getZ()));
        }
        if (chunk >= 0) {
            lines.left.set(chunk, Look118.chunkRow(pos.getX(), pos.getY(), pos.getZ()));
        }
        debugEdits++;
    }

    /** 1.18's autosave indicator, registered only while no older Caves &amp; Cliffs Backport jar runs its own. */
    public static final class AutosaveIndicator {

        /** True while the integrated server is inside a save; {@link #saves} counts the saves begun. */
        private static volatile boolean saving;
        private static volatile int saves;
        private int seenSaves;
        private float indicator;
        private float lastIndicator;

        /** Every save of a world on the integrated server (the autosave, pausing, /save-all), on the server thread. */
        @SubscribeEvent
        public void onSave(WorldEvent.Save event) {
            World world = event.getWorld();
            if (!world.isRemote && Minecraft.getMinecraft().isIntegratedServerRunning()) {
                saving = true;
                saves++;
            }
        }

        /** The save ran inside this server tick ({@code MinecraftServer.isCurrentlySaving} goes false after it). */
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                saving = false;
            }
        }

        /**
         * 1.18's {@code Gui.tickAutosaveIndicator}. A save is shorter than a client tick, so a save begun since the
         * last client tick counts as one in progress, as 1.18's would have been seen by a tick that overlapped it.
         */
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            // 1.18's Gui.tick(paused) ticks the indicator while paused too, so the pause menu's save shows.
            Minecraft mc = Minecraft.getMinecraft();
            int now = saves;
            boolean flag = mc.isIntegratedServerRunning() && (saving || now != this.seenSaves);
            this.seenSaves = now;
            this.lastIndicator = this.indicator;
            this.indicator = Look118.autosaveStep(this.indicator, flag);
        }

        /** 1.18's {@code Gui.renderSavingIndicator}: "Saving world", white, fading, 10 from the right and 15 from the bottom. */
        @SubscribeEvent
        public void onOverlay(RenderGameOverlayEvent.Post event) {
            if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.gameSettings.hideGUI || !Look118.AUTOSAVE_INDICATOR.isShown() || !Look118.AUTOSAVE_INDICATOR.get()
                    || (this.indicator <= 0.0F && this.lastIndicator <= 0.0F)) {
                return;
            }
            int alpha = Look118.autosaveAlpha(this.lastIndicator, this.indicator, event.getPartialTicks());
            if (alpha <= 8) {
                return;
            }
            ScaledResolution res = event.getResolution();
            FontRenderer font = mc.fontRenderer;
            String text = I18n.format("menu.savingLevel");
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            font.drawStringWithShadow(text, res.getScaledWidth() - font.getStringWidth(text) - 10, res.getScaledHeight() - 15,
                    0xFFFFFF | alpha << 24);
            GlStateManager.disableBlend();
            indicatorFrames++;
        }
    }
}
