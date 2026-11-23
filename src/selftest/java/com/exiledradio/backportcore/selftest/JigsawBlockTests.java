package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.block.BlockJigsaw;
import com.exiledradio.backportcore.block.CoreBlocks;
import com.exiledradio.backportcore.block.FrontAndTop;
import com.exiledradio.backportcore.block.JigsawNetwork;
import com.exiledradio.backportcore.block.TileEntityJigsaw;
import com.exiledradio.backportcore.structure.ModernJigsaw;
import com.exiledradio.backportcore.structure.ModernPalette;
import com.exiledradio.backportcore.structure.ModernTemplate;
import com.exiledradio.backportcore.structure.TemplatePlacement;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Today's jigsaw block ({@code block.CoreBlocks}): operator-only placing and breaking, the orientation 26.3's
 * {@code getStateForPlacement} gives, the tile entity's NBT, the screen's set and generate packets (with their operator
 * checks), a 1.12.2 structure block's save read back by {@code ModernTemplate} (jigsaws and final states), and Generate
 * from a block over a small test pool, checked against the start random worked out here.
 */
final class JigsawBlockTests {

    private static final String POOL = SelfTestMod.MOD_ID + ":jt/start";

    private JigsawBlockTests() {
    }

    /** A creative-mode player, an operator or not. */
    static final class Player extends FakePlayer {
        private final boolean op;

        Player(WorldServer world, String name, boolean op) {
            super(world, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
            this.op = op;
            this.capabilities.isCreativeMode = true;
        }

        @Override
        public boolean canUseCommand(int level, String command) {
            return this.op;
        }
    }

    private static BlockPos base(WorldServer world) {
        BlockPos spawn = world.getSpawnPoint();
        return new BlockPos(spawn.getX() - 96, 170, spawn.getZ() - 96);
    }

    private static IBlockState jigsawState(FrontAndTop o) {
        return CoreBlocks.jigsaw.getDefaultState().withProperty(BlockJigsaw.ORIENTATION, o);
    }

    private static TileEntityJigsaw jigsawAt(WorldServer world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileEntityJigsaw ? (TileEntityJigsaw) te : null;
    }

    /** Places a jigsaw block with its fields. */
    private static TileEntityJigsaw put(WorldServer world, BlockPos pos, FrontAndTop o, String name, String target, String pool,
                                        String finalState, TileEntityJigsaw.Joint joint, int placement, int selection) {
        world.setBlockState(pos, jigsawState(o), 2);
        TileEntityJigsaw te = jigsawAt(world, pos);
        te.setName(name);
        te.setTarget(target);
        te.setPool(pool);
        te.setFinalState(finalState);
        te.setJoint(joint);
        te.setPlacementPriority(placement);
        te.setSelectionPriority(selection);
        te.markDirty();
        return te;
    }

    /** What a 1.12.2 structure block's Save does ({@code TileEntityStructure.save}). */
    private static boolean save(WorldServer world, String name, BlockPos origin, BlockPos size) {
        MinecraftServer server = world.getMinecraftServer();
        TemplateManager manager = world.getStructureTemplateManager();
        ResourceLocation id = new ResourceLocation(name);
        Template t = manager.getTemplate(server, id);
        t.takeBlocksFromWorld(world, origin, size, false, Blocks.STRUCTURE_VOID);
        t.setAuthor("selftest");
        return manager.writeTemplate(server, id);
    }

    /** 26.x's {@code WorldgenRandom.setLargeFeatureSeed}, written out again here. */
    private static Random largeFeature(long seed, int chunkX, int chunkZ) {
        Random r = new Random(seed);
        long a = r.nextLong();
        long b = r.nextLong();
        r.setSeed((long) chunkX * a ^ (long) chunkZ * b ^ seed);
        return r;
    }

    private static boolean isBlock(WorldServer world, BlockPos pos, Block block) {
        return world.getBlockState(pos).getBlock() == block;
    }

