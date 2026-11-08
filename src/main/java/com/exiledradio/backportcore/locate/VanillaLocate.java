package com.exiledradio.backportcore.locate;

import com.exiledradio.backportcore.structure.NetherStructureSlots;
import com.exiledradio.backportcore.structure.VanillaPlacements;
import com.exiledradio.backportcore.worldgen.ModernTemples;
import com.exiledradio.backportcore.worldgen.StructurePlacements;

/**
 * The structures 1.12.2 already has, under today's ids, so {@code /locate structure minecraft:stronghold} works
 * beside {@code /locate Stronghold}. Registered by Core in init.
 *
 * <ul>
 *   <li>Strongholds, monuments, mansions, end cities and mineshafts: 1.12.2's own search, whichever generator the
 *       world has.</li>
 *   <li>Fortresses: on 1.16.5's shared nether grid when a backport turned it on ({@link NetherStructureSlots}),
 *       else 1.12.2's search.</li>
 *   <li>Desert pyramids, jungle temples, swamp huts and igloos: only when the world's temples are Core's
 *       {@link ModernTemples} (a 1.13 or later backport installed), each on its own grid. 1.12.2's temples share
 *       one grid and are found with {@code /locate Temple}.</li>
 *   <li>{@code #minecraft:village}: 1.12.2's village search until a backport registers today's village types.</li>
 * </ul>
 */
public final class VanillaLocate {

    private VanillaLocate() {
    }

    public static void register() {
        legacy("minecraft:stronghold", "Stronghold");
        legacy("minecraft:monument", "Monument");
        legacy("minecraft:mansion", "Mansion");
        legacy("minecraft:end_city", "EndCity");
        legacy("minecraft:mineshaft", "Mineshaft");
        legacy("#minecraft:village", "Village");

        StructureLocate.placement("minecraft:nether_complexes/fortress", world -> {
            if (world.provider.getDimension() != -1 || !NetherStructureSlots.fortressRuleActive()
                    || !NetherStructureSlots.drives(world)) {
                return null;
            }
            return new StructureLocate.Grid(VanillaPlacements.V1_16_5.NETHER_BRIDGE,
                    (x, z) -> NetherStructureSlots.fortressStartsAt(world, x, z) ? "minecraft:fortress" : null);
        }, "minecraft:fortress");
        legacy("minecraft:fortress", "Fortress");

        if (StructurePlacements.isAtLeast("1.13")) {
            String[] ids = ModernTemples.ids();
            for (int i = 0; i < ids.length; i++) {
                final int kind = i;
                final String id = ids[i];
                StructureLocate.placement(id + "s", world -> {
                    ModernTemples temples = ModernTemples.of(world);
                    return temples == null ? null
                            : new StructureLocate.Grid(temples.grid(kind), (x, z) -> temples.kindAt(world, x, z) == kind ? id : null);
                }, id);
            }
        }
    }

    private static void legacy(String idOrTag, final String name) {
        StructureLocate.finder(idOrTag, (world, from) -> world.findNearestStructure(name, from, false));
    }
}
