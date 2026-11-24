package com.exiledradio.backportcore.presentation.versions;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.backportcore.presentation.option.ToggleOption;
import com.exiledradio.backportcore.recipe.book.SmeltingBook;
import com.exiledradio.backportcore.recipe.book.SmeltingBooks;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 1.13's look and feel that Backport Core owns since 1.4.7 (moved out of Update Aquatic Backport): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.13 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look113Client}.
 *
 * <ul>
 *   <li>Video Settings' Biome Blend ({@link #BIOME_BLEND}, after Entity Shadows): OFF, 3x3 to 15x15, 5x5 by default,
 *       applied by patch P68 ({@link BiomeBlend});</li>
 *   <li>17w47a: command suggestions in the chat box, with Chat Settings' "Command Suggestions" ({@link #AUTO_SUGGEST});</li>
 *   <li>the three splashes 1.13.2's {@code splashes.txt} adds (none removed); 18w21a's Allow Snooper off by default;</li>
 *   <li>the furnace's recipe book ({@code SmeltingBook.furnace()}), with 1.13.2's {@code recipe_book.png} and
 *       {@code recipe_button.png};</li>
 *   <li>vanilla's own text as 1.13.2 words it ({@code look/1.13/vanilla_text});</li>
 *   <li>client side: F3 + C, F3 + I, 1.13's F3 text and its packet counter.</li>
 * </ul>
 * The ids stay Update Aquatic's ({@code updateaquaticbackport:biome_blend_radius}), so saved values carry over. The
 * ocean panorama, the underwater fog and the underwater music stay in Update Aquatic.
 */
public final class Look113 {

    /** The ids' namespace: the backport the options came from, so options.txt keeps the player's values. */
    private static final String UA = "updateaquaticbackport";

    /**
     * 1.13's Biome Blend (Video Settings): the radius of the square the biome colors are averaged over, 0 to 7,
     * shown as OFF or "5x5" (1.13.2's {@code biomeBlendRadius}, default 2). Its id's path is the name Core's graphics
     * presets set.
     */
    public static final SliderOption BIOME_BLEND = new SliderOption(UA + ":biome_blend_radius", "options.biomeBlendRadius",
            OptionScreen.VIDEO, 0.0, 7.0, 1.0, 2.0);

    /** 1.13's "Command Suggestions" (Chat Settings; 1.13.2's {@code autoSuggestions}, on). */
    public static final ToggleOption AUTO_SUGGEST = new ToggleOption(UA + ":auto_suggestions", "options.autoSuggestCommands",
            OptionScreen.CHAT, true);

    /** For the self-tests: whether the furnace's recipe book was registered (an installed version is 1.13 or later). */
    public static volatile boolean furnaceBook;

    private Look113() {
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
        Contribution v113 = Presentation.core("1.13");
        v113.addSplashes("Thank you for the fish!", "All rumors are true!", "Truly gone fishing!")
                .gameOptionDefault("snooperEnabled", "false")
                .vanillaText(new ResourceLocation("backportcore", "look/1.13/vanilla_text"));
        BIOME_BLEND.zeroKey("options.off").valueText(v -> {
            int side = 2 * (int) Math.round(v) + 1;
            return side + "x" + side;
        });
        v113.option(BIOME_BLEND.after("ENTITY_SHADOWS"));
        v113.option(AUTO_SUGGEST.after("REDUCED_DEBUG_INFO")).commandSuggestions(AUTO_SUGGEST);
        v113.replaceAsset("textures/gui/recipe_book.png", new ResourceLocation("backportcore", "look/1.13/textures/gui/recipe_book.png"))
                .replaceAsset("textures/gui/recipe_button.png", new ResourceLocation("backportcore", "look/1.13/textures/gui/recipe_button.png"));
        // The furnace book is registered once the backports have declared their versions (pre-init); the registry
        // events come after every mod's pre-init.
        MinecraftForge.EVENT_BUS.register(new Look113());
    }

    /** @return Biome Blend's radius, 0 to 7 */
    public static int biomeBlendRadius() {
        return (int) Math.round(BIOME_BLEND.get());
    }

    /** After pre-init, both sides: 1.13's furnace book, when an installed backport is 1.13 or later. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!furnaceBook && Presentation.installedNewest().isAtLeast("1.13")) {
            furnaceBook = true;
            // An older Update Aquatic jar registers the same book; the same id replaces it, so there is one.
            SmeltingBooks.register(SmeltingBook.furnace());
        }
    }
}
