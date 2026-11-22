package com.exiledradio.backportcore.boat;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.datafix.IFixableData;
import net.minecraftforge.common.util.ModFixs;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
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
 * ...). A backport names its old ones with {@link #renamed}, and two things move them:
 * <ul>
 *   <li>Core's data fixer (Forge's {@code ModFixs}, Core's data version 1): every entity saved before it, in a chunk,
 *       riding another, in a spawner or under a player who logged out riding it ({@code RootVehicle}), has its old name
 *       replaced by Core's as it is read. Its wood, its chest's items, its passengers, damage and position are all in
 *       the same tags.</li>
 *   <li>{@code MissingMappings}: the world's registry lists the old names, so Forge asks for them; each is remapped to
 *       Core's, which also makes it an alias for that session (anything else that saved an entity, another mod's item
 *       holding a mob for one). Forge 1.12.2 saves aliases wrongly (its bug 4894) and drops them on the next load,
 *       which is why the fixer is the real move.</li>
 * </ul>
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

    /** Core's data version; 1 renames the backports' old boat entities. */
    public static final int DATA_VERSION = 1;

    /** From Core's pre-init: the fixer that renames saved boats. */
    public static void registerFixes() {
        ModFixs fixes = FMLCommonHandler.instance().getDataFixer().init(BackportCore.MOD_ID, DATA_VERSION);
        fixes.registerFix(FixTypes.ENTITY, new IFixableData() {
            @Override
            public int getFixVersion() {
                return 1;
            }

            @Override
            public NBTTagCompound fixTagCompound(NBTTagCompound tag) {
                rename(tag);
                return tag;
            }
        });
        // A player who logged out riding a boat: vanilla walks no fixes into RootVehicle.
        fixes.registerFix(FixTypes.PLAYER, new IFixableData() {
            @Override
            public int getFixVersion() {
                return 1;
            }

            @Override
            public NBTTagCompound fixTagCompound(NBTTagCompound tag) {
                if (tag.hasKey("RootVehicle", 10) && tag.getCompoundTag("RootVehicle").hasKey("Entity", 10)) {
                    renameAll(tag.getCompoundTag("RootVehicle").getCompoundTag("Entity"));
                }
                return tag;
            }
        });
    }

    /** Renames one saved entity's id, if it is a backport's old boat name. @return true if it was */
    public static boolean rename(NBTTagCompound entity) {
        if (!entity.hasKey("id", 8)) {
            return false;
        }
        ResourceLocation to = RENAMED.get(new ResourceLocation(entity.getString("id")));
        if (to == null) {
            return false;
        }
        entity.setString("id", to.toString());
        return true;
    }

    /** {@link #rename} for an entity and everything riding it. */
    public static void renameAll(NBTTagCompound entity) {
        rename(entity);
        NBTTagList riders = entity.getTagList("Passengers", 10);
        for (int i = 0; i < riders.tagCount(); i++) {
            renameAll(riders.getCompoundTagAt(i));
        }
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
