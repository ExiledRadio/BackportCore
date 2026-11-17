package com.exiledradio.backportcore.pose.client;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.pose.Pose;
import com.exiledradio.backportcore.pose.PoseAccess;
import com.exiledradio.backportcore.pose.Poses;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The pose engine's client side: poses the server sends for other players, which player a model is being
 * drawn for, and the camera's eased eye height. Loaded only on a client (from Backport Core's construction).
 */
public final class PoseClient {

    private static final List<PlayerModelHook> MODEL_HOOKS = new CopyOnWriteArrayList<PlayerModelHook>();
    /** The players being drawn in third person right now, innermost last (a render can nest, as in an inventory screen). */
    private static final Deque<Frame> FRAMES = new ArrayDeque<Frame>();
    /** Poses for players this client has not been sent yet (packets can arrive just before the player does). */
    private static final Map<Integer, Pose> PENDING = new HashMap<Integer, Pose>();

    private static Boolean modelHooksWork;
    private static Entity cameraEntity;
    private static float cameraHeight;
    private static float prevCameraHeight;

    /** One third-person draw of a player. */
    static final class Frame {
        final EntityPlayer player;
        final Pose pose;
        final float swim;
        float prevHeadX;
        /** P3: the call's limbSwing and headPitch, taken at the method's first line (see BipedAnglesTransformer). */
        float limbSwing;
        float headPitch;
        /** Held crouched with the sneak key up (1.14's short crouch): drawn sneaking for this render only. */
        boolean forcedSneak;
        /** P3: the model whose angles are being set for this player, so the later hooks need no parameters. */
        net.minecraft.client.model.ModelBiped model;

        Frame(EntityPlayer player, Pose pose, float swim) {
            this.player = player;
            this.pose = pose;
            this.swim = swim;
        }
    }

    private PoseClient() {
    }

    public static void init() {
        PoseAccess.setOwnPlayer(p -> p == Minecraft.getMinecraft().player);
        MinecraftForge.EVENT_BUS.register(new PoseClient());
    }

    /**
     * Adds a backport's arm or leg pose for players (see {@link PlayerModelHook}). It runs only when
     * {@link #modelHooksWork()} is true.
     */
    public static void addModelHook(PlayerModelHook hook) {
        MODEL_HOOKS.add(hook);
    }

    /**
     * @return true if player model hooks run: patch P3 is in and no installed mod draws players its own way.
     * Known once the player renderers exist (before init); false until then.
     */
    public static boolean modelHooksWork() {
        if (modelHooksWork == null) {
            String state = Asm.state("P3");
            if (state == null) {
                return false;
            }
            modelHooksWork = "applied".equals(state) && !Loader.isModLoaded("mobends");
        }
        return modelHooksWork;
    }

    static List<PlayerModelHook> modelHooks() {
        return MODEL_HOOKS;
    }

    /** @return the draw going on for this entity, or null outside a third-person player render */
    /** P3's later hooks: this player's frame while {@code model}'s angles are being set, else null. */
    static Frame frameFor(net.minecraft.client.model.ModelBiped model) {
        Frame top = FRAMES.peekLast();
        return top != null && top.model == model ? top : null;
    }

    static Frame frame(Entity entity) {
        Frame top = FRAMES.peekLast();
        return top != null && top.player == entity ? top : null;
    }

    // ------------------------------------------------------------------------------------------------
    // Poses from the server
    // ------------------------------------------------------------------------------------------------

    /** Network thread: the server says a player is in a pose. */
    public static void receive(int entityId, int poseId) {
        final Pose pose = Pose.values()[Math.max(0, Math.min(Pose.values().length - 1, poseId))];
        Minecraft.getMinecraft().addScheduledTask(() -> apply(entityId, pose));
    }

