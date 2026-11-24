package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.Splashes;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.versions.Look121;
import com.exiledradio.backportcore.presentation.versions.look121.Rarities;
import com.exiledradio.backportcore.presentation.versions.look121.Rules;

import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.4.7's 1.21.x pieces moved from Tricky Trials Backport ({@code versions.Look121}), as far as a server sees
 * them: the core contributions, the options (Tricky Trials' ids), the bubble pop sound, the assets and splash files,
 * the rules the client handlers follow and the item rarities.
 */
final class Look121Tests {

    private Look121Tests() {
    }

    static void schedule() {
        then(1, "1.21 core pieces", world -> {
            Contribution c2 = Presentation.coreContribution("1.21.2");
            Contribution c6 = Presentation.coreContribution("1.21.6");
            Contribution c9 = Presentation.coreContribution("1.21.9");
            Contribution c11 = Presentation.coreContribution("1.21.11");
            check("1.21: core contributions of 1.21.2, 1.21.6, 1.21.9 and 1.21.11", c2 != null && c6 != null && c9 != null && c11 != null);
            if (c2 == null || c6 == null || c9 == null || c11 == null) {
                return;
            }
            check("1.21.2: slot highlight sprites", c2.replacedAssets().containsKey("textures/gui/sprites/container/slot_highlight_back.png")
                    && c2.replacedAssets().containsKey("textures/gui/sprites/container/slot_highlight_front.png"), c2.replacedAssets().keySet());
            check("1.21.6: ghast and music toast sprites", c6.replacedAssets().keySet().containsAll(Arrays.asList(
                    "textures/entity/ghast/ghast.png", "textures/entity/ghast/ghast_shooting.png",
                    "textures/gui/sprites/toast/now_playing.png", "textures/gui/sprites/icon/music_notes.png")), c6.replacedAssets().keySet());
            boolean assets = true;
            for (Contribution c : Arrays.asList(c2, c6, c11)) {
                for (ResourceLocation rl : c.replacedAssets().values()) {
                    assets &= resource(rl) != null;
                }
            }
            check("1.21: every replaced asset is in the jar", assets);
            for (String p : new String[]{"look/1.21.2/textures/gui/sprites/hud/air.png", "look/1.21.2/textures/gui/sprites/hud/air_bursting.png",
                    "look/1.21.2/textures/gui/sprites/hud/air_empty.png", "look/1.21.11/textures/gui/title/mojangstudios.png",
                    "look/1.21.11/vanilla_text/en_us.lang", "look/1.21.11/vanilla_text/de_de.lang", "sounds/ui/hud/hud_bubble.ogg", "sounds.json"}) {
                check("1.21: " + p + " in the jar", resource(new ResourceLocation("backportcore", p)) != null);
            }
            List<String> en = read(new ResourceLocation("backportcore", "look/1.21.11/vanilla_text/en_us.lang"));
            check("1.21.11 wording: Hostile Mobs, Closed Captions, Invert Mouse Y, Walk Forward", en.contains("soundCategory.hostile=Hostile Mobs")
                    && en.contains("options.showSubtitles=Closed Captions") && en.contains("options.invertMouse=Invert Mouse Y")
                    && en.contains("key.forward=Walk Forward"));

            List<String> base = Arrays.asList("Minors welcome!", "Pumpa kungen!", "Hello!");
            List<String> pool = Splashes.pool(base, Arrays.asList(c6, c11), Look121Tests::read);
            check("splashes: 1.21.6 removes \"Minors welcome!\" and changes \"Pumpa kungen!\"", !pool.contains("Minors welcome!")
                    && !pool.contains("Pumpa kungen!") && pool.contains("Pumpakungen!") && pool.contains("Hello!"), pool);
            check("splashes: 1.21.6 and 1.21.11's additions", pool.containsAll(Arrays.asList("Chicken Jockey!", "Flint and Steel!",
                    "Music by Hyper Potions!")), pool);
        });

        then(1, "1.21 options", world -> {
            List<PresentationOption> all = Arrays.<PresentationOption>asList(Look121.INACTIVITY_FPS_LIMIT, Look121.HIGH_CONTRAST_OUTLINE,
                    Look121.INVERT_MOUSE_X, Look121.SPRINT_WINDOW, Look121.SAVE_CHAT_DRAFTS);
            for (PresentationOption o : all) {
                check("option " + o.id() + ": registered, Core's, Tricky Trials' id", Presentation.option(o.id()) == o && o.isCoreOwned()
                        && o.id().startsWith("trickytrialsbackport:"));
            }
            check("option versions: 1.21.2, 1.21.2, 1.21.9, 1.21.9, 1.21.9", "1.21.2".equals(String.valueOf(Look121.INACTIVITY_FPS_LIMIT.since()))
                    && "1.21.2".equals(String.valueOf(Look121.HIGH_CONTRAST_OUTLINE.since()))
                    && "1.21.9".equals(String.valueOf(Look121.INVERT_MOUSE_X.since()))
                    && "1.21.9".equals(String.valueOf(Look121.SPRINT_WINDOW.since()))
                    && "1.21.9".equals(String.valueOf(Look121.SAVE_CHAT_DRAFTS.since())));
            check("option screens", Look121.INACTIVITY_FPS_LIMIT.screen() == OptionScreen.VIDEO
                    && "ENABLE_VSYNC".equals(Look121.INACTIVITY_FPS_LIMIT.after())
                    && Look121.HIGH_CONTRAST_OUTLINE.screen() == OptionScreen.ACCESSIBILITY && Look121.INVERT_MOUSE_X.screen() == OptionScreen.MOUSE
                    && Look121.SPRINT_WINDOW.screen() == OptionScreen.CONTROLS && Look121.SAVE_CHAT_DRAFTS.screen() == OptionScreen.CHAT);
            check("option defaults: AFK, off, off, 7, off", "afk".equals(Look121.INACTIVITY_FPS_LIMIT.get()) && !Look121.HIGH_CONTRAST_OUTLINE.get()
                    && !Look121.INVERT_MOUSE_X.get() && Look121.SPRINT_WINDOW.get() == 7.0 && !Look121.SAVE_CHAT_DRAFTS.get(),
                    Look121.INACTIVITY_FPS_LIMIT.get() + " " + Look121.SPRINT_WINDOW.get());
            check("option: AFK's own tooltip, Sprint Window OFF at 0", "options.inactivityFpsLimit.afk.tooltip".equals(Look121.INACTIVITY_FPS_LIMIT.tooltipKey())
                    && "options.off".equals(Look121.SPRINT_WINDOW.zeroKey()));
            check("sound: backportcore:ui.hud.bubble_pop registered", ForgeRegistries.SOUND_EVENTS.getValue(
                    new ResourceLocation("backportcore", "ui.hud.bubble_pop")) == Look121.BUBBLE_POP);
            List<String> lang = read(new ResourceLocation("backportcore", "lang/en_us.lang"));
            boolean keys = true;
            for (String key : new String[]{"options.inactivityFpsLimit", "options.inactivityFpsLimit.afk", "options.inactivityFpsLimit.minimized",
                    "options.accessibility.high_contrast_block_outline", "options.invertMouseX", "options.sprintWindow", "options.chat.drafts",
                    "options.value", "subtitles.ui.hud.bubble_pop"}) {
                boolean found = false;
                for (String l : lang) {
                    found |= l.startsWith(key + "=");
                }
                keys &= found;
            }
            check("lang: the moved option and subtitle keys in en_us", keys);
        });

        then(1, "1.21 rules", world -> {
            check("framerate: minimized 10, a minute idle 30 (or the lower limit), ten minutes 10, Minimized ignores idling",
                    Rules.framerateLimit(false, 0, true, 120) == 10 && Rules.framerateLimit(true, 59999, false, 120) == 0
                            && Rules.framerateLimit(true, 60001, false, 120) == 30 && Rules.framerateLimit(true, 60001, false, 20) == 20
                            && Rules.framerateLimit(true, 600001, false, 120) == 10 && Rules.framerateLimit(false, 600001, false, 120) == 0);
            int full = 0;
            for (int i = 1; i <= 10; i++) {
                full += Rules.bubble(i, 300, 300, true) == Rules.Bubble.FULL ? 1 : 0;
            }
            check("bubbles: ten full, empty at no air, the second bursting at 31, none out of water", full == 10
                    && Rules.bubble(10, 0, 300, true) == Rules.Bubble.EMPTY && Rules.bubble(2, 31, 300, true) == Rules.Bubble.BURSTING
                    && Rules.bubble(2, 31, 300, false) == Rules.Bubble.NONE);
            check("bubbles: pop volume and pitch", Rules.bubblePopVolume(1) == 0.5F && Math.abs(Rules.bubblePopVolume(5) - 0.8F) < 1e-4
                    && Rules.bubblePopPitch(4) == 1.0F && Math.abs(Rules.bubblePopPitch(6) - 1.2F) < 1e-4);
            check("rain fog", Rules.rainFogTarget(1.0F, 15, true) == 1.0F && Rules.rainFogTarget(1.0F, 15, false) == 0.5F
                    && Rules.rainFogTarget(1.0F, 8, true) == 0.0F && Rules.rainFogStart(144.0F, 1.0F) == -16.0F
                    && Rules.rainFogEnd(192.0F, 1.0F) == 96.0F && Rules.rainFogEnd(64.0F, 1.0F) == 64.0F && Rules.rainFogEnd(192.0F, 0.125F) == 160.0F);
            float[] glow = {1.0F, 0.8F, 0.6F, 0.5F};
            Rules.weatherSunrise(glow, 1.0F, 0.0F);
            check("sunrise: rain dims it", Math.abs(glow[0] - 0.5F) < 1e-6 && Math.abs(glow[2] - 0.36F) < 1e-6 && glow[3] == 0.5F, Arrays.toString(glow));
            check("nether fog 10 to 96", Rules.NETHER_FOG_START == 10.0F && Rules.NETHER_FOG_END == 96.0F);
            check("chat drafts", Rules.keepsDraft(Rules.ChatExit.INTERRUPTED, false, "hi") && !Rules.keepsDraft(Rules.ChatExit.INTENTIONAL, false, "hi")
                    && Rules.keepsDraft(Rules.ChatExit.INTENTIONAL, true, "hi") && !Rules.keepsDraft(Rules.ChatExit.DONE, true, "hi")
                    && !Rules.keepsDraft(Rules.ChatExit.INTERRUPTED, true, "  ") && Rules.restoresDraft("", "/gamemode")
                    && Rules.restoresDraft("/", "/gamemode") && !Rules.restoresDraft("/", "hello") && !Rules.restoresDraft("hello", "draft"));
            check("loading screen text", Rules.LOADING_VIEW_RADIUS == 16 && Rules.loadingTextY(240, true) == 240 / 2 - 32 - 27
                    && Rules.loadingTextY(240, false) == 240 / 2 - 50);
        });

        then(1, "1.21 rarities", world -> {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.addEnchantment(Enchantments.SHARPNESS, 1);
            ItemStack elytra = new ItemStack(Items.ELYTRA);
            elytra.addEnchantment(Enchantments.UNBREAKING, 1);
            check("rarities: golden apples, discs, heads", Rarities.of(new ItemStack(Items.GOLDEN_APPLE)) == EnumRarity.COMMON
                    && Rarities.of(new ItemStack(Items.GOLDEN_APPLE, 1, 1)) == EnumRarity.RARE && Rarities.of(new ItemStack(Items.RECORD_13)) == EnumRarity.UNCOMMON
                    && Rarities.of(new ItemStack(Items.SKULL, 1, 5)) == EnumRarity.EPIC && Rarities.of(new ItemStack(Items.SKULL, 1, 1)) == EnumRarity.RARE
                    && Rarities.of(new ItemStack(Items.SKULL, 1, 3)) == EnumRarity.UNCOMMON);
            check("rarities: elytra, nether star, book, chainmail, beacon, command block, stone", Rarities.of(new ItemStack(Items.ELYTRA)) == EnumRarity.EPIC
                    && Rarities.of(new ItemStack(Items.NETHER_STAR)) == EnumRarity.RARE && Rarities.of(new ItemStack(Items.ENCHANTED_BOOK)) == EnumRarity.RARE
                    && Rarities.of(new ItemStack(Items.CHAINMAIL_HELMET)) == EnumRarity.UNCOMMON && Rarities.of(new ItemStack(Blocks.BEACON)) == EnumRarity.RARE
                    && Rarities.of(new ItemStack(Blocks.COMMAND_BLOCK)) == EnumRarity.EPIC && Rarities.of(new ItemStack(Blocks.STONE)) == EnumRarity.COMMON);
            check("rarities: enchanted Common to Rare, Epic stays Epic", Rarities.of(sword) == EnumRarity.RARE && Rarities.of(elytra) == EnumRarity.EPIC);
            check("rarities: another mod's item keeps its own", Rarities.of(new ItemStack(net.minecraft.item.Item.getItemFromBlock(
                    com.exiledradio.backportcore.block.CoreBlocks.jigsaw))) == null);
        });
    }

    private static InputStream resource(ResourceLocation rl) {
        return Look121Tests.class.getResourceAsStream("/assets/" + rl.getNamespace() + "/" + rl.getPath());
    }

    private static List<String> read(ResourceLocation rl) {
        List<String> out = new ArrayList<String>();
        InputStream in = resource(rl);
        if (in == null) {
            return out;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    out.add(line.trim());
                }
            }
        } catch (java.io.IOException e) {
            return new ArrayList<String>();
        }
        return out;
    }
}
