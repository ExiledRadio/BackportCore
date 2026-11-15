package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.Sound;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.client.event.sound.SoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import paulscode.sound.SoundSystem;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code attenuation_distance} in {@code sounds.json}, as 1.14 reads it. 1.12.2's {@code Sound} has no such field and
 * every sound fades out over 16 blocks (times its volume above 1), so a ravager's roar (35), a fox's screech (32) or a
 * sculk sensor's clicks (8 to 12) carried as far as anything else. 1.14's {@code SoundEngine.play}: the distance is
 * {@code max(volume, 1) * attenuation_distance}. Read from every resource pack's {@code sounds.json} at each resource
 * load, keyed by sound event and file, and set on the source as paulscode starts it.
 */
@SideOnly(Side.CLIENT)
public final class SoundDistances {

    /** sound event, then sound file, to its attenuation distance (only those that set one other than 16). */
    private static volatile Map<ResourceLocation, Map<ResourceLocation, Integer>> distances = new HashMap<>();

    /** For the client self-test: sources whose distance was set. */
    public static volatile int applied;

    private SoundDistances() {
    }

    /** From Core's init: re-read at every resource load, after the sound handler's own reload. */
    static void register(Minecraft mc) {
        IResourceManager rm = mc.getResourceManager();
        if (rm instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) rm).registerReloadListener(SoundDistances::reload);
        }
    }

    static void reload(IResourceManager rm) {
        Map<ResourceLocation, Map<ResourceLocation, Integer>> map = new HashMap<>();
        for (String domain : rm.getResourceDomains()) {
            List<IResource> files;
            try {
                files = rm.getAllResources(new ResourceLocation(domain, "sounds.json"));
            } catch (Exception e) {
                continue;
            }
            for (IResource file : files) {
                try (Reader r = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
                    read(domain, new JsonParser().parse(r).getAsJsonObject(), map);
                } catch (Exception e) {
                    BackportCore.LOGGER.debug("Sound distances: {}'s sounds.json unreadable ({})", domain, e.toString());
                } finally {
                    try {
                        file.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        distances = map;
        int n = 0;
        for (Map<ResourceLocation, Integer> m : map.values()) {
            n += m.size();
        }
        BackportCore.LOGGER.info("Sound distances: {} sounds of {} events set attenuation_distance", n, map.size());
    }

    private static void read(String domain, JsonObject root, Map<ResourceLocation, Map<ResourceLocation, Integer>> map) {
        for (Map.Entry<String, JsonElement> event : root.entrySet()) {
            if (!event.getValue().isJsonObject() || !event.getValue().getAsJsonObject().has("sounds")) {
                continue;
            }
            ResourceLocation eventId = new ResourceLocation(domain, event.getKey());
            JsonObject def = event.getValue().getAsJsonObject();
            if (def.has("replace") && def.get("replace").getAsBoolean()) {
                map.remove(eventId);
            }
            for (JsonElement s : def.getAsJsonArray("sounds")) {
                if (!s.isJsonObject()) {
                    continue;
                }
                JsonObject o = s.getAsJsonObject();
                if (!o.has("attenuation_distance") || o.has("type") && !"file".equals(o.get("type").getAsString())) {
                    continue;
                }
                int d = o.get("attenuation_distance").getAsInt();
                String name = o.get("name").getAsString();
                ResourceLocation fileId = name.contains(":") ? new ResourceLocation(name) : new ResourceLocation(domain, name);
                map.computeIfAbsent(eventId, k -> new HashMap<>()).put(fileId, d);
            }
        }
    }

    /** @return the attenuation distance {@code sounds.json} gives this event's file, or null for 1.12.2's 16 */
    @Nullable
    public static Integer distance(ResourceLocation event, ResourceLocation file) {
        Map<ResourceLocation, Integer> m = distances.get(event);
        return m == null ? null : m.get(file);
    }

    private static void apply(SoundEvent.SoundSourceEvent event) {
        ISound sound = event.getSound();
        Sound file = sound.getSound();
        if (file == null || sound.getAttenuationType() != ISound.AttenuationType.LINEAR) {
            return;
        }
        Integer d = distance(sound.getSoundLocation(), file.getSoundLocation());
        if (d == null || d == 16) {
            return;
        }
        try {
            SoundSystem system = ReflectionHelper.getPrivateValue(SoundManager.class, event.getManager(), "sndSystem", "field_148620_e");
            system.setDistOrRoll(event.getUuid(), Math.max(sound.getVolume(), 1.0F) * d);
            applied++;
        } catch (Exception e) {
            BackportCore.LOGGER.debug("Sound distances: could not set {}'s distance ({})", sound.getSoundLocation(), e.toString());
        }
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        @SubscribeEvent
        public void onPlay(PlaySoundSourceEvent event) {
            apply(event);
        }

        @SubscribeEvent
        public void onStream(PlayStreamingSourceEvent event) {
            apply(event);
        }
    }
}
