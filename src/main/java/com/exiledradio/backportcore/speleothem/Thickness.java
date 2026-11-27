package com.exiledradio.backportcore.speleothem;

import net.minecraft.util.IStringSerializable;

/** 26.4's {@code SpeleothemThickness} (1.17's {@code DripstoneThickness}), in vanilla's order. */
public enum Thickness implements IStringSerializable {
    TIP_MERGE("tip_merge"), TIP("tip"), FRUSTUM("frustum"), MIDDLE("middle"), BASE("base");

    private final String name;

    Thickness(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
