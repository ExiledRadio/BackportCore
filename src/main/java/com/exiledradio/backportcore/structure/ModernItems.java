package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Today's item names, as {@link ModernPalette} is for blocks: a loot table, recipe or trade read from a newer
 * game's files names {@code minecraft:cobweb}, {@code minecraft:red_dye} or {@code minecraft:short_grass}; this gives
 * 1.12.2's item and metadata ({@code web}, {@code dye:1}, {@code tallgrass:1}).
 *
 * <pre>{@code
 * ItemStack s = ModernItems.stack("minecraft:cobweb");   // 1.12.2's web, or EMPTY when 1.12.2 has none
 * ModernItems.map("minecraft:resin_clump", () -> new ItemStack(MyItems.RESIN_CLUMP));
 * }</pre>
 *
 * Resolution: a backport's own {@link #map} for the name, then the table of 1.12.2's renamed items (every item
 * whose name or metadata changed since, from Minecraft's data fixers: {@code structure/item_names.txt}, built by
 * {@code tools/flattening/items.py}), then the same name in the item registry. Items 1.12.2 never had and no
 * backport maps answer EMPTY. Ask from init or later (the registries must be filled).
 */
public final class ModernItems {

    private static final Map<String, Supplier<ItemStack>> MAPPED = new HashMap<String, Supplier<ItemStack>>();
    private static Map<String, Legacy> table;

    private static final class Legacy {
        final ResourceLocation id;
        final int meta;

        Legacy(ResourceLocation id, int meta) {
            this.id = id;
            this.meta = meta;
        }
    }

    private ModernItems() {
    }

    /** Today's name to a backport's own item (the first mapping of a name wins). */
    public static synchronized void map(String modernName, Supplier<ItemStack> to) {
        String key = normalize(modernName);
        if (!MAPPED.containsKey(key)) {
            MAPPED.put(key, to);
        }
    }

    /** @return 1.12.2's stack for today's item name (count 1), or EMPTY when there is none */
    public static ItemStack stack(String modernName) {
        String key = normalize(modernName);
        Supplier<ItemStack> mapped;
        synchronized (ModernItems.class) {
            mapped = MAPPED.get(key);
        }
        if (mapped != null) {
            ItemStack s = mapped.get();
            if (s != null && !s.isEmpty()) {
                return s.copy();
            }
        }
        Legacy legacy = table().get(key);
        if (legacy != null) {
            Item item = registered(legacy.id);
            return item == null ? ItemStack.EMPTY : new ItemStack(item, 1, legacy.meta);
        }
        Item item = registered(new ResourceLocation(key));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** @return 1.12.2's item for today's name (its metadata is {@link #stack}'s), or null */
    @Nullable
    public static Item item(String modernName) {
        ItemStack s = stack(modernName);
        return s.isEmpty() ? null : s.getItem();
    }

    /** @return the table's size, for the self-tests */
    public static int tableSize() {
        return table().size();
    }

    static String normalize(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        return n.indexOf(':') < 0 ? "minecraft:" + n : n;
    }

    @Nullable
    private static Item registered(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR || !id.equals(item.getRegistryName()) ? null : item;
    }

    private static synchronized Map<String, Legacy> table() {
        if (table == null) {
            table = load();
        }
        return table;
    }

    private static Map<String, Legacy> load() {
        Map<String, Legacy> out = new HashMap<String, Legacy>();
        String path = "/assets/" + BackportCore.MOD_ID + "/structure/item_names.txt";
        InputStream in = ModernItems.class.getResourceAsStream(path);
        if (in == null) {
            BackportCore.LOGGER.error("Today's item names {} are missing from the jar; only unchanged names resolve.", path);
            return out;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] cells = line.split(" ");
                out.put("minecraft:" + cells[0], new Legacy(new ResourceLocation("minecraft", cells[1]), Integer.parseInt(cells[2])));
            }
        } catch (IOException | RuntimeException e) {
            BackportCore.LOGGER.error("Could not read today's item names {}.", path, e);
        }
        return out;
    }
}