    private static void apply(int entityId, Pose pose) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity entity = mc.world == null ? null : mc.world.getEntityByID(entityId);
        if (entity instanceof EntityPlayer) {
            if (entity != mc.player) {
                PoseAccess.received((EntityPlayer) entity, pose);
            }
        } else if (mc.world != null) {
            PENDING.put(entityId, pose);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void preRenderPlayer(RenderPlayerEvent.Pre event) {
        EntityPlayer player = event.getEntityPlayer();
        boolean posed = Poses.drawn();
        Frame frame = new Frame(player, posed ? Poses.get(player) : Pose.STANDING,
                posed ? Poses.swimAmount(player, event.getPartialRenderTick()) : 0.0F);
        if (posed && frame.pose == Pose.SNEAKING && !player.isSneaking() && Poses.shortCrouch()) {
            // 1.14 draws by pose (isVisuallySneaking): a player held crouched under a low ceiling crouches. 1.12.2's
            // renderer, model and every layer ask isSneaking(), so it says yes for this one render.
            frame.forcedSneak = true;
            setSneak(player, true);
        }
        FRAMES.addLast(frame);
    }

    private static void setSneak(EntityPlayer player, boolean sneak) {
        if (player instanceof EntityPlayerSP) {
            // The client's own player answers isSneaking() from its input.
            MovementInput input = ((EntityPlayerSP) player).movementInput;
            if (input != null) {
                input.sneak = sneak;
            }
        } else {
            player.setSneaking(sneak);
        }
    }

    /**
     * 1.14's {@code ClientPlayerEntity.livingTick}: a player drawn crouching moves at sneaking speed (0.3) even with
     * the sneak key up. Forge posts this right after vanilla's own 0.3 for the key.
     */
    @SubscribeEvent
    public void inputUpdate(InputUpdateEvent event) {
        MovementInput input = event.getMovementInput();
        EntityPlayer player = event.getEntityPlayer();
        if (!input.sneak && Poses.shortCrouch() && Poses.get(player) == Pose.SNEAKING && !player.capabilities.isFlying) {
            input.moveStrafe *= 0.3F;
            input.moveForward *= 0.3F;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void postRenderPlayer(RenderPlayerEvent.Post event) {
        Frame top = FRAMES.peekLast();
        if (top != null && top.player == event.getEntityPlayer()) {
            FRAMES.removeLast();
            if (top.forcedSneak) {
                setSneak(top.player, false);
            }
        }
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            // A render another mod cancelled after we saw its Pre never posts a Post.
            for (Frame frame : FRAMES) {
                if (frame.forcedSneak) {
                    setSneak(frame.player, false);
                }
            }
            FRAMES.clear();
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.START || mc.world == null || mc.isGamePaused()) {
            return;
        }
        // 1.14.4's ActiveRenderInfo.interpolateHeight, ticked before the world as 1.14's GameRenderer.tick is.
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            cameraEntity = null;
        } else if (view != cameraEntity) {
            cameraEntity = view;
            cameraHeight = prevCameraHeight = view.getEyeHeight();
        } else {
            prevCameraHeight = cameraHeight;
            cameraHeight += (view.getEyeHeight() - cameraHeight) * 0.5F;
        }
        if (!PENDING.isEmpty()) {
            for (Iterator<Map.Entry<Integer, Pose>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, Pose> e = it.next();
                Entity entity = mc.world.getEntityByID(e.getKey());
                if (entity instanceof EntityPlayer) {
                    if (entity != mc.player) {
                        PoseAccess.received((EntityPlayer) entity, e.getValue());
                    }
                    it.remove();
                }
            }
            if (PENDING.size() > 256) {
                PENDING.clear();
            }
        }
    }

    @SubscribeEvent
    public void worldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            PENDING.clear();
            cameraEntity = null;
        }
    }

    /** Patch P5: where the camera sits above the entity's feet this frame. */
    static float cameraEyeHeight(Entity entity, float partialTicks) {
        if (entity != cameraEntity || !Poses.active()) {
            return entity.getEyeHeight();
        }
        return prevCameraHeight + (cameraHeight - prevCameraHeight) * partialTicks;
    }

    /**
     * Patch P18: the block 1.14's {@code WorldRenderer.setupTerrain} starts its visibility search from,
     * {@code ActiveRenderInfo.getBlockPos()}: the camera's own block. 1.12.2 starts at the eyes, so in third person
     * the chunk the camera sits in is skipped whenever the eyes' chunk opens only away from the view (a crawl or
     * swim through a tunnel), and the camera looks out through solid blocks. First person keeps vanilla's block.
     *
     * <p>1.14 also redoes the search whenever the camera moves, where 1.12.2 waits for the entity to move or turn, so
     * F5 pressed while lying still kept first person's search; a moved camera marks the terrain dirty here.
     *
     * <p>The camera is where this frame's modelview (read by {@code ActiveRenderInfo.updateRenderInfo} just before
     * {@code setupTerrain}) puts its origin, relative to the view entity's interpolated position.
     */
    static BlockPos terrainStart(BlockPos eyes) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null || !Poses.active()) {
            return eyes;
        }
        Vec3d offset = cameraOffset();
        if (offset == null) {
            return eyes;
        }
        double t = mc.getRenderPartialTicks();
        Vec3d camera = new Vec3d(view.lastTickPosX + (view.posX - view.lastTickPosX) * t + offset.x,
                view.lastTickPosY + (view.posY - view.lastTickPosY) * t + offset.y,
                view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * t + offset.z);
        if (!camera.equals(lastCamera)) {
            lastCamera = camera;
            mc.renderGlobal.setDisplayListEntitiesDirty();
        }
        return mc.gameSettings.thirdPersonView == 0 ? eyes : new BlockPos(camera);
    }

    private static Vec3d lastCamera;

    private static FloatBuffer modelview;
    private static boolean modelviewMissing;

    /** @return the camera relative to the view entity's interpolated position this frame, or null if unknown */
    static Vec3d cameraOffset() {
        if (modelview == null && !modelviewMissing) {
            try {
                modelview = ObfuscationReflectionHelper.getPrivateValue(ActiveRenderInfo.class, null, "field_178812_b");
            } catch (RuntimeException e) {
                modelviewMissing = true;
                BackportCore.LOGGER.warn("Backport Core: no camera matrix; the third-person terrain search starts at the eyes", e);
            }
        }
        if (modelview == null) {
            return null;
        }
        FloatBuffer m = modelview;
        // A rigid transform [R t] sends the camera to the origin: the camera is -R^T t.
        double tx = m.get(12);
        double ty = m.get(13);
        double tz = m.get(14);
        return new Vec3d(-(m.get(0) * tx + m.get(1) * ty + m.get(2) * tz),
                -(m.get(4) * tx + m.get(5) * ty + m.get(6) * tz),
                -(m.get(8) * tx + m.get(9) * ty + m.get(10) * tz));
    }
}
