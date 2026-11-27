package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.water.WaterColors;
import com.exiledradio.backportcore.worldgen.DeferredPlacement;
import com.exiledradio.backportcore.worldgen.UndergroundBiomes;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;

import java.util.Arrays;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.4.4's world generation answers to Game Drops Backport's sulfur caves: {@link DeferredPlacement} (writes past
 * the population window, finished when the chunks are ready, saved with the world) and {@link UndergroundBiomes}
 * (column and height spans, saved with the chunk; water colors and patch P44's color blend read them).
 */
final class GdWorldgenTests {

    private static final ResourceLocation ID = new ResourceLocation("backportcore", "selftest_deferred");
    private static int ran;

    private GdWorldgenTests() {
    }

    private static void generate(WorldServer world, int cx, int cz, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                world.getChunk(cx + dx, cz + dz);
            }
        }
    }

    static void schedule() {
        then(1, "GD sulfur gaps: deferred placement waits for its chunks", world -> {
            DeferredPlacement.register(ID, (w, min, max, data) -> {
                ran++;
                w.setBlockState(min.up(data.getInteger("Up")), Blocks.GLASS.getDefaultState(), 2);
            });
            // Far from anything generated: chunk (5000, 5000).
            int cx = 5000, cz = 5000;
            BlockPos at = new BlockPos(cx * 16 + 3, 60, cz * 16 + 4);
            check("the target chunk is not generated", world.getChunkProvider().getLoadedChunk(cx, cz) == null
                    && !world.isChunkGeneratedAt(cx, cz), "");
            int before = DeferredPlacement.pending(world);
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("Up", 1);
            DeferredPlacement.queue(world, ID, at, at.add(0, 4, 0), data);
            check("queued: one more entry waits", DeferredPlacement.pending(world) == before + 1, DeferredPlacement.pending(world));
            check("not ready while ungenerated", !DeferredPlacement.ready(world, at.getX(), at.getZ(), at.getX(), at.getZ()), "");
            ran = 0;
            DeferredPlacement.flush(world);
            check("flush runs nothing yet, loads nothing", ran == 0 && world.getChunkProvider().getLoadedChunk(cx, cz) == null, ran);
            // The chunk alone: generated, but neither it nor its -x/-z neighbors have populated.
            world.getChunk(cx, cz);
            check("generated alone, not populated: not ready",
                    !DeferredPlacement.ready(world, at.getX(), at.getZ(), at.getX(), at.getZ()), "");
            // The 3 by 3 around it: the chunk and its -x, -z, -x -z neighbors all populate.
            generate(world, cx, cz, 1);
            check("with its neighbors populated: ready", DeferredPlacement.ready(world, at.getX(), at.getZ(), at.getX(), at.getZ()), "");
            int n = DeferredPlacement.flush(world);
            check("flush runs it once", n >= 1 && ran == 1, n + " ran, handler " + ran);
            check("the handler wrote with its data", world.getBlockState(at.up()).getBlock() == Blocks.GLASS, world.getBlockState(at.up()));
            check("the entry is gone", DeferredPlacement.pending(world) == before, DeferredPlacement.pending(world));
            DeferredPlacement.flush(world);
            check("never twice", ran == 1, ran);
        });

        then(1, "GD sulfur gaps: deferred placement across a box and saved", world -> {
            int cx = 5100, cz = 5100;
            // A box over two chunks: ready only when both (and their -x/-z neighbors) are.
            BlockPos a = new BlockPos(cx * 16 + 14, 50, cz * 16 + 2), b = new BlockPos(cx * 16 + 18, 52, cz * 16 + 3);
            DeferredPlacement.queue(world, ID, a, b, new NBTTagCompound());
            generate(world, cx, cz, 1);
            check("half the box ready, the other chunk's +x neighbor missing: not ready",
                    !DeferredPlacement.ready(world, a.getX(), a.getZ(), b.getX(), b.getZ()), "");
            ran = 0;
            DeferredPlacement.flush(world);
            check("not run", ran == 0, ran);
            generate(world, cx + 1, cz, 1);
            DeferredPlacement.flush(world);
            check("run once its other chunk populated", ran == 1, ran);

            // The queue's file: an entry survives writing and reading back.
            DeferredPlacement.Queue q = new DeferredPlacement.Queue("test");
            NBTTagCompound tag = new NBTTagCompound();
            NBTTagCompound saved = new NBTTagCompound();
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Id", ID.toString());
            entry.setIntArray("Box", new int[] {1, 2, 3, 4, 5, 6});
            NBTTagCompound d = new NBTTagCompound();
            d.setLong("Seed", 42L);
            entry.setTag("Data", d);
            net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
            list.appendTag(entry);
            saved.setTag("Entries", list);
            q.readFromNBT(saved);
            q.writeToNBT(tag);
            NBTTagCompound back = tag.getTagList("Entries", 10).getCompoundTagAt(0);
            check("saved entry round-trips (id, box, data)", ID.toString().equals(back.getString("Id"))
                    && Arrays.equals(new int[] {1, 2, 3, 4, 5, 6}, back.getIntArray("Box"))
                    && back.getCompoundTag("Data").getLong("Seed") == 42L, back);
        });

        then(1, "GD sulfur gaps: underground biomes", world -> {
            int cx = 5200, cz = 5200;
            generate(world, cx, cz, 0);
            int x = cx * 16 + 5, z = cz * 16 + 9;
            Biome column = world.getBiome(new BlockPos(x, 64, z));
            Biome swamp = Biomes.SWAMPLAND == column ? Biomes.DESERT : Biomes.SWAMPLAND;
            UndergroundBiomes.set(world, x, z, 10, 40, swamp);
            check("inside the span", UndergroundBiomes.at(world, new BlockPos(x, 10, z)) == swamp
                    && UndergroundBiomes.at(world, new BlockPos(x, 39, z)) == swamp, "");
            check("outside it: none", UndergroundBiomes.at(world, new BlockPos(x, 40, z)) == null
                    && UndergroundBiomes.at(world, new BlockPos(x, 9, z)) == null
                    && UndergroundBiomes.at(world, new BlockPos(x + 1, 20, z)) == null, "");
            check("biomeAt falls back to the column", UndergroundBiomes.biomeAt(world, new BlockPos(x, 60, z)) == column
                    && UndergroundBiomes.biomeAt(world, new BlockPos(x, 20, z)) == swamp, "");
            // Clearing the middle splits the span.
            UndergroundBiomes.set(world, x, z, 20, 25, null);
            check("cleared middle: split in two", UndergroundBiomes.at(world, new BlockPos(x, 19, z)) == swamp
                    && UndergroundBiomes.at(world, new BlockPos(x, 22, z)) == null
                    && UndergroundBiomes.at(world, new BlockPos(x, 25, z)) == swamp, "");
            // A mask: runs become spans, the biome's old spans in the column go.
            boolean[] mask = new boolean[256];
            for (int y = 100; y < 110; y++) {
                mask[y] = true;
            }
            UndergroundBiomes.setColumn(world, x, z, 0, mask, swamp);
            check("setColumn replaces the biome's spans", UndergroundBiomes.at(world, new BlockPos(x, 105, z)) == swamp
                    && UndergroundBiomes.at(world, new BlockPos(x, 15, z)) == null, "");

            // Water colors read it.
            int[] colors = WaterColors.at(world, new BlockPos(x, 105, z));
            int[] want = WaterColors.of(swamp);
            check("WaterColors.at reads the underground biome", colors != null && want != null && colors[0] == want[0],
                    colors == null ? "null" : Integer.toHexString(colors[0]));
            check("Adventuring Time reads the underground biome",
                    com.exiledradio.backportcore.advancement.AdventuringTime.standingIn(world, new BlockPos(x, 105, z)).equals("minecraft:swamp")
                    && com.exiledradio.backportcore.advancement.AdventuringTime.standingIn(world, new BlockPos(x, 60, z))
                    .equals(com.exiledradio.backportcore.advancement.AdventuringTime.biomeAt(world, new BlockPos(x, 60, z))),
                    com.exiledradio.backportcore.advancement.AdventuringTime.standingIn(world, new BlockPos(x, 105, z)));

            // Saved with the chunk: write, forget, read back.
            Chunk chunk = world.getChunk(cx, cz);
            NBTTagCompound level = new NBTTagCompound();
            UndergroundBiomes.Events events = new UndergroundBiomes.Events();
            events.onSave(new ChunkDataEvent.Save(chunk, level));
            check("saved in the chunk's data", level.hasKey("backportcore_underground", 11), level.getKeySet());
            UndergroundBiomes.setColumn(world, x, z, 0, null, swamp);
            check("cleared", UndergroundBiomes.at(world, new BlockPos(x, 105, z)) == null, "");
            events.onLoad(new ChunkDataEvent.Load(chunk, level));
            check("read back from the chunk's data", UndergroundBiomes.at(world, new BlockPos(x, 105, z)) == swamp, "");

            // P44's hook (IBlockAccess.getBiome is client only): the transformer test checks the call; the client test the color.
            UndergroundBiomes.setColumn(world, x, z, 0, null, swamp);
        });
    }
}