    static void schedule() {
        then(1, "jigsaw: placing", world -> {
            BlockPos base = base(world);
            world.getChunk(base);
            Player op = new Player(world, "[BCJigsawOp]", true);
            Player non = new Player(world, "[BCJigsawCreative]", false);
            for (int i = 0; i < 6; i++) {
                world.setBlockState(base.add(i * 3, 0, 0), Blocks.STONE.getDefaultState());
            }

            non.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(CoreBlocks.jigsawItem));
            EnumActionResult r = non.getHeldItemMainhand().onItemUse(non, world, base, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
            check("jigsaw: a creative player who is not an operator cannot place it", r == EnumActionResult.FAIL && world.isAirBlock(base.up()), r);

            Object[][] cases = {
                    // clicked face, the player's yaw (0 south, 90 west, 180 north, 270 east), the orientation
                    {EnumFacing.UP, 0.0F, FrontAndTop.UP_NORTH},
                    {EnumFacing.UP, 90.0F, FrontAndTop.UP_EAST},
                    {EnumFacing.DOWN, 180.0F, FrontAndTop.DOWN_SOUTH},
                    {EnumFacing.EAST, 0.0F, FrontAndTop.EAST_UP},
                    {EnumFacing.NORTH, 270.0F, FrontAndTop.NORTH_UP},
                    {EnumFacing.WEST, 90.0F, FrontAndTop.WEST_UP},
            };
            for (int i = 0; i < cases.length; i++) {
                BlockPos floor = base.add(i * 3, 0, 0);
                EnumFacing face = (EnumFacing) cases[i][0];
                op.rotationYaw = (Float) cases[i][1];
                op.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(CoreBlocks.jigsawItem));
                EnumActionResult placed = op.getHeldItemMainhand().onItemUse(op, world, floor, EnumHand.MAIN_HAND, face, 0.5F, 0.5F, 0.5F);
                IBlockState s = world.getBlockState(floor.offset(face));
                boolean ok = placed == EnumActionResult.SUCCESS && s.getBlock() == CoreBlocks.jigsaw && s.getValue(BlockJigsaw.ORIENTATION) == cases[i][2]
                        && world.getTileEntity(floor.offset(face)) instanceof TileEntityJigsaw;
                check("jigsaw: an operator places it; clicked " + face + " facing " + op.getHorizontalFacing() + " gives " + cases[i][2], ok,
                        placed + " " + s);
            }
            check("jigsaw: getStateForPlacement as 26.3 (up: top toward the player)",
                    BlockJigsaw.orientationFor(EnumFacing.UP, EnumFacing.WEST) == FrontAndTop.UP_EAST
                            && BlockJigsaw.orientationFor(EnumFacing.DOWN, EnumFacing.NORTH) == FrontAndTop.DOWN_SOUTH
                            && BlockJigsaw.orientationFor(EnumFacing.SOUTH, EnumFacing.EAST) == FrontAndTop.SOUTH_UP, "");

            BlockPos placedAt = base.up();
            IBlockState state = world.getBlockState(placedAt);
            check("jigsaw: unbreakable (hardness -1), drops nothing", state.getBlockHardness(world, placedAt) < 0
                    && CoreBlocks.jigsaw.getDrops(world, placedAt, state, 0).isEmpty(), state.getBlockHardness(world, placedAt));
            BlockEvent.BreakEvent byNon = new BlockEvent.BreakEvent(world, placedAt, state, non);
            MinecraftForge.EVENT_BUS.post(byNon);
            BlockEvent.BreakEvent byOp = new BlockEvent.BreakEvent(world, placedAt, state, op);
            MinecraftForge.EVENT_BUS.post(byOp);
            check("jigsaw: only an operator breaks it in creative mode", byNon.isCanceled() && !byOp.isCanceled(), "");
            check("jigsaw: no creative tab (1.19.3's Operator Utilities lists it), epic", CoreBlocks.jigsaw.getCreativeTab() == null
                    && new ItemStack(CoreBlocks.jigsawItem).getRarity() == net.minecraft.item.EnumRarity.EPIC, "");
            check("jigsaw: files' minecraft:jigsaw is the block",
                    ModernPalette.resolve("minecraft:jigsaw[orientation=east_up]").state == jigsawState(FrontAndTop.EAST_UP), "");

            check("jigsaw: turned and mirrored as 26.3", FrontAndTop.NORTH_UP.rotate(Rotation.CLOCKWISE_90) == FrontAndTop.EAST_UP
                    && FrontAndTop.UP_NORTH.rotate(Rotation.CLOCKWISE_90) == FrontAndTop.UP_EAST
                    && FrontAndTop.DOWN_WEST.rotate(Rotation.CLOCKWISE_180) == FrontAndTop.DOWN_EAST
                    && FrontAndTop.EAST_UP.mirror(Mirror.FRONT_BACK) == FrontAndTop.WEST_UP
                    && FrontAndTop.UP_NORTH.mirror(Mirror.LEFT_RIGHT) == FrontAndTop.UP_SOUTH
                    && jigsawState(FrontAndTop.SOUTH_UP).withRotation(Rotation.COUNTERCLOCKWISE_90) == jigsawState(FrontAndTop.EAST_UP), "");
            boolean metas = true;
            for (FrontAndTop o : FrontAndTop.values()) {
                IBlockState s = jigsawState(o);
                metas &= CoreBlocks.jigsaw.getStateFromMeta(CoreBlocks.jigsaw.getMetaFromState(s)) == s;
            }
            check("jigsaw: twelve orientations, one metadata each", metas && FrontAndTop.values().length == 12, "");
        });

