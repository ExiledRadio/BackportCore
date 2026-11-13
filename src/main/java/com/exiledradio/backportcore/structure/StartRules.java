package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The start rule a world's chunk generator gives a structure kind ({@link StructureSource}), for structures that
 * place themselves on a grid of their own. A {@link ScatteredStructure} asks for itself; a mod with its own
 * {@code MapGenStructure} (Village &amp; Pillage's outposts) asks here in {@code canSpawnStructureAtCoords} and its
 * locator:
 *
 * <pre>
 * StartRule rule = StartRules.of(world, Structures.PILLAGER_OUTPOST);
 * if (rule != null) {
 *     return rule.startAt(chunkX, chunkZ) != null;   // and rule.grid() for the nearest search
 * }
 * // ...the kind's own rule
 * </pre>
 */
public final class StartRules {

    /** Marks a kind the world's generator does not rule. */
    private static final StartRule NONE = new StartRule() {
        @Override
        public ScatteredPlacement grid() {
            throw new IllegalStateException();
        }

        @Override
        public String startAt(int chunkX, int chunkZ) {
            return null;
        }
    };

    private static final Map<World, Map<String, StartRule>> BY_WORLD = new WeakHashMap<World, Map<String, StartRule>>();

    private StartRules() {
    }

    /**
     * @return the rule this world's chunk generator gives the kind, or null where the kind keeps its own (no
     * generator that rules structures, a kind it does not know, a client world)
     */
    @Nullable
    public static synchronized StartRule of(World world, String kind) {
        if (world == null || world.isRemote || !(world instanceof WorldServer)) {
            return null;
        }
        Map<String, StartRule> known = BY_WORLD.get(world);
        if (known == null) {
            known = new HashMap<String, StartRule>();
            BY_WORLD.put(world, known);
        }
        StartRule rule = known.get(kind);
        if (rule == null) {
            ChunkProviderServer provider = ((WorldServer) world).getChunkProvider();
            IChunkGenerator generator = provider == null ? null : provider.chunkGenerator;
            if (generator == null) {
                // Too early (the world's constructor): ask again later.
                return null;
            }
            rule = generator instanceof StructureSource ? ((StructureSource) generator).startRule(world, kind) : null;
            if (rule == null) {
                rule = NONE;
            } else {
                BackportCore.LOGGER.debug("{} decides where {} starts in {}.", generator.getClass().getSimpleName(), kind,
                        world.getWorldInfo().getWorldName());
            }
            known.put(kind, rule);
        }
        return rule == NONE ? null : rule;
    }

    /** @return true if the world's chunk generator decides where this kind starts */
    public static boolean ruled(World world, String kind) {
        return of(world, kind) != null;
    }
}
