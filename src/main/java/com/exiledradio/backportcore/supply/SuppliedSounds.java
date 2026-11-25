package com.exiledradio.backportcore.supply;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.block.SoundType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Today's sound types 1.12.2 lacks (deepslate, tuff, copper, nether wood and the rest), for the supplied blocks:
 * {@code assets/backportcore/supplied/sounds.txt} gives each type's volume, pitch and five events, written by
 * {@code tools/supplied-blocks/generate.py}; the events are {@code backportcore:block.<name>.<action>}, with today's
 * sound files in {@code sounds/modern/}. Types 1.12.2 has (stone, wood, metal, glass...) stay 1.12.2's.
 *
 * <p>Sound events are registered after blocks, so the types are built from unregistered events first and those
 * events are registered in their own registry event. Since 1.5.0.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class SuppliedSounds {

    private static Map<String, SoundType> types;
    private static final Map<String, SoundEvent> EVENTS = new LinkedHashMap<String, SoundEvent>();

    private SuppliedSounds() {
    }

    /** @return Core's sound type of today's name ({@code DEEPSLATE_BRICKS}), or null for one 1.12.2 has */
    @Nullable
    public static synchronized SoundType type(String modernName) {
        if (types == null) {
            load();
        }
        return types.get(modernName);
    }

    private static void load() {
        types = new HashMap<String, SoundType>();
        try (InputStream in = SuppliedSounds.class.getResourceAsStream("/assets/backportcore/supplied/sounds.txt")) {
            if (in == null) {
                return;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\t");
                if (f.length < 8) {
                    continue;
                }
                types.put(f[0], new SoundType(Float.parseFloat(f[1]), Float.parseFloat(f[2]), event(f[3]), event(f[4]), event(f[5]),
                        event(f[6]), event(f[7])));
            }
        } catch (Exception e) {
            BackportCore.LOGGER.error("Could not read the supplied blocks' sound types.", e);
        }
    }

    private static SoundEvent event(String id) {
        SoundEvent e = EVENTS.get(id);
        if (e == null) {
            ResourceLocation rl = new ResourceLocation(BackportCore.MOD_ID, id);
            e = new SoundEvent(rl).setRegistryName(rl);
            EVENTS.put(id, e);
        }
        return e;
    }

    @SubscribeEvent
    public static synchronized void register(RegistryEvent.Register<SoundEvent> event) {
        if (types == null) {
            load();
        }
        for (SoundEvent e : EVENTS.values()) {
            event.getRegistry().register(e);
        }
    }
}
