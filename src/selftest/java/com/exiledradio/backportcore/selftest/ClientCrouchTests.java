package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.map.MapIcons;
import com.exiledradio.backportcore.pose.Pose;
import com.exiledradio.backportcore.pose.Poses;
import com.exiledradio.backportcore.pose.client.ClientHooks;

import net.minecraft.block.BlockSlab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapDecoration;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * 1.14's short crouch on a real client ({@code gradlew runClient -Pselftest}), with the test rules' short crouch on.
 * The player stands on a stone strip at y 200 whose far part has a ceiling of top-half slabs one block up (a 1.5
 * gap). Driven through the real key bindings: sneak lowers the box to 1.5 and the eyes to 1.27, with the camera
 * easing down (P5); sneaking forward walks into the gap, and the server (integrated) keeps the player there; letting go
 * of sneak under the ceiling holds the crouch, at sneaking speed, drawn crouching; walking back out stands up. Also
 * that a {@link MapIcons} decoration is drawn by Core (P34) and a vanilla one is not.
 */
@SideOnly(Side.CLIENT)
final class ClientCrouchTests {

    private static BlockPos base;
    private static int stage;
    private static int wait;
    private static int retries;
    private static double startX;
    static boolean done;

    private ClientCrouchTests() {
    }

    /** What the test holds down: given to the player's input each tick, as the keys would be. */
    private static boolean sneakDown;
    private static boolean forwardDown;
    private static Object inputDriver;

    private static void key(KeyBinding binding, boolean down) {
        if (binding == Minecraft.getMinecraft().gameSettings.keyBindSneak) {
            sneakDown = down;
        } else {
            forwardDown = down;
        }
    }

    /** Highest priority: before Backport Core's own handler, as vanilla's key reading comes before it. */
    public static final class InputDriver {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
        public void onInput(net.minecraftforge.client.event.InputUpdateEvent event) {
            net.minecraft.util.MovementInput input = event.getMovementInput();
            input.sneak = sneakDown;
            input.moveStrafe = 0.0F;
            input.moveForward = forwardDown ? (sneakDown ? 0.3F : 1.0F) : 0.0F;
            input.forwardKeyDown = forwardDown;
        }
    }

    private static String size(Minecraft mc) {
        return mc.player.height + " tall, eyes " + mc.player.getEyeHeight() + ", pose " + Poses.get(mc.player) + " at " + mc.player.getPositionVector();
    }

    private static EntityPlayerMP server(Minecraft mc) {
        return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
    }

