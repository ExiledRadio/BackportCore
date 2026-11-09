package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.pose.Pose;
import com.exiledradio.backportcore.pose.Poses;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * The client's third-person camera in the low pose ({@code gradlew runClient -Pselftest}). The player crawls in
 * a one-block stone tunnel that crosses a chunk border, then swims in the same tunnel full of water.
 *
 * <p>For back and front view at several pitches and yaws, the real (patched) {@code EntityRenderer.orientCamera}
 * runs once: the view must start (its near plane, 0.05 ahead of the camera) outside blocks, and along the open
 * tunnel the camera must keep its full distance. Then the game draws back view along the tunnel for a few frames,
 * with the camera 4 blocks back in the chunk before the eyes' chunk: P18 must draw that chunk too (1.12.2 draws
 * only the eyes' chunk there, and the camera looks out through the stone).
 */
@SideOnly(Side.CLIENT)
final class ClientCameraTests {

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final Pattern RENDERED = Pattern.compile("C: (\\d+)/");
    private static Method orientCamera;

    private static BlockPos base;
    private static int stage;
    private static int wait;
    private static String what;
    private static int retries;
    static boolean done;

    private ClientCameraTests() {
    }

    /** Called every client tick in a world until {@link #done}. */
    static void tick(Minecraft mc) throws Exception {
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
                PoseTests.crawlOn = true;
                build(mc, false);
                what = "crawling";
                stage = 1;
                wait = 30;
                break;
            case 1:
            case 3:
                if (!ready(mc) && ++retries < 100) {
                    wait = 5; // the integrated server can take a while to send the tunnel's chunks
                    return;
                }
                retries = 0;
                probe(mc, what);
                look(mc, 1, 0.0F, 0.0F);
                stage++;
                wait = 10;
                break;
            case 2:
                drawn(mc, what);
                build(mc, true);
                what = "swimming";
                stage = 3;
                wait = 30;
                break;
            case 4:
                drawn(mc, what);
                look(mc, 0, 0.0F, 0.0F);
                PoseTests.rulesOn = false;
                PoseTests.crawlOn = false;
                done = true;
                break;
            default:
                break;
        }
    }

    /** A 1x1 tunnel along z, 21 long, at y 200; the player lies in it facing +z, half a block past a chunk border. */
    private static void build(Minecraft mc, boolean water) {
        mc.getIntegratedServer().addScheduledTask(() -> {
            World world = mc.getIntegratedServer().getWorld(0);
            if (base == null) {
                BlockPos spawn = world.getSpawnPoint();
                base = new BlockPos(spawn.getX(), 200, spawn.getZ() >> 4 << 4);
            }
            for (BlockPos p : BlockPos.getAllInBox(base.add(-2, -2, -12), base.add(2, 2, 12))) {
                world.setBlockState(p, Blocks.STONE.getDefaultState(), 2);
            }
            IBlockState fill = water ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
            for (BlockPos p : BlockPos.getAllInBox(base.add(0, 0, -10), base.add(0, 0, 10))) {
                world.setBlockState(p, fill, 2);
            }
            EntityPlayerMP player = mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
            player.capabilities.isFlying = false;
            player.sendPlayerAbilities();
            player.connection.setPlayerLocation(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0.0F, 0.0F);
        });
    }

    /** @return true once the client has the tunnel and has put the player in the low pose in it */
    private static boolean ready(Minecraft mc) {
        return base != null && mc.world.getBlockState(base.up()).getBlock() == Blocks.STONE
                && mc.player.getDistanceSq(base.getX() + 0.5, base.getY(), base.getZ() + 0.5) < 0.01
                && Poses.get(mc.player) == Pose.SWIMMING && Math.abs(mc.player.getEyeHeight() - 0.4F) < 1.0E-4F;
    }

    private static void look(Minecraft mc, int view, float yaw, float pitch) {
        mc.gameSettings.thirdPersonView = view;
        mc.player.rotationYaw = mc.player.prevRotationYaw = yaw;
        mc.player.rotationPitch = mc.player.prevRotationPitch = pitch;
    }

    private static void probe(Minecraft mc, String what) throws Exception {
        check("client: camera: " + what + ": player in the swimming pose with eyes at 0.4",
                Poses.get(mc.player) == Pose.SWIMMING && Math.abs(mc.player.getEyeHeight() - 0.4F) < 1.0E-4F,
                Poses.get(mc.player) + " eyes " + mc.player.getEyeHeight() + " at " + mc.player.getPositionVector());
        if (orientCamera == null) {
            orientCamera = ReflectionHelper.findMethod(EntityRenderer.class, "orientCamera", "func_78467_g", float.class);
        }
        Vec3d eyes = mc.player.getPositionVector().add(0.0, 0.4, 0.0);
        int bad = 0;
        int short_ = 0;
        StringBuilder log = new StringBuilder();
        for (int view = 1; view <= 2; view++) {
            for (float yaw : new float[] {0.0F, 45.0F, 90.0F, 180.0F}) {
                for (float pitch : new float[] {-80.0F, -45.0F, -20.0F, 0.0F, 20.0F, 45.0F, 80.0F}) {
                    look(mc, view, yaw, pitch);
                    Vec3d[] cam = camera(mc);
                    boolean inside = inside(mc, cam[0].add(cam[1].scale(0.05)));
                    double dist = cam[0].distanceTo(eyes);
                    log.append(String.format("%n  view %d yaw %3.0f pitch %3.0f -> camera %.3f %.3f %.3f dist %.3f%s",
                            view, yaw, pitch, cam[0].x, cam[0].y, cam[0].z, dist, inside ? " VIEW STARTS INSIDE A BLOCK" : ""));
                    if (inside) {
                        bad++;
                    }
                    if ((yaw == 0.0F || yaw == 180.0F) && pitch == 0.0F && dist < 3.99) {
                        short_++;
                    }
                }
            }
        }
        BackportCore.LOGGER.info("CAMERA PROBE {} (player {}):{}", what, mc.player.getPositionVector(), log);
        check("client: camera: " + what + ": third person's view starts outside blocks", bad == 0, bad + " views inside");
        check("client: camera: " + what + ": along the open tunnel the camera keeps its distance", short_ == 0, short_ + " views short");
    }

    /** After a few frames of back view along the tunnel: the camera's chunk is drawn, not only the eyes'. */
    private static void drawn(Minecraft mc, String what) {
        int camChunk = MathHelper.floor(mc.player.posZ - 4.0) >> 4;
        check("client: camera: " + what + ": the camera sits in the chunk before the eyes' chunk",
                camChunk != MathHelper.floor(mc.player.posZ) >> 4, mc.player.getPositionVector());
        String info = mc.renderGlobal.getDebugInfoRenders();
        Matcher m = RENDERED.matcher(info);
        int rendered = m.find() ? Integer.parseInt(m.group(1)) : -1;
        check("client: camera: " + what + ": P18: third person draws the camera's chunk too", rendered >= 2, info);
    }

    private static boolean inside(Minecraft mc, Vec3d point) {
        BlockPos at = new BlockPos(point);
        IBlockState state = mc.world.getBlockState(at);
        AxisAlignedBB box = state.getCollisionBoundingBox(mc.world, at);
        return box != null && box.offset(at).contains(point);
    }

    /** Runs orientCamera once for the player's look; returns the camera and its forward direction, in the world. */
    private static Vec3d[] camera(Minecraft mc) throws Exception {
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.loadIdentity();
            orientCamera.invoke(mc.entityRenderer, 1.0F);
            MATRIX.clear();
            GlStateManager.getFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        } finally {
            GlStateManager.popMatrix();
        }
        float[] m = new float[16];
        MATRIX.get(m);
        // A rigid transform M = [R t]: the camera is where M sends to the origin, -R^T t (relative to the feet),
        // and it looks down eye space's -z, the third row of R negated.
        double cx = -(m[0] * m[12] + m[1] * m[13] + m[2] * m[14]);
        double cy = -(m[4] * m[12] + m[5] * m[13] + m[6] * m[14]);
        double cz = -(m[8] * m[12] + m[9] * m[13] + m[10] * m[14]);
        return new Vec3d[] {new Vec3d(mc.player.posX + cx, mc.player.posY + cy, mc.player.posZ + cz),
                new Vec3d(-m[2], -m[6], -m[10])};
    }
}
