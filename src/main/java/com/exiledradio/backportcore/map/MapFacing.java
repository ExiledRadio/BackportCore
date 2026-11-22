package com.exiledradio.backportcore.map;

import com.exiledradio.backportcore.presentation.Presentation;
import javax.annotation.Nullable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapDecoration;

/**
 * 26.3: a player marker off the map ({@code player_off_map}, {@code player_off_limits}) turns with the player as the
 * on-map one does ({@code MapItemSavedData.calculateRotation}: the Nether's spin included), and is drawn from 26.3's
 * pointed sprites ({@link #sprite}); 1.12.2 and 1.21.11 always give it rotation 0 and a round dot. With Game Drops
 * Backport installed (a backport registering 26.3) only; otherwise 1.12.2's.
 *
 * <p>Patch P43's hooks ({@code MapData.updateDecorations}); public only because patched vanilla code calls them.
 */
public final class MapFacing {

    private static final class Call {
        World world;
        String name;
        double x;
        double z;
        double rotation;
    }

    private static final ThreadLocal<Call> CALL = ThreadLocal.withInitial(Call::new);

    private MapFacing() {
    }

    /** @return true when the installed backports reach 26.3 */
    public static boolean modern() {
        return Presentation.installedNewest().isAtLeast("26.3");
    }

    private static final ResourceLocation OFF_MAP = new ResourceLocation("backportcore", "textures/map/decorations/player_off_map.png");
    private static final ResourceLocation OFF_LIMITS = new ResourceLocation("backportcore", "textures/map/decorations/player_off_limits.png");

    /**
     * @return 26.3's pointed sprite of an off-map or off-limits player marker (it turns, so 1.12.2's round dots
     *         would not show it), or null for 1.12.2's sheet
     */
    @Nullable
    static ResourceLocation sprite(MapDecoration.Type type) {
        if (type == MapDecoration.Type.PLAYER_OFF_MAP) {
            return modern() ? OFF_MAP : null;
        }
        if (type == MapDecoration.Type.PLAYER_OFF_LIMITS) {
            return modern() ? OFF_LIMITS : null;
        }
        return null;
    }

    /** For tests: {@link #sprite}. */
    public static ResourceLocation spriteOf(MapDecoration.Type type) {
        return sprite(type);
    }

    public static void enter(World world, String name, double x, double z, double rotation) {
        Call c = CALL.get();
        c.world = world;
        c.name = name;
        c.x = x;
        c.z = z;
        c.rotation = rotation;
    }

    public static MapDecoration facing(MapDecoration decoration) {
        MapDecoration.Type type = decoration.getType();
        if (type != MapDecoration.Type.PLAYER_OFF_MAP && type != MapDecoration.Type.PLAYER_OFF_LIMITS || !modern()) {
            return decoration;
        }
        Call c = CALL.get();
        return new MapDecoration(type, decoration.getX(), decoration.getY(), rotation(world(c), c.name, c.x, c.z, c.rotation));
    }

    private static World world(Call c) {
        World w = c.world;
        c.world = null;
        return w;
    }

    /** 1.12.2's on-map rotation, as 26.3's {@code calculateRotation}: 16 steps, the Nether's spin through Forge's hook. */
    static byte rotation(World world, String name, double x, double z, double rotation) {
        double adjusted = rotation + (rotation < 0.0D ? -8.0D : 8.0D);
        byte r = (byte) ((int) (adjusted * 16.0D / 360.0D));
        if (world != null && world.provider.shouldMapSpin(name, x, z, adjusted)) {
            int l = (int) (world.getWorldTime() / 10L);
            r = (byte) (l * l * 34187121 + l * 121 >> 15 & 15);
        }
        return r;
    }
}
