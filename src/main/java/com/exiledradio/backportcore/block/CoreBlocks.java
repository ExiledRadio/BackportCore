package com.exiledradio.backportcore.block;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.structure.ModernPalette;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Core's own blocks: today's jigsaw block ({@code backportcore:jigsaw}, with its tile entity and item). Since 1.4.5.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class CoreBlocks {

    public static final ResourceLocation JIGSAW_ID = new ResourceLocation(BackportCore.MOD_ID, "jigsaw");

    public static BlockJigsaw jigsaw;
    public static ItemJigsaw jigsawItem;

    private CoreBlocks() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        jigsaw = new BlockJigsaw();
        jigsaw.setRegistryName(JIGSAW_ID);
        jigsaw.setTranslationKey(BackportCore.MOD_ID + ".jigsaw");
        event.getRegistry().register(jigsaw);
        GameRegistry.registerTileEntity(TileEntityJigsaw.class, JIGSAW_ID);
        // A structure file's minecraft:jigsaw is this block wherever a file's block is wanted as it stands (the
        // jigsaws a template places stay their final state unless kept: ModernTemplate).
        ModernPalette.map("minecraft:jigsaw", () -> jigsaw);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        jigsawItem = new ItemJigsaw(jigsaw);
        jigsawItem.setRegistryName(JIGSAW_ID);
        jigsawItem.setTranslationKey(BackportCore.MOD_ID + ".jigsaw");
        event.getRegistry().register(jigsawItem);
    }

    /** Today's game-master rule: only a player who may use operator blocks breaks one, even in creative mode. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof BlockJigsaw && !event.getPlayer().canUseCommandBlock()) {
            event.setCanceled(true);
        }
    }
}
