package com.exiledradio.backportcore.presentation;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.common.util.EnumHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * 1.15's {@code copy_to_clipboard} click action, added to 1.12.2's {@link ClickEvent.Action} so text components can
 * carry it: {@code /tellraw @a {"text":"x","clickEvent":{"action":"copy_to_clipboard","value":"..."}}} is read, kept,
 * sent and shown like any other click. Clicking it in chat copies the value ({@code client.CopyClick}). Both sides add
 * it at pre-init, before any component is read.
 */
public final class CopyToClipboard {

    /** The added action, or null if 1.12.2's enum could not take it (then the JSON is dropped as before). */
    @Nullable
    public static ClickEvent.Action ACTION;

    private CopyToClipboard() {
    }

    /** From Core's pre-init, both sides. */
    @SuppressWarnings("unchecked")
    public static void init() {
        if (ACTION != null) {
            return;
        }
        try {
            ClickEvent.Action action = ClickEvent.Action.getValueByCanonicalName("copy_to_clipboard");
            if (action == null) {
                action = EnumHelper.addEnum(ClickEvent.Action.class, "COPY_TO_CLIPBOARD", new Class<?>[] {String.class, boolean.class},
                        "copy_to_clipboard", true);
            }
            if (action == null) {
                return;
            }
            // getValueByCanonicalName reads a map filled once by the enum's static block: the new name goes in too.
            for (Field f : ClickEvent.Action.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    ((Map<String, ClickEvent.Action>) f.get(null)).put("copy_to_clipboard", action);
                }
            }
            ACTION = ClickEvent.Action.getValueByCanonicalName("copy_to_clipboard") == action ? action : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            BackportCore.LOGGER.warn("Could not add the copy_to_clipboard click action; such clicks are dropped as in 1.12.2.", e);
        }
    }
}