        then(1, "jigsaw: tile entity", world -> {
            BlockPos at = base(world).add(0, 0, 4);
            TileEntityJigsaw te = put(world, at, FrontAndTop.EAST_UP, "minecraft:a", "minecraft:b", "minecraft:village/x", "minecraft:stone",
                    TileEntityJigsaw.Joint.ROLLABLE, 5, 7);
            NBTTagCompound tag = te.writeToNBT(new NBTTagCompound());
            boolean keys = "minecraft:a".equals(tag.getString("name")) && "minecraft:b".equals(tag.getString("target"))
                    && "minecraft:village/x".equals(tag.getString("pool")) && "minecraft:stone".equals(tag.getString("final_state"))
                    && "rollable".equals(tag.getString("joint")) && tag.getInteger("placement_priority") == 5
                    && tag.getInteger("selection_priority") == 7 && "backportcore:jigsaw".equals(tag.getString("id"));
            check("jigsaw: saved with 26.x's keys", keys, tag);
            TileEntityJigsaw back = new TileEntityJigsaw();
            back.readFromNBT(tag);
            check("jigsaw: read back", "minecraft:a".equals(back.getName()) && "minecraft:b".equals(back.getTarget())
                    && "minecraft:village/x".equals(back.getPool()) && "minecraft:stone".equals(back.getFinalState())
                    && back.getJoint() == TileEntityJigsaw.Joint.ROLLABLE && back.getPlacementPriority() == 5 && back.getSelectionPriority() == 7, back.writeFields(new NBTTagCompound()));
            TileEntityJigsaw fresh = new TileEntityJigsaw();
            check("jigsaw: new as 26.3's (empty, air, rollable, 0)", "minecraft:empty".equals(fresh.getName()) && "minecraft:empty".equals(fresh.getPool())
                    && "minecraft:air".equals(fresh.getFinalState()) && fresh.getJoint() == TileEntityJigsaw.Joint.ROLLABLE, "");

            NBTTagCompound noJoint = tag.copy();
            noJoint.removeTag("joint");
            noJoint.setString("name", "Not An Id");
            te.readFromNBT(noJoint);
            BlockPos up = at.add(2, 0, 0);
            TileEntityJigsaw vertical = put(world, up, FrontAndTop.UP_NORTH, "minecraft:a", "minecraft:b", "minecraft:empty", "minecraft:air",
                    TileEntityJigsaw.Joint.ALIGNED, 0, 0);
            NBTTagCompound verticalTag = noJoint.copy();
            verticalTag.setInteger("x", up.getX());
            verticalTag.setInteger("y", up.getY());
            verticalTag.setInteger("z", up.getZ());
            vertical.readFromNBT(verticalTag);
            check("jigsaw: no joint in the data: aligned facing sideways, rollable facing up",
                    te.getJoint() == TileEntityJigsaw.Joint.ALIGNED && vertical.getJoint() == TileEntityJigsaw.Joint.ROLLABLE, te.getJoint() + " " + vertical.getJoint());
            check("jigsaw: a malformed id reads as minecraft:empty", "minecraft:empty".equals(te.getName()), te.getName());
            check("jigsaw: ids as 26.3's Identifier", BlockJigsaw.isValidId("minecraft:a/b.c-d_e") && BlockJigsaw.isValidId("plain")
                    && !BlockJigsaw.isValidId("Upper:case") && !BlockJigsaw.isValidId("a:b:c") && !BlockJigsaw.isValidId("sp ace"), "");
        });

