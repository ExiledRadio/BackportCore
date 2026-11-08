package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.Splashes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The title screen's splash: picked from the installed versions' pool ({@link Splashes}) when the screen is
 * made, as vanilla picks from {@code splashes.txt} in the same place, so 1.12.2's date splashes (Christmas Eve,
 * New Year's Day, Halloween), which the screen sets afterwards, still win. 1.20.3's Hide Splash Texts empties
 * it once a 1.20.3 or later backport shows that option.
 */
@SideOnly(Side.CLIENT)
public final class TitleSplashes {

    private static final ResourceLocation SPLASHES = new ResourceLocation("texts/splashes.txt");
    /** The line vanilla never shows ("This message will never appear on the splash screen, isn't that weird?"). */
    private static final int NEVER_SHOWN = 125780783;
    private static final Random RANDOM = new Random();

    private static Field splashText;

    /** For the client self-test: the pool used last. */
    public static volatile List<String> lastPool;

    private TitleSplashes() {
    }

    /** The title screen was just made: pick its splash from the pool. */
    static void pick(GuiMainMenu menu) {
        List<Contribution> installed = Presentation.installed();
        if (!Splashes.changed(installed)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        List<String> pool = new ArrayList<String>(Splashes.pool(read(SPLASHES), installed, TitleSplashes::read));
        pool.removeIf(s -> s.hashCode() == NEVER_SHOWN);
        lastPool = pool;
        if (pool.isEmpty()) {
            return;
        }
        set(menu, Splashes.personalize(pool.get(RANDOM.nextInt(pool.size())), mc.getSession().getUsername()));
    }

    /** The title screen laid itself out (after its date splashes): 1.20.3's Hide Splash Texts. */
    static void afterInit(GuiMainMenu menu) {
        if (Presentation.HIDE_SPLASH_TEXTS.isShown() && Presentation.HIDE_SPLASH_TEXTS.get()) {
            set(menu, "");
        }
    }

    private static void set(GuiMainMenu menu, String text) {
        try {
            if (splashText == null) {
                splashText = ReflectionHelper.findField(GuiMainMenu.class, "splashText", "field_73975_c");
            }
            splashText.set(menu, text);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Presentation: could not set the splash", e);
        }
    }

    /** @return the non-empty lines of a text resource, or none if it is missing */
    static List<String> read(ResourceLocation file) {
        List<String> out = new ArrayList<String>();
        try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            BackportCore.LOGGER.warn("Presentation: splash file {} could not be read: {}", file, e.toString());
        }
        return out;
    }
}
