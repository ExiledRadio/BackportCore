package com.exiledradio.backportcore.map;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.MapDecoration;
import net.minecraftforge.common.util.EnumHelper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Map decorations newer than 1.12.2 (today's {@code MapDecorationTypes}: the five village icons, the swamp hut, the
 * jungle temple, the trial chambers, the red X), shared by the backports so one map can carry another's icons.
 *
 * <p>1.12.2 numbers a decoration by its {@link MapDecoration.Type} ordinal and draws it from a 4 x 4 sheet,
 * {@code textures/map/map_icons.png}, already holding ten. A type added here is one more enum constant (so it saves,
 * syncs and colors the map item like vanilla's); patch P34 (docs/PATCHES.md) draws it from its own sprite,
 * {@code backportcore:textures/map/decorations/<name>.png} (today's file name), instead of the sheet.
 *
 * <p>Call {@link #get} in pre-init, on both sides, so a server and its clients number the types the same.
 */
public final class MapIcons {

    /** Today's {@code MapColor.COLOR_LIGHT_GRAY}, the map item color of the village, swamp hut and jungle maps. */
    public static final int LIGHT_GRAY = 10066329;
    /** Today's trial chambers map color. */
    public static final int TRIAL_CHAMBERS_COLOR = 12741452;

    /** Draws a type from its sprite. Set by the client; a dedicated server never draws. */
    public interface Renderer {
        void draw(ResourceLocation sprite, MapDecoration decoration, int index);
    }

    private static final Map<String, MapDecoration.Type> BY_NAME = new LinkedHashMap<String, MapDecoration.Type>();
    private static final Map<MapDecoration.Type, ResourceLocation> SPRITES = new HashMap<MapDecoration.Type, ResourceLocation>();
    private static volatile Renderer renderer;

    private MapIcons() {
    }

    /**
     * @param name     today's sprite name ({@code plains_village}, {@code swamp_hut}, {@code trial_chambers})
     * @param mapColor the map item's color, or -1 for none
     * @return the decoration type, added the first time any backport asks for it
     */
    public static synchronized MapDecoration.Type get(String name, int mapColor) {
        MapDecoration.Type type = BY_NAME.get(name);
        if (type == null) {
            String constant = "BACKPORT_" + name.toUpperCase(Locale.ROOT);
            type = EnumHelper.addEnum(MapDecoration.Type.class, constant, new Class<?>[] {boolean.class, int.class},
                    true, mapColor);
            if (type == null) {
                throw new IllegalStateException("Backport Core: cannot add map decoration " + constant);
            }
            BY_NAME.put(name, type);
            SPRITES.put(type, new ResourceLocation("backportcore", "textures/map/decorations/" + name + ".png"));
        }
        return type;
    }

    /** @return the type added under this name, or null if no backport asked for it */
    public static synchronized MapDecoration.Type find(String name) {
        return BY_NAME.get(name);
    }

    /** @return the sprite of a type added here, or null for vanilla's */
    public static synchronized ResourceLocation sprite(MapDecoration.Type type) {
        return SPRITES.get(type);
    }

    /** Client only. Internal. */
    public static void setRenderer(Renderer r) {
        renderer = r;
    }

    /**
     * P34: the body of Forge's {@code MapDecoration.render(int)}, which vanilla's map renderer asks first for each
     * decoration ("true to skip vanilla logic").
     *
     * <p>Public only because patched vanilla code calls it; not for backports to call.
     */
    public static boolean render(MapDecoration decoration, int index) {
        Renderer r = renderer;
        if (r == null) {
            return false;
        }
        ResourceLocation sprite = sprite(decoration.getType());
        if (sprite == null) {
            return false;
        }
        r.draw(sprite, decoration, index);
        return true;
    }
}