        then(1, "jigsaw: packets", world -> {
            BlockPos at = base(world).add(0, 0, 8);
            TileEntityJigsaw te = put(world, at, FrontAndTop.UP_SOUTH, "minecraft:empty", "minecraft:empty", "minecraft:empty", "minecraft:air",
                    TileEntityJigsaw.Joint.ROLLABLE, 0, 0);
            JigsawNetwork.Set sent = new JigsawNetwork.Set(at, "bc:name", "target", "bc:pool/a", "minecraft:oak_log[axis=x]",
                    TileEntityJigsaw.Joint.ALIGNED, 4, 9);
            ByteBuf buf = Unpooled.buffer();
            sent.toBytes(buf);
            JigsawNetwork.Set got = new JigsawNetwork.Set();
            got.fromBytes(buf);
            check("jigsaw: set packet round trip", at.equals(got.pos()) && "bc:name".equals(got.name()) && "target".equals(got.target())
                    && "bc:pool/a".equals(got.pool()) && "minecraft:oak_log[axis=x]".equals(got.finalState())
                    && got.joint() == TileEntityJigsaw.Joint.ALIGNED && got.selectionPriority() == 4 && got.placementPriority() == 9, "");
            JigsawNetwork.Generate gen = new JigsawNetwork.Generate(at, 7, false);
            ByteBuf buf2 = Unpooled.buffer();
            gen.toBytes(buf2);
            JigsawNetwork.Generate gen2 = new JigsawNetwork.Generate();
            gen2.fromBytes(buf2);
            check("jigsaw: generate packet round trip", gen2.levels() == 7 && !gen2.keepJigsaws(), "");

            Player non = new Player(world, "[BCJigsawCreative]", false);
            Player op = new Player(world, "[BCJigsawOp]", true);
            check("jigsaw: the server ignores a non-operator's fields", !JigsawNetwork.apply(non, got) && "minecraft:empty".equals(te.getName()), te.getName());
            boolean applied = JigsawNetwork.apply(op, got);
            check("jigsaw: an operator's fields apply", applied && "bc:name".equals(te.getName()) && "minecraft:target".equals(te.getTarget())
                    && "bc:pool/a".equals(te.getPool()) && "minecraft:oak_log[axis=x]".equals(te.getFinalState())
                    && te.getJoint() == TileEntityJigsaw.Joint.ALIGNED && te.getSelectionPriority() == 4 && te.getPlacementPriority() == 9,
                    te.writeFields(new NBTTagCompound()));
            JigsawNetwork.Set bad = new JigsawNetwork.Set(at, "Bad Name", "t", "p", "minecraft:air", TileEntityJigsaw.Joint.ROLLABLE, 0, 0);
            check("jigsaw: a malformed id is refused", !JigsawNetwork.apply(op, bad) && "bc:name".equals(te.getName()), te.getName());
            check("jigsaw: a non-operator cannot generate", !JigsawNetwork.apply(non, gen2), "");
        });

