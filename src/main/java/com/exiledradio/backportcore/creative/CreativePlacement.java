package com.exiledradio.backportcore.creative;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Puts the backports' blocks and items in vanilla's creative tabs, each next to its vanilla neighbor, as vanilla
 * lists it. A backport has no tab of its own.
 *
 * <p>An item is placed by the vanilla item it is: {@code place(block)} for a block registered as
 * {@code <modid>:stripped_oak_log} gives it 1.19.2's tab for {@code minecraft:stripped_oak_log} (Building Blocks)
 * and lists it where 1.19.2 does, after the last log. Its neighbor is found when the tab is listed: the nearest
 * item before it in vanilla's order that the tab really shows, vanilla's or a backport's, so stripped logs sit
 * after the dark oak log in 1.12.2 and after the warped stem once a backport adds that. An item newer than 1.19.2
 * is placed where Mojang's later item registry puts it ({@link VanillaOrder}).
 *
 * <p>The item keeps the vanilla tab object itself ({@code setCreativeTab(CreativeTabs.BUILDING_BLOCKS)}), so
 * {@code Item.isInCreativeTab}, the search tab, JEI and every mod that asks an item for its tab see vanilla's
 * tab. Only the listing changes: on the client Core replaces each tab that has placements in
 * {@code CreativeTabs.CREATIVE_TAB_ARRAY} with a copy that lists the original tab and then moves the placed
 * stacks ({@code creative.client.VanillaTab}). No patch.
 *
 * <p>Call these where the block or item is registered (the registry events), on both sides: the tab is set on
 * both, the order is used only by a client.
 */
public final class CreativePlacement {

    /** Any metadata. */
    public static final int ANY = -1;

    private static final List<Placement> PLACEMENTS = new ArrayList<Placement>();
    private static final List<String> EGG_MODS = new ArrayList<String>();
    private static int changes;

    private CreativePlacement() {
    }

    /**
     * Places a block (its item) as the vanilla block of the same name, in that block's 1.19.2 tab. One vanilla
     * keeps out of the creative menu (or does not have) is left out, with a warning.
     */
    public static <B extends Block> B place(B block) {
        String id = vanillaName(block.getRegistryName());
        CreativeTabs tab = vanillaTab(id);
        return tab == null ? block : place(block, tab, id);
    }

    /** Places a block as the vanilla block of the same name, in the tab given (for a block newer than 1.19.2). */
    public static <B extends Block> B place(B block, CreativeTabs tab) {
        return place(block, tab, vanillaName(block.getRegistryName()));
    }

    /** Places a block as the vanilla item {@code modernId} ({@code minecraft:...}), in the tab given. */
    public static <B extends Block> B place(B block, CreativeTabs tab, String modernId) {
        block.setCreativeTab(tab);
        add(new Placement(block, null, ANY, null, modernId, null, tab));
        return block;
    }

    /** Places an item as the vanilla item of the same name, in that item's 1.19.2 tab. */
    public static <I extends Item> I place(I item) {
        String id = vanillaName(item.getRegistryName());
        CreativeTabs tab = vanillaTab(id);
        return tab == null ? item : place(item, tab, id);
    }

    /** Places an item as the vanilla item of the same name, in the tab given (for an item newer than 1.19.2). */
    public static <I extends Item> I place(I item, CreativeTabs tab) {
        return place(item, tab, vanillaName(item.getRegistryName()));
    }

    /** Places an item as the vanilla item {@code modernId}, in the tab given. */
    public static <I extends Item> I place(I item, CreativeTabs tab, String modernId) {
        item.setCreativeTab(tab);
        add(new Placement(null, item, ANY, null, modernId, null, tab));
        return item;
    }

    /**
     * Names the vanilla item one metadata of an already placed item (or block) stands for: one block with a
     * variant per stone, say. Its tab is the item's; a variant not named this way goes where the item does.
     */
    public static void variant(Item item, int meta, String modernId) {
        add(new Placement(null, item, meta, null, modernId, null, null));
    }

    /** {@link #variant(Item, int, String)} for a block's item. */
    public static void variant(Block block, int meta, String modernId) {
        add(new Placement(block, null, meta, null, modernId, null, null));
    }

    /**
     * Places an item that vanilla does not have right after {@code anchor}: a vanilla item ({@code minecraft:...})
     * or another placed backport item (its registry name).
     */
    public static <I extends Item> I placeAfter(I item, int meta, CreativeTabs tab, String anchor) {
        item.setCreativeTab(tab);
        add(new Placement(null, item, meta, null, null, anchor, tab));
        return item;
    }

    /** {@link #placeAfter(Item, int, CreativeTabs, String)} for a block's item. */
    public static <B extends Block> B placeAfter(B block, int meta, CreativeTabs tab, String anchor) {
        block.setCreativeTab(tab);
        add(new Placement(block, null, meta, null, null, anchor, tab));
        return block;
    }

    /**
     * Lists a backport mob's spawn egg (vanilla's {@code minecraft:spawn_egg}, in Miscellaneous) where vanilla
     * lists that mob's egg: {@code <modid>:fox} as {@code minecraft:fox_spawn_egg}.
     */
    public static void placeSpawnEgg(ResourceLocation entity) {
        placeSpawnEgg(entity, "minecraft:" + entity.getPath() + "_spawn_egg");
    }

