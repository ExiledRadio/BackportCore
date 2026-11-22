package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 26.3's Right Click Emulation ({@link Presentation#CTRL_CLICK_RIGHT_CLICK}, macOS only; SDL's
 * {@code SDL_MAC_CTRL_CLICK_EMULATE_RIGHT_CLICK}): a left click with Control held is a right click, and its release
 * the right button's, wherever Control is by then.
 *
 * <p>1.12.2 reads each mouse event in {@code Minecraft.runTickMouse}, which first posts Forge's cancellable
 * {@link MouseEvent}. Such a left click is cancelled there and played again as the right button: in a world the
 * key bindings of button 1 ({@code KeyBinding.setKeyBindState} and {@code onTick}, as vanilla's loop does); on a screen
 * {@code mouseClicked} and {@code mouseReleased} with button 1, its {@code eventButton} set as
 * {@code GuiScreen.handleMouseInput} sets it, so dragging is a right drag.
 */
@SideOnly(Side.CLIENT)
public final class RightClickEmulation {

    private static final int LEFT = 0;
    private static final int RIGHT = 1;

    private boolean held;
    private static Field eventButton;
    private static Field lastMouseEvent;
    private static Method mouseClicked;
    private static Method mouseReleased;
    private static boolean failed;

    /** For the client self-test: clicks turned into right clicks. */
    public static volatile int emulated;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouse(MouseEvent event) {
        if (Mouse.getEventButton() != LEFT || failed) {
            return;
        }
        boolean down = Mouse.getEventButtonState();
        if (down) {
            if (!on() || !(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
                return;
            }
            this.held = true;
            emulated++;
        } else if (!this.held) {
            return;
        } else {
            this.held = false;
        }
        event.setCanceled(true);
        Minecraft mc = Minecraft.getMinecraft();
        KeyBinding.setKeyBindState(RIGHT - 100, down);
        if (mc.currentScreen == null) {
            if (down) {
                KeyBinding.onTick(RIGHT - 100);
                if (!mc.inGameHasFocus) {
                    mc.setIngameFocus();
                }
            }
            return;
        }
        screen(mc.currentScreen, down);
    }

    private static boolean on() {
        return Presentation.CTRL_CLICK_RIGHT_CLICK.isShown() && Presentation.CTRL_CLICK_RIGHT_CLICK.get();
    }

    /** {@code GuiScreen.handleMouseInput} for button 1. */
    private static void screen(GuiScreen gui, boolean down) {
        try {
            if (mouseClicked == null) {
                eventButton = ReflectionHelper.findField(GuiScreen.class, "eventButton", "field_146287_f");
                lastMouseEvent = ReflectionHelper.findField(GuiScreen.class, "lastMouseEvent", "field_146288_g");
                mouseClicked = ReflectionHelper.findMethod(GuiScreen.class, "mouseClicked", "func_73864_a", int.class, int.class, int.class);
                mouseReleased = ReflectionHelper.findMethod(GuiScreen.class, "mouseReleased", "func_146286_b", int.class, int.class, int.class);
            }
            Minecraft mc = Minecraft.getMinecraft();
            int x = Mouse.getEventX() * gui.width / mc.displayWidth;
            int y = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
            if (down) {
                eventButton.setInt(gui, RIGHT);
                lastMouseEvent.setLong(gui, Minecraft.getSystemTime());
                mouseClicked.invoke(gui, x, y, RIGHT);
            } else {
                eventButton.setInt(gui, -1);
                mouseReleased.invoke(gui, x, y, RIGHT);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            BackportCore.LOGGER.error("Right Click Emulation: could not pass the click to {}; left clicks stay left clicks", gui.getClass().getName(), e);
        }
    }
}
