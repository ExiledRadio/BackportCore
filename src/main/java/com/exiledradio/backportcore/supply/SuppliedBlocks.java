package com.exiledradio.backportcore.supply;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.structure.ModernBlockScan;
import com.exiledradio.backportcore.structure.ModernPalette;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSlab;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Modern blocks Core supplies itself (docs/PLAN_MODERN_BLOCKS.md): decorative copies, {@code backportcore:<name>}, of
 * the blocks the structure files use that nothing else in the game provides.
 *
 * <h2>Which blocks</h2>
 * A row of {@link SuppliedTable} is registered when a structure file names it ({@link ModernBlockScan}) or
 * {@code config/backportcore/supplied_blocks.txt} lists it, and
 * <ul>
 * <li>its owner (the backport the table names) has not registered the block: when the owner has, structures place
 * the owner's block, with its behavior, and Core registers nothing;</li>
 * <li>and, for a block Core has never supplied, no other source gives it ({@link ModernPalette}'s mappings, a family
 * mod's block, 1.12.2's own).</li>
 * </ul>
 * Every block Core registers goes into {@code supplied_blocks.txt} and stays there, so removing a structure pack never
 * removes those blocks from a world.
 *
 * <h2>Names</h2>
 * Core's copies have their owner's metadata layout. When the owner is added to a world, Core's names go missing and
 * become the owner's ({@code backportcore:deepslate} to {@code cavesandcliffsbackport:deepslate}); when the owner is
 * removed, its names become Core's copies, for every block Core registers. Items follow their blocks.
 *
 * @since 1.5.0
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class SuppliedBlocks {

    /** Registered this game: name to block (slabs' doubles under their own path). */
    private static final Map<String, Block> BLOCKS = new LinkedHashMap<String, Block>();
    /** For the self-test and the log. */
    public static volatile int remapped;

    private SuppliedBlocks() {
    }

    /** @return Core's registered copy of this block (today's path, or a slab's {@code _double_slab}), or null */
    @Nullable
    public static synchronized Block block(String path) {
        return BLOCKS.get(path);
    }

    /** @return every block Core supplies in this game, by path */
    public static synchronized Map<String, Block> blocks() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Block>(BLOCKS));
    }

    /** @return the file listing every block Core has supplied in this instance */
    public static File listFile() {
        return new File(Loader.instance().getConfigDir(), "backportcore/supplied_blocks.txt");
    }

    // ---- registering

    /** Late, so every other mod's blocks are in and the owners' are known. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static synchronized void registerBlocks(RegistryEvent.Register<Block> event) {
        BLOCKS.clear();
        Set<String> listed = readList();
        Map<String, ModernBlockScan.Use> used = ModernBlockScan.uses();
        Set<String> kept = new LinkedHashSet<String>(listed);
        int owned = 0;
        for (SuppliedTable.Row row : SuppliedTable.rows().values()) {
            boolean wanted = listed.contains(row.name) || used.containsKey("minecraft:" + row.name);
            if (!wanted) {
                continue;
            }
            if (ownerHas(row)) {
                owned++;
                continue;
            }
            if (!listed.contains(row.name) && providedElsewhere(row)) {
                continue;
            }
            Block block = SuppliedShapes.create(row);
            name(block, row.name);
            event.getRegistry().register(block);
            BLOCKS.put(row.name, block);
            if (block instanceof SuppliedShapes.Slab) {
                Block full = SuppliedShapes.doubleSlab(row, (SuppliedShapes.Slab) block);
                name(full, row.doubleSlab());
                event.getRegistry().register(full);
                BLOCKS.put(row.doubleSlab(), full);
            }
            kept.add(row.name);
        }
        if (!kept.equals(listed)) {
            writeList(kept);
        }
        if (!BLOCKS.isEmpty() || owned > 0) {
            BackportCore.LOGGER.info("Supplied blocks: {} registered, {} left to their installed backports.", BLOCKS.size(), owned);
        }
    }

    @SubscribeEvent
    public static synchronized void registerItems(RegistryEvent.Register<Item> event) {
        for (Map.Entry<String, Block> e : BLOCKS.entrySet()) {
            Block block = e.getValue();
            Item item;
            if (block instanceof SuppliedShapes.DoubleSlab) {
                continue;
            } else if (block instanceof SuppliedShapes.HalfSlab) {
                SuppliedTable.Row row = ((SuppliedShapes.Slab) block).row;
                item = new ItemSlab(block, (SuppliedShapes.Slab) block, (SuppliedShapes.Slab) BLOCKS.get(row.doubleSlab()));
            } else {
                item = new ItemBlock(block);
            }
            item.setRegistryName(block.getRegistryName());
            event.getRegistry().register(item);
        }
    }

    private static void name(Block block, String path) {
        block.setRegistryName(new ResourceLocation(BackportCore.MOD_ID, path));
        block.setTranslationKey(BackportCore.MOD_ID + "." + path);
    }

    /** @return true when the row's owner is installed and registered its block */
    static boolean ownerHas(SuppliedTable.Row row) {
        return row.owner != null && Loader.isModLoaded(row.owner) && registered(new ResourceLocation(row.owner, row.ownerName)) != null;
    }

    /** @return true if a structure would get this block from another source today */
    private static boolean providedElsewhere(SuppliedTable.Row row) {
        try {
            ModernPalette.Resolved r = ModernPalette.resolveUpToDate("minecraft:" + row.name, Collections.<String, String>emptyMap());
            return r.via != null && !r.via.startsWith("stand-in") && !"supplied".equals(r.via);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Nullable
    static Block registered(ResourceLocation id) {
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            return null;
        }
        Block b = ForgeRegistries.BLOCKS.getValue(id);
        return b == Blocks.AIR ? null : b;
    }

    // ---- supplied_blocks.txt

    private static Set<String> readList() {
        Set<String> out = new LinkedHashSet<String>();
        File f = listFile();
        if (f.isFile()) {
            try {
                for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    String n = line.trim();
                    if (!n.isEmpty() && !n.startsWith("#")) {
                        out.add(n.startsWith("minecraft:") ? n.substring("minecraft:".length()) : n);
                    }
                }
            } catch (IOException e) {
                BackportCore.LOGGER.error("Could not read {}.", f, e);
            }
        }
        return out;
    }

    private static void writeList(Set<String> names) {
        File f = listFile();
        f.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(f, "UTF-8")) {
            w.println("# Modern blocks Backport Core has supplied in this instance (backportcore:<name>). Core keeps registering");
            w.println("# each of them, so worlds that hold them keep them. Removing a line can remove that block from your worlds.");
            for (String n : names) {
                w.println(n);
            }
        } catch (IOException e) {
            BackportCore.LOGGER.error("Could not write {}.", f, e);
        }
    }

    // ---- renames between Core's copies and their owners'

    /** Core's copy, missing because the owner now has the block, becomes the owner's; the owner's, missing, becomes Core's. */
    @SubscribeEvent
    public static void missingBlocks(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> m : event.getAllMappings()) {
            ResourceLocation to = target(m.key);
            Block block = to == null ? null : registered(to);
            if (block != null) {
                m.remap(block);
                log(m.key, to);
            }
        }
    }

    @SubscribeEvent
    public static void missingItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> m : event.getAllMappings()) {
            ResourceLocation to = target(m.key);
            Item item = to == null || !ForgeRegistries.ITEMS.containsKey(to) ? null : ForgeRegistries.ITEMS.getValue(to);
            if (item != null) {
                m.remap(item);
                log(m.key, to);
            }
        }
    }

    /** @return the name a missing one becomes: Core's to the owner's, the owner's to Core's; null for anything else */
    @Nullable
    static ResourceLocation target(ResourceLocation missing) {
        String ns = missing.getNamespace();
        String path = missing.getPath();
        for (SuppliedTable.Row row : SuppliedTable.rows().values()) {
            if (row.owner == null) {
                continue;
            }
            boolean slab = "slab".equals(row.shape);
            if (BackportCore.MOD_ID.equals(ns)) {
                if (path.equals(row.name)) {
                    return new ResourceLocation(row.owner, row.ownerName);
                }
                if (slab && path.equals(row.doubleSlab())) {
                    return new ResourceLocation(row.owner, row.ownerDoubleSlab());
                }
            } else if (row.owner.equals(ns) && block(row.name) != null) {
                if (path.equals(row.ownerName)) {
                    return new ResourceLocation(BackportCore.MOD_ID, row.name);
                }
                if (slab && path.equals(row.ownerDoubleSlab())) {
                    return new ResourceLocation(BackportCore.MOD_ID, row.doubleSlab());
                }
            }
        }
        return null;
    }

    private static void log(ResourceLocation from, ResourceLocation to) {
        remapped++;
        BackportCore.LOGGER.info("Supplied blocks: {} is now {}.", from, to);
    }
}
