package com.exiledradio.backportcore;

import com.exiledradio.backportcore.config.ConfigImport;
import com.exiledradio.backportcore.duplicate.DuplicateBlocks;
import com.exiledradio.backportcore.migrate.FutureMcCommand;
import com.exiledradio.backportcore.pose.PoseEvents;
import com.exiledradio.backportcore.pose.PoseNetwork;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.SpawnProgress;
import com.exiledradio.backportcore.presentation.VersionedDefaults;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Backport Core - the code and assets the update backports share.
 *
 * <p>It adds no blocks, items or entities. What it holds (see {@code docs/API.md}):
 * <ul>
 * <li>{@code client.ModernTextures} - 1.14's Texture Update for vanilla, and the switch back to
 * 1.12.2's look ({@code textures.modernTextures}).</li>
 * <li>{@code duplicate.DuplicateBlocks} - hiding one half of each block pair two mods both add.</li>
 * <li>{@code migrate} - turning another mod's blocks and items into the backports' own, in a world
 * that still has that mod or one it has been taken out of. Each backport adds its own pairs.</li>
 * <li>{@code ownership.BlockOwnership} - which backport owns each shared block.</li>
 * <li>{@code smithing.SmithingUpgrades} - the netherite-style upgrades a smithing table reads.</li>
 * <li>{@code trade.TradeRegistry} - villager and wandering trader trades other mods add.</li>
 * <li>{@code terrain} - where the ground will be, before a chunk exists, on vanilla, superflat and
 * modded terrain: for laying out structures ahead of generation.</li>
 * <li>{@code structure} - today's structure files read into 1.12.2 blocks, structures scattered the
 * way today's game scatters them, and finding the nearest of a kind.</li>
 * <li>{@code waterlog} - 1.13's waterlogging for the family's blocks, through Fluidlogged API.</li>
 * <li>{@code pose} - 1.14's player poses: the swimming pose and crawling, their hitbox and eyes, sent to
 * other players and drawn; and the movement hooks a backport's swimming rules use.</li>
 * <li>{@code worldgen} - seed parity: the structure placement table each backport fills with its version's
 * numbers (newest wins), and the overworld rules 1.13 and later share (biome search, hills seeding, 1.15's
 * biome zoom, temples on four grids).</li>
 * <li>{@code presentation} - each version's look and feel (panorama, world-loading screen, options screens,
 * splashes, menu music, F3 keys, fog), following the newest installed backport.</li>
 * </ul>
 *
 * <p>Its own small coremod ({@code asm}, every patch in {@code docs/PATCHES.md}) gives the pose engine the
 * hooks 1.12.2 lacks; each patch does exactly what vanilla does until a backport turns its rules on. No
 * Mixin. The one mod it requires is Fluidlogged API; every other mod is reached by name, behind a
 * {@code Loader.isModLoaded} check.
 */
@Mod(
        modid = BackportCore.MOD_ID,
        name = BackportCore.MOD_NAME,
        version = BackportCore.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        // Waterlogging is Fluidlogged API's, on both sides (see waterlog/). Required, never bundled.
        dependencies = "required-after:fluidlogged_api@[3.3.3,)",
        // Its one channel carries only poses, and only while a backport has them on; that backport requires
        // itself on both sides. Without one, a client without the library can join a server that has it.
        acceptableRemoteVersions = "*"
)
public final class BackportCore {

    public static final String MOD_ID = "backportcore";
    public static final String MOD_NAME = "Backport Core";
    public static final String VERSION = "@VERSION@";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    /**
     * FML builds this before it reads this mod's config for the first time, which is the one moment
     * an older mod's setting can become this one's default (see {@link ConfigImport}).
     */
    public BackportCore() {
        ConfigImport.importOnce();
    }

    /**
     * The texture packs go in before FML's one resource load ahead of pre-init - see ModernTextures.
     * The call names no client type, so a dedicated server never loads that class.
     */
    @Mod.EventHandler
    public void construct(FMLConstructionEvent event) {
        if (event.getSide().isClient()) {
            com.exiledradio.backportcore.client.ModernTextures.install();
            com.exiledradio.backportcore.presentation.client.VanillaAssetPack.install();
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new CoreConfig.EventHandler());
        PoseNetwork.init();
        MinecraftForge.EVENT_BUS.register(new PoseEvents());
        MinecraftForge.EVENT_BUS.register(new SpawnProgress.Events());
        // The overworld rules 1.13 and later share, applied by the newest installed backport (worldgen/).
        com.exiledradio.backportcore.worldgen.ModernWorldgen.init();
        if (event.getSide().isClient()) {
            com.exiledradio.backportcore.pose.client.PoseClient.init();
            com.exiledradio.backportcore.presentation.client.PresentationClient.init();
        }
    }

    /** Every mod's blocks are registered and none has been listed anywhere yet. */
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        DuplicateBlocks.hideFromCreative();
        if (event.getSide().isClient()) {
            com.exiledradio.backportcore.presentation.client.PresentationClient.initLate();
        }
        // Saved jigsaw pieces (bastions and the like) of every backport that uses Core's assembler.
        net.minecraft.world.gen.structure.MapGenStructureIO.registerStructureComponent(
                com.exiledradio.backportcore.structure.JigsawPiece.class, "BCJigsaw");
    }

    /** Recipes another mod added after the recipe registry event. */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        DuplicateBlocks.removeLateRecipes();
        // World generators of another mod whose duplicate blocks are hidden.
        DuplicateBlocks.removeGenerators();
    }

    /** Every mod has loaded: the backports' vanilla text and assets go into the client's languages and textures. */
    @Mod.EventHandler
    public void loadComplete(net.minecraftforge.fml.common.event.FMLLoadCompleteEvent event) {
        if (event.getSide().isClient()) {
            com.exiledradio.backportcore.presentation.client.PresentationClient.loadComplete();
        }
    }

    /** The world-loading grid records the spawn area from here (integrated and dedicated servers alike). */
    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        SpawnProgress.start();
        // A client applies them at its title screen; a dedicated server has none, so here.
        if (event.getSide().isServer()) {
            VersionedDefaults.apply(Presentation.installed(), null);
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new FutureMcCommand());
    }

    /** Also starts {@code -Dbackportcore.seeddump=<folder>}: the overworld's seed-parity dump (a dev tool). */
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        SpawnProgress.finish();
        com.exiledradio.backportcore.seedcheck.OverworldDump.serverStarted(
                net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance());
    }
}
