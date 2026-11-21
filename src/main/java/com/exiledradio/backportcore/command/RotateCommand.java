package com.exiledradio.backportcore.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * 1.21.2's {@code /rotate}, as 26.3 has it (permission level 2): {@code /rotate <target> <yaw> <pitch>} ({@code ~} is
 * relative to the command's own rotation, as a rotation argument is), {@code /rotate <target> facing <x> <y> <z>} and
 * {@code /rotate <target> facing entity <entity> [eyes|feet]}. The target turns from its feet (a command source's
 * default anchor); a player's client is told, as 26.3 sends its rotation packet.
 */
public final class RotateCommand extends CommandBase {

    @Override
    public String getName() {
        return "rotate";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.backportcore.rotate.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3) {
            throw new WrongUsageException(getUsage(sender));
        }
        Entity target = getEntity(server, sender, args[0]);
        if ("facing".equals(args[1])) {
            Vec3d at;
            if ("entity".equals(args[2]) && (args.length == 4 || args.length == 5)) {
                Entity facing = getEntity(server, sender, args[3]);
                String anchor = args.length == 5 ? args[4] : "feet";
                if (!"feet".equals(anchor) && !"eyes".equals(anchor)) {
                    throw new CommandException("argument.anchor.invalid", anchor);
                }
                at = new Vec3d(facing.posX, facing.posY + ("eyes".equals(anchor) ? facing.getEyeHeight() : 0.0), facing.posZ);
            } else if (args.length == 5) {
                Vec3d origin = sender.getPositionVector();
                at = new Vec3d(parseDouble(origin.x, args[2], true), parseDouble(origin.y, args[3], false), parseDouble(origin.z, args[4], true));
            } else {
                throw new WrongUsageException(getUsage(sender));
            }
            lookAt(target, at);
        } else if (args.length == 3) {
            Entity self = sender.getCommandSenderEntity();
            float sourceYaw = self != null ? self.rotationYaw : 0.0F;
            float sourcePitch = self != null ? self.rotationPitch : 0.0F;
            setRotation(target, (float) parseDouble(sourceYaw, args[1], false), (float) parseDouble(sourcePitch, args[2], false));
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
        notifyCommandListener(sender, this, "commands.rotate.success", target.getDisplayName());
    }

    /** 26.3's {@code Entity.lookAt(FEET, position)}. */
    public static void lookAt(Entity entity, Vec3d at) {
        double dx = at.x - entity.posX;
        double dy = at.y - entity.posY;
        double dz = at.z - entity.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float pitch = MathHelper.wrapDegrees((float) (-(MathHelper.atan2(dy, horizontal) * (180.0 / Math.PI))));
        float yaw = MathHelper.wrapDegrees((float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
        setRotation(entity, yaw, pitch);
    }

    /** 26.3's {@code forceSetRotation}: the body and head turn, and a player's client turns too. */
    public static void setRotation(Entity entity, float yaw, float pitch) {
        pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            // Position relative by zero, rotation absolute.
            player.connection.setPlayerLocation(0.0, 0.0, 0.0, yaw, pitch,
                    EnumSet.of(SPacketPlayerPosLook.EnumFlags.X, SPacketPlayerPosLook.EnumFlags.Y, SPacketPlayerPosLook.EnumFlags.Z));
            player.setRotationYawHead(yaw);
            return;
        }
        entity.rotationYaw = yaw;
        entity.rotationPitch = pitch;
        entity.prevRotationYaw = yaw;
        entity.prevRotationPitch = pitch;
        entity.setRotationYawHead(yaw);
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            living.renderYawOffset = yaw;
            living.prevRenderYawOffset = yaw;
            living.prevRotationYawHead = yaw;
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, "~", "facing");
        }
        if ("facing".equals(args[1])) {
            if (args.length == 3) {
                return getListOfStringsMatchingLastWord(args, "entity", "~");
            }
            if ("entity".equals(args[2])) {
                return args.length == 4 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames())
                        : args.length == 5 ? getListOfStringsMatchingLastWord(args, "eyes", "feet") : Collections.<String>emptyList();
            }
            return args.length <= 5 ? getListOfStringsMatchingLastWord(args, "~") : Collections.<String>emptyList();
        }
        return args.length == 3 ? getListOfStringsMatchingLastWord(args, "~") : Collections.<String>emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 0;
    }
}
