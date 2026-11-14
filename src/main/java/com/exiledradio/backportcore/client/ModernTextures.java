package com.exiledradio.backportcore.client;

import java.util.List;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Vanilla's textures as the newest installed backport's version has them (1.14's Texture Update, 1.17's ores and
 * so on), and the option to keep 1.12.2's.
 *
 * <p>Two packs ship inside the jar; exactly one is active:
 * <ul>
 * <li><b>modern</b> ({@link LayeredPack}) - one layer per release line that changed vanilla's textures, saved
 * under 1.12.2's file names ({@link TextureLayers}): 1.14.4's textures (plus 1.15's enchantment glint) always,
 * and each later line's changes up to the newest installed backport's version ({@code Presentation.newest()}).
 * It goes straight above vanilla, so every other mod's deliberate override of a vanilla texture, and every
 * resource pack the player picks, still wins over it.</li>
 * <li><b>classic</b> ({@link BuiltInPack}) - 1.12.2's look for the few of the backports' own textures that are 1.14 copies of
 * something 1.12.2 already had (the three cat skins an ocelot could be tamed into). It goes straight
 * above the highest of the mods whose textures it covers.</li>
 * </ul>
 *
 * <h2>Why at construction</h2>
 * FML loads every resource pack once, after mods are constructed and before pre-init, and the texture
 * atlas is first stitched from that load. A pack added in pre-init would sit unused until the player
 * reloaded resources. So {@link #install()} runs from the construction event, which is after FML has
 * listed the mods' own packs and before that load.
 */
@SideOnly(Side.CLIENT)
public final class ModernTextures {

    private static LayeredPack modern;
    private static BuiltInPack classic;
    private static boolean active;

    private ModernTextures() {
    }

    public static void install() {
        // ConfigManager has already synced in the normal course of construction, but whether that runs
        // before or after this mod's construction handler is up to Guava's event bus, so make sure.
        ConfigManager.sync(BackportCore.MOD_ID, Config.Type.INSTANCE);
        modern = new LayeredPack();
        classic = new BuiltInPack("classic");
        active = CoreConfig.textures.modernTextures;
        apply(active);
        MinecraftForge.EVENT_BUS.register(new ModernTextures.Listener());
        BackportCore.LOGGER.info("Texture Update: {} ({} texture layers, {} classic textures).",
                active ? "modern textures" : "1.12.2 textures", TextureLayers.all().size(), classic.size());
    }

    /**
     * From Core's load-complete event: the backports registered their versions in pre-init, after the game's first
     * resource load. The texture atlas was stitched after that and already has every layer that applies; any other
     * texture the game read before then, and a later layer now serves, is read again.
     */
    public static void afterLoading(Minecraft mc) {
        if (modern == null) {
            return;
        }
        int reloaded = 0;
        java.util.List<TextureLayers.Layer> layers = modern.active();
        BackportCore.LOGGER.info("Texture Update: layers {} for {}{}", layers, com.exiledradio.backportcore.presentation.Presentation.newest(),
                active ? "" : " (off: 1.12.2 textures)");
        if (!active) {
            return;
        }
        for (TextureLayers.Layer layer : layers) {
            if (layer.since == null) {
                continue;
            }
            for (String file : layer.files()) {
                int slash = file.indexOf('/', 7);
                if (!file.endsWith(".png") || slash < 0) {
                    continue;
                }
                ResourceLocation rl = new ResourceLocation(file.substring(7, slash), file.substring(slash + 1));
                net.minecraft.client.renderer.texture.ITextureObject tex = mc.getTextureManager().getTexture(rl);
                if (tex instanceof net.minecraft.client.renderer.texture.SimpleTexture) {
                    try {
                        ((net.minecraft.client.renderer.texture.SimpleTexture) tex).loadTexture(mc.getResourceManager());
                        reloaded++;
                    } catch (java.io.IOException e) {
                        BackportCore.LOGGER.warn("Texture Update: could not reload {}", rl, e);
                    }
                }
            }
        }
        if (reloaded > 0) {
            BackportCore.LOGGER.info("Texture Update: read {} texture(s) again from a later layer", reloaded);
        }
    }

    /** For the self-tests: the layers the modern pack serves from now, oldest first. */
    public static java.util.List<TextureLayers.Layer> activeLayers() {
        return modern == null ? java.util.Collections.<TextureLayers.Layer>emptyList() : modern.active();
    }

    private static List<IResourcePack> defaultPacks() {
        return ObfuscationReflectionHelper.getPrivateValue(Minecraft.class, Minecraft.getMinecraft(),
                "field_110449_ao");
    }

    private static void apply(boolean useModern) {
        List<IResourcePack> packs = defaultPacks();
        packs.remove(modern);
        packs.remove(classic);
        if (useModern) {
            int at = 0;
            for (int i = 0; i < packs.size(); i++) {
                if (packs.get(i) instanceof DefaultResourcePack) {
                    at = i + 1;
                    break;
                }
            }
            packs.add(at, modern);
        } else {
            // Above every mod whose own textures it puts back, and nothing more: anything a mod
            // loaded later chose to override stays overridden.
            int at = -1;
            for (String domain : classic.getResourceDomains()) {
                IResourcePack owner = FMLClientHandler.instance().getResourcePackFor(domain);
                if (owner != null) {
                    at = Math.max(at, packs.indexOf(owner));
                }
            }
            packs.add(at < 0 ? packs.size() : at + 1, classic);
        }
    }

    /** Switches packs when the option is changed from the in-game config screen. */
    public static final class Listener {

        // Post, not On: the config's own handler copies the screen's values into the fields on the
        // On event, and the order two handlers of one event run in is not defined.
        @SubscribeEvent
        public void onConfigChanged(ConfigChangedEvent.PostConfigChangedEvent event) {
            if (!BackportCore.MOD_ID.equals(event.getModID()) || CoreConfig.textures.modernTextures == active) {
                return;
            }
            active = CoreConfig.textures.modernTextures;
            apply(active);
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(mc::refreshResources);
        }
    }
}
