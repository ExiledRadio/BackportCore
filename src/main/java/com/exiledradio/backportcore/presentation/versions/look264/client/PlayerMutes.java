package com.exiledradio.backportcore.presentation.versions.look264.client;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 26.4's mute (Player Options): chat messages from a muted player are hidden on this client. 1.12.2's chat comes as
 * text; a player's message is {@code chat.type.text} (or {@code chat.type.emote}) with the sender's name first, which is
 * what is matched. Kept for the session, as vanilla keeps its own list per session.
 */
public final class PlayerMutes {

    private static final Set<UUID> MUTED = ConcurrentHashMap.newKeySet();
    private static final Set<String> NAMES = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

    public static boolean muted(UUID id) {
        return MUTED.contains(id);
    }

    public static synchronized void set(UUID id, String name, boolean muted) {
        if (muted) {
            MUTED.add(id);
            NAMES.add(name);
        } else {
            MUTED.remove(id);
            NAMES.remove(name);
        }
    }

    static synchronized boolean mutedName(String name) {
        return NAMES.contains(name);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void chat(ClientChatReceivedEvent event) {
        ITextComponent m = event.getMessage();
        if (m instanceof TextComponentTranslation) {
            TextComponentTranslation t = (TextComponentTranslation) m;
            String key = t.getKey();
            if (("chat.type.text".equals(key) || "chat.type.emote".equals(key)) && t.getFormatArgs().length > 0) {
                Object sender = t.getFormatArgs()[0];
                String name = sender instanceof ITextComponent ? ((ITextComponent) sender).getUnformattedText() : String.valueOf(sender);
                if (mutedName(name.trim().toLowerCase(Locale.ROOT)) || mutedName(name.trim())) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public void left(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        synchronized (PlayerMutes.class) {
            MUTED.clear();
            NAMES.clear();
        }
    }
}
