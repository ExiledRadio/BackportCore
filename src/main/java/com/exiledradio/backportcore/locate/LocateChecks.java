package com.exiledradio.backportcore.locate;

import com.exiledradio.backportcore.structure.ScatteredPlacement;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Checks the backports' self-tests (and a curious developer) run against {@link StructureLocate}: that a located
 * start is the one today's search order picks, that the world's generator really lays a start out there, and
 * what {@code /locate} says. Loads chunks only in {@link #generateAround}.
 */
public final class LocateChecks {

    private LocateChecks() {
    }

    /**
     * Walks every wanted grid region by region (not sector by sector, as the search does) and checks that no
     * ring nearer than the found one starts anything wanted, and that the found start is the nearest of the
     * found ring's first starts.
     *
     * @return null when the result is today's, else what is wrong
     */
    @Nullable
    public static String checkRingOrder(WorldServer world, BlockPos from, String idOrTag, StructureLocate.Found found) {
        String key = StructureLocate.normalize(idOrTag);
        Collection<String> wanted = key.startsWith("#") ? StructureLocate.members(key) : Collections.singleton(key);
        List<StructureLocate.Placement> placements = new ArrayList<StructureLocate.Placement>();
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, StructureLocate.PlacementFactory> e : StructureLocate.factoriesFor(wanted).entrySet()) {
            try {
                StructureLocate.Placement p = e.getValue().in(world);
                if (p != null) {
                    placements.add(p);
                    names.add(e.getKey());
                }
            } catch (StructureLocate.Unavailable u) {
                // Not in this world.
            }
        }
        if (placements.isEmpty()) {
            return "no grid for " + key + " in this world";
        }
        long seed = world.getSeed();
        int fromX = from.getX() >> 4;
        int fromZ = from.getZ() >> 4;
        int foundX = found.pos.getX() >> 4;
        int foundZ = found.pos.getZ() >> 4;
        int ring = -1;
        for (StructureLocate.Placement p : placements) {
            ScatteredPlacement g = p.grid();
            ChunkPos c = g.candidate(seed, foundX, foundZ);
            if (c.x == foundX && c.z == foundZ && wanted.contains(p.startAt(foundX, foundZ))) {
                int r = Math.max(Math.abs(Math.floorDiv(foundX, g.spacing()) - Math.floorDiv(fromX, g.spacing())),
                        Math.abs(Math.floorDiv(foundZ, g.spacing()) - Math.floorDiv(fromZ, g.spacing())));
                ring = ring < 0 ? r : Math.min(ring, r);
            }
        }
        if (ring < 0) {
            return "chunk " + foundX + ", " + foundZ + " is no wanted grid's start";
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i < placements.size(); i++) {
            StructureLocate.Placement p = placements.get(i);
            ScatteredPlacement g = p.grid();
            int ox = Math.floorDiv(fromX, g.spacing());
            int oz = Math.floorDiv(fromZ, g.spacing());
            for (int r = 0; r <= ring; r++) {
                for (int rx = ox - r; rx <= ox + r; rx++) {
                    for (int rz = oz - r; rz <= oz + r; rz++) {
                        if (Math.max(Math.abs(rx - ox), Math.abs(rz - oz)) != r) {
                            continue;
                        }
                        ChunkPos c = g.candidate(seed, rx * g.spacing(), rz * g.spacing());
                        String id = p.startAt(c.x, c.z);
                        if (id == null || !wanted.contains(id)) {
                            continue;
                        }
                        if (r < ring) {
                            return names.get(i) + " starts " + id + " at chunk " + c.x + ", " + c.z + " in ring " + r
                                    + ", nearer than the found ring " + ring;
                        }
                        // The first of this ring in x-outer, z-inner order is the one today's search takes.
                        best = Math.min(best, from.distanceSq(p.locatePos(c.x, c.z)));
                        rx = ox + r + 1;
                        break;
                    }
                }
            }
        }
        double d = from.distanceSq(found.pos);
        return d <= best ? null : "found " + found + " at distance " + Math.sqrt(d) + ", but ring " + ring
                + " has one at " + Math.sqrt(best);
    }

    /** Loads (generating as needed) every chunk within {@code radius} of a chunk, so starts there are laid out and populated. */
    public static void generateAround(WorldServer world, int chunkX, int chunkZ, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.getChunk(chunkX + dx, chunkZ + dz);
            }
        }
    }

    /** @return the start a structure generator has laid out in a chunk (generated or loaded), or null */
    @Nullable
    public static StructureStart savedStart(MapGenStructure generator, int chunkX, int chunkZ) {
        Map<Long, StructureStart> map = ReflectionHelper.getPrivateValue(MapGenStructure.class, generator,
                "structureMap", "field_75053_d");
        return map.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** Runs a command as an operator standing at {@code at} in {@code world}, and returns what it said. */
    public static List<String> run(MinecraftServer server, WorldServer world, BlockPos at, String command) {
        Sender sender = new Sender(server, world, at);
        server.getCommandManager().executeCommand(sender, command);
        return sender.said;
    }

    /** Tab completion of a command line, as an operator at {@code at}. */
    public static List<String> complete(MinecraftServer server, WorldServer world, BlockPos at, String line) {
        return server.getCommandManager().getTabCompletions(new Sender(server, world, at), line, at);
    }

    private static final class Sender implements ICommandSender {
        private final MinecraftServer server;
        private final WorldServer world;
        private final BlockPos at;
        final List<String> said = new ArrayList<String>();

        Sender(MinecraftServer server, WorldServer world, BlockPos at) {
            this.server = server;
            this.world = world;
            this.at = at;
        }

        @Override
        public String getName() {
            return "LocateCheck";
        }

        @Override
        public ITextComponent getDisplayName() {
            return new TextComponentString(getName());
        }

        @Override
        public void sendMessage(ITextComponent component) {
            this.said.add(component.getUnformattedText());
        }

        @Override
        public boolean canUseCommand(int permLevel, String commandName) {
            return true;
        }

        @Override
        public BlockPos getPosition() {
            return this.at;
        }

        @Override
        public Vec3d getPositionVector() {
            return new Vec3d(this.at.getX() + 0.5, this.at.getY(), this.at.getZ() + 0.5);
        }

        @Override
        public World getEntityWorld() {
            return this.world;
        }

        @Nullable
        @Override
        public Entity getCommandSenderEntity() {
            return null;
        }

        @Override
        public boolean sendCommandFeedback() {
            return true;
        }

        @Nullable
        @Override
        public MinecraftServer getServer() {
            return this.server;
        }
    }
}
