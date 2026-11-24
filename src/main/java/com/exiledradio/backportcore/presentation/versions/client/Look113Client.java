package com.exiledradio.backportcore.presentation.versions.client;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.LegacyCopies;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.DebugText;
import com.exiledradio.backportcore.presentation.client.PresentationHooks;
import com.exiledradio.backportcore.presentation.versions.BiomeBlend;
import com.exiledradio.backportcore.presentation.versions.Look113;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The client side of {@code versions.Look113}: 1.13's look and feel that Backport Core owns since 1.4.7 (moved out of
 * Update Aquatic Backport's {@code lookfeel.client}).
 * <ul>
 *   <li>F3 + C copies the location as a {@code /tp} command and F3 + I the targeted block or entity as a
 *       {@code /setblock} or {@code /summon} command (1.14.4's {@code KeyboardHandler}, the same as 1.13's), written
 *       so 1.12.2's own commands accept them;</li>
 *   <li>F3 (18w22c to 1.13.1): the integrated server's tick time and packets, the dimension and its forced chunks,
 *       "Looking at block" and "Looking at liquid" (20 blocks), and the right column's "Targeted Block", "Targeted
 *       Fluid" and "Targeted Entity" sections;</li>
 *   <li>Biome Blend: the colors' square follows the option, and a change rebuilds the chunks.</li>
 * </ul>
 * An older Update Aquatic jar's copies: its F3 keys replace Core's (same keys), its F3 step of the same name is left
 * out, and while it still has {@code water.BiomeBlend} Core's patch P68 is off and that jar sets its own radius.
 */
@SideOnly(Side.CLIENT)
public final class Look113Client {

    /** For the client self-test. */
    public static volatile String lastClipboard;
    /** For the client self-test. */
    public static volatile int debugEdits;

    /** The older Update Aquatic jar's Biome Blend hook (its patch is {@code asm.BiomeBlendPatch}). */
    static final String LEGACY_BLEND = "com.exiledradio.updateaquaticbackport.water.BiomeBlend";

    private Look113Client() {
    }

    /** From Backport Core's client pre-init, before any backport's client code. */
    static void init() {
        Contribution v113 = Presentation.core("1.13");
        v113.debugKey(Keyboard.KEY_C, "debug.copy_location.help", Look113Client::copyLocation);
        v113.debugKey(Keyboard.KEY_I, "debug.inspect.help", Look113Client::inspect);
        // Update Aquatic's exact (version, name): an older jar's copy of the step is left out.
        DebugText.add("1.13", "targeted block, fluid and entity", Look113Client::debugText);
        MinecraftForge.EVENT_BUS.register(new Events());
        Look113.BIOME_BLEND.onChange(v -> blendChanged());
    }

    // ---- Biome Blend ----

    /** @return the radius the colors use now: the option while it shows, else 1.12.2's 3x3 */
    static int wantedRadius() {
        return Look113.BIOME_BLEND.isShown() ? Look113.biomeBlendRadius() : 1;
    }

