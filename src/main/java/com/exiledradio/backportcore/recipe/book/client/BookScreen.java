package com.exiledradio.backportcore.recipe.book.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** A container screen that draws a smelting book (1.13's {@code IRecipeShownListener}): updates reach it here. */
@SideOnly(Side.CLIENT)
public interface BookScreen {

    SmeltingBookGui smeltingBook();
}
