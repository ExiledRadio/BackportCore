package com.exiledradio.backportcore.ownership;

import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * {@link BlockOwnership}'s table for items and effects (Rule 1): which backport owns an item or a status effect
 * that later backports need, and the lookups for them. Block items are {@link BlockOwnership}'s: their names are
 * the blocks'.
 *
 * <pre>{@code
 * Item ingot = ItemOwnership.item("copper_ingot", "yourmod");   // the owner's if it provides it, else yours
 * Potion omen = ItemOwnership.effect("bad_omen", "yourmod");
 * if (ItemOwnership.shouldRegisterItem("yourmod", "copper_ingot")) { ... }
 * }</pre>
 * Ask from the item (or effect) registry event or later. An owner that switches parts off says so with
 * {@link #provides}; a pseudo-owner {@code "modid:switched_off"} works as in {@link BlockOwnership#declare}.
 */
public final class ItemOwnership {

    private static final Map<String, String> ITEMS = new LinkedHashMap<String, String>();
    private static final Map<String, String> EFFECTS = new LinkedHashMap<String, String>();
    private static final Map<String, BooleanSupplier> PROVIDES = new HashMap<String, BooleanSupplier>();

    static {
        // Caves & Cliffs (1.17): copper's items.
        ITEMS.put("copper_ingot", "cavesandcliffsbackport");
        ITEMS.put("raw_copper", "cavesandcliffsbackport");
        ITEMS.put("amethyst_shard", "cavesandcliffsbackport");
        // Buzzy Bees (1.15): honeycomb, which waxes copper and makes candles.
        ITEMS.put("honeycomb", "buzzybeesbackport");
        // Village & Pillage (1.14): the ominous bottle (metadata 0-4 = levels I-V) and Bad Omen, which its raids use.
        ITEMS.put("ominous_bottle", "villagerbackport");
        EFFECTS.put("bad_omen", "villagerbackport");
    }

    private ItemOwnership() {
    }

    /** Claims items for {@code ownerModId}; the first claim keeps an item (or its owner's own pseudo-owner). */
    public static synchronized boolean declareItems(String ownerModId, String... names) {
        return declare(ITEMS, ownerModId, names);
    }

    /** Claims effects for {@code ownerModId}, as {@link #declareItems}. */
    public static synchronized boolean declareEffects(String ownerModId, String... names) {
        return declare(EFFECTS, ownerModId, names);
    }

    private static boolean declare(Map<String, String> table, String ownerModId, String... names) {
        boolean all = true;
        for (String name : names) {
            String owner = table.get(name);
            if (owner == null || ownerModId.startsWith(owner + ":")) {
                table.put(name, ownerModId);
            } else if (!owner.equals(ownerModId)) {
                all = false;
            }
        }
        return all;
    }

    /** Whether the owner registers these items or effects in this game, read when another mod asks. */
    public static synchronized void provides(BooleanSupplier registering, String... names) {
        for (String name : names) {
            PROVIDES.put(name, registering);
        }
    }

    @Nullable
    public static synchronized String itemOwner(String name) {
        return ITEMS.get(name);
    }

    @Nullable
    public static synchronized String effectOwner(String name) {
        return EFFECTS.get(name);
    }

    private static synchronized boolean provided(@Nullable String owner, String name) {
        if (owner == null || !Loader.isModLoaded(owner)) {
            return false;
        }
        BooleanSupplier registering = PROVIDES.get(name);
        return registering == null || registering.getAsBoolean();
    }

    /** @return true if the item's owner is installed and registers it */
    public static boolean itemProvided(String name) {
        return provided(itemOwner(name), name);
    }

    /** @return true if the effect's owner is installed and registers it */
    public static boolean effectProvided(String name) {
        return provided(effectOwner(name), name);
    }

    /** @return true if {@code modId} should register its own copy of this item */
    public static boolean shouldRegisterItem(String modId, String name) {
        return modId.equals(itemOwner(name)) || !itemProvided(name);
    }

    /** @return true if {@code modId} should register its own copy of this effect */
    public static boolean shouldRegisterEffect(String modId, String name) {
        return modId.equals(effectOwner(name)) || !effectProvided(name);
    }

    /** @return the owner's item if it provides it, else {@code fallbackModId}'s; null if neither is registered */
    @Nullable
    public static Item item(String name, String fallbackModId) {
        return lookup(ForgeRegistries.ITEMS, itemProvided(name) ? itemOwner(name) : fallbackModId, name);
    }

    /** @return the owner's effect if it provides it, else {@code fallbackModId}'s; null if neither is registered */
    @Nullable
    public static Potion effect(String name, String fallbackModId) {
        return lookup(ForgeRegistries.POTIONS, effectProvided(name) ? effectOwner(name) : fallbackModId, name);
    }

    @Nullable
    private static <T extends IForgeRegistryEntry<T>> T lookup(IForgeRegistry<T> registry, @Nullable String modId, String name) {
        if (modId == null) {
            return null;
        }
        ResourceLocation id = new ResourceLocation(modId, name);
        T value = registry.getValue(id);
        return value == null || !id.equals(value.getRegistryName()) ? null : value;
    }

    /** @return every claimed item and its owner */
    public static synchronized Map<String, String> items() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(ITEMS));
    }

    /** @return every claimed effect and its owner */
    public static synchronized Map<String, String> effects() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(EFFECTS));
    }
}
