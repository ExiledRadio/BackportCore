package com.exiledradio.backportcore.pose.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.player.EntityPlayer;

/**
 * A backport's own arm or leg pose for players, applied by patch P3 to every biped model drawing a player in
 * a third-person render (the player model and its armor), after vanilla's angles and before the swim stroke,
 * where 1.14's {@code BipedModel} sets its arm poses. Register with {@link PoseClient#addModelHook}.
 */
public interface PlayerModelHook {

    void apply(ModelBiped model, EntityPlayer player);
}
