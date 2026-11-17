package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.pose.MovementHooks;
import com.exiledradio.backportcore.pose.PlayerMovement;
import com.exiledradio.backportcore.pose.Pose;
import com.exiledradio.backportcore.pose.PoseNetwork;
import com.exiledradio.backportcore.pose.PoseRules;
import com.exiledradio.backportcore.pose.Poses;
import com.google.common.collect.MapMaker;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * The pose engine with a test rule set (a player is "swimming" when the test says so) and a test movement:
 * sizes and eyes, crawling in a one-block gap for 200 ticks with no suffocation, standing up when the ceiling
 * goes, rules off, the pose packet, and the movement hooks reached from inside vanilla's travel (patch P1).
 */
final class PoseTests {

    static boolean rulesOn;
    static boolean crawlOn;
    static boolean crouchOn;
    static final Set<EntityPlayer> SWIMMERS = Collections.newSetFromMap(new MapMaker().weakKeys().<EntityPlayer, Boolean>makeMap());
    static int travelCalls;
    static float slowDown = -1.0F;
    static double gravity = -1.0D;

    private static BlockPos base;

    private PoseTests() {
    }

    static void register() {
        Poses.register(new PoseRules() {
            @Override
            public boolean enabled() {
                return rulesOn;
            }

            @Override
            public boolean isSwimming(EntityPlayer player) {
                return SWIMMERS.contains(player);
            }

            @Override
            public boolean crawls() {
                return crawlOn;
            }

            @Override
            public boolean shortCrouch() {
                return crouchOn;
            }
        });
        Poses.registerMovement(new PlayerMovement() {
            @Override
            public void beforeTravel(EntityPlayer player) {
                travelCalls++;
            }

            @Override
            public float waterSlowDown(EntityPlayer player, float vanilla) {
                return slowDown >= 0.0F ? slowDown : vanilla;
            }

            @Override
            public double waterGravity(EntityPlayer player, double vanilla) {
                return gravity >= 0.0D ? gravity : vanilla;
            }
        });
    }

    static EntityPlayerMP player(WorldServer world, String name) {
        EntityPlayerMP player = new EntityPlayerMP(world.getMinecraftServer(), world,
                new GameProfile(UUID.nameUUIDFromBytes(("coreselftest:" + name).getBytes()), "Pose" + name),
                new PlayerInteractionManager(world));
        // A channel that keeps what it is sent: entering water grants an advancement, whose recipe packet
        // needs one.
        NetworkManager manager = new NetworkManager(EnumPacketDirection.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(manager);
        player.connection = new NetHandlerPlayServer(world.getMinecraftServer(), manager, player);
        player.interactionManager.setGameType(GameType.SURVIVAL);
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayerMP.class, player, 0, "field_147101_bU");
        return player;
    }

    private static void fill(World world, BlockPos from, BlockPos to, net.minecraft.block.state.IBlockState state) {
        for (BlockPos p : BlockPos.getAllInBox(from, to)) {
            world.setBlockState(p, state, 2);
        }
    }

    private static boolean sized(EntityPlayer p, float w, float h, float eye) {
        return p.width == w && p.height == h && Math.abs(p.getEyeHeight() - eye) < 1.0E-6F;
    }

    private static String size(EntityPlayer p) {
        return p.width + " x " + p.height + ", eyes " + p.getEyeHeight() + ", pose " + Poses.get(p);
    }

