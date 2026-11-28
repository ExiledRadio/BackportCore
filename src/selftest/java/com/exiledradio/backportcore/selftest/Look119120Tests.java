package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.DebugKeys;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.LoadingStatus;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.Splashes;
import com.exiledradio.backportcore.presentation.VersionedDefaults;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.backportcore.presentation.versions.Look119;
import com.exiledradio.backportcore.presentation.versions.Look120;
import com.exiledradio.backportcore.presentation.versions.LookHooks;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.4.7's 1.19 and 1.20 pieces (moved out of Wild Update and Trails &amp; Tales): what Core registers on its own
 * contributions, the files they name, the options, the hooks of P73 to P76 at vanilla's values (nothing is installed on
 * Core's own server) and the rules the client code uses. The patches themselves are in {@code PatchTests}.
 */
final class Look119120Tests {

    private Look119120Tests() {
    }

    static void schedule() {
        then(1, "look 1.19/1.20: Core's contributions and their files", world -> {
            for (String v : new String[] {"1.19", "1.19.1", "1.19.3", "1.19.4", "1.20", "1.20.3", "1.20.5"}) {
                Contribution c = Presentation.coreContribution(v);
                check("look: Core has its own " + v, c != null && c.isCore() && !c.owners().contains("wildupdatebackport")
                        && !c.owners().contains("trailsandtalesbackport"), c);
            }
            check("look: Core's pieces do not declare 1.19 or 1.20 installed", Presentation.contribution("1.19") == null
                    && Presentation.contribution("1.20") == null, Presentation.installedNewest());
            Object[][] assets = {
                    {"1.19", "textures/gui/title/minecraft.png", "256x256"},
                    {"1.19", "textures/gui/title/mojangstudios.png", null},
                    {"1.19", "textures/gui/widgets.png", "256x256"},
                    {"1.19", "textures/gui/checkbox.png", "64x64"},
                    {"1.19.3", "textures/gui/container/creative_inventory/tabs.png", "256x256"},
                    {"1.20", "textures/gui/title/minecraft.png", "1024x256"},
                    {"1.20", "textures/gui/title/minecraft.png.mcmeta", null},
                    {"1.20", "textures/gui/title/minceraft.png", "1024x256"},
                    {"1.20", "textures/gui/title/minceraft.png.mcmeta", null},
                    {"1.20", "textures/gui/title/edition.png", "512x64"},
                    {"1.20", "textures/gui/title/edition.png.mcmeta", null},
                    {"1.20.5", "textures/gui/menu_background.png", "16x16"},
            };
            for (Object[] a : assets) {
                ResourceLocation src = Presentation.coreContribution((String) a[0]).replacedAssets().get(a[1]);
                byte[] bytes = src == null ? null : bytes("/assets/" + src.getNamespace() + "/" + src.getPath());
                String size = bytes == null ? null : pngSize(bytes);
                check("look: " + a[0] + " replaces " + a[1] + " with a file in Core's jar", bytes != null && bytes.length > 0
                        && (a[2] == null || a[2].equals(size)), src + " " + size);
            }
            for (String p : new String[] {"look/1.19/textures/gui/accessibility.png", "look/1.20/icons/icon_16x16.png",
                    "look/1.20/icons/icon_32x32.png", "look/1.20.5/textures/environment/end_sky.png",
                    "look/1.20.5/textures/entity/end_portal.png", "shaders/post/menu_blur.json"}) {
                check("look: Core's jar has " + p, bytes("/assets/backportcore/" + p) != null, p);
            }
        });
        then(1, "look 1.19/1.20: splashes, F3 + F, defaults", world -> {
            check("look: 1.19 adds 7 splashes", lines("look/1.19/texts/splashes_added.txt").size() == 7, "");
            check("look: 1.19 removes 1", lines("look/1.19/texts/splashes_removed.txt").size() == 1, "");
            check("look: 1.19.1 adds 1", lines("look/1.19.1/texts/splashes_added.txt").size() == 1, "");
            check("look: 1.19.3 adds 6", lines("look/1.19.3/texts/splashes_added.txt").size() == 6, "");
            check("look: 1.20 adds 8", lines("look/1.20/texts/splashes_added.txt").size() == 8, "");
            check("look: 1.20 removes 2", lines("look/1.20/texts/splashes_removed.txt").size() == 2, "");
            List<Contribution> all = cores("1.19", "1.19.1", "1.19.3", "1.19.4", "1.20", "1.20.5");
            List<String> base = new ArrayList<String>(Arrays.asList("Привет Россия!", "Hello!"));
            List<String> pool = Splashes.pool(base, all, f -> lines(f.getPath()));
            check("look: the pool loses 1.12.2's \"Привет Россия!\" and gains 1.19's frogs", !pool.contains("Привет Россия!")
                    && pool.contains("Who let the frogs out?") && pool.contains("Hello!"), pool.size());
            Map<Integer, DebugKeys.Key> keys = DebugKeys.map(cores("1.19"));
            check("look: F3 + F is gone from 1.19", !keys.containsKey(33) && keys.containsKey(DebugKeys.KEY_Q), keys.keySet());
            boolean autoJump = false;
            for (VersionedDefaults.Entry e : VersionedDefaults.pending(cores("1.19.4"))) {
                autoJump |= "autoJump".equals(e.key) && "false".equals(e.value) && e.version.equals(GameVersion.parse("1.19.4"));
            }
            check("look: 1.19.4's Auto-Jump OFF is Core's", autoJump, "");
        });
        then(1, "look 1.19/1.20: options", world -> {
            Object[][] want = {
                    {Look119.DAMAGE_TILT, "wildupdatebackport:damage_tilt_strength", "1.19.4", "options.damageTiltStrength", 1.0, Look119.DARKNESS_ID},
                    {Look119.GLINT_SPEED, "wildupdatebackport:glint_speed", "1.19.4", "options.glintSpeed", 0.5, Look119.DAMAGE_TILT.id()},
                    {Look119.GLINT_STRENGTH, "wildupdatebackport:glint_strength", "1.19.4", "options.glintStrength", 0.75, Look119.GLINT_SPEED.id()},
                    {Look120.MENU_BLUR, "trailsandtalesbackport:menu_background_blurriness", "1.20.5",
                            "options.accessibility.menu_background_blurriness", 0.5, "AUTO_JUMP"},
            };
            for (Object[] w : want) {
                SliderOption o = (SliderOption) w[0];
                PresentationOption registered = Presentation.option((String) w[1]);
                check("look: " + w[1] + " is registered, owned by Core", registered == o && o.isCoreOwned(), registered);
                check("look: " + w[1] + " is on Accessibility Settings and Video Settings' alsoOn as before", o.screen() == OptionScreen.ACCESSIBILITY, o.screen());
                check("look: " + w[1] + "'s since", GameVersion.parse((String) w[2]).equals(o.since()), o.since());
                check("look: " + w[1] + "'s label and tooltip", w[3].equals(o.labelKey()) && (w[3] + ".tooltip").equals(o.tooltipKey()), o.labelKey());
                check("look: " + w[1] + " reads OFF at zero", "options.off".equals(o.zeroKey()), o.zeroKey());
                check("look: " + w[1] + "'s default", ((Double) w[4]) == o.defaultValue(), o.defaultValue());
                check("look: " + w[1] + " follows " + w[5], w[5].equals(o.after()), o.after());
                check("look: " + w[1] + " does not show with nothing installed", !o.isShown(), "");
            }
        });
        then(1, "look 1.19/1.20: hooks and rules", world -> {
            check("look: P73 keeps vanilla's tilt while Damage Tilt does not show", LookHooks.damageTilt(14.0F) == 14.0F, LookHooks.damageTilt(14.0F));
            check("look: P74 keeps vanilla's clock and color", LookHooks.glintTime(10000L) == 10000L && LookHooks.glintColor(-8372020) == -8372020, "");
            check("look: P75 keeps vanilla's clock and color", LookHooks.glintTicks(3.0F) == 3.0F && LookHooks.glintComponent(0.38F) == 0.38F, "");
            ItemStack chest = new ItemStack(Blocks.CHEST);
            NBTTagCompound display = new NBTTagCompound();
            NBTTagList lore = new NBTTagList();
            lore.appendTag(new NBTTagString("(+NBT)"));
            display.setTag("Lore", lore);
            chest.setTagInfo("display", display);
            LookHooks.pickedBlock(chest);
            check("look: P76 keeps \"(+NBT)\" before 1.20.5", chest.getTagCompound().getCompoundTag("display").hasKey("Lore"), chest.getTagCompound());

            check("look: Ctrl + scroll, Auto down: 4", Look120.nextGuiScale(0, -1, 4) == 4, "");
            check("look: Ctrl + scroll, Auto up: stays Auto", Look120.nextGuiScale(0, 1, 4) == 0, "");
            check("look: Ctrl + scroll, 2 up: 3", Look120.nextGuiScale(2, 1, 4) == 3, "");
            check("look: Ctrl + scroll, 4 up: stays 4", Look120.nextGuiScale(4, 1, 4) == 4, "");
            check("look: Ctrl + scroll, 1 down: stays 1", Look120.nextGuiScale(1, -1, 4) == 1, "");
            check("look: 1.20's grid: carvers blue", Look120.gridColor(LoadingStatus.LIQUID_CARVERS) == 0x303572, "");
            check("look: 1.20's grid: light pale yellow", Look120.gridColor(LoadingStatus.LIGHT) == 0xFFE0A0, "");
            check("look: 1.20's grid: untracked black", Look120.gridColor(null) == 0x000000, "");
            check("look: to the Nether: nether portal", Look120.dimensionReason(false, 0, -1) == Look120.Reason.NETHER_PORTAL, "");
            check("look: from the End: end portal", Look120.dimensionReason(false, 1, 0) == Look120.Reason.END_PORTAL, "");
            check("look: a respawn: the menu background", Look120.dimensionReason(true, -1, 0) == Look120.Reason.OTHER, "");
            check("look: another dimension: the menu background", Look120.dimensionReason(false, 0, 7) == Look120.Reason.OTHER, "");
        });
        then(1, "look 1.19/1.20: lang and vanilla text", world -> {
            String[] keys = {"options.damageTiltStrength", "options.damageTiltStrength.tooltip", "options.glintSpeed", "options.glintSpeed.tooltip",
                    "options.glintStrength", "options.glintStrength.tooltip", "options.accessibility.menu_background_blurriness",
                    "options.accessibility.menu_background_blurriness.tooltip", "debug.profiler_chart.help", "debug.fps_charts.help"};
            String[] codes = {"de_de", "en_us", "es_ar", "es_es", "es_mx", "es_uy", "es_ve", "fr_fr", "it_it", "ja_jp", "ko_kr", "nl_nl",
                    "pl_pl", "pt_br", "ru_ru", "tr_tr", "uk_ua", "zh_cn", "zh_tw"};
            Map<String, String> en = lang("lang/en_us.lang");
            check("look: en_us Damage Tilt", "Damage Tilt".equals(en.get("options.damageTiltStrength")), en.get("options.damageTiltStrength"));
            check("look: en_us Menu Background Blur", "Menu Background Blur".equals(en.get("options.accessibility.menu_background_blurriness")), "");
            for (String code : codes) {
                Map<String, String> l = lang("lang/" + code + ".lang");
                List<String> missing = new ArrayList<String>();
                for (String k : keys) {
                    if (l.get(k) == null || l.get(k).isEmpty()) {
                        missing.add(k);
                    }
                }
                check("look: " + code + " has the moved keys", missing.isEmpty(), missing);
                for (String v : new String[] {"1.19", "1.20"}) {
                    int n = lang("look/" + v + "/vanilla_text/" + code + ".lang").size();
                    int base = lang("look/" + v + "/vanilla_text/en_us.lang").size();
                    check("look: " + v + "'s vanilla text in " + code + " has every key", n == base && n > 0, n + "/" + base);
                }
            }
            check("look: 1.19's vanilla text has the multiplayer warning", "Caution: Third-Party Online Play".equals(
                    lang("look/1.19/vanilla_text/en_us.lang").get("multiplayerWarning.header")), "");
            check("look: 1.20's vanilla text says Game Over!", "Game Over!".equals(lang("look/1.20/vanilla_text/en_us.lang").get("deathScreen.title.hardcore")), "");
        });
        then(1, "look: the spawner's names (1.13, 1.19.3) and inner faces (1.20.3)", world -> {
            String[] codes = {"de_de", "en_us", "es_ar", "es_es", "es_mx", "es_uy", "es_ve", "fr_fr", "it_it", "ja_jp", "ko_kr", "nl_nl",
                    "pl_pl", "pt_br", "ru_ru", "tr_tr", "uk_ua", "zh_cn", "zh_tw"};
            String key = "tile.mobSpawner.name";
            check("look: 1.13 calls it Spawner (17w47a)", "Spawner".equals(lang("look/1.13/vanilla_text/en_us.lang").get(key)),
                    lang("look/1.13/vanilla_text/en_us.lang").get(key));
            check("look: 1.19.3 calls it Monster Spawner again (22w44a)",
                    "Monster Spawner".equals(lang("look/1.19.3/vanilla_text/en_us.lang").get(key)), "");
            List<String> missing = new ArrayList<String>();
            for (String code : codes) {
                for (String v : new String[] {"1.13", "1.19.3"}) {
                    String name = lang("look/" + v + "/vanilla_text/" + code + ".lang").get(key);
                    if (name == null || name.isEmpty()) {
                        missing.add(v + " " + code);
                    }
                }
            }
            check("look: both names in all 19 languages", missing.isEmpty(), missing);
            check("creative: 1.12.2's mob_spawner is today's minecraft:spawner (1.19.3's Spawn Eggs lists it)", "minecraft:spawner".equals(
                    com.exiledradio.backportcore.creative.VanillaOrder.modernId(new ItemStack(Blocks.MOB_SPAWNER))),
                    com.exiledradio.backportcore.creative.VanillaOrder.modernId(new ItemStack(Blocks.MOB_SPAWNER)));
            check("look: the 1.19.3 layer is only the name", lang("look/1.19.3/vanilla_text/en_us.lang").size() == 1, "");
            try {
                java.lang.reflect.Field texts = Contribution.class.getDeclaredField("texts");
                texts.setAccessible(true);
                check("look: Core's 1.19.3 composes its vanilla text",
                        ((List<?>) texts.get(Presentation.coreContribution("1.19.3"))).contains(new ResourceLocation("backportcore",
                                "look/1.19.3/vanilla_text")), texts.get(Presentation.coreContribution("1.19.3")));
            } catch (ReflectiveOperationException e) {
                check("look: Core's 1.19.3 composes its vanilla text", false, e);
            }
            Contribution v1203 = Presentation.coreContribution("1.20.3");
            ResourceLocation model = v1203 == null ? null : v1203.replacedAssets().get(Look120.SPAWNER_MODEL);
            check("look: Core's 1.20.3 replaces the spawner's model", model != null, v1203);
            byte[] json = model == null ? null : bytes("/assets/" + model.getNamespace() + "/" + model.getPath());
            com.google.gson.JsonObject o = null;
            try {
                o = json == null ? null : new com.google.gson.JsonParser().parse(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException e) {
                o = null;
            }
            com.google.gson.JsonArray elements = o == null || !o.has("elements") ? null : o.getAsJsonArray("elements");
            check("look: 1.20.3's model has the outer and the inverted inner cube", elements != null && elements.size() == 2
                    && elements.get(1).getAsJsonObject().getAsJsonArray("from").get(0).getAsDouble()
                    > elements.get(1).getAsJsonObject().getAsJsonArray("to").get(0).getAsDouble(), o);
            check("look: on 1.12.2's spawner texture", o != null && "blocks/mob_spawner".equals(
                    o.getAsJsonObject("textures").get("all").getAsString()), o);
        });
    }

    private static List<Contribution> cores(String... versions) {
        List<Contribution> out = new ArrayList<Contribution>();
        for (String v : versions) {
            out.add(Presentation.coreContribution(v));
        }
        return out;
    }

    private static Map<String, String> lang(String path) {
        Map<String, String> out = new HashMap<String, String>();
        for (String line : lines(path)) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.startsWith("#")) {
                out.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return out;
    }

    /** @return the non-empty lines of a file under {@code assets/backportcore/} */
    private static List<String> lines(String path) {
        List<String> out = new ArrayList<String>();
        InputStream in = Look119120Tests.class.getResourceAsStream("/assets/backportcore/" + path);
        if (in == null) {
            return out;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    out.add(line);
                }
            }
            r.close();
        } catch (IOException e) {
            out.clear();
        }
        return out;
    }

    private static byte[] bytes(String path) {
        InputStream in = Look119120Tests.class.getResourceAsStream(path);
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static String pngSize(byte[] b) {
        if (b.length < 24 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') {
            return null;
        }
        return readInt(b, 16) + "x" + readInt(b, 20);
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }
}