    /**
     * Lists every spawn egg of one mod's mobs where vanilla lists the egg of the same name:
     * {@code <modid>:fox} as {@code minecraft:fox_spawn_egg}. One call per mod, at any time before the menu opens.
     */
    public static synchronized void placeSpawnEggs(String modId) {
        if (!EGG_MODS.contains(modId)) {
            EGG_MODS.add(modId);
            changes++;
        }
    }

    /** @return a placement for one egg of a {@link #placeSpawnEggs(String)} mod (not kept in the list) */
    public static Placement eggByName(ResourceLocation entity) {
        return new Placement(null, null, ANY, entity, "minecraft:" + entity.getPath() + "_spawn_egg", null,
                CreativeTabs.MISC);
    }

    /** @return the mods whose spawn eggs are placed by name */
    public static synchronized List<String> spawnEggMods() {
        return Collections.unmodifiableList(new ArrayList<String>(EGG_MODS));
    }

    /** {@link #placeSpawnEgg(ResourceLocation)} as the vanilla egg named. */
    public static void placeSpawnEgg(ResourceLocation entity, String modernId) {
        add(new Placement(null, null, ANY, entity, modernId, null, CreativeTabs.MISC));
    }

    /**
     * Adds an enchantment type to a vanilla tab's enchanted books (Combat lists crossbow and trident books, as in
     * 1.19.2), keeping the types it has.
     */
    public static void addEnchantmentType(CreativeTabs tab, EnumEnchantmentType type) {
        EnumEnchantmentType[] old = tab.getRelevantEnchantmentTypes();
        for (EnumEnchantmentType t : old) {
            if (t == type) {
                return;
            }
        }
        EnumEnchantmentType[] types = java.util.Arrays.copyOf(old, old.length + 1);
        types[old.length] = type;
        tab.setRelevantEnchantmentTypes(types);
    }

    /** @return the vanilla item of the same name: {@code minecraft:<path>} */
    public static String vanillaName(@Nullable ResourceLocation name) {
        if (name == null) {
            throw new IllegalStateException("Creative tabs: place a block or item after naming it");
        }
        return "minecraft:" + name.getPath();
    }

    /**
     * @return 1.19.2's tab for the item, or null: the item is left out of the creative menu, as vanilla leaves out
     * its debug stick and suspicious stew (a warning if vanilla has no such item at all)
     */
    @Nullable
    private static CreativeTabs vanillaTab(String modernId) {
        CreativeTabs tab = VanillaOrder.tab(modernId);
        if (tab == null && VanillaOrder.position(modernId) >= 0) {
            BackportCore.LOGGER.info("Creative tabs: {} is in no creative tab, as in vanilla", modernId);
        } else if (tab == null) {
            BackportCore.LOGGER.warn("Creative tabs: vanilla has no {}; name a tab with place(..., tab)", modernId);
        }
        return tab;
    }

    private static synchronized void add(Placement p) {
        if (p.modernId != null && VanillaOrder.position(p.modernId) < 0) {
            BackportCore.LOGGER.warn("Creative tabs: {} is not a vanilla item; it stays where its mod lists it",
                    p.modernId);
        } else if (p.modernId != null && p.tab != null) {
            CreativeTabs vanilla = VanillaOrder.tab(p.modernId);
            if (vanilla != null && vanilla != p.tab) {
                BackportCore.LOGGER.warn("Creative tabs: {} is in {} in vanilla, placed in {}", p.modernId,
                        VanillaOrder.nameOf(vanilla), VanillaOrder.nameOf(p.tab));
            }
        }
        PLACEMENTS.add(p);
        changes++;
    }

    /** @return every placement, in the order made */
    public static synchronized List<Placement> all() {
        return Collections.unmodifiableList(new ArrayList<Placement>(PLACEMENTS));
    }

    /** @return a count that changes whenever a placement is added */
    public static synchronized int changes() {
        return changes;
    }

    /** One block's or item's place: by the vanilla item it is, or right after an anchor. */
    public static final class Placement {
        @Nullable public final Block block;
        @Nullable public final Item item;
        public final int meta;
        @Nullable public final ResourceLocation eggEntity;
        @Nullable public final String modernId;
        @Nullable public final String anchor;
        @Nullable public final CreativeTabs tab;

        Placement(@Nullable Block block, @Nullable Item item, int meta, @Nullable ResourceLocation eggEntity,
                  @Nullable String modernId, @Nullable String anchor, @Nullable CreativeTabs tab) {
            this.block = block;
            this.item = item;
            this.meta = meta;
            this.eggEntity = eggEntity;
            this.modernId = modernId;
            this.anchor = anchor;
            this.tab = tab;
        }

        /** @return the item placed, once it exists (a block's item is registered after the block) */
        @Nullable
        public Item item() {
            if (this.item != null) {
                return this.item;
            }
            if (this.block != null) {
                Item it = Item.getItemFromBlock(this.block);
                return it == net.minecraft.init.Items.AIR ? null : it;
            }
            return null;
        }
    }
}
