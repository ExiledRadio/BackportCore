package com.exiledradio.backportcore.boat;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 1.19's inventory key on a chest boat ({@code ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY} with a vehicle that
 * {@code HasCustomInventoryScreen}): the client, riding one, asks the server on Core's channel {@code backportcore_boat}
 * to open the boat's chest instead of its own inventory ({@code client.ChestBoatKey}). 1.12.2's own packet opens only a
 * horse's inventory.
 */
public final class BoatNetwork {

    public static final String CHANNEL = "backportcore_boat";

    private static SimpleNetworkWrapper channel;

    private BoatNetwork() {
    }

    /** From Core's pre-init, both sides. */
    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(OpenHandler.class, OpenVehicleInventory.class, 0, Side.SERVER);
    }

    /** Client: asks the server to open the inventory of the vehicle the player rides. */
    public static void openVehicleInventory() {
        if (channel != null) {
            channel.sendToServer(new OpenVehicleInventory());
        }
    }

    /**
     * Server: opens the chest of the chest boat the player rides, as 1.19's {@code ChestBoat.openCustomInventoryScreen}.
     *
     * @return true if the player rides a chest boat
     */
    public static boolean open(EntityPlayerMP player) {
        Entity vehicle = player.getRidingEntity();
        if (!(vehicle instanceof EntityWoodChestBoat) || vehicle.isDead) {
            return false;
        }
        player.displayGUIChest((EntityWoodChestBoat) vehicle);
        return true;
    }

    /** No data: the vehicle is the one the player rides on the server. */
    public static final class OpenVehicleInventory implements IMessage {
        public OpenVehicleInventory() {
        }

        @Override
        public void fromBytes(ByteBuf buf) {
        }

        @Override
        public void toBytes(ByteBuf buf) {
        }
    }

    public static final class OpenHandler implements IMessageHandler<OpenVehicleInventory, IMessage> {
        @Override
        public IMessage onMessage(OpenVehicleInventory message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> open(player));
            return null;
        }
    }
}
