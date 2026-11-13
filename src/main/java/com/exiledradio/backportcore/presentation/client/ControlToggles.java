package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.CycleOption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 1.21.9's Attack/Destroy and Use Item/Place Block set to Toggle ({@link Presentation#TOGGLE_ATTACK},
 * {@link Presentation#TOGGLE_USE}; {@code ToggleKeyMapping}): one press holds the key down until the next press. While
 * held, the game reads the key as down every tick, as if it were held. Opening a screen lets it go, as vanilla's
 * toggle does when the game loses focus.
 */
@SideOnly(Side.CLIENT)
public final class ControlToggles {

    private boolean attack;
    private boolean use;

    /** For the client self-test. */
    public static volatile int toggles;

    @SubscribeEvent
    public void mouse(InputEvent.MouseInputEvent event) {
        if (org.lwjgl.input.Mouse.getEventButtonState()) {
            press(org.lwjgl.input.Mouse.getEventButton() - 100);
        }
    }

    @SubscribeEvent
    public void key(InputEvent.KeyInputEvent event) {
        if (org.lwjgl.input.Keyboard.getEventKeyState() && !org.lwjgl.input.Keyboard.isRepeatEvent()) {
            press(org.lwjgl.input.Keyboard.getEventKey());
        }
    }

    /** A key or button went down (mouse buttons as 1.12.2's key codes, -100 + button); the press itself is left to vanilla. */
    private void press(int code) {
        Minecraft mc = Minecraft.getMinecraft();
        if (on(Presentation.TOGGLE_ATTACK) && mc.gameSettings.keyBindAttack.getKeyCode() == code) {
            this.attack = !this.attack;
            toggles++;
        }
        if (on(Presentation.TOGGLE_USE) && mc.gameSettings.keyBindUseItem.getKeyCode() == code) {
            this.use = !this.use;
            toggles++;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null) {
            this.attack = false;
            this.use = false;
            return;
        }
        hold(mc.gameSettings.keyBindAttack, on(Presentation.TOGGLE_ATTACK) && this.attack);
        hold(mc.gameSettings.keyBindUseItem, on(Presentation.TOGGLE_USE) && this.use);
    }

    private static void hold(KeyBinding key, boolean held) {
        if (held) {
            KeyBinding.setKeyBindState(key.getKeyCode(), true);
        }
    }

    private static boolean on(CycleOption option) {
        return option.isShown() && "toggle".equals(option.get());
    }
}
