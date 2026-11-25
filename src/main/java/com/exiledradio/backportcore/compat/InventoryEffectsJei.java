package com.exiledradio.backportcore.compat;

import com.exiledradio.backportcore.presentation.client.InventoryEffects;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import net.minecraft.client.renderer.InventoryEffectRenderer;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * JEI's and HEI's item list keeps clear of the active effects where 1.18 and later draw them, right of the inventory
 * ({@link InventoryEffects#areas}), as modern JEI keeps clear of vanilla's. JEI 1.12 knows only 1.12.2's place, left
 * of the inventory; its list sits right of it, under the effects. Only JEI (or HEI, same API) loads this class, on the
 * client.
 */
@JEIPlugin
public final class InventoryEffectsJei implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        registry.addAdvancedGuiHandlers(new Handler());
    }

    /** For every {@code InventoryEffectRenderer}: the survival and creative inventories, and mods' that extend it. */
    public static final class Handler implements IAdvancedGuiHandler<InventoryEffectRenderer> {

        @Override
        public Class<InventoryEffectRenderer> getGuiContainerClass() {
            return InventoryEffectRenderer.class;
        }

        @Override
        public List<Rectangle> getGuiExtraAreas(InventoryEffectRenderer gui) {
            int[] a = InventoryEffects.areas(gui);
            if (a.length == 0) {
                return null;
            }
            List<Rectangle> areas = new ArrayList<Rectangle>(a.length / 4);
            for (int i = 0; i < a.length; i += 4) {
                areas.add(new Rectangle(a[i], a[i + 1], a[i + 2], a[i + 3]));
            }
            return areas;
        }
    }
}
