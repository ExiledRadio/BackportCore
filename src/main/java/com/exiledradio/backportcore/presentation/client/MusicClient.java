package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 1.21.6's music changes, once a backport of 1.21.6 or later is installed (their options are Core's:
 * {@link Presentation#MUSIC_FREQUENCY}, {@link Presentation#MUSIC_TOAST}, {@link Presentation#UI_VOLUME}):
 *
 * <ul>
 *   <li>Music Frequency: the wait between tracks ({@link #delay}, used by {@code GameMusic.CoreTicker}).</li>
 *   <li>Music keeps playing while the game is paused: 1.12.2 pauses every sound; the music channels are let go on.</li>
 *   <li>Music Toast: the playing track's name at the top left ({@code NowPlayingToast}), for five seconds when a
 *       track starts and/or the whole time the pause menu is open. Drawn with 1.21.6's sprites when a backport
 *       supplies them ({@code textures/gui/sprites/toast/now_playing.png}, {@code icon/music_notes.png}); names
 *       are vanilla's {@code music.<path>} lang keys, 1.12.2's own tracks by their 1.20.5 names.</li>
 *   <li>UI volume: button clicks play at that volume (1.12.2 plays them on the master channel).</li>
 *   <li>1.21.9: dragging a volume slider outside a world plays a short sound of that kind when let go.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class MusicClient {

    /** 1.12.2's music files by path, to their names in later versions' lang ({@code music.<new path>}). */
    static final Map<String, String> OLD_TRACKS = new HashMap<String, String>();

    static {
        String[][] names = {
                {"game/calm1", "game.minecraft"}, {"game/calm2", "game.clark"}, {"game/calm3", "game.sweden"},
                {"game/hal1", "game.subwoofer_lullaby"}, {"game/hal2", "game.living_mice"}, {"game/hal3", "game.haggstrom"},
                {"game/hal4", "game.danny"}, {"game/nuance1", "game.key"}, {"game/nuance2", "game.oxygene"},
                {"game/piano1", "game.dry_hands"}, {"game/piano2", "game.wet_hands"}, {"game/piano3", "game.mice_on_venus"},
                {"game/creative/creative1", "game.creative.biome_fest"}, {"game/creative/creative2", "game.creative.blind_spots"},
                {"game/creative/creative3", "game.creative.haunt_muskie"}, {"game/creative/creative4", "game.creative.aria_math"},
                {"game/creative/creative5", "game.creative.dreiton"}, {"game/creative/creative6", "game.creative.taswell"},
                {"game/nether/nether1", "game.nether.concrete_halls"}, {"game/nether/nether2", "game.nether.dead_voxel"},
                {"game/nether/nether3", "game.nether.warmth"}, {"game/nether/nether4", "game.nether.ballad_of_the_cats"},
                {"game/end/end", "game.end.the_end"}, {"game/end/boss", "game.end.boss"}, {"game/end/credits", "game.end.alpha"},
                {"menu/menu1", "menu.mutation"}, {"menu/menu2", "menu.moog_city_2"}, {"menu/menu3", "menu.beginning_2"},
                {"menu/menu4", "menu.floating_trees"}};
        for (String[] n : names) {
            OLD_TRACKS.put("music/" + n[0], "music." + n[1]);
        }
    }

    private static final ResourceLocation TOAST = new ResourceLocation("textures/gui/sprites/toast/now_playing.png");
    private static final ResourceLocation NOTES = new ResourceLocation("textures/gui/sprites/icon/music_notes.png");
    /** {@code DyeColor.LIGHT_GRAY.getTextColor()}. */
    private static final int TEXT_COLOR = 0x9D9D97;
    private static final long TOAST_MS = 5000L;
    private static final long SLIDE_MS = 600L;

    /** For the client self-test. */
    public static volatile int toastFrames;
    public static volatile int resumedChannels;
    public static volatile int previews;

    @Nullable
    private ISound lastTrack;
    private long toastStart = -1L;
    private boolean draggingVolume;

    // ---- Music Frequency ----

    /** @return true once a version with 1.21.6's music options is installed */
    public static boolean active() {
        return Presentation.MUSIC_FREQUENCY.isShown();
    }

    /**
     * 1.21.6's {@code MusicFrequency.getNextSongDelay}: Default waits at most 20 minutes, Frequent 10, Constant 5
     * seconds, within the track's own wait.
     */
    public static int delay(int minDelay, int maxDelay, Random random) {
        return com.exiledradio.backportcore.presentation.MenuMusic.frequencyDelay(Presentation.MUSIC_FREQUENCY.get(), minDelay, maxDelay, random);
    }

    // ---- music while paused ----

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isGamePaused()) {
            resumeMusic(mc.getSoundHandler());
        }
        if (this.draggingVolume && !Mouse.isButtonDown(0)) {
            this.draggingVolume = false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void resumeMusic(SoundHandler handler) {
        try {
            SoundManager manager = ObfuscationReflectionHelper.getPrivateValue(SoundHandler.class, handler, "field_147694_f");
            Set<String> paused = ObfuscationReflectionHelper.getPrivateValue(SoundManager.class, manager, "field_189000_p");
            if (paused == null || paused.isEmpty()) {
                return;
            }
            com.google.common.collect.Multimap<SoundCategory, String> categories =
                    ObfuscationReflectionHelper.getPrivateValue(SoundManager.class, manager, "field_188776_k");
            Object system = ObfuscationReflectionHelper.getPrivateValue(SoundManager.class, manager, "field_148620_e");
            Collection<String> music = categories.get(SoundCategory.MUSIC);
            for (String channel : music.toArray(new String[0])) {
                if (paused.remove(channel)) {
                    ((paulscode.sound.SoundSystem) system).play(channel);
                    resumedChannels++;
                }
            }
        } catch (RuntimeException e) {
            BackportCore.LOGGER.debug("Presentation: music could not go on while paused", e);
        }
    }

    // ---- UI volume and the 1.21.9 volume preview ----

    @SubscribeEvent(priority = EventPriority.LOW)
    public void sound(PlaySoundEvent event) {
        ISound sound = event.getResultSound();
        if (sound == null || !active() || !SoundEvents.UI_BUTTON_CLICK.getSoundName().equals(sound.getSoundLocation())
                || sound.getCategory() != SoundCategory.MASTER) {
            return;
        }
        float volume = (float) Presentation.UI_VOLUME.get();
        if (volume < 1.0F) {
            event.setResultSound(volume <= 0.0F ? null : new UiSound(volume));
        }
    }

    /** A button click at the UI volume. */
    static final class UiSound extends net.minecraft.client.audio.PositionedSound {
        UiSound(float volume) {
            super(SoundEvents.UI_BUTTON_CLICK, SoundCategory.MASTER);
            this.volume = 0.25F * volume;
            this.pitch = 1.0F;
            this.attenuationType = ISound.AttenuationType.NONE;
        }
    }

    @SubscribeEvent
    public void mouse(GuiScreenEvent.MouseInputEvent.Post event) {
        if (!(event.getGui() instanceof GuiScreenOptionsSounds) || !Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.MUSIC, "1.21.9")) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (Mouse.getEventButton() != 0) {
            return;
        }
        if (Mouse.getEventButtonState()) {
            this.draggingVolume = true;
        } else if (this.draggingVolume && mc.world == null) {
            this.draggingVolume = false;
            preview(mc);
        }
    }

    /** 1.21.9's {@code SoundPreviewHandler}: a short sound of the kind whose slider was let go (outside a world). */
    private static void preview(Minecraft mc) {
        SoundCategory category = hoveredCategory(mc);
        if (category == null || category == SoundCategory.MUSIC || category == SoundCategory.MASTER) {
            return;
        }
        net.minecraft.util.SoundEvent sample;
        switch (category) {
            case RECORDS: sample = SoundEvents.BLOCK_NOTE_HARP; break;
            case WEATHER: sample = SoundEvents.WEATHER_RAIN; break;
            case BLOCKS: sample = SoundEvents.BLOCK_GRASS_PLACE; break;
            case HOSTILE: sample = SoundEvents.ENTITY_ZOMBIE_AMBIENT; break;
            case NEUTRAL: sample = SoundEvents.ENTITY_COW_AMBIENT; break;
            case PLAYERS: sample = SoundEvents.ENTITY_GENERIC_EAT; break;
            case AMBIENT: sample = SoundEvents.AMBIENT_CAVE; break;
            case VOICE: sample = SoundEvents.ENTITY_VILLAGER_AMBIENT; break;
            default: return;
        }
        mc.getSoundHandler().playSound(new net.minecraft.client.audio.PositionedSoundRecord(sample.getSoundName(), category, 1.0F, 1.0F,
                false, 0, ISound.AttenuationType.NONE, 0.0F, 0.0F, 0.0F));
        previews++;
    }

    @Nullable
    private static SoundCategory hoveredCategory(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiScreenOptionsSounds)) {
            return null;
        }
        java.util.List<net.minecraft.client.gui.GuiButton> buttons =
                ObfuscationReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class, mc.currentScreen, "field_146292_n");
        int x = Mouse.getEventX() * mc.currentScreen.width / mc.displayWidth;
        int y = mc.currentScreen.height - Mouse.getEventY() * mc.currentScreen.height / mc.displayHeight - 1;
        for (net.minecraft.client.gui.GuiButton b : buttons) {
            if (x >= b.x && y >= b.y && x < b.x + b.width && y < b.y + b.height && b.id >= 0 && b.id < SoundCategory.values().length) {
                return SoundCategory.values()[b.id];
            }
        }
        return null;
    }

    // ---- Music Toast ----

    /** @return the lang key of a playing track's name: {@code music.<path>}, 1.12.2's tracks by their later names */
    @Nullable
    static String trackKey(@Nullable ISound sound) {
        if (sound == null || sound.getSound() == null) {
            return null;
        }
        String path = sound.getSound().getSoundLocation().getPath();
        String old = OLD_TRACKS.get(path);
        return old != null ? old : path.replace('/', '.');
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active() || "never".equals(Presentation.MUSIC_TOAST.get())) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ISound playing = GameMusic.currentTrack(mc);
        long now = Minecraft.getSystemTime();
        if (playing != this.lastTrack) {
            this.lastTrack = playing;
            this.toastStart = playing != null && "pause_menu_and_toast".equals(Presentation.MUSIC_TOAST.get()) ? now : -1L;
        }
        String key = trackKey(playing);
        if (key == null || !I18n.hasKey(key) || Presentation.asset(TOAST.getPath()) == null) {
            return;
        }
        float shown;
        if (mc.currentScreen instanceof GuiIngameMenu) {
            shown = 1.0F;
        } else if (this.toastStart >= 0 && now - this.toastStart < TOAST_MS + SLIDE_MS) {
            long t = now - this.toastStart;
            shown = t < SLIDE_MS ? t / (float) SLIDE_MS : t > TOAST_MS ? 1.0F - (t - TOAST_MS) / (float) SLIDE_MS : 1.0F;
        } else {
            return;
        }
        draw(mc, I18n.format(key), shown);
    }

    /** {@code NowPlayingToast.renderToast} at the top left, slid in by {@code shown} (0 to 1). */
    private static void draw(Minecraft mc, String name, float shown) {
        FontRenderer font = mc.fontRenderer;
        int width = 30 + font.getStringWidth(name) + 7;
        net.minecraft.client.gui.ScaledResolution res = new net.minecraft.client.gui.ScaledResolution(mc);
        GlStateManager.clear(256);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, res.getScaledWidth_double(), res.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        GlStateManager.translate(width * shown - width, 0.0F, -2000.0F);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(TOAST);
        nineSlice(0, 0, width, 30, 160, 32, 4);
        if (Presentation.asset(NOTES.getPath()) != null) {
            mc.getTextureManager().bindTexture(NOTES);
            // 16 by 128: eight frames of 16 by 16.
            int frame = (int) (Minecraft.getSystemTime() / 100L % 8L);
            Gui.drawModalRectWithCustomSizedTexture(7, 7, 0.0F, frame * 16.0F, 16, 16, 16.0F, 128.0F);
        }
        font.drawString(name, 30, 15 - 9 / 2, TEXT_COLOR);
        toastFrames++;
    }

    /** A nine-slice sprite of {@code tw} by {@code th} with a {@code border}-pixel edge, stretched to w by h. */
    private static void nineSlice(int x, int y, int w, int h, int tw, int th, int border) {
        int[] xs = {0, border, w - border, w};
        int[] us = {0, border, tw - border, tw};
        int[] ys = {0, border, h - border, h};
        int[] vs = {0, border, th - border, th};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int dw = xs[i + 1] - xs[i];
                int dh = ys[j + 1] - ys[j];
                if (dw > 0 && dh > 0) {
                    Gui.drawScaledCustomSizeModalRect(x + xs[i], y + ys[j], (float) us[i], (float) vs[j], us[i + 1] - us[i], vs[j + 1] - vs[j],
                            dw, dh, (float) tw, (float) th);
                }
            }
        }
    }
}
