package com.exiledradio.backportcore.pose.client;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.pose.Pose;
import com.exiledradio.backportcore.pose.PoseAccess;
import com.exiledradio.backportcore.pose.Poses;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

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
        FRAMES.addLast(new Frame(player, posed ? Poses.get(player) : Pose.STANDING,
                posed ? Poses.swimAmount(player, event.getPartialRenderTick()) : 0.0F));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void postRenderPlayer(RenderPlayerEvent.Post event) {
        Frame top = FRAMES.peekLast();
        if (top != null && top.player == event.getEntityPlayer()) {
            FRAMES.removeLast();
        }
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            // A render another mod cancelled after we saw its Pre never posts a Post.
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
}
