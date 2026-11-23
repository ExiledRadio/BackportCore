package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.command.tick.ServerTickRateManager;
import com.exiledradio.backportcore.command.tick.TickRateManager;
import com.exiledradio.backportcore.command.tick.Ticks;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import paulscode.sound.SoundSystemConfig;

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

/**
 * The debug screen's parts 1.12.2 has no lines for, each its own Debug Options entry ({@link DebugEntries}), as the
 * version that added it words it:
 *
 * <ul>
 *   <li><b>tps</b> (1.21.9, {@code DebugEntryTps}): "Integrated server @ 4.2/50.0 ms, 23 tx, 412 rx" (the server's
 *       smoothed tick time, the target, and the packets sent and received a second, smoothed as 1.14's
 *       {@code Connection.tickSecond}), or "\"brand\" server, ..." on another server, with "(frozen)", "(frozen -
 *       stepping)" or "(sprinting)" from {@code /tick}.</li>
 *   <li><b>looking_at_fluid</b> (1.21.9): "Targeted Fluid: x, y, z", the fluid as 1.13 names it (water, flowing_water,
 *       ..., minecraft:empty for a block without one) and its state ({@code falling}, {@code level}).</li>
 *   <li><b>looking_at_entity</b> (1.21.9): "Targeted Entity" and its id. 1.12.2 has no entity type tags, so
 *       {@code looking_at_entity_tags} (26.1) is not listed.</li>
 *   <li><b>sound_mood</b> (1.21.9): "Sounds: playing/channels + streaming/channels", with "(Mood n%)" when a backport
 *       supplies the cave mood ({@link #setMood}).</li>
 *   <li><b>heightmap</b> (1.21.11): the client's ("CH S: M:") and the server's ("SH S: O: M: ML:") heights at the camera's
 *       column, read from the blocks as 1.13's heightmaps define them.</li>
 *   <li><b>detailed_memory</b> (26.1): the heap and non-heap use, initial, used, committed and maximum.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class DebugParts {

    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static final AtomicInteger SENT = new AtomicInteger();
    private static final AtomicInteger RECEIVED = new AtomicInteger();
    private static float averageSent;
    private static float averageReceived;
    private static int ticks;
    @Nullable
    private static DoubleSupplier mood;
    private static Field playingSounds;
    private static Field soundManager;

    /** For the client self-test: edits made. */
    public static volatile int edits;

    private DebugParts() {
    }

    /** For a backport with 1.16's cave mood: its value now, 0 to 1 (null: no mood, the line has none). */
    public static void setMood(@Nullable DoubleSupplier supplier) {
        mood = supplier;
    }

    static void init() {
        DebugText.add("1.21.9", "tps, looking at fluid and entity, sound mood", DebugParts::edit1219);
        DebugText.add("1.21.11", "heightmap", DebugParts::heightmapStep);
        DebugText.add("26.1", "detailed memory", DebugParts::memoryStep);
    }

    static void edit1219(DebugText.Lines lines) {
        Minecraft mc = lines.mc;
        String tps = tpsLine(mc);
        if (tps != null) {
            block(lines.left, tps);
        }
        String sounds = soundLine(mc);
        if (sounds != null) {
            block(lines.left, sounds);
        }
        List<String> fluid = fluidLines(mc, lines.partialTicks);
        if (!fluid.isEmpty()) {
            block(lines.right, fluid.toArray(new String[0]));
        }
        List<String> entity = entityLines(mc);
        if (!entity.isEmpty()) {
            block(lines.right, entity.toArray(new String[0]));
        }
        edits++;
    }

    static void heightmapStep(DebugText.Lines lines) {
        List<String> h = heightmapLines(lines.mc);
        if (!h.isEmpty()) {
            block(lines.left, h.toArray(new String[0]));
        }
    }

    static void memoryStep(DebugText.Lines lines) {
        block(lines.right, memoryLine(MEMORY.getHeapMemoryUsage(), "heap"), memoryLine(MEMORY.getNonHeapMemoryUsage(), "non-heap"));
    }

    /** Adds lines at a column's end, after a separator, so no earlier part claims them. */
    private static void block(List<String> column, String... add) {
        if (!column.isEmpty() && !"".equals(column.get(column.size() - 1))) {
            column.add("");
        }
        for (String s : add) {
            column.add(s);
        }
    }

    // ---- tps

    @Nullable
    static String tpsLine(Minecraft mc) {
        if (mc.getConnection() == null || mc.world == null) {
            return null;
        }
        TickRateManager rate = Ticks.client();
        String status = "";
        if (rate != null && rate.isSteppingForward()) {
            status = " (frozen - stepping)";
        } else if (rate != null && rate.isFrozen()) {
            status = " (frozen)";
        }
        IntegratedServer server = mc.getIntegratedServer();
        if (server != null) {
            ServerTickRateManager s = Ticks.server();
            boolean sprinting = s != null && s.isSprinting();
            if (sprinting) {
                status = " (sprinting)";
            }
            String target = sprinting ? "-" : String.format(Locale.ROOT, "%.1f", rate == null ? 50.0F : rate.millisecondsPerTick());
            return String.format(Locale.ROOT, "Integrated server @ %.1f/%s ms%s, %.0f tx, %.0f rx", FrameCharts.smoothedTickMillis, target, status,
                    averageSent, averageReceived);
        }
        String brand = mc.player == null ? null : mc.player.getServerBrand();
        return String.format(Locale.ROOT, "\"%s\" server%s, %.0f tx, %.0f rx", brand, status, averageSent, averageReceived);
    }

    /** Counts the packets the client's connection sends and receives. */
    public static final class PacketCounter extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Packet) {
                RECEIVED.incrementAndGet();
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Packet) {
                SENT.incrementAndGet();
            }
            super.write(ctx, msg, promise);
        }
    }

    // ---- sounds

    @Nullable
    static String soundLine(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        int normal = 0;
        int streaming = 0;
        try {
            if (soundManager == null) {
                soundManager = ReflectionHelper.findField(SoundHandler.class, "sndManager", "field_147694_f");
                playingSounds = ReflectionHelper.findField(SoundManager.class, "playingSounds", "field_148629_h");
            }
            Object manager = soundManager.get(mc.getSoundHandler());
            @SuppressWarnings("unchecked")
            Map<String, ISound> playing = (Map<String, ISound>) playingSounds.get(manager);
            for (ISound s : new ArrayList<ISound>(playing.values())) {
                if (s.getSound() != null && s.getSound().isStreaming()) {
                    streaming++;
                } else {
                    normal++;
                }
            }
        } catch (Exception e) {
            return null;
        }
        String line = String.format(Locale.ROOT, "Sounds: %d/%d + %d/%d", normal, SoundSystemConfig.getNumberNormalChannels(), streaming,
                SoundSystemConfig.getNumberStreamingChannels());
        DoubleSupplier m = mood;
        if (m != null) {
            line += String.format(Locale.ROOT, " (Mood %d%%)", Math.round(m.getAsDouble() * 100.0));
        }
        return line;
    }

    // ---- what the camera looks at

    /** {@code DebugEntryLookingAt.FluidStateInfo}: the camera's pick with fluids, 20 blocks. */
    static List<String> fluidLines(Minecraft mc, float partialTicks) {
        List<String> out = new ArrayList<String>();
        Entity camera = mc.getRenderViewEntity();
        if (camera == null || mc.world == null) {
            return out;
        }
        Vec3d eye = camera.getPositionEyes(partialTicks);
        Vec3d look = camera.getLook(partialTicks);
        Vec3d end = eye.add(look.x * 20.0, look.y * 20.0, look.z * 20.0);
        RayTraceResult hit = mc.world.rayTraceBlocks(eye, end, true, false, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return out;
        }
        BlockPos pos = hit.getBlockPos();
        out.add(TextFormatting.UNDERLINE + "Targeted Fluid: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        IBlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof BlockLiquid) {
            int level = state.getValue(BlockLiquid.LEVEL);
            String name = state.getMaterial() == Material.LAVA ? "lava" : "water";
            if (level == 0) {
                out.add("minecraft:" + name);
            } else {
                out.add("minecraft:flowing_" + name);
                out.add("falling: " + bool(level >= 8));
                out.add("level: " + (level >= 8 ? 8 : 8 - level));
            }
        } else if (block instanceof IFluidBlock) {
            IFluidBlock fluid = (IFluidBlock) block;
            ResourceLocation id = block.getRegistryName();
            out.add(id == null ? fluid.getFluid().getName() : id.toString());
        } else {
            out.add("minecraft:empty");
        }
        return out;
    }

    private static String bool(boolean b) {
        return (b ? TextFormatting.GREEN : TextFormatting.RED) + String.valueOf(b);
    }

    /** {@code DebugEntryLookingAtEntity}: the entity under the crosshair. */
    static List<String> entityLines(Minecraft mc) {
        List<String> out = new ArrayList<String>();
        // 26.3's crosshairPickEntity is any entity under the crosshair; 1.12.2's pointedEntity only living ones and frames.
        RayTraceResult hit = mc.objectMouseOver;
        Entity e = hit != null && hit.typeOfHit == RayTraceResult.Type.ENTITY ? hit.entityHit : mc.pointedEntity;
        if (e != null) {
            out.add(TextFormatting.UNDERLINE + "Targeted Entity");
            ResourceLocation id = e instanceof EntityPlayer ? new ResourceLocation("player") : EntityList.getKey(e);
            out.add(String.valueOf(id));
        }
        return out;
    }

    // ---- heightmaps

    /** {@code DebugEntryHeightmap}: 1.13's heightmap types read from the blocks at the camera's column. */
    static List<String> heightmapLines(Minecraft mc) {
        List<String> out = new ArrayList<String>();
        Entity camera = mc.getRenderViewEntity();
        if (camera == null || mc.world == null) {
            return out;
        }
        BlockPos feet = new BlockPos(camera.posX, camera.getEntityBoundingBox().minY, camera.posZ);
        Chunk client = mc.world.getChunkProvider().getLoadedChunk(feet.getX() >> 4, feet.getZ() >> 4);
        if (client == null || client.isEmpty()) {
            return out;
        }
        int[] c = heights(client, feet.getX() & 15, feet.getZ() & 15);
        out.add("CH S: " + c[0] + " M: " + c[2]);
        Chunk server = null;
        IntegratedServer s = mc.getIntegratedServer();
        if (s != null && mc.player != null) {
            WorldServer w = s.getWorld(mc.player.dimension);
            if (w != null) {
                server = w.getChunkProvider().getLoadedChunk(feet.getX() >> 4, feet.getZ() >> 4);
            }
        }
        if (server != null) {
            int[] h = heights(server, feet.getX() & 15, feet.getZ() & 15);
            out.add("SH S: " + h[0] + " O: " + h[1] + " M: " + h[2] + " ML: " + h[3]);
        } else {
            out.add("SH S: ?? O: ?? M: ?? ML: ??");
        }
        return out;
    }

    /**
     * @return one above the highest block of each type at (x, z) in the chunk, 0 for none: WORLD_SURFACE (not air),
     *         OCEAN_FLOOR (blocks motion), MOTION_BLOCKING (blocks motion or holds a fluid), MOTION_BLOCKING_NO_LEAVES
     */
    static int[] heights(Chunk chunk, int x, int z) {
        int[] out = new int[4];
        boolean[] found = new boolean[4];
        World world = chunk.getWorld();
        int top = chunk.getTopFilledSegment() + 15;
        for (int y = top; y >= 0 && !(found[0] && found[1] && found[2] && found[3]); y--) {
            IBlockState state = chunk.getBlockState(x, y, z);
            Material m = state.getMaterial();
            if (m == Material.AIR) {
                continue;
            }
            boolean motion = m.blocksMovement();
            boolean fluid = m.isLiquid();
            boolean[] is = {true, motion, motion || fluid, (motion || fluid) && !(state.getBlock() instanceof BlockLeaves)};
            for (int i = 0; i < 4; i++) {
                if (!found[i] && is[i]) {
                    found[i] = true;
                    out[i] = y + 1;
                }
            }
        }
        return out;
    }

    // ---- memory

    /** {@code DebugEntryDetailedMemory.printMemoryUsage}. */
    static String memoryLine(MemoryUsage usage, String type) {
        return String.format(Locale.ROOT, "Memory (%s): i=%03dMiB u=%03dMiB c=%03dMiB m=%03dMiB", type, mib(usage.getInit()), mib(usage.getUsed()),
                mib(usage.getCommitted()), mib(usage.getMax()));
    }

    private static long mib(long bytes) {
        return bytes / 1024L / 1024L;
    }

    /** Forge's handlers; public for the event bus. */
    public static final class Events {
        @SubscribeEvent
        public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            SENT.set(0);
            RECEIVED.set(0);
            averageSent = 0.0F;
            averageReceived = 0.0F;
            ChannelPipeline pipeline = event.getManager().channel().pipeline();
            if (pipeline.get("backportcore_packets") == null && pipeline.get("packet_handler") != null) {
                pipeline.addBefore("packet_handler", "backportcore_packets", new PacketCounter());
            }
        }

        /** 1.14's {@code Connection.tickSecond}: every 20 ticks, each average keeps three quarters of itself. */
        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || Minecraft.getMinecraft().getConnection() == null) {
                return;
            }
            if (ticks++ % 20 == 0) {
                averageSent = lerp(0.75F, SENT.getAndSet(0), averageSent);
                averageReceived = lerp(0.75F, RECEIVED.getAndSet(0), averageReceived);
                // Mth.lerp(0.75, count, average): a quarter of the new count, three quarters of the old average.
            }
        }

        /** {@code Mth.lerp(delta, start, end)}. */
        private static float lerp(float delta, float start, float end) {
            return start + delta * (end - start);
        }
    }
}
