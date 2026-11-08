package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VersionedDefaults;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies the installed versions' changed defaults ({@link VersionedDefaults}) once, when the title screen
 * first shows: by then loading has finished, and 1.12.2 saves options.txt again (it refuses to while the game
 * loads). Vanilla settings are written into options.txt by their own keys and read back with vanilla's own
 * {@code GameSettings.loadOptions}, so every setting 1.12.2 knows can be defaulted without a table here.
 */
@SideOnly(Side.CLIENT)
public final class ClientDefaults {

    private static boolean done;
    /** For the client self-test: how many entries were applied this launch. */
    public static int applied = -1;

    private ClientDefaults() {
    }

    static void applyOnce(Minecraft mc) {
        if (done) {
            return;
        }
        done = true;
        applied = VersionedDefaults.apply(Presentation.installed(), values -> write(mc, values));
    }

    static boolean write(Minecraft mc, Map<String, String> values) {
        GameSettings settings = mc.gameSettings;
        File file = new File(mc.gameDir, "options.txt");
        try {
            if (!file.isFile()) {
                settings.saveOptions();
            }
            List<String> lines = file.isFile() ? Files.readAllLines(file.toPath(), StandardCharsets.UTF_8) : new ArrayList<String>();
            Set<String> written = new HashSet<String>();
            List<String> out = new ArrayList<String>();
            for (String line : lines) {
                int colon = line.indexOf(':');
                String key = colon < 0 ? line : line.substring(0, colon);
                if (values.containsKey(key)) {
                    out.add(key + ":" + values.get(key));
                    written.add(key);
                } else {
                    out.add(line);
                }
            }
            for (Map.Entry<String, String> e : values.entrySet()) {
                if (!written.contains(e.getKey())) {
                    out.add(e.getKey() + ":" + e.getValue());
                }
            }
            Files.write(file.toPath(), out, StandardCharsets.UTF_8);
            settings.loadOptions();
            settings.saveOptions();
            return true;
        } catch (IOException | RuntimeException e) {
            BackportCore.LOGGER.warn("Presentation: could not write versioned defaults into options.txt: {}", e.toString());
            return false;
        }
    }
}
