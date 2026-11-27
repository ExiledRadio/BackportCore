package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.presentation.Darkness;
import com.exiledradio.backportcore.presentation.LightmapColors;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.init.MobEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 26.1's lightmap in 1.12.2's ({@link LightmapColors}), once the sky's look is 26.1 or later
 * ({@link VanillaRevisions#modernLightmap()}): P65 calls {@link #update} at the head of
 * {@code EntityRenderer.updateLightmap}, which fills {@code lightmapColors}, uploads it and clears the update flag, or
 * leaves the method to 1.12.2 (and to OptiFine's version of it).
 *
 * <h2>Inputs</h2>
 * 26.3's {@code LightmapRenderStateExtractor}: the block factor is its flicker plus 1.4 (1.12.2's {@code torchFlickerX}
 * is ten times 26.3's flicker, so {@code torchFlickerX * 0.1 + 1.4}); the boss darkening is 1.12.2's
 * {@code bossColorModifier}; Night Vision's strength is 1.12.2's {@code getNightVisionBrightness} (Update Aquatic's
 * Conduit Power lends the player Night Vision, so it counts too); the brightness is the Brightness option. The dimension:
 * {@code isNether()} takes the Nether's values, the End's provider the End's, any other with sky light the Overworld's
 * day timeline, set by its sun ({@code getCelestialAngle}, so a dimension with its own day keeps in step) and its
 * weather; a dimension without sky light keeps the Overworld's ambient light and no sky light.
 *
 * <h2>Forge and other mods</h2>
 * {@code WorldProvider.getLightmapColors} is still called for each cell, where 1.12.2 calls it: after the boss
 * darkening, before the clamp. Wild Update's Darkness computes 1.12.2's lightmap itself before each frame
 * ({@code warden.client.DarknessClient}) and registers itself with {@link Darkness}; while its factor is above 0 this class fills the lightmap first (a render tick
 * listener at the highest priority), taking 26.3's two Darkness steps from it: the brightness lowered by the factor and
 * the pulse taken off before the clamp, both times Darkness Pulsing. Wild Update then finds nothing to do.
 *
 * <h2>Steps aside</h2>
 * With an OptiFine shader pack loaded (its shaders read their own light), and where OptiFine's Custom Colors has a
 * lightmap for the dimension ({@code lightmap/world*.png}, which replaces the formula outright): one log line each, and
 * 1.12.2's method (OptiFine's) runs.
 */
@SideOnly(Side.CLIENT)
public final class ModernLightmap {

    /** Frames filled by this class, for the self-tests. */
    public static int frames;

    private static final LightmapColors.Inputs INPUTS = new LightmapColors.Inputs();

    private static Field updateNeeded;
    private static Field torchFlickerX;
    private static Field bossColor;
    private static Field bossColorPrev;
    private static Field colors;
    private static Field texture;
    private static boolean fieldsFailed;

    private static Boolean optifine;
    private static Field shaderPackLoaded;
    private static Method customColorsOn;
    private static Field lightMapPacks;
    private static Field lightmapMinDimension;
    private static String steppedAside;

    private static Boolean wildDarkness;
    private static Method darknessFactor;
    private static Method darknessScale;

    private ModernLightmap() {
    }

    /** P65: true when 26.1's lightmap was filled in, so {@code updateLightmap} returns. */
    public static boolean update(EntityRenderer renderer, float partialTicks) {
        if (!VanillaRevisions.modernLightmap()) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.world;
        EntityPlayerSP player = mc.player;
        if (world == null || player == null || fieldsFailed || !fields()) {
            return false;
        }
        try {
            if (!updateNeeded.getBoolean(renderer) || stepAside(world)) {
                return false;
            }
            mc.profiler.startSection("lightTex");
            fill(mc, world, player, partialTicks, torchFlickerX.getFloat(renderer), bossColor.getFloat(renderer),
                    bossColorPrev.getFloat(renderer), (int[]) colors.get(renderer));
            ((DynamicTexture) texture.get(renderer)).updateDynamicTexture();
            updateNeeded.setBoolean(renderer, false);
            mc.profiler.endSection();
            frames++;
            return true;
        } catch (Exception e) {
            fieldsFailed = true;
            BackportCore.LOGGER.warn("26.1's lightmap: {}; 1.12.2's lightmap kept.", e.toString());
            return false;
        }
    }

    /** Before Wild Update's Darkness fills 1.12.2's lightmap for the frame, fill 26.1's with its Darkness steps. */
    public static final class Events {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void beforeFrame(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.START || !"applied".equals(Asm.state("P65"))) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world != null && mc.player != null && mc.entityRenderer != null && darkness(event.renderTickTime) > 0.0F) {
                update(mc.entityRenderer, event.renderTickTime);
            }
        }
    }

    private static void fill(Minecraft mc, WorldClient world, EntityPlayerSP player, float partialTicks, float flicker,
                             float boss, float bossPrev, int[] out) {
        LightmapColors.Inputs in = INPUTS;
        final WorldProvider provider = world.provider;
        if (provider.isNether()) {
            LightmapColors.nether(in);
        } else if (provider instanceof WorldProviderEnd || provider.getDimensionType() == DimensionType.THE_END) {
            LightmapColors.end(in);
        } else if (provider.hasSkyLight()) {
            float dayTime = LightmapColors.dayTimeOfCelestialAngle(world.getCelestialAngle(partialTicks));
            LightmapColors.overworld(in, dayTime, world.getRainStrength(partialTicks), world.getThunderStrength(partialTicks),
                    world.getLastLightningBolt() > 0);
        } else {
            LightmapColors.overworld(in, 6000.0F, 0.0F, 0.0F, false);
            in.skyFactor = 0.0F;
        }
        in.blockFactor = flicker * 0.1F + 1.4F;
        in.bossDarkening = boss > 0.0F ? bossPrev + (boss - bossPrev) * partialTicks : 0.0F;
        in.nightVision = player.isPotionActive(MobEffects.NIGHT_VISION) ? nightVision(player, partialTicks) : 0.0F;
        float gamma = mc.gameSettings.gammaSetting;
        float darkness = darkness(partialTicks);
        if (darkness > 0.0F) {
            // 26.3: the effect's blend times Darkness Pulsing lowers the brightness; the pulse, times it again.
            float scale = darknessScale();
            float modifier = darkness * scale;
            gamma = Math.max(0.0F, gamma - modifier);
            in.darknessScale = Math.max(0.0F, MathHelper.cos((player.ticksExisted - partialTicks) * (float) Math.PI * 0.025F)
                    * 0.45F * modifier) * scale;
        } else {
            in.darknessScale = 0.0F;
        }
        in.brightness = gamma;
        final float sun = world.getSunBrightness(1.0F);
        LightmapColors.compute(in, (sky, block, rgb) -> provider.getLightmapColors(partialTicks, sun, sky, block, rgb), out);
    }

    private static float nightVision(EntityPlayerSP player, float partialTicks) {
        int i = player.getActivePotionEffect(MobEffects.NIGHT_VISION).getDuration();
        return i > 200 ? 1.0F : 0.7F + MathHelper.sin((i - partialTicks) * (float) Math.PI * 0.2F) * 0.3F;
    }

    private static boolean fields() {
        if (updateNeeded != null) {
            return true;
        }
        try {
            updateNeeded = ReflectionHelper.findField(EntityRenderer.class, "lightmapUpdateNeeded", "field_78536_aa");
            torchFlickerX = ReflectionHelper.findField(EntityRenderer.class, "torchFlickerX", "field_78514_e");
            bossColor = ReflectionHelper.findField(EntityRenderer.class, "bossColorModifier", "field_82831_U");
            bossColorPrev = ReflectionHelper.findField(EntityRenderer.class, "bossColorModifierPrev", "field_82832_V");
            colors = ReflectionHelper.findField(EntityRenderer.class, "lightmapColors", "field_78504_Q");
            texture = ReflectionHelper.findField(EntityRenderer.class, "lightmapTexture", "field_78513_d");
            return true;
        } catch (Exception e) {
            updateNeeded = null;
            fieldsFailed = true;
            BackportCore.LOGGER.warn("26.1's lightmap: EntityRenderer's lightmap fields not found ({}); 1.12.2's lightmap kept.", e.toString());
            return false;
        }
    }

    // ---- OptiFine ----

    /** @return true (logged once per reason) while OptiFine's shaders or its custom lightmap for this dimension own it */
    private static boolean stepAside(WorldClient world) {
        String why = optifineReason(world);
        if (why != null && !why.equals(steppedAside)) {
            BackportCore.LOGGER.info("26.1's lightmap steps aside: {}; 1.12.2's lightmap (OptiFine's) is drawn.", why);
        }
        steppedAside = why;
        return why != null;
    }

    private static String optifineReason(WorldClient world) {
        if (optifine == null) {
            try {
                shaderPackLoaded = Class.forName("net.optifine.shaders.Shaders").getField("shaderPackLoaded");
                optifine = Boolean.TRUE;
            } catch (Throwable t) {
                optifine = Boolean.FALSE;
            }
            if (optifine) {
                try {
                    customColorsOn = Class.forName("Config").getMethod("isCustomColors");
                    Class<?> cc = Class.forName("net.optifine.CustomColors");
                    lightMapPacks = cc.getDeclaredField("lightMapPacks");
                    lightMapPacks.setAccessible(true);
                    lightmapMinDimension = cc.getDeclaredField("lightmapMinDimensionId");
                    lightmapMinDimension.setAccessible(true);
                } catch (Throwable t) {
                    customColorsOn = null;
                    BackportCore.LOGGER.info("26.1's lightmap: OptiFine's Custom Colors not found ({}); its lightmaps are not checked.", t.toString());
                }
            }
        }
        if (!optifine) {
            return null;
        }
        try {
            if (shaderPackLoaded.getBoolean(null)) {
                return "an OptiFine shader pack is loaded";
            }
            if (customColorsOn != null && (Boolean) customColorsOn.invoke(null)) {
                Object[] packs = (Object[]) lightMapPacks.get(null);
                int index = world.provider.getDimension() - lightmapMinDimension.getInt(null);
                if (packs != null && index >= 0 && index < packs.length && packs[index] != null) {
                    return "OptiFine's Custom Colors has a lightmap for dimension " + world.provider.getDimension();
                }
            }
        } catch (Throwable t) {
            return "OptiFine could not be read (" + t + ")";
        }
        return null;
    }

    // ---- Wild Update's Darkness ----

    /**
     * @return the Darkness factor for the frame: from the registered {@link Darkness} source, or, for a Wild Update
     *         that predates the hook, its {@code DarknessClient.factor} by reflection; 0 without either
     */
    static float darkness(float partialTicks) {
        Darkness.Source source = Darkness.source();
        if (source != null) {
            try {
                return MathHelper.clamp(source.factor(partialTicks), 0.0F, 1.0F);
            } catch (Throwable t) {
                Darkness.register(null);
                BackportCore.LOGGER.warn("26.1's lightmap: the Darkness source failed ({}); the light ignores it.", t.toString());
                return 0.0F;
            }
        }
        if (wildDarkness == null) {
            try {
                darknessFactor = Class.forName("com.exiledradio.wildupdatebackport.warden.client.DarknessClient")
                        .getMethod("factor", float.class);
                darknessScale = Class.forName("com.exiledradio.wildupdatebackport.lookfeel.LookAndFeel").getMethod("darknessScale");
                wildDarkness = Boolean.TRUE;
            } catch (Throwable t) {
                wildDarkness = Boolean.FALSE;
            }
        }
        if (!wildDarkness) {
            return 0.0F;
        }
        try {
            return (Float) darknessFactor.invoke(null, partialTicks);
        } catch (Throwable t) {
            wildDarkness = Boolean.FALSE;
            BackportCore.LOGGER.warn("26.1's lightmap: Wild Update's Darkness could not be read ({}); the light ignores it.", t.toString());
            return 0.0F;
        }
    }

    private static float darknessScale() {
        Darkness.Source source = Darkness.source();
        if (source != null) {
            try {
                return MathHelper.clamp(source.scale(), 0.0F, 1.0F);
            } catch (Throwable t) {
                return 1.0F;
            }
        }
        try {
            return MathHelper.clamp((Float) darknessScale.invoke(null), 0.0F, 1.0F);
        } catch (Throwable t) {
            return 1.0F;
        }
    }
}
