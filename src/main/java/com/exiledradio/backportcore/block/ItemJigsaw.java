package com.exiledradio.backportcore.block;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Today's {@code GameMasterBlockItem} for the jigsaw block: only a player who may use operator blocks (creative mode,
 * permission level 2, {@code canUseCommandBlock}) places it. Epic rarity, as 26.3's item.
 *
 * @since 1.4.5
 */
public class ItemJigsaw extends ItemBlock {

    public ItemJigsaw(Block block) {
        super(block);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX,
                                      float hitY, float hitZ) {
        if (player != null && !player.canUseCommandBlock()) {
            return EnumActionResult.FAIL;
        }
        return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }
}