    /** Called every client tick in a world until {@link #done}. */
    static void tick(Minecraft mc) {
        if (done) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (stage) {
            case 0:
                PoseTests.rulesOn = true;
                PoseTests.crouchOn = true;
                inputDriver = new InputDriver();
                MinecraftForge.EVENT_BUS.register(inputDriver);
                mapIcons();
                build(mc);
                stage = 1;
                wait = 30;
                break;
            case 1:
                if (!(base != null && mc.world.getBlockState(base.add(4, 1, 0)).getBlock() == Blocks.STONE_SLAB
                        && mc.player.getDistanceSq(base.getX() + 0.5, base.getY(), base.getZ() + 0.5) < 0.01 && mc.player.onGround)
                        && ++retries < 100) {
                    wait = 5;
                    return;
                }
                check("client: crouch: standing 1.8, eyes 1.62", mc.player.height == 1.8F && Math.abs(mc.player.getEyeHeight() - 1.62F) < 1.0E-4F, size(mc));
                mc.player.rotationYaw = mc.player.prevRotationYaw = -90.0F; // facing +x
                mc.player.rotationPitch = mc.player.prevRotationPitch = 0.0F;
                key(mc.gameSettings.keyBindSneak, true);
                stage = 2;
                wait = 1;
                break;
            case 2: {
                float camera = ClientHooks.cameraEyeHeight(mc.player, 0.0F);
                check("client: crouch: the camera eases down, not jumps", camera > 1.28F && camera < 1.62F, camera);
                stage = 3;
                wait = 15;
                break;
            }
            case 3: {
                float camera = ClientHooks.cameraEyeHeight(mc.player, 1.0F);
                check("client: crouch: sneaking: SNEAKING, 1.5 tall, eyes 1.27",
                        Poses.get(mc.player) == Pose.SNEAKING && mc.player.height == 1.5F && Math.abs(mc.player.getEyeHeight() - 1.27F) < 1.0E-4F, size(mc));
                check("client: crouch: the camera settles at 1.27", Math.abs(camera - 1.27F) < 0.01F, camera);
                EntityPlayerMP sp = server(mc);
                check("client: crouch: the server agrees, 1.5 tall", sp != null && sp.height == 1.5F && Poses.get(sp) == Pose.SNEAKING,
                        sp == null ? "no server player" : sp.height + " " + Poses.get(sp));
                key(mc.gameSettings.keyBindForward, true);
                stage = 4;
                wait = 70;
                break;
            }
            case 4: {
                key(mc.gameSettings.keyBindForward, false);
                check("client: crouch: sneaking walks into the 1.5-block gap", mc.player.posX > base.getX() + 4.2D, size(mc));
                key(mc.gameSettings.keyBindSneak, false);
                stage = 5;
                wait = 10;
                break;
            }
            case 5: {
                EntityPlayerMP sp = server(mc);
                check("client: crouch: the server keeps the player in the gap (no pull back)",
                        sp != null && Math.abs(sp.posX - mc.player.posX) < 0.3D && sp.posX > base.getX() + 4.0D,
                        sp == null ? "no server player" : sp.posX + " vs " + mc.player.posX);
                check("client: crouch: sneak let go under the ceiling: held crouched, 1.5, eyes 1.27",
                        !mc.player.isSneaking() && Poses.get(mc.player) == Pose.SNEAKING && mc.player.height == 1.5F
                                && Math.abs(mc.player.getEyeHeight() - 1.27F) < 1.0E-4F && Poses.isVisuallySneaking(mc.player), size(mc));
                check("client: crouch: held crouched on the server too", sp != null && Poses.get(sp) == Pose.SNEAKING && sp.height == 1.5F,
                        sp == null ? "no server player" : sp.height + " " + Poses.get(sp));
                check("client: crouch: eyes never inside the ceiling", !mc.player.isEntityInsideOpaqueBlock(), size(mc));
                // Drawn crouching: for the one render, the player says it is sneaking.
                RenderPlayer renderer = (RenderPlayer) (Object) mc.getRenderManager().getEntityRenderObject(mc.player);
                MinecraftForge.EVENT_BUS.post(new RenderPlayerEvent.Pre(mc.player, renderer, 0.0F, 0.0D, 0.0D, 0.0D));
                boolean during = mc.player.isSneaking();
                MinecraftForge.EVENT_BUS.post(new RenderPlayerEvent.Post(mc.player, renderer, 0.0F, 0.0D, 0.0D, 0.0D));
                check("client: crouch: drawn crouching while held, and only for the render", during && !mc.player.isSneaking(), during + " " + mc.player.isSneaking());
                startX = mc.player.posX;
                key(mc.gameSettings.keyBindForward, true);
                stage = 6;
                wait = 20;
                break;
            }
            case 6: {
                key(mc.gameSettings.keyBindForward, false);
                double moved = mc.player.posX - startX;
                // Walking covers about 4.3 blocks in 20 ticks; sneaking speed, 0.3 of it, about 1.3.
                check("client: crouch: held crouched moves at sneaking speed", moved > 0.3D && moved < 2.0D, moved);
                mc.player.rotationYaw = mc.player.prevRotationYaw = 90.0F; // facing -x, back out
                key(mc.gameSettings.keyBindForward, true);
                stage = 7;
                wait = 100;
                break;
            }
            case 7:
                key(mc.gameSettings.keyBindForward, false);
                stage = 8;
                wait = 10;
                break;
            case 8:
                check("client: crouch: out from under the ceiling: stands, 1.8, eyes 1.62",
                        mc.player.posX < base.getX() + 2.7D && Poses.get(mc.player) == Pose.STANDING && mc.player.height == 1.8F
                                && Math.abs(mc.player.getEyeHeight() - 1.62F) < 1.0E-4F, size(mc));
                PoseTests.crouchOn = false;
                PoseTests.rulesOn = false;
                MinecraftForge.EVENT_BUS.unregister(inputDriver);
                done = true;
                break;
            default:
                break;
        }
    }

    private static void mapIcons() {
        MapDecoration.Type plains = MapIcons.get("plains_village", MapIcons.LIGHT_GRAY);
        check("client: map icons: Core draws its own (P34)", MapIcons.render(new MapDecoration(plains, (byte) 0, (byte) 0, (byte) 8), 0), "");
        check("client: map icons: vanilla's are left to vanilla", !MapIcons.render(new MapDecoration(MapDecoration.Type.MANSION, (byte) 0, (byte) 0, (byte) 8), 0), "");
        check("client: map icons: P34 applied", "applied".equals(com.exiledradio.backportcore.asm.Asm.state("P34")), com.exiledradio.backportcore.asm.Asm.state("P34"));
        BackportCore.LOGGER.info("CROUCH map icon {} is number {}", plains, plains.getIcon());
    }

    /** A stone strip along x at y 200; x 3..12 has top-half slabs one block up (a 1.5-block gap) and a wall at x 13. */
    private static void build(Minecraft mc) {
        mc.getIntegratedServer().addScheduledTask(() -> {
            World world = mc.getIntegratedServer().getWorld(0);
            if (base == null) {
                BlockPos spawn = world.getSpawnPoint();
                base = new BlockPos(spawn.getX() + 40, 200, spawn.getZ() + 40);
            }
            for (BlockPos p : BlockPos.getAllInBox(base.add(-4, -1, -3), base.add(14, 4, 3))) {
                world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
            }
            for (BlockPos p : BlockPos.getAllInBox(base.add(-4, -1, -3), base.add(14, -1, 3))) {
                world.setBlockState(p, Blocks.STONE.getDefaultState(), 2);
            }
            for (BlockPos p : BlockPos.getAllInBox(base.add(3, 1, -3), base.add(12, 1, 3))) {
                world.setBlockState(p, Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP), 2);
            }
            for (BlockPos p : BlockPos.getAllInBox(base.add(13, 0, -3), base.add(13, 2, 3))) {
                world.setBlockState(p, Blocks.STONE.getDefaultState(), 2);
            }
            EntityPlayerMP player = mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
            player.capabilities.isFlying = false;
            player.sendPlayerAbilities();
            player.connection.setPlayerLocation(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, -90.0F, 0.0F);
        });
    }
}