        then(1, "jigsaw: structure block save", world -> {
            BlockPos s = base(world).add(0, 4, 16);
            // bcjt_start (and _alt): bc:start facing west, a block, bc:out facing east into jt/branch.
            for (int alt = 0; alt < 2; alt++) {
                BlockPos o = s.add(0, 0, alt * 3);
                put(world, o, FrontAndTop.WEST_UP, "bc:start", "minecraft:empty", "minecraft:empty", "minecraft:gold_block",
                        TileEntityJigsaw.Joint.ALIGNED, 0, 0);
                world.setBlockState(o.east(), alt == 0 ? Blocks.STONE.getDefaultState() : Blocks.SANDSTONE.getDefaultState());
                put(world, o.east(2), FrontAndTop.EAST_UP, "bc:out", "bc:in", SelfTestMod.MOD_ID + ":jt/branch", "minecraft:iron_block",
                        TileEntityJigsaw.Joint.ALIGNED, 3, 2);
                check("jigsaw: 1.12.2 structure block saves bcjt_start" + (alt == 0 ? "" : "_alt"),
                        save(world, alt == 0 ? "bcjt_start" : "bcjt_start_alt", o, new BlockPos(3, 1, 1)), o);
            }
            BlockPos b = s.add(0, 0, 6);
            put(world, b, FrontAndTop.WEST_UP, "bc:in", "minecraft:empty", "minecraft:empty", "minecraft:diamond_block",
                    TileEntityJigsaw.Joint.ALIGNED, 0, 0);
            world.setBlockState(b.east(), Blocks.LAPIS_BLOCK.getDefaultState());
            check("jigsaw: saves bcjt_branch", save(world, "bcjt_branch", b, new BlockPos(2, 1, 1)), b);
            // Vertical jigsaws, a 1.12.2 block name as the final state, and a structure void.
            BlockPos v = s.add(0, 0, 9);
            put(world, v, FrontAndTop.UP_NORTH, "bc:v1", "bc:v2", "minecraft:empty", "minecraft:spruce_planks", TileEntityJigsaw.Joint.ALIGNED, 0, 0);
            put(world, v.east(), FrontAndTop.DOWN_EAST, "bc:v2", "bc:v1", "minecraft:empty", "minecraft:planks[variant=birch]",
                    TileEntityJigsaw.Joint.ROLLABLE, 0, 0);
            put(world, v.east(2), FrontAndTop.NORTH_UP, "bc:v3", "minecraft:empty", "minecraft:empty", "minecraft:structure_void",
                    TileEntityJigsaw.Joint.ALIGNED, 0, 0);
            check("jigsaw: saves bcjt_vert", save(world, "bcjt_vert", v, new BlockPos(3, 1, 1)), v);

            File file = new File(new File(DimensionManager.getCurrentSaveRootDirectory(), "structures"), "bcjt_start.nbt");
            NBTTagCompound root;
            try (InputStream in = new FileInputStream(file)) {
                root = CompressedStreamTools.readCompressed(in);
            }
            NBTTagList palette = root.getTagList("palette", 10);
            boolean named = false;
            for (int i = 0; i < palette.tagCount(); i++) {
                named |= "backportcore:jigsaw".equals(palette.getCompoundTagAt(i).getString("Name"));
            }
            NBTTagList blocks = root.getTagList("blocks", 10);
            boolean tiles = false;
            for (int i = 0; i < blocks.tagCount(); i++) {
                NBTTagCompound n = blocks.getCompoundTagAt(i).getCompoundTag("nbt");
                tiles |= "bc:out".equals(n.getString("name")) && "bc:in".equals(n.getString("target")) && n.getInteger("placement_priority") == 3;
            }
            check("jigsaw: the saved file has the block and its tile data", file.isFile() && named && tiles, file);

            ModernTemplate.forgetWorldTemplates();
            ModernTemplate t = ModernTemplate.load(new ResourceLocation(SelfTestMod.MOD_ID, "bcjt_start"));
            List<ModernTemplate.Jigsaw> j = t.jigsaws();
            ModernTemplate.Jigsaw in = j.size() == 2 ? (j.get(0).pos.getX() == 0 ? j.get(0) : j.get(1)) : null;
            ModernTemplate.Jigsaw out = j.size() == 2 ? (in == j.get(0) ? j.get(1) : j.get(0)) : null;
            check("jigsaw: ModernTemplate reads the 1.12.2 file's jigsaws", in != null && out != null
                    && in.pos.equals(BlockPos.ORIGIN) && in.front == EnumFacing.WEST && in.top == EnumFacing.UP && "bc:start".equals(in.name)
                    && "minecraft:gold_block".equals(in.finalState) && "aligned".equals(in.joint)
                    && out.pos.equals(new BlockPos(2, 0, 0)) && out.front == EnumFacing.EAST && "bc:out".equals(out.name) && "bc:in".equals(out.target)
                    && (SelfTestMod.MOD_ID + ":jt/branch").equals(out.pool) && out.placementPriority == 3 && out.selectionPriority == 2
                    && "minecraft:iron_block".equals(out.finalState), j);
            ModernTemplate tv = ModernTemplate.load(new ResourceLocation(SelfTestMod.MOD_ID, "bcjt_vert"));
            ModernTemplate.Jigsaw v1 = null;
            ModernTemplate.Jigsaw v2 = null;
            for (ModernTemplate.Jigsaw x : tv.jigsaws()) {
                if ("bc:v1".equals(x.name)) {
                    v1 = x;
                } else if ("bc:v2".equals(x.name)) {
                    v2 = x;
                }
            }
            check("jigsaw: vertical jigsaws keep front, top and joint", v1 != null && v2 != null && v1.front == EnumFacing.UP
                    && v1.top == EnumFacing.NORTH && "aligned".equals(v1.joint) && v2.front == EnumFacing.DOWN && v2.top == EnumFacing.EAST
                    && "rollable".equals(v2.joint), tv.jigsaws());
            check("jigsaw: no unresolved blocks in the saved files", t.unresolvedBlocks().isEmpty() && tv.unresolvedBlocks().isEmpty(),
                    t.unresolvedBlocks() + " " + tv.unresolvedBlocks());
        });

