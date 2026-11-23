package com.exiledradio.backportcore.block;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The jigsaw screen's two packets, as 26.3's: {@code ServerboundSetJigsawBlockPacket} (the fields) and
 * {@code ServerboundJigsawGeneratePacket} (levels and Keep Jigsaws). The server applies either only for a player who
 * may use operator blocks ({@code canUseGameMasterBlocks}: creative mode and permission level 2), as 26.3's
 * {@code handleSetJigsawBlock} and {@code handleJigsawGenerate} do. Since Core 1.4.5.
 */
public final class JigsawNetwork {

    public static final String CHANNEL = "backportcore_jigsaw";
    static final int MAX_ID = 32767;

    private static SimpleNetworkWrapper channel;

    private JigsawNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(SetHandler.class, Set.class, 0, Side.SERVER);
        channel.registerMessage(GenerateHandler.class, Generate.class, 1, Side.SERVER);
    }

    /** Client side. */
    public static void send(IMessage message) {
        channel.sendToServer(message);
    }

    /** Today's {@code handleSetJigsawBlock}. @return whether the block took the fields */
    public static boolean apply(EntityPlayerMP player, Set p) {
        if (!player.canUseCommandBlock()) {
            return false;
        }
        World world = player.world;
        if (!world.isBlockLoaded(p.pos) || !BlockJigsaw.isValidId(p.name) || !BlockJigsaw.isValidId(p.target)
                || !BlockJigsaw.isValidId(p.pool)) {
            return false;
        }
        TileEntity te = world.getTileEntity(p.pos);
        if (!(te instanceof TileEntityJigsaw)) {
            return false;
        }
        TileEntityJigsaw jigsaw = (TileEntityJigsaw) te;
        jigsaw.setName(full(p.name));
        jigsaw.setTarget(full(p.target));
        jigsaw.setPool(full(p.pool));
        jigsaw.setFinalState(p.finalState);
        jigsaw.setJoint(p.joint);
        jigsaw.setPlacementPriority(p.placementPriority);
        jigsaw.setSelectionPriority(p.selectionPriority);
        jigsaw.markDirty();
        IBlockState state = world.getBlockState(p.pos);
        world.notifyBlockUpdate(p.pos, state, state, 3);
        return true;
    }

    /** Today's {@code handleJigsawGenerate}. @return whether anything was placed */
    public static boolean apply(EntityPlayerMP player, Generate p) {
        if (!player.canUseCommandBlock() || !player.world.isBlockLoaded(p.pos)) {
            return false;
        }
        TileEntity te = player.world.getTileEntity(p.pos);
        return te instanceof TileEntityJigsaw && ((TileEntityJigsaw) te).generate(player.world, p.levels, p.keepJigsaws);
    }

    /** {@code Identifier.parse}: no colon is {@code minecraft:}. */
    static String full(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            return "minecraft:" + id;
        }
        return colon == 0 ? "minecraft" + id : id;
    }

    /** Today's {@code ServerboundSetJigsawBlockPacket}, field for field. */
    public static final class Set implements IMessage {
        BlockPos pos = BlockPos.ORIGIN;
        String name = "";
        String target = "";
        String pool = "";
        String finalState = "";
        TileEntityJigsaw.Joint joint = TileEntityJigsaw.Joint.ALIGNED;
        int selectionPriority;
        int placementPriority;

        public Set() {
        }

        public Set(BlockPos pos, String name, String target, String pool, String finalState, TileEntityJigsaw.Joint joint,
                   int selectionPriority, int placementPriority) {
            this.pos = pos;
            this.name = name;
            this.target = target;
            this.pool = pool;
            this.finalState = finalState;
            this.joint = joint;
            this.selectionPriority = selectionPriority;
            this.placementPriority = placementPriority;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            PacketBuffer in = new PacketBuffer(buf);
            this.pos = in.readBlockPos();
            this.name = in.readString(MAX_ID);
            this.target = in.readString(MAX_ID);
            this.pool = in.readString(MAX_ID);
            this.finalState = in.readString(MAX_ID);
            this.joint = TileEntityJigsaw.Joint.byName(in.readString(MAX_ID), TileEntityJigsaw.Joint.ALIGNED);
            this.selectionPriority = in.readVarInt();
            this.placementPriority = in.readVarInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            PacketBuffer out = new PacketBuffer(buf);
            out.writeBlockPos(this.pos);
            out.writeString(this.name);
            out.writeString(this.target);
            out.writeString(this.pool);
            out.writeString(this.finalState);
            out.writeString(this.joint.getName());
            out.writeVarInt(this.selectionPriority);
            out.writeVarInt(this.placementPriority);
        }

        public BlockPos pos() {
            return this.pos;
        }

        public String name() {
            return this.name;
        }

        public String target() {
            return this.target;
        }

        public String pool() {
            return this.pool;
        }

        public String finalState() {
            return this.finalState;
        }

        public TileEntityJigsaw.Joint joint() {
            return this.joint;
        }

        public int selectionPriority() {
            return this.selectionPriority;
        }

        public int placementPriority() {
            return this.placementPriority;
        }
    }

    /** Today's {@code ServerboundJigsawGeneratePacket}. */
    public static final class Generate implements IMessage {
        BlockPos pos = BlockPos.ORIGIN;
        int levels;
        boolean keepJigsaws;

        public Generate() {
        }

        public Generate(BlockPos pos, int levels, boolean keepJigsaws) {
            this.pos = pos;
            this.levels = levels;
            this.keepJigsaws = keepJigsaws;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            PacketBuffer in = new PacketBuffer(buf);
            this.pos = in.readBlockPos();
            this.levels = in.readVarInt();
            this.keepJigsaws = in.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            PacketBuffer out = new PacketBuffer(buf);
            out.writeBlockPos(this.pos);
            out.writeVarInt(this.levels);
            out.writeBoolean(this.keepJigsaws);
        }

        public int levels() {
            return this.levels;
        }

        public boolean keepJigsaws() {
            return this.keepJigsaws;
        }
    }

    public static final class SetHandler implements IMessageHandler<Set, IMessage> {
        @Override
        public IMessage onMessage(Set message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            ((WorldServer) player.world).addScheduledTask(() -> apply(player, message));
            return null;
        }
    }

    public static final class GenerateHandler implements IMessageHandler<Generate, IMessage> {
        @Override
        public IMessage onMessage(Generate message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            ((WorldServer) player.world).addScheduledTask(() -> apply(player, message));
            return null;
        }
    }
}
