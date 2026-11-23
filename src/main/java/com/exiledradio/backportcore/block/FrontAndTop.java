package com.exiledradio.backportcore.block;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;

/**
 * Today's {@code FrontAndTop}: a jigsaw block's {@code orientation}, the way it faces and which way is up for what joins
 * it. The twelve values in 26.3's order, so a block's metadata is the ordinal.
 *
 * @since 1.4.5
 */
public enum FrontAndTop implements IStringSerializable {
    DOWN_EAST("down_east", EnumFacing.DOWN, EnumFacing.EAST),
    DOWN_NORTH("down_north", EnumFacing.DOWN, EnumFacing.NORTH),
    DOWN_SOUTH("down_south", EnumFacing.DOWN, EnumFacing.SOUTH),
    DOWN_WEST("down_west", EnumFacing.DOWN, EnumFacing.WEST),
    UP_EAST("up_east", EnumFacing.UP, EnumFacing.EAST),
    UP_NORTH("up_north", EnumFacing.UP, EnumFacing.NORTH),
    UP_SOUTH("up_south", EnumFacing.UP, EnumFacing.SOUTH),
    UP_WEST("up_west", EnumFacing.UP, EnumFacing.WEST),
    WEST_UP("west_up", EnumFacing.WEST, EnumFacing.UP),
    EAST_UP("east_up", EnumFacing.EAST, EnumFacing.UP),
    NORTH_UP("north_up", EnumFacing.NORTH, EnumFacing.UP),
    SOUTH_UP("south_up", EnumFacing.SOUTH, EnumFacing.UP);

    private static final FrontAndTop[] BY_FRONT_TOP = new FrontAndTop[36];

    static {
        for (FrontAndTop v : values()) {
            BY_FRONT_TOP[v.front.getIndex() * 6 + v.top.getIndex()] = v;
        }
    }

    private final String name;
    private final EnumFacing front;
    private final EnumFacing top;

    FrontAndTop(String name, EnumFacing front, EnumFacing top) {
        this.name = name;
        this.front = front;
        this.top = top;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public EnumFacing front() {
        return this.front;
    }

    public EnumFacing top() {
        return this.top;
    }

    /** @return the value, or null for a pair no jigsaw has (front and top on one axis) */
    public static FrontAndTop of(EnumFacing front, EnumFacing top) {
        return BY_FRONT_TOP[front.getIndex() * 6 + top.getIndex()];
    }

    /** @return the value of this name ({@code north_up}), or null */
    public static FrontAndTop byName(String name) {
        for (FrontAndTop v : values()) {
            if (v.name.equals(name)) {
                return v;
            }
        }
        return null;
    }

    /** Today's {@code rotation.rotation().rotate}: a turn about the Y axis turns both. */
    public FrontAndTop rotate(Rotation rotation) {
        FrontAndTop out = of(rotation.rotate(this.front), rotation.rotate(this.top));
        return out == null ? this : out;
    }

    /** Today's {@code mirror.rotation().rotate}. */
    public FrontAndTop mirror(Mirror mirror) {
        FrontAndTop out = of(mirror.mirror(this.front), mirror.mirror(this.top));
        return out == null ? this : out;
    }
}
