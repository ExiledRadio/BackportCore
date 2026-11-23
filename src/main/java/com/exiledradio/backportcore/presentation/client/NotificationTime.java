package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 1.19.4's Notification Time ({@link Presentation#NOTIFICATION_TIME}): what patch P50 calls. 1.19.4 multiplies how long
 * each notification stays: a toast's display time ({@code 5000.0 * getNotificationDisplayTimeMultiplier()}), a
 * subtitle's ({@code 3000 * notificationDisplayTime}) and the held item's name ({@code 40 * notificationDisplayTime}
 * ticks). With the option hidden (older look, or the options screens' part of the look off) every hook gives back
 * vanilla 1.12.2's value.
 */
@SideOnly(Side.CLIENT)
public final class NotificationTime {

    private NotificationTime() {
    }

    /** @return the multiplier: 1 while the option does not show */
    public static double multiplier() {
        if (!Presentation.NOTIFICATION_TIME.isShown()) {
            return 1.0;
        }
        double m = Presentation.NOTIFICATION_TIME.get();
        return m > 0.0 ? m : 1.0;
    }

    /**
     * P50: the time {@code GuiToast.ToastInstance.render} hands the toast ({@code IToast.draw(gui, delta)}). 1.12.2's
     * toasts each decide when to hide from that time (5000 ms for advancements and recipes), so the time runs slower by
     * the multiplier: a toast stays that much longer, whichever mod made it.
     */
    public static long toastTime(long delta) {
        double m = multiplier();
        return m == 1.0 ? delta : (long) (delta / m);
    }

    /** P50: {@code GuiSubtitleOverlay.renderSubtitles}' 3000 ms (when a subtitle goes), as a long. */
    public static long subtitleTime(long vanilla) {
        double m = multiplier();
        return m == 1.0 ? vanilla : (long) (vanilla * m);
    }

    /** P50: {@code GuiSubtitleOverlay.renderSubtitles}' 3000 ms (how a subtitle fades), as a float. */
    public static float subtitleFade(float vanilla) {
        return (float) (vanilla * multiplier());
    }

    /** P50: {@code GuiIngame.updateTick}'s 40 ticks for the held item's name (1.19.4: {@code (int) (40.0 * m)}). */
    public static int highlightTicks(int vanilla) {
        double m = multiplier();
        return m == 1.0 ? vanilla : (int) (vanilla * m);
    }
}
