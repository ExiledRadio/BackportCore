package com.exiledradio.backportcore.mechanics;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.BlockJukebox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Patch P41 (docs/PATCHES.md): a jukebox's comparator signal for discs added after 1.12.2. 1.12.2's
 * {@code BlockJukebox.getComparatorInputOverride} counts from the disc's item id, right for vanilla's twelve discs
 * only. Since 1.21 each song carries its own {@code comparator_output}; a backport's {@code ItemRecord} subclass gives
 * it by declaring {@code public int comparatorSignal()} (found by name, no Core type to implement). Other discs keep
 * 1.12.2's answer.
 */
public final class JukeboxSignal {

    private static final Map<Class<?>, Method> METHODS = new ConcurrentHashMap<Class<?>, Method>();
    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private JukeboxSignal() {
    }

    /** @return the disc's own signal, 0 to 15, or -1 when the jukebox holds no disc that gives one */
    public static int signal(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof BlockJukebox.TileEntityJukebox)) {
            return -1;
        }
        return signal(((BlockJukebox.TileEntityJukebox) te).getRecord());
    }

    /** @return this disc's own signal, 0 to 15, or -1 when it gives none */
    public static int signal(ItemStack disc) {
        if (disc == null || disc.isEmpty()) {
            return -1;
        }
        Method m = method(disc.getItem());
        if (m == NONE) {
            return -1;
        }
        try {
            return Math.max(0, Math.min(15, ((Number) m.invoke(disc.getItem())).intValue()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    /** Hook of P41: true when {@link #signal(World, BlockPos)} answers. */
    public static boolean has(World world, BlockPos pos) {
        return signal(world, pos) >= 0;
    }

    private static Method method(Item item) {
        Class<?> c = item.getClass();
        Method m = METHODS.get(c);
        if (m == null) {
            m = NONE;
            try {
                Method found = c.getMethod("comparatorSignal");
                if (found.getReturnType() == int.class) {
                    found.setAccessible(true);
                    m = found;
                }
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
            METHODS.put(c, m);
        }
        return m;
    }
}
