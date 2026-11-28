package com.exiledradio.backportcore.creative.client;

import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 1.19.3's pick block on a spawner (22w44a: "Pick block once again works on monster spawners"). 1.12.2's
 * {@code BlockMobSpawner.getItem} gives an empty stack (12w21a took pick block away), so the middle click does nothing.
 *
 * <p>No patch: the pick block key's press is seen in Forge's input events, which fire after the key's state is set and
 * before {@code processKeyBinds} acts on it. With a spawner targeted, the press is taken here (vanilla's own pick would
 * do nothing on it) and the spawner picked the way Forge's {@code ForgeHooks.onPickBlock} picks a block: Ctrl in
 * creative keeps the tile entity's data ({@code storeTEInStack}, which P76 then gives 1.20.5's look), creative puts the
 * stack in hand, survival selects a spawner already in the inventory. Every other block is left to vanilla.
 *
 * <p>From 1.19.3, with the inventory's part of the look on ({@link Presentation#isAtLeast(LookArea, String)}), as the
 * spawner in 1.19.3's Spawn Eggs tab ({@link ModernTabs}).
 *
 * @since 1.5.0
 */
@SideOnly(Side.CLIENT)
public final class SpawnerPick {

    public static final String VERSION = "1.19.3";

    /** For the self-tests: spawners picked this game. */
    public static volatile int picked;

    private SpawnerPick() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new SpawnerPick());
    }

    /** @return true when picking a spawner gives one: from 1.19.3 */
    public static boolean active() {
        return Presentation.isAtLeast(LookArea.INVENTORY, VERSION);
    }

    /** @return the stack 1.19.3 picks from a spawner (an empty one without Ctrl, as 1.19.3's {@code getCloneItemStack}) */
    public static ItemStack stack() {
        Item item = Item.getItemFromBlock(Blocks.MOB_SPAWNER);
        return new ItemStack(item);
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        check();
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        check();
    }

    private static void check() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.currentScreen != null || !active()) {
            return;
        }
        RayTraceResult target = mc.objectMouseOver;
        if (target == null || target.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = target.getBlockPos();
        IBlockState state = mc.world.getBlockState(pos);
        if (state.getBlock() != Blocks.MOB_SPAWNER) {
            return;
        }
        boolean pressed = false;
        while (mc.gameSettings.keyBindPickBlock.isPressed()) {
            pressed = true;
        }
        if (pressed) {
            pick(mc, mc.player, pos);
        }
    }

    /** Forge's {@code ForgeHooks.onPickBlock} with the spawner's stack in place of 1.12.2's empty one. */
    static boolean pick(Minecraft mc, EntityPlayerSP player, BlockPos pos) {
        ItemStack result = stack();
        if (result.isEmpty()) {
            return false;
        }
        TileEntity te = mc.world.getTileEntity(pos);
        if (player.capabilities.isCreativeMode && GuiScreen.isCtrlKeyDown() && te != null) {
            result = mc.storeTEInStack(result, te);
        }
        picked++;
        if (player.capabilities.isCreativeMode) {
            player.inventory.setPickedItemStack(result);
            mc.playerController.sendSlotPacket(player.getHeldItem(EnumHand.MAIN_HAND), 36 + player.inventory.currentItem);
            return true;
        }
        int slot = player.inventory.getSlotFor(result);
        if (slot != -1) {
            if (net.minecraft.entity.player.InventoryPlayer.isHotbar(slot)) {
                player.inventory.currentItem = slot;
            } else {
                mc.playerController.pickItem(slot);
            }
            return true;
        }
        return false;
    }
}
