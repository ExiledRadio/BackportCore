package com.exiledradio.backportcore.structure;

import java.util.HashSet;
import java.util.Set;

/** Ready-made {@link TemplateProcessor}s for the rules today's structures use most. */
public final class TemplateProcessors {

    private TemplateProcessors() {
    }

    /** Today's {@code BlockIgnoreProcessor}: these blocks of the file are left out. */
    public static TemplateProcessor ignore(String... modernNames) {
        Set<String> names = new HashSet<String>();
        for (String name : modernNames) {
            names.add(ModernPalette.full(name));
        }
        return (world, pos, block, random) -> names.contains(block.name()) ? null : block;
    }

    /**
     * Today's rule processor with a {@code random_block_match}: each {@code modernName} block becomes
     * {@code replacement} with this chance ("30% of the stone bricks are cracked").
     *
     * @param replacement a state as today's game writes it, {@code minecraft:cracked_stone_bricks}
     */
    public static TemplateProcessor replace(String modernName, float chance, String replacement) {
        String name = ModernPalette.full(modernName);
        return (world, pos, block, random) -> block.name().equals(name) && random.nextFloat() < chance
                ? block.replacedBy(replacement) : block;
    }

    /** {@link #replace} for several blocks at once, each with the same chance and replacement. */
    public static TemplateProcessor replaceAny(float chance, String replacement, String... modernNames) {
        Set<String> names = new HashSet<String>();
        for (String name : modernNames) {
            names.add(ModernPalette.full(name));
        }
        return (world, pos, block, random) -> names.contains(block.name()) && random.nextFloat() < chance
                ? block.replacedBy(replacement) : block;
    }
}
