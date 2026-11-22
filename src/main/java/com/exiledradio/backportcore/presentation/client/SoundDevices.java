package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.LWJGLException;
import org.lwjgl.openal.AL;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.18's Device and 1.19's Directional Audio (Music and Sounds; {@link Presentation#SOUND_DEVICE},
 * {@link Presentation#DIRECTIONAL_AUDIO}). 1.12.2's sound system (Paulscode's {@code LibraryLWJGLOpenAL}) opens OpenAL
 * with {@code AL.create()}: the system's default device, no attributes. Patch P52 has it call {@link #createAL()}
 * instead, which opens the chosen device and, with Directional Audio on, asks OpenAL Soft for HRTF
 * ({@code ALC_HRTF_SOFT}, extension {@code ALC_SOFT_HRTF}), as 1.19's {@code Library.init} does. A change reloads the
 * sound system ({@code SoundManager.reloadSoundSystem}), as 26.3's {@code SoundManager.reload}.
 * <ul>
 * <li>LWJGL 2: {@code AL.create(device, 44100, 60, false)} opens a device by name. For HRTF the context it made is made
 * again with the attribute (LWJGL 2's {@code AL} has no way to pass one), where the device has the extension.</li>
 * <li>Cleanroom (lwjglx over LWJGL 3): lwjglx's {@code AL.create} always opens the default device (and sets HRTF from
 * Cleanroom's own config), so the device it opened is moved to the chosen one with OpenAL Soft's
 * {@code alcReopenDeviceSOFT}, or reset with the HRTF attribute ({@code alcResetDeviceSOFT}).</li>
 * </ul>
 * A device that is gone falls back to System Default, as 26.3's. 26.3 also follows the system's default device when it
 * changes while the game runs; this does not (it is picked when the sound system starts).
 */
@SideOnly(Side.CLIENT)
public final class SoundDevices {

    static final int ALC_HRTF_SOFT = 0x1992;
    static final int ALC_ALL_DEVICES_SPECIFIER = 0x1013;
    static final int ALC_DEVICE_SPECIFIER = 0x1005;
    /** 1.18's {@code SoundEngine.OPEN_AL_SOFT_PREFIX}: device names without it on the button. */
    static final String PREFIX = "OpenAL Soft on ";

    private static volatile boolean hrtfExtension;
    private static volatile String opened = "";
    private static volatile boolean hrtfOn;
    private static Field sndManager;

    /** For the client self-test: the devices last listed. */
    public static volatile List<String> lastListed = Collections.emptyList();

    private SoundDevices() {
    }

    /** From Core's client setup: Directional Audio shows once OpenAL is known to do HRTF; a change restarts the sound. */
    public static void init() {
        Presentation.setDirectionalAudioPossible(() -> hrtfExtension);
        Presentation.DIRECTIONAL_AUDIO.onChange(on -> reload());
    }

    static boolean cleanroom() {
        return AL.class.getName().startsWith("org.lwjglx");
    }

    /** @return true while the device and Directional Audio rows show (1.18 and 1.19 or later) */
    public static boolean deviceShown() {
        return Presentation.SOUND_DEVICE.isShown();
    }

    /** P52: {@code LibraryLWJGLOpenAL.init}'s {@code AL.create()}, on Paulscode's thread. */
    public static void createAL() throws LWJGLException {
        String device = deviceShown() ? Presentation.SOUND_DEVICE.get() : "";
        boolean hrtf = Presentation.DIRECTIONAL_AUDIO.isShownAt(Presentation.newest(Presentation.DIRECTIONAL_AUDIO.area()))
                && Presentation.DIRECTIONAL_AUDIO.get();
        if (cleanroom()) {
            AL.create();
            Lwjgl3.adjust(device, hrtf);
            return;
        }
        Lwjgl2.create(device, hrtf);
    }

    static void opened(String device, boolean hrtfAvailable, boolean hrtf) {
        opened = device;
        hrtfExtension = hrtfAvailable;
        hrtfOn = hrtf;
        BackportCore.LOGGER.info("Sound: OpenAL on {}{}{}", device.isEmpty() ? "the system default device" : device,
                hrtfAvailable ? "" : " (no HRTF in this OpenAL)", hrtf ? " with HRTF" : "");
    }

    /** @return the device OpenAL was last opened on (empty: the system default) */
    public static String opened() {
        return opened;
    }

    /** @return true when HRTF was turned on when OpenAL last opened */
    public static boolean hrtfOn() {
        return hrtfOn;
    }

    /** @return the devices OpenAL lists now (full names), System Default not among them */
    public static List<String> devices() {
        List<String> out;
        try {
            out = cleanroom() ? Lwjgl3.devices() : Lwjgl2.devices();
        } catch (Throwable t) {
            BackportCore.LOGGER.warn("Sound: could not list the OpenAL devices ({})", t.toString());
            out = new ArrayList<String>();
        }
        // OpenAL can name one device twice (two endpoints of one card): the button cycles through names.
        out = new ArrayList<String>(new java.util.LinkedHashSet<String>(out));
        lastListed = out;
        return out;
    }

    /** @return the device's name as the button shows it: 26.3's System Default, or the name without "OpenAL Soft on " */
    public static String label(String device) {
        if (device.isEmpty()) {
            return I18n.format("options.audioDevice.default");
        }
        return device.startsWith(PREFIX) ? device.substring(PREFIX.length()) : device;
    }

    /** Chooses the next (or previous) device, System Default first, and restarts the sound on it. */
    public static void cycle(boolean backwards) {
        List<String> all = new ArrayList<String>();
        all.add("");
        all.addAll(devices());
        int at = all.indexOf(Presentation.SOUND_DEVICE.get());
        int next = at < 0 ? 0 : Math.floorMod(at + (backwards ? -1 : 1), all.size());
        Presentation.SOUND_DEVICE.set(all.get(next));
        reload();
    }

    /** 26.3's {@code SoundManager.reload()}: 1.12.2's {@code SoundManager.reloadSoundSystem()} on the client thread. */
    public static void reload() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            try {
                if (sndManager == null) {
                    sndManager = ReflectionHelper.findField(SoundHandler.class, "sndManager", "field_147694_f");
                }
                ((SoundManager) sndManager.get(mc.getSoundHandler())).reloadSoundSystem();
            } catch (Exception e) {
                BackportCore.LOGGER.error("Sound: could not restart the sound system", e);
            }
        });
    }

    /** LWJGL 2's OpenAL (loaded only there). */
    static final class Lwjgl2 {

        static void create(String device, boolean hrtf) throws LWJGLException {
            String used = device;
            if (device.isEmpty()) {
                AL.create();
            } else {
                try {
                    AL.create(device, 44100, 60, false);
                } catch (LWJGLException | RuntimeException e) {
                    BackportCore.LOGGER.warn("Sound: device \"{}\" did not open ({}); using the system default", device, e.toString());
                    used = "";
                    AL.create();
                }
            }
            boolean ext = org.lwjgl.openal.ALC10.alcIsExtensionPresent(AL.getDevice(), "ALC_SOFT_HRTF");
            boolean on = false;
            if (hrtf && ext) {
                on = remake(true);
            }
            opened(used, ext, on);
        }

        /** Makes the context again with {@code ALC_HRTF_SOFT}; false (the old context kept) when OpenAL refuses. */
        private static boolean remake(boolean hrtf) {
            try {
                org.lwjgl.openal.ALCcontext old = AL.getContext();
                java.nio.IntBuffer attrs = org.lwjgl.BufferUtils.createIntBuffer(9);
                attrs.put(org.lwjgl.openal.ALC10.ALC_FREQUENCY).put(44100).put(org.lwjgl.openal.ALC10.ALC_REFRESH).put(60)
                        .put(org.lwjgl.openal.ALC10.ALC_SYNC).put(0).put(ALC_HRTF_SOFT).put(hrtf ? 1 : 0).put(0);
                attrs.flip();
                org.lwjgl.openal.ALCcontext made = org.lwjgl.openal.ALC10.alcCreateContext(AL.getDevice(), attrs);
                if (made == null) {
                    return false;
                }
                org.lwjgl.openal.ALC10.alcMakeContextCurrent(made);
                org.lwjgl.openal.ALC10.alcDestroyContext(old);
                Field context = AL.class.getDeclaredField("context");
                context.setAccessible(true);
                context.set(null, made);
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                BackportCore.LOGGER.warn("Sound: could not turn HRTF on ({})", e.toString());
                return false;
            }
        }

        static List<String> devices() {
            List<String> out = new ArrayList<String>();
            if (!AL.isCreated()) {
                return out;
            }
            int token = org.lwjgl.openal.ALC10.alcIsExtensionPresent(null, "ALC_ENUMERATE_ALL_EXT") ? ALC_ALL_DEVICES_SPECIFIER : ALC_DEVICE_SPECIFIER;
            String list = org.lwjgl.openal.ALC10.alcGetString(null, token);
            if (list != null) {
                for (String s : list.split("\u0000")) {
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
            return out;
        }
    }

    /** Cleanroom's LWJGL 3 OpenAL, by reflection (Core compiles against LWJGL 2). */
    static final class Lwjgl3 {

        private static Class<?> c(String name) throws ClassNotFoundException {
            return Class.forName(name, true, SoundDevices.class.getClassLoader());
        }

        private static long device() throws ReflectiveOperationException {
            Class<?> alc = c("org.lwjgl.openal.ALC10");
            long context = (Long) alc.getMethod("alcGetCurrentContext").invoke(null);
            return (Long) alc.getMethod("alcGetContextsDevice", long.class).invoke(null, context);
        }

        private static boolean has(long device, String extension) throws ReflectiveOperationException {
            return (Boolean) c("org.lwjgl.openal.ALC10").getMethod("alcIsExtensionPresent", long.class, CharSequence.class)
                    .invoke(null, device, extension);
        }

        static void adjust(String device, boolean hrtf) {
            String used = "";
            boolean ext = false;
            boolean on = false;
            try {
                long handle = device();
                ext = has(handle, "ALC_SOFT_HRTF");
                java.nio.IntBuffer attrs = java.nio.ByteBuffer.allocateDirect(4 * 3).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
                attrs.put(ALC_HRTF_SOFT).put(hrtf ? 1 : 0).put(0);
                attrs.flip();
                if (!device.isEmpty() && has(handle, "ALC_SOFT_reopen_device")) {
                    boolean ok = (Boolean) c("org.lwjgl.openal.SOFTReopenDevice").getMethod("alcReopenDeviceSOFT", long.class,
                            CharSequence.class, java.nio.IntBuffer.class).invoke(null, handle, device, ext ? attrs : null);
                    used = ok ? device : "";
                    on = ok && ext && hrtf;
                    if (!ok) {
                        BackportCore.LOGGER.warn("Sound: device \"{}\" did not open; using the system default", device);
                    }
                }
                if (used.isEmpty() && ext && hrtf) {
                    on = (Boolean) c("org.lwjgl.openal.SOFTHRTF").getMethod("alcResetDeviceSOFT", long.class, java.nio.IntBuffer.class)
                            .invoke(null, handle, attrs);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                BackportCore.LOGGER.warn("Sound: could not set the device or HRTF on LWJGL 3 ({})", e.toString());
            }
            opened(used, ext, on);
        }

        @SuppressWarnings("unchecked")
        static List<String> devices() throws ReflectiveOperationException {
            Class<?> alc = c("org.lwjgl.openal.ALC10");
            boolean all = (Boolean) alc.getMethod("alcIsExtensionPresent", long.class, CharSequence.class).invoke(null, 0L, "ALC_ENUMERATE_ALL_EXT");
            List<String> list = (List<String>) c("org.lwjgl.openal.ALUtil").getMethod("getStringList", long.class, int.class)
                    .invoke(null, 0L, all ? ALC_ALL_DEVICES_SPECIFIER : ALC_DEVICE_SPECIFIER);
            return list == null ? new ArrayList<String>() : new ArrayList<String>(list);
        }
    }
}
