package com.exiledradio.backportcore.boat;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The family's one boat and one chest boat (Wild Update's request 4, Tricky Trials' #9, Game Drops' stream 5):
 * {@code backportcore:boat} (id 0) and {@code backportcore:chest_boat} (id 1), tracked as vanilla's boat is (80
 * blocks, every 3 ticks, with velocity). Each backport registers its woods ({@link BoatWood#register}) and items
 * ({@link ItemWoodBoat}); Core draws them ({@code client.RenderWoodBoat}).
 *
 * <h2>Boats saved before (Core 1.4.4)</h2>
 * Each backport had its own two entities ({@code wildupdatebackport:boat}, {@code trailsandtalesbackport:chest_boat},
 * ...). A backport names its old ones with {@link #renamed}; when a world that has them loads, Forge asks
 * {@code MissingMappings} for them and each becomes an alias of Core's, which the world keeps. Every saved boat is then
 * read by Core's entity: in chunks, carrying passengers, under a player who logged out riding it, and in a spawner. Its
 * wood, its chest's items, its passengers, damage and position are all in the same tags.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class Boats {

    public static final ResourceLocation BOAT = new ResourceLocation(BackportCore.MOD_ID, "boat");
    public static final ResourceLocation CHEST_BOAT = new ResourceLocation(BackportCore.MOD_ID, "chest_boat");

    /** A backport's old entity name -> Core's. */
    private static final Map<ResourceLocation, ResourceLocation> RENAMED = new ConcurrentHashMap<ResourceLocation, ResourceLocation>();

    /** For the self-test and the log: old names remapped since start. */
    public static volatile int remapped;

    private Boats() {
    }

    /**
     * Names an entity a backport saved its boats (or chest boats) as before Core 1.4.4, so worlds that have them load
     * them as Core's. Call it in pre-init.
     */
    public static void renamed(ResourceLocation oldName, boolean chest) {
        RENAMED.put(oldName, chest ? CHEST_BOAT : BOAT);
    }

    /** @return Core's entity a saved name now loads as, or null */
    public static ResourceLocation renamedTo(ResourceLocation oldName) {
        return RENAMED.get(oldName);
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(EntityEntryBuilder.create().entity(EntityWoodBoat.class).id(BOAT, 0)
                .name(BackportCore.MOD_ID + ".boat").tracker(80, 3, true).build());
        event.getRegistry().register(EntityEntryBuilder.create().entity(EntityWoodChestBoat.class).id(CHEST_BOAT, 1)
                .name(BackportCore.MOD_ID + ".chest_boat").tracker(80, 3, true).build());
    }

    @SubscribeEvent
    public static void missingEntities(RegistryEvent.MissingMappings<EntityEntry> event) {
        // Every mod's missing names: the old names are under the backports' ids, not Core's.
        for (RegistryEvent.MissingMappings.Mapping<EntityEntry> m : event.getAllMappings()) {
            ResourceLocation to = RENAMED.get(m.key);
            EntityEntry entry = to == null ? null : ForgeRegistries.ENTITIES.getValue(to);
            if (entry != null && to.equals(entry.getRegistryName())) {
                m.remap(entry);
                remapped++;
                BackportCore.LOGGER.info("Saved {} entities now load as {}.", m.key, to);
            }
        }
    }
}
