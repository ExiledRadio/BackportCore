package com.exiledradio.backportcore.command.swing.client;

import com.exiledradio.backportcore.command.swing.SwingType;
import com.exiledradio.backportcore.command.swing.Swings;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * {@code /swing} on the client: 26.3's client {@code SwingState} for a swing the server sent, drawn with 1.12.2's arm
 * swing. 1.12.2's swing lasts a fixed {@code getArmSwingAnimationEnd} (6 ticks) and counts in whole ticks; here, after
 * each client tick, the entity's {@code swingProgress} is set to the swing's own ticks over its own duration (the
 * previous value kept for the frame in between, as 1.12.2's {@code getSwingProgress} reads them), and its tick counter
 * held below the end so 1.12.2's own update never ends it early. {@code none} holds the arm still; {@code stab} is
 * drawn as {@code whack} (1.12.2's arm has no stab pose).
 */
public final class ClientSwings {

    private static final Map<EntityLivingBase, Swing> ACTIVE = new WeakHashMap<EntityLivingBase, Swing>();

    private ClientSwings() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ClientSwings());
        Swings.client = (entityId, hand, type, duration) -> Minecraft.getMinecraft().addScheduledTask(() -> start(entityId, hand, type, duration));
    }

    static final class Swing {
        final EnumHand hand;
        final SwingType type;
        final int duration;
        int ticks;
        float progress;

        Swing(EnumHand hand, SwingType type, int duration) {
            this.hand = hand;
            this.type = type;
            this.duration = Math.max(1, duration);
        }
    }

    static void start(int entityId, EnumHand hand, SwingType type, int duration) {
        World world = Minecraft.getMinecraft().world;
        Entity entity = world == null ? null : world.getEntityByID(entityId);
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        Swing swing = new Swing(hand, type, duration);
        ACTIVE.put(living, swing);
        living.swingingHand = hand;
        living.isSwingInProgress = type != SwingType.NONE;
        living.swingProgressInt = -1;
        living.prevSwingProgress = 0.0F;
        living.swingProgress = 0.0F;
    }

    /** For the self-test: the swing's state on the entity, or null. {ticks, duration} */
    public static int[] state(EntityLivingBase entity) {
        Swing swing = ACTIVE.get(entity);
        return swing == null ? null : new int[]{swing.ticks, swing.duration};
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        if (mc.world == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isGamePaused()) {
            return;
        }
        for (Iterator<Map.Entry<EntityLivingBase, Swing>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<EntityLivingBase, Swing> e = it.next();
            EntityLivingBase entity = e.getKey();
            Swing swing = e.getValue();
            if (entity.isDead || entity.world != mc.world) {
                it.remove();
                continue;
            }
            swing.ticks++;
            entity.prevSwingProgress = swing.progress;
            if (swing.ticks >= swing.duration) {
                entity.isSwingInProgress = false;
                entity.swingProgressInt = 0;
                entity.swingProgress = 0.0F;
                if (swing.type == SwingType.NONE) {
                    entity.prevSwingProgress = 0.0F;
                }
                it.remove();
                continue;
            }
            entity.swingingHand = swing.hand;
            entity.swingProgressInt = -1;
            if (swing.type == SwingType.NONE) {
                entity.isSwingInProgress = false;
                swing.progress = 0.0F;
            } else {
                entity.isSwingInProgress = true;
                swing.progress = (float) swing.ticks / (float) swing.duration;
            }
            entity.swingProgress = swing.progress;
        }
    }
}
