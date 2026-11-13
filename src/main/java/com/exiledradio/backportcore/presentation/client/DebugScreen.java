package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.DebugKeys;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.21.9's F3 screen on 1.12.2's ({@link DebugEntries}): the text shows only the parts that are on, the Always parts
 * show with F3 hidden too, the 3D crosshair and the renderers follow their entries, F3 + F6 opens Debug Options, F3 +
 * B and F3 + G switch their entries, and F3's combinations work with a menu open (not while typing).
 */
@SideOnly(Side.CLIENT)
public final class DebugScreen {

    private static DebugEntries.Lines lines;
    private static Field actionKeyF3;
    private static final Map<Class<?>, List<Field>> TEXT_FIELDS = new HashMap<Class<?>, List<Field>>();

    /** For the client self-test: F3 combinations handled with a menu open. */
    public static volatile int menuCombos;

    private DebugScreen() {
    }

    /** From Core's client init: Core's own combinations of 1.21.9. */
    static void init() {
        DebugKeys.coreKey("1.21.9", new DebugKeys.Key(DebugBindings.OPTIONS, "debug.options.help", DebugScreen::toggleOptions));
        DebugKeys.coreKey("1.21.9", new DebugKeys.Key(Keyboard.KEY_B, "debug.show_hitboxes.help",
                () -> toggle(DebugEntries.ENTITY_HITBOXES, "debug.show_hitboxes.on", "debug.show_hitboxes.off")));
        DebugKeys.coreKey("1.21.9", new DebugKeys.Key(Keyboard.KEY_G, "debug.chunk_boundaries.help",
                () -> toggle(DebugEntries.CHUNK_BORDERS, "debug.chunk_boundaries.on", "debug.chunk_boundaries.off")));
    }

    /** F3 + F6: Debug Options, or closes it when it is open. */
    static boolean toggleOptions() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof DebugOptionsScreen) {
            mc.displayGuiScreen(null);
            return true;
        }
        if (mc.currentScreen instanceof GuiContainer && mc.player != null) {
            // Vanilla closes the container first (onClose), so the server knows.
            mc.player.closeScreen();
        }
        mc.displayGuiScreen(new DebugOptionsScreen());
        return true;
    }

    private static boolean toggle(String id, String on, String off) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.isReducedDebug()) {
            return true;
        }
        boolean now = DebugEntries.toggle(id, mc.gameSettings.showDebugInfo);
        PresentationHooks.debugFeedback(now ? on : off);
        DebugEntries.syncRenderers(mc);
        return true;
    }

    /** From {@link DebugText.Events}, after the versions' steps: the parts that are on. */
    static void text(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) {
            DebugEntries.apply(event.getLeft(), event.getRight(), true);
            return;
        }
        if (mc.world == null || mc.getRenderViewEntity() == null || !anyAlwaysText(mc.isReducedDebug())) {
            return;
        }
        if (lines == null) {
            lines = new DebugEntries.Lines(mc);
        }
        List<String> left = lines.left();
        List<String> right = lines.right();
        DebugText.apply(new DebugText.Lines(left, right, mc, event.getPartialTicks()));
        DebugEntries.apply(left, right, false);
        event.getLeft().addAll(0, left);
        event.getRight().addAll(0, right);
    }

    private static boolean anyAlwaysText(boolean reduced) {
        for (DebugEntries.Entry e : DebugEntries.ENTRIES) {
            if (!e.renderer && e.allowed(reduced) && DebugEntries.status(e.id) == DebugEntries.Status.ALWAYS_ON) {
                return true;
            }
        }
        return false;
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {
        private boolean savedShow;
        private boolean swapped;

        /** The 3D crosshair (1.12.2 draws it while F3 shows) follows its entry. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onCrosshairPre(RenderGameOverlayEvent.Pre event) {
            if (event.getType() != RenderGameOverlayEvent.ElementType.CROSSHAIRS || !DebugEntries.active()) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            this.savedShow = mc.gameSettings.showDebugInfo;
            this.swapped = true;
            mc.gameSettings.showDebugInfo = DebugEntries.enabled(DebugEntries.THREE_DIMENSIONAL_CROSSHAIR, this.savedShow, mc.isReducedDebug());
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onCrosshairPost(RenderGameOverlayEvent.Post event) {
            if (event.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS && this.swapped) {
                Minecraft.getMinecraft().gameSettings.showDebugInfo = this.savedShow;
                this.swapped = false;
            }
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && DebugEntries.active()) {
                Minecraft mc = Minecraft.getMinecraft();
                if (this.swapped) {
                    // A crosshair pass that never finished (another mod cancelled it after Core): put F3 back.
                    mc.gameSettings.showDebugInfo = this.savedShow;
                    this.swapped = false;
                }
                DebugEntries.syncRenderers(mc);
            }
        }

        /** 1.21.9: F3's combinations with a menu open, unless the menu is being typed in. */
        @SubscribeEvent
        public void onKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
            if (!DebugEntries.active() || !Keyboard.getEventKeyState() || Keyboard.isRepeatEvent()) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            int key = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
            int modifier = DebugBindings.modifierKey(Keyboard.KEY_F3);
            if (key == modifier || modifier <= 0 || modifier >= 256 || !Keyboard.isKeyDown(modifier)) {
                return;
            }
            GuiScreen screen = event.getGui();
            int known = DebugBindings.translate(key);
            boolean options = known == DebugBindings.OPTIONS;
            if (!options && (typing(screen) || mc.world == null && known != Keyboard.KEY_T || mc.player == null && known != Keyboard.KEY_T)) {
                return;
            }
            if (PresentationHooks.processKeyF3(mc, key)) {
                menuCombos++;
                markF3Used(mc);
                event.setCanceled(true);
            }
        }
    }

    /** Tells 1.12.2 an F3 combination ran, so letting go of F3 does not also toggle the debug screen. */
    private static void markF3Used(Minecraft mc) {
        try {
            if (actionKeyF3 == null) {
                actionKeyF3 = ReflectionHelper.findField(Minecraft.class, "actionKeyF3", "field_184129_aV");
            }
            actionKeyF3.setBoolean(mc, true);
        } catch (Exception e) {
            BackportCore.LOGGER.debug("F3 keys: actionKeyF3 unreadable", e);
        }
    }

    /** @return true when the screen is chat or has a text field with the focus (a combination's key is then typed) */
    static boolean typing(GuiScreen screen) {
        if (screen instanceof GuiChat) {
            return true;
        }
        List<Field> fields = TEXT_FIELDS.get(screen.getClass());
        if (fields == null) {
            fields = new ArrayList<Field>();
            for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && GuiTextField.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        fields.add(f);
                    }
                }
            }
            TEXT_FIELDS.put(screen.getClass(), fields);
        }
        for (Field f : fields) {
            try {
                Object v = f.get(screen);
                if (v instanceof GuiTextField && ((GuiTextField) v).isFocused()) {
                    return true;
                }
            } catch (IllegalAccessException e) {
                return true;
            }
        }
        return screen instanceof net.minecraft.client.gui.inventory.GuiEditSign || screen instanceof net.minecraft.client.gui.GuiScreenBook;
    }
}
