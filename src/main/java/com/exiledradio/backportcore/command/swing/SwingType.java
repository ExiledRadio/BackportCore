package com.exiledradio.backportcore.command.swing;

import javax.annotation.Nullable;

/** 26.3's {@code SwingAnimationType}, ids as its stream codec. */
public enum SwingType {
    NONE(0, "none"),
    WHACK(1, "whack"),
    STAB(2, "stab");

    public final int id;
    public final String name;

    SwingType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Nullable
    public static SwingType byName(String name) {
        for (SwingType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return null;
    }

    /** {@code ByIdMap.OutOfBoundsStrategy.ZERO}: an unknown id is {@code none}. */
    public static SwingType byId(int id) {
        for (SwingType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return NONE;
    }

    static String[] names() {
        String[] names = new String[values().length];
        for (int i = 0; i < names.length; i++) {
            names[i] = values()[i].name;
        }
        return names;
    }
}