        then(1, "jigsaw: template final states", world -> {
            BlockPos p = base(world).add(0, 4, 30);
            ModernTemplate t = ModernTemplate.load(new ResourceLocation(SelfTestMod.MOD_ID, "bcjt_start"));
            t.place(world, p, TemplatePlacement.of(Rotation.NONE, Mirror.NONE));
            check("jigsaw: a template places each jigsaw as its final state", isBlock(world, p, Blocks.GOLD_BLOCK)
                    && isBlock(world, p.east(), Blocks.STONE) && isBlock(world, p.east(2), Blocks.IRON_BLOCK), world.getBlockState(p));
            ModernTemplate tv = ModernTemplate.load(new ResourceLocation(SelfTestMod.MOD_ID, "bcjt_vert"));
            BlockPos q = p.add(0, 0, 3);
            world.setBlockState(q.east(2), Blocks.GLASS.getDefaultState());
            tv.place(world, q, TemplatePlacement.of(Rotation.NONE, Mirror.NONE));
            IBlockState spruce = world.getBlockState(q);
            IBlockState birch = world.getBlockState(q.east());
            check("jigsaw: today's state and 1.12.2's name both place; a structure void places nothing",
                    spruce.getBlock() == Blocks.PLANKS && "spruce".equals(spruce.getValue(net.minecraft.block.BlockPlanks.VARIANT).getName())
                            && birch.getBlock() == Blocks.PLANKS && "birch".equals(birch.getValue(net.minecraft.block.BlockPlanks.VARIANT).getName())
                            && isBlock(world, q.east(2), Blocks.GLASS), spruce + " " + birch + " " + world.getBlockState(q.east(2)));

            BlockPos k = p.add(0, 0, 6);
            t.place(world, k, TemplatePlacement.of(Rotation.CLOCKWISE_90, Mirror.NONE).keepJigsaws(true));
            // Turned a quarter clockwise around the corner: x becomes z.
            IBlockState first = world.getBlockState(k);
            IBlockState third = world.getBlockState(k.south(2));
            TileEntityJigsaw outTile = jigsawAt(world, k.south(2));
            check("jigsaw: kept jigsaws stay jigsaw blocks, turned, with their data", first == jigsawState(FrontAndTop.NORTH_UP)
                    && third == jigsawState(FrontAndTop.SOUTH_UP) && outTile != null && "bc:out".equals(outTile.getName())
                    && "bc:in".equals(outTile.getTarget()) && outTile.getPlacementPriority() == 3 && "minecraft:iron_block".equals(outTile.getFinalState()),
                    first + " " + third);
        });