    /**
     * As 1.13's, a change rebuilds the chunks. While an older Update Aquatic jar patches the colors itself (P68 is off),
     * that jar's own radius is the one read, and it rebuilds them.
     */
    static void blendChanged() {
        if (!LegacyCopies.coreRuns(LEGACY_BLEND)) {
            return;
        }
        int r = wantedRadius();
        if (r != BiomeBlend.radius) {
            BiomeBlend.radius = r;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.renderGlobal != null && mc.world != null) {
                mc.renderGlobal.loadRenderers();
            }
        }
    }

    // ---- F3 + C and F3 + I ----

    /** F3 + C: the player's position and look as a teleport command. Holding F3 + C still crashes the game, as 1.12.2's. */
    static boolean copyLocation() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || player.hasReducedDebug()) {
            return false;
        }
        PresentationHooks.debugFeedback("debug.copy_location.message");
        clipboard(locationCommand(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch));
        return true;
    }

    /** {@code /tp @s x y z yaw pitch} (1.13.0's form; 1.13.1's {@code /execute in} has no 1.12.2 command). */
    public static String locationCommand(double x, double y, double z, float yaw, float pitch) {
        return String.format(Locale.ROOT, "/tp @s %.2f %.2f %.2f %.2f %.2f", x, y, z, yaw, pitch);
    }

    /**
     * F3 + I: the targeted block or entity as a command that recreates it, with its data when the player may use
     * commands. Without Shift, 1.13 asks the server for the data; 1.12.2 has no packet for that, so the server's
     * copy is read only in singleplayer (on the server thread), and a multiplayer client copies its own data.
     */
    static boolean inspect() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || player.hasReducedDebug()) {
            return true;
        }
        boolean privileged = player.canUseCommand(2, "");
        boolean askServer = !GuiScreen.isShiftKeyDown();
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null) {
            return true;
        }
        final IntegratedServer server = mc.getIntegratedServer();
        final int dimension = player.dimension;
        if (hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            final BlockPos pos = hit.getBlockPos();
            final IBlockState state = mc.world.getBlockState(pos);
            if (privileged && askServer && server != null) {
                server.addScheduledTask(() -> {
                    WorldServer world = server.getWorld(dimension);
                    TileEntity te = world.getTileEntity(pos);
                    final NBTTagCompound nbt = te == null ? null : te.writeToNBT(new NBTTagCompound());
                    mc.addScheduledTask(() -> {
                        clipboard(blockCommand(state, pos, nbt));
                        PresentationHooks.debugFeedback("debug.inspect.server.block");
                    });
                });
            } else {
                TileEntity te = privileged ? mc.world.getTileEntity(pos) : null;
                clipboard(blockCommand(state, pos, te == null ? null : te.writeToNBT(new NBTTagCompound())));
                PresentationHooks.debugFeedback("debug.inspect.client.block");
            }
        } else if (hit.typeOfHit == RayTraceResult.Type.ENTITY && hit.entityHit != null) {
            final Entity entity = hit.entityHit;
            final ResourceLocation id = EntityList.getKey(entity);
            final double x = entity.posX;
            final double y = entity.posY;
            final double z = entity.posZ;
            if (id == null) {
                return true;
            }
            if (privileged && askServer && server != null) {
                final int entityId = entity.getEntityId();
                server.addScheduledTask(() -> {
                    Entity onServer = server.getWorld(dimension).getEntityByID(entityId);
                    final NBTTagCompound nbt = onServer == null ? null : onServer.writeToNBT(new NBTTagCompound());
                    mc.addScheduledTask(() -> {
                        clipboard(entityCommand(id, x, y, z, nbt));
                        PresentationHooks.debugFeedback("debug.inspect.server.entity");
                    });
                });
            } else {
                clipboard(entityCommand(id, x, y, z, privileged ? entity.writeToNBT(new NBTTagCompound()) : null));
                PresentationHooks.debugFeedback("debug.inspect.client.entity");
            }
        }
        return true;
    }

    /** 1.14.4 {@code copyCreateBlockCommand}, in 1.12.2's {@code /setblock x y z id state replace {nbt}} syntax. */
    public static String blockCommand(IBlockState state, BlockPos pos, @Nullable NBTTagCompound nbt) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "/setblock %d %d %d %s %s", pos.getX(), pos.getY(), pos.getZ(),
                Block.REGISTRY.getNameForObject(state.getBlock()), properties(state)));
        if (nbt != null) {
            nbt.removeTag("x");
            nbt.removeTag("y");
            nbt.removeTag("z");
            nbt.removeTag("id");
            b.append(" replace ").append(nbt);
        }
        return b.toString();
    }

    /** 1.14.4 {@code copyCreateEntityCommand}. */
    public static String entityCommand(ResourceLocation id, double x, double y, double z, @Nullable NBTTagCompound nbt) {
        if (nbt == null) {
            return String.format(Locale.ROOT, "/summon %s %.2f %.2f %.2f", id, x, y, z);
        }
        nbt.removeTag("UUIDMost");
        nbt.removeTag("UUIDLeast");
        nbt.removeTag("Pos");
        nbt.removeTag("Dimension");
        return String.format(Locale.ROOT, "/summon %s %.2f %.2f %.2f %s", id, x, y, z, nbt);
    }

    /** The state as 1.12.2's commands read it: {@code facing=north,half=top}, or {@code default}. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static String properties(IBlockState state) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<IProperty<?>, Comparable<?>> e : state.getProperties().entrySet()) {
            if (b.length() > 0) {
                b.append(',');
            }
            IProperty p = e.getKey();
            b.append(p.getName()).append('=').append(p.getName((Comparable) e.getValue()));
        }
        return b.length() == 0 ? "default" : b.toString();
    }

    private static void clipboard(String text) {
        lastClipboard = text;
        GuiScreen.setClipboardString(text);
    }

    // ---- F3 text ----

    static void debugText(DebugText.Lines lines) {
        Minecraft mc = lines.mc;
        if (mc.world == null || mc.getRenderViewEntity() == null || mc.gameSettings.reducedDebugInfo) {
            return;
        }
        Entity eye = mc.getRenderViewEntity();
        List<String> left = lines.left;
        // Second line on: the server line (18w22c's tick time and packets).
        if (left.size() > 2 && mc.getConnection() != null) {
            if (mc.getIntegratedServer() != null) {
                float ms = mean(mc.getIntegratedServer().tickTimeArray) * 1.0E-6F;
                left.add(2, String.format("Integrated server @ %.0f ms ticks, %.0f tx, %.0f rx", ms, Packets.sent, Packets.received));
            } else {
                left.add(2, String.format("\"%s\" server, %.0f tx, %.0f rx", mc.player.getServerBrand(), Packets.sent, Packets.received));
            }
        }
        // 1.13.1: the dimension and its forced chunks, above the blank line before XYZ.
        int xyz = DebugText.Lines.find(left, "XYZ: ");
        if (xyz > 0) {
            int forced = ForgeChunkManager.getPersistentChunksFor(mc.world).size();
            left.add(xyz - 1, "minecraft:" + mc.world.provider.getDimensionType().getName() + " FC: " + forced);
        }
        // "Looking at" becomes "Looking at block", 20 blocks out (pick(20, 0, false)), and the fluid gets its own line.
        Vec3d from = eye.getPositionEyes(lines.partialTicks);
        Vec3d look = eye.getLook(lines.partialTicks);
        Vec3d to = from.add(look.x * 20.0, look.y * 20.0, look.z * 20.0);
        RayTraceResult block = mc.world.rayTraceBlocks(from, to, false, false, false);
        RayTraceResult liquid = mc.world.rayTraceBlocks(from, to, true, false, false);
        int looking = DebugText.Lines.find(left, "Looking at: ");
        if (looking >= 0) {
            left.remove(looking);
        }
        int at = looking >= 0 ? looking : left.size();
        if (block != null && block.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos p = block.getBlockPos();
            left.add(at++, String.format("Looking at block: %d %d %d", p.getX(), p.getY(), p.getZ()));
        }
        Fluid fluid = liquid == null || liquid.typeOfHit != RayTraceResult.Type.BLOCK ? null : fluidAt(mc, liquid.getBlockPos());
        if (fluid != null) {
            BlockPos p = liquid.getBlockPos();
            left.add(at, String.format("Looking at liquid: %d %d %d", p.getX(), p.getY(), p.getZ()));
        }
        // Right column: headings for the block 1.12.2 lists, then the fluid and the entity.
        List<String> right = lines.right;
        RayTraceResult hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && hit.getBlockPos() != null) {
            IBlockState state = mc.world.getBlockState(hit.getBlockPos());
            int name = right.indexOf(String.valueOf(Block.REGISTRY.getNameForObject(state.getBlock())));
            if (name > 0) {
                right.add(name, TextFormatting.UNDERLINE + "Targeted Block");
            }
        }
        if (fluid != null) {
            right.add("");
            right.add(TextFormatting.UNDERLINE + "Targeted Fluid");
            right.add(fluid == FluidRegistry.WATER ? "minecraft:water" : fluid == FluidRegistry.LAVA ? "minecraft:lava" : fluid.getName());
            IBlockState there = mc.world.getBlockState(liquid.getBlockPos());
            for (IProperty<?> prop : there.getPropertyKeys()) {
                if (prop.getName().equals("level")) {
                    right.add("level: " + there.getValue(prop));
                }
            }
        }
        Entity pointed = mc.pointedEntity;
        if (pointed != null) {
            ResourceLocation key = EntityList.getKey(pointed);
            right.add("");
            right.add(TextFormatting.UNDERLINE + "Targeted Entity");
            right.add(String.valueOf(key));
        }
        debugEdits++;
    }

    /**
     * 1.13's {@code NetworkManager} packet averages ({@code tick}: {@code lerp(0.75, count, average)} each client
     * tick), counted by a small handler put into the connection's pipeline when it opens. Core's own counter, read
     * only by Core's F3 step (an older Update Aquatic jar's counter keeps its own name in the pipeline).
     */
    static final class Packets extends io.netty.channel.ChannelDuplexHandler {
        static final String NAME = "backportcore:packets";
        static volatile float sent;
        static volatile float received;
        private static final java.util.concurrent.atomic.AtomicInteger SENT = new java.util.concurrent.atomic.AtomicInteger();
        private static final java.util.concurrent.atomic.AtomicInteger RECEIVED = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) throws Exception {
            RECEIVED.incrementAndGet();
            super.channelRead(ctx, msg);
        }

        @Override
        public void write(io.netty.channel.ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise) throws Exception {
            SENT.incrementAndGet();
            super.write(ctx, msg, promise);
        }

        static void tick() {
            sent = SENT.getAndSet(0) * 0.25F + sent * 0.75F;
            received = RECEIVED.getAndSet(0) * 0.25F + received * 0.75F;
        }
    }

    /** Forge's events: the packet counter, and the Biome Blend radius kept equal to the option. */
    public static final class Events {
        @SubscribeEvent
        public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            if (!Presentation.installedNewest().isAtLeast("1.13")) {
                return;
            }
            try {
                io.netty.channel.ChannelPipeline pipeline = event.getManager().channel().pipeline();
                if (pipeline.get(Packets.NAME) == null && pipeline.get("packet_handler") != null) {
                    pipeline.addBefore("packet_handler", Packets.NAME, new Packets());
                }
            } catch (RuntimeException e) {
                // The F3 line then shows no traffic; nothing else depends on it.
            }
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Packets.tick();
                // The option shows once a 1.13 backport is installed and while Video Settings' look is on.
                blendChanged();
            }
        }
    }

    @Nullable
    private static Fluid fluidAt(Minecraft mc, BlockPos pos) {
        Material m = mc.world.getBlockState(pos).getMaterial();
        return m == Material.WATER ? FluidRegistry.WATER : m == Material.LAVA ? FluidRegistry.LAVA : null;
    }

    private static float mean(long[] values) {
        if (values == null || values.length == 0) {
            return 0.0F;
        }
        long sum = 0L;
        for (long v : values) {
            sum += v;
        }
        return (float) sum / values.length;
    }
}