    static void schedule() {
        then(1, "pose setup", world -> {
            BlockPos spawn = world.getSpawnPoint();
            base = new BlockPos(spawn.getX(), 180, spawn.getZ() + 96);
            fill(world, base.add(-2, -1, -2), base.add(14, 4, 2), Blocks.AIR.getDefaultState());
            fill(world, base.add(-2, -1, -2), base.add(14, -1, 2), Blocks.STONE.getDefaultState());
            check("pose rules off: engine inactive", !Poses.active());
        });

        then(1, "swimming pose", world -> {
            rulesOn = true;
            check("pose rules on: engine active", Poses.active());
            EntityPlayerMP p = player(world, "swim");
            p.setLocationAndAngles(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D, 0.0F, 0.0F);
            p.onUpdateEntity();
            check("standing: 0.6 x 1.8, eyes 1.62", sized(p, 0.6F, 1.8F, 1.62F), size(p));
            SWIMMERS.add(p);
            p.onUpdateEntity();
            check("swimming: pose SWIMMING", Poses.get(p) == Pose.SWIMMING, size(p));
            check("swimming: 0.6 x 0.6, eyes 0.4", sized(p, 0.6F, 0.6F, 0.4F), size(p));
            check("swimming: box is 0.6 tall", Math.abs(p.getEntityBoundingBox().maxY - p.getEntityBoundingBox().minY - 0.6D) < 1.0E-6D, p.getEntityBoundingBox());
            p.setSneaking(true);
            p.onUpdateEntity();
            check("swimming and sneaking: still SWIMMING, eyes 0.4", Poses.get(p) == Pose.SWIMMING && sized(p, 0.6F, 0.6F, 0.4F), size(p));
            p.setSneaking(false);
            SWIMMERS.remove(p);
            p.onUpdateEntity();
            check("no longer swimming, room above: stands, 0.6 x 1.8, eyes 1.62", Poses.get(p) == Pose.STANDING && sized(p, 0.6F, 1.8F, 1.62F), size(p));
            SWIMMERS.add(p);
            p.onUpdateEntity();
            rulesOn = false;
            p.onUpdateEntity();
            check("rules switched off mid-swim: back to vanilla's 1.8 and 1.62", Poses.get(p) == Pose.STANDING && sized(p, 0.6F, 1.8F, 1.62F), size(p));
            SWIMMERS.remove(p);
            rulesOn = true;
        });

        then(1, "crawling in a one-block gap", world -> {
            crawlOn = true;
            // A gap one block tall, x 3..14 of the area; the player starts at x 6.
            fill(world, base.add(3, 1, -2), base.add(14, 1, 2), Blocks.STONE.getDefaultState());
            EntityPlayerMP p = player(world, "crawl");
            p.setLocationAndAngles(base.getX() + 6.5D, base.getY(), base.getZ() + 0.5D, 0.0F, 0.0F);
            p.onUpdateEntity();
            check("in the gap: pose SWIMMING (crawling)", Poses.get(p) == Pose.SWIMMING && Poses.isCrawling(p), size(p));
            check("crawling: 0.6 x 0.6, eyes 0.4", sized(p, 0.6F, 0.6F, 0.4F), size(p));
            // The first tick, put there standing, can hurt (vanilla checks before the pose changes, as 1.14 does).
            float lowest = p.getHealth();
            float start = lowest;
            boolean everInside = false;
            boolean everStood = false;
            for (int i = 0; i < 200; i++) {
                p.onUpdateEntity();
                everInside |= p.isEntityInsideOpaqueBlock();
                everStood |= Poses.get(p) != Pose.SWIMMING || p.height != 0.6F;
                lowest = Math.min(lowest, p.getHealth());
            }
            check("200 ticks in the gap: stays crawling", !everStood, size(p));
            check("200 ticks in the gap: eyes never inside a block", !everInside, "");
            check("200 ticks in the gap: no suffocation damage", lowest >= start, lowest + " from " + start);
            fill(world, base.add(3, 1, -2), base.add(14, 1, 2), Blocks.AIR.getDefaultState());
            p.onUpdateEntity();
            check("ceiling gone: stands, 0.6 x 1.8, eyes 1.62", Poses.get(p) == Pose.STANDING && sized(p, 0.6F, 1.8F, 1.62F), size(p));

            // Control: with crawling off the same gap puts a standing player's eyes in the ceiling.
            fill(world, base.add(3, 1, -2), base.add(14, 1, 2), Blocks.STONE.getDefaultState());
            crawlOn = false;
            EntityPlayerMP q = player(world, "nocrawl");
            q.setLocationAndAngles(base.getX() + 6.5D, base.getY(), base.getZ() + 0.5D, 0.0F, 0.0F);
            q.onUpdateEntity();
            check("crawling off: no crawl, vanilla's 1.8 box", Poses.get(q) != Pose.SWIMMING && q.height == 1.8F, size(q));
            check("crawling off: the test can see suffocation (eyes in the ceiling)", q.isEntityInsideOpaqueBlock(), "");
            crawlOn = true;
            // Room to sneak but not to stand (snow 0.25 deep under a ceiling 2 up: 1.75): a crawler gets up
            // only to vanilla's 1.65 sneaking box, as 1.14's pose rule goes from swimming to sneaking.
            fill(world, base.add(3, 1, -2), base.add(14, 1, 2), Blocks.STONE.getDefaultState());
            EntityPlayerMP r = player(world, "sneakroom");
            r.setLocationAndAngles(base.getX() + 6.5D, base.getY(), base.getZ() + 0.5D, 0.0F, 0.0F);
            r.onUpdateEntity();
            check("sneak-room setup: crawling first", Poses.get(r) == Pose.SWIMMING, size(r));
            fill(world, base.add(3, 1, -2), base.add(14, 1, 2), Blocks.AIR.getDefaultState());
            fill(world, base.add(3, 2, -2), base.add(14, 2, 2), Blocks.STONE.getDefaultState());
            fill(world, base.add(3, 0, -2), base.add(14, 0, 2), Blocks.SNOW_LAYER.getDefaultState()
                    .withProperty(net.minecraft.block.BlockSnow.LAYERS, 3));
            r.setLocationAndAngles(base.getX() + 6.5D, base.getY() + 0.25D, base.getZ() + 0.5D, 0.0F, 0.0F);
            r.onUpdateEntity();
            check("room to sneak, not stand: pose SNEAKING with 1.12.2's 0.6 x 1.65, eyes 1.54",
                    Poses.get(r) == Pose.SNEAKING && sized(r, 0.6F, 1.65F, 1.54F), size(r));
            fill(world, base.add(3, 0, -2), base.add(14, 2, 2), Blocks.AIR.getDefaultState());
            r.onUpdateEntity();
            check("then room to stand: 0.6 x 1.8, eyes 1.62", Poses.get(r) == Pose.STANDING && sized(r, 0.6F, 1.8F, 1.62F), size(r));
        });

        then(1, "1.14's short crouch", world -> {
            rulesOn = true;
            crawlOn = true;
            crouchOn = false;
            check("short crouch: off until the rules ask", !Poses.shortCrouch() && Poses.height(Pose.SNEAKING) == 1.65F, Poses.height(Pose.SNEAKING));
            crouchOn = true;
            check("short crouch: on, sneaking is 1.5 tall", Poses.shortCrouch() && Poses.height(Pose.SNEAKING) == 1.5F, Poses.height(Pose.SNEAKING));
            fill(world, base.add(-2, 0, -2), base.add(14, 4, 2), Blocks.AIR.getDefaultState());
            EntityPlayerMP p = player(world, "crouch");
            p.setLocationAndAngles(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D, 0.0F, 0.0F);
            p.onUpdateEntity();
            check("crouch, open air: standing 0.6 x 1.8, eyes 1.62", Poses.get(p) == Pose.STANDING && sized(p, 0.6F, 1.8F, 1.62F), size(p));
            p.setSneaking(true);
            p.onUpdateEntity();
            check("sneaking: pose SNEAKING, 0.6 x 1.5, eyes 1.27", Poses.get(p) == Pose.SNEAKING && sized(p, 0.6F, 1.5F, 1.27F), size(p));
            check("sneaking: box is 1.5 tall", Math.abs(p.getEntityBoundingBox().maxY - p.getEntityBoundingBox().minY - 1.5D) < 1.0E-6D, p.getEntityBoundingBox());
            p.onUpdateEntity();
            check("sneaking, next tick: still 1.5 (vanilla's 1.65 put back)", sized(p, 0.6F, 1.5F, 1.27F), size(p));
            p.setSneaking(false);
            p.onUpdateEntity();
            check("sneak released in the open: stands, 1.8, eyes 1.62", Poses.get(p) == Pose.STANDING && sized(p, 0.6F, 1.8F, 1.62F), size(p));

            // A gap 1.5 tall: top-half stone slabs one block up, x 3..14. The player sneaks in from x 1.5.
            net.minecraft.block.state.IBlockState topSlab = Blocks.STONE_SLAB.getDefaultState()
                    .withProperty(net.minecraft.block.BlockSlab.HALF, net.minecraft.block.BlockSlab.EnumBlockHalf.TOP);
            fill(world, base.add(3, 1, -2), base.add(14, 1, 2), topSlab);
            p.setLocationAndAngles(base.getX() + 1.5D, base.getY(), base.getZ() + 0.5D, 0.0F, 0.0F);
            p.setSneaking(true);
            p.onUpdateEntity();
            p.move(net.minecraft.entity.MoverType.SELF, 3.0D, 0.0D, 0.0D);
            check("crouched: walks into a 1.5-block gap", p.posX > base.getX() + 4.0D, p.posX - base.getX());
            p.onUpdateEntity();
            p.setSneaking(false);
            p.onUpdateEntity();
            check("sneak released under the 1.5 ceiling: held crouched, 1.5, eyes 1.27",
                    Poses.get(p) == Pose.SNEAKING && sized(p, 0.6F, 1.5F, 1.27F) && Poses.isVisuallySneaking(p), size(p));
            float start = p.getHealth();
            boolean everInside = false;
            for (int i = 0; i < 100; i++) {
                p.onUpdateEntity();
                everInside |= p.isEntityInsideOpaqueBlock();
            }
            check("100 ticks held crouched: stays SNEAKING", Poses.get(p) == Pose.SNEAKING && p.height == 1.5F, size(p));
            check("100 ticks held crouched: eyes never inside a block, no damage", !everInside && p.getHealth() >= start, p.getHealth());
            p.move(net.minecraft.entity.MoverType.SELF, -6.0D, 0.0D, 0.0D);
            p.onUpdateEntity();
            check("out from under the ceiling: stands", Poses.get(p) == Pose.STANDING && sized(p, 0.6F, 1.8F, 1.62F), size(p));

            // Control: 1.12.2's 1.65 crouch can't go in.
            crouchOn = false;
            EntityPlayerMP q = player(world, "nocrouch");
            q.setLocationAndAngles(base.getX() + 1.5D, base.getY(), base.getZ() + 0.5D, 0.0F, 0.0F);
            q.setSneaking(true);
            q.onUpdateEntity();
            q.onUpdateEntity();
            q.move(net.minecraft.entity.MoverType.SELF, 3.0D, 0.0D, 0.0D);
            check("short crouch off: 1.65 sneaking stops at the 1.5 gap", q.posX < base.getX() + 3.0D, q.posX - base.getX() + " " + size(q));
            check("short crouch off: sneaking eyes 1.54", Math.abs(q.getEyeHeight() - 1.54F) < 1.0E-6F, size(q));
            crouchOn = true;
            fill(world, base.add(3, 1, -2), base.add(14, 1, 2), Blocks.AIR.getDefaultState());
            crouchOn = false;
            p.onUpdateEntity();
        });

        then(1, "pose packet", world -> {
            ByteBuf buf = Unpooled.buffer();
            new PoseNetwork.PosePacket(4242, Pose.SPIN_ATTACK).toBytes(buf);
            PoseNetwork.PosePacket back = new PoseNetwork.PosePacket();
            back.fromBytes(buf);
            check("pose packet round trip", back.entityId() == 4242 && back.pose() == Pose.SPIN_ATTACK, back.entityId() + " " + back.pose());
            check("pose packet is 5 bytes", buf.writerIndex() == 5, buf.writerIndex());
        });

        then(1, "movement hooks from vanilla's travel (P1)", world -> {
            BlockPos pool = base.add(0, 0, 0);
            fill(world, pool.add(-2, 0, -2), pool.add(2, 3, 2), Blocks.WATER.getDefaultState());
            EntityPlayerMP p = player(world, "travel");
            p.setLocationAndAngles(pool.getX() + 0.5D, pool.getY() + 0.5D, pool.getZ() + 0.5D, 0.0F, 0.0F);
            p.onUpdateEntity();
            check("player is in water", p.isInWater(), "");
            int calls = travelCalls;
            slowDown = 0.5F;
            gravity = 0.0D;
            p.motionX = 0.1D;
            p.motionY = 0.0D;
            p.motionZ = 0.0D;
            p.travel(0.0F, 0.0F, 0.0F);
            check("beforeTravel reached from travel", travelCalls == calls + 1, travelCalls - calls);
            check("waterSlowDown used by travel: 0.1 x 0.5", Math.abs(p.motionX - 0.05D) < 1.0E-9D, p.motionX);
            check("waterGravity used by travel: no pull", Math.abs(p.motionY) < 1.0E-9D, p.motionY);
            slowDown = -1.0F;
            gravity = -1.0D;
            p.motionX = 0.1D;
            p.motionY = 0.0D;
            p.travel(0.0F, 0.0F, 0.0F);
            check("hooks at their defaults: vanilla's 0.8 slowdown", Math.abs(p.motionX - 0.08D) < 1.0E-7D, p.motionX);
            check("hooks at their defaults: vanilla's 0.02 pull", Math.abs(p.motionY + MovementHooks.WATER_GRAVITY) < 1.0E-9D, p.motionY);

            EntityPig pig = new EntityPig(world);
            pig.setLocationAndAngles(pool.getX() + 0.5D, pool.getY() + 0.5D, pool.getZ() + 0.5D, 0.0F, 0.0F);
            world.spawnEntity(pig);
            pig.onUpdate();
            slowDown = 0.5F;
            gravity = 0.0D;
            calls = travelCalls;
            pig.motionX = 0.1D;
            pig.motionY = 0.0D;
            pig.motionZ = 0.0D;
            pig.travel(0.0F, 0.0F, 0.0F);
            check("a pig never reaches the player movement", travelCalls == calls, travelCalls - calls);
            check("a pig keeps vanilla's 0.8 and 0.02", Math.abs(pig.motionX - 0.08D) < 1.0E-7D && Math.abs(pig.motionY + 0.02D) < 1.0E-9D,
                    pig.motionX + " " + pig.motionY);
            slowDown = -1.0F;
            gravity = -1.0D;
            pig.setDead();
            rulesOn = false;
            calls = travelCalls;
            p.travel(0.0F, 0.0F, 0.0F);
            check("rules off: movement hooks not called", travelCalls == calls, "");
            fill(world, pool.add(-2, 0, -2), pool.add(2, 3, 2), Blocks.AIR.getDefaultState());
        });
    }
}