        then(1, "jigsaw: generate", world -> {
            Player op = new Player(world, "[BCJigsawOp]", true);
            BlockPos g = base(world).add(0, 8, 48);
            int[] levels = {1, 1, 0};
            boolean[] keep = {false, true, false};
            for (int c = 0; c < 3; c++) {
                BlockPos at = g.add(0, 0, c * 12);
                world.getChunk(at.add(-8, 0, -8));
                world.getChunk(at.add(8, 0, 8));
                put(world, at, FrontAndTop.EAST_UP, "minecraft:empty", "bc:start", POOL, "minecraft:air", TileEntityJigsaw.Joint.ALIGNED, 0, 0);
                BlockPos position = at.east();
                Random r = largeFeature(world.getSeed(), position.getX() >> 4, position.getZ() >> 4);
                Rotation rotation = Rotation.values()[r.nextInt(4)];
                boolean alt = r.nextInt(3) != 0;
                List<ModernJigsaw.GeneratedPiece> layout = ModernJigsaw.layout(world, new ResourceLocation(POOL), "bc:start", levels[c], position);
                check("jigsaw: generate lays out the start with the chunk's start random (" + rotation + (alt ? ", alt" : "") + ")",
                        layout != null && layout.size() == (levels[c] > 0 ? 2 : 1) && layout.get(0).rotation() == rotation
                                && layout.get(0).element().template().getPath().equals(alt ? "bcjt_start_alt" : "bcjt_start"), layout);
                boolean placed = JigsawNetwork.apply(op, new JigsawNetwork.Generate(at, levels[c], keep[c]));
                EnumFacing d = rotation.rotate(EnumFacing.EAST);
                BlockPos p0 = position.down();
                Block middle = alt ? Blocks.SANDSTONE : Blocks.STONE;
                boolean ok;
                String detail = "";
                if (keep[c]) {
                    TileEntityJigsaw a = jigsawAt(world, p0);
                    TileEntityJigsaw out = jigsawAt(world, p0.offset(d, 2));
                    TileEntityJigsaw in = jigsawAt(world, p0.offset(d, 3));
                    ok = a != null && "bc:start".equals(a.getName()) && isBlock(world, p0.offset(d), middle) && out != null
                            && "bc:out".equals(out.getName()) && out.front() == d && in != null && "bc:in".equals(in.getName())
                            && in.front() == d.getOpposite() && isBlock(world, p0.offset(d, 4), Blocks.LAPIS_BLOCK);
                    detail = world.getBlockState(p0) + " " + world.getBlockState(p0.offset(d, 3));
                } else {
                    ok = isBlock(world, p0, Blocks.GOLD_BLOCK) && isBlock(world, p0.offset(d), middle) && isBlock(world, p0.offset(d, 2), Blocks.IRON_BLOCK)
                            && (levels[c] == 0 ? world.isAirBlock(p0.offset(d, 3)) && world.isAirBlock(p0.offset(d, 4))
                            : isBlock(world, p0.offset(d, 3), Blocks.DIAMOND_BLOCK) && isBlock(world, p0.offset(d, 4), Blocks.LAPIS_BLOCK));
                    for (int i = 0; i < 5; i++) {
                        detail += world.getBlockState(p0.offset(d, i)).getBlock().getRegistryName().getPath() + " ";
                    }
                }
                check("jigsaw: generate levels " + levels[c] + (keep[c] ? ", keep jigsaws" : "") + " builds the start (floor one below) and its branch",
                        placed && ok && world.getBlockState(at).getBlock() == CoreBlocks.jigsaw, detail);
            }
            BlockPos none = g.add(0, 0, 40);
            put(world, none, FrontAndTop.EAST_UP, "minecraft:empty", "bc:nowhere", POOL, "minecraft:air", TileEntityJigsaw.Joint.ALIGNED, 0, 0);
            check("jigsaw: no such start jigsaw: nothing placed", !JigsawNetwork.apply(op, new JigsawNetwork.Generate(none, 1, false))
                    && world.isAirBlock(none.east().down()), "");
        });
    }
}
