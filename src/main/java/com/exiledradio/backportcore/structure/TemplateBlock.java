package com.exiledradio.backportcore.structure;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One block of a {@link ModernTemplate} on its way into the world, as a {@link TemplateProcessor}
 * sees it: today's name and properties (what processors written against today's game match on),
 * and the 1.12.2 state that will be placed. Immutable; the {@code with} methods return copies.
 */
public final class TemplateBlock {

    private final String name;
    private final Map<String, String> props;
    @Nullable
    private final IBlockState state;
    @Nullable
    private final NBTTagCompound tile;
    private final boolean water;

    TemplateBlock(String name, Map<String, String> props, @Nullable IBlockState state, @Nullable NBTTagCompound tile,
                  boolean water) {
        this.name = name;
        this.props = Collections.unmodifiableMap(props);
        this.state = state;
        this.tile = tile;
        this.water = water;
    }

    /** @return the block as a structure file of today's game names it: {@code minecraft:stone_bricks} */
    public String name() {
        return this.name;
    }

    /** @return true if this is the named block (today's name; {@code minecraft:} may be left off) */
    public boolean is(String modernName) {
        return this.name.equals(ModernPalette.full(modernName));
    }

    /** @return the file's properties, today's names and values, {@code waterlogged} included */
    public Map<String, String> props() {
        return this.props;
    }

    /** @return the 1.12.2 state to place, or null if nothing provides this block */
    @Nullable
    public IBlockState state() {
        return this.state;
    }

    /** @return a copy of the tile entity data to load into the placed block, or null */
    @Nullable
    public NBTTagCompound tile() {
        return this.tile == null ? null : this.tile.copy();
    }

    /** @return true if the block gets a water source */
    public boolean water() {
        return this.water;
    }

    /** @return this block with another 1.12.2 state, everything else kept */
    public TemplateBlock withState(@Nullable IBlockState state) {
        return new TemplateBlock(this.name, this.props, state, this.tile, this.water);
    }

    /** @return this block with or without water */
    public TemplateBlock withWater(boolean water) {
        return new TemplateBlock(this.name, this.props, this.state, this.tile, water);
    }

    /** @return this block with other tile entity data */
    public TemplateBlock withTile(@Nullable NBTTagCompound tile) {
        return new TemplateBlock(this.name, this.props, this.state, tile == null ? null : tile.copy(), this.water);
    }

    /**
     * @return a different block, written as today's game writes a state
     * ({@code minecraft:cracked_stone_bricks}, {@code minecraft:oak_stairs[facing=east]}); the tile
     * data is dropped, the water kept
     */
    public TemplateBlock replacedBy(String modernState) {
        Map<String, String> p = new LinkedHashMap<String, String>();
        String n = ModernPalette.full(Flattening.parse(modernState.trim(), p));
        ModernPalette.Resolved r = ModernPalette.resolve(n, p, Integer.MAX_VALUE);
        return new TemplateBlock(n, p, r.state, r.tile, this.water || r.water);
    }

    @Override
    public String toString() {
        return this.name + this.props + " -> " + this.state;
    }
}
