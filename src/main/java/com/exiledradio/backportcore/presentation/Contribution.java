package com.exiledradio.backportcore.presentation;

import com.exiledradio.backportcore.presentation.option.PresentationOption;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one installed version brings to the look of the game: get it with {@link Presentation#version(String)}
 * and call the methods below, all chainable, from the backport's pre-init or init. Everything here is plain
 * data, safe to call on a dedicated server; Core uses the client-side parts only on a client.
 *
 * <p>Each piece is applied the way vanilla applies it at the newest installed version: single-slot things
 * (panorama, loading screen) come from the newest version that has one; lists (splashes, menu music, F3 keys,
 * versioned defaults) are built by applying every installed version's additions and removals in version
 * order.
 */
public final class Contribution {

    private final GameVersion version;
    private final List<String> owners = new ArrayList<String>();

    @Nullable
    ResourceLocation[] panorama;
    @Nullable
    ResourceLocation panoramaOverlay;
    final List<String> splashAdds = new ArrayList<String>();
    final List<String> splashRemoves = new ArrayList<String>();
    final List<ResourceLocation> splashAddFiles = new ArrayList<ResourceLocation>();
    final List<ResourceLocation> splashRemoveFiles = new ArrayList<ResourceLocation>();
    @Nullable
    LoadingRevision loadingRevision;
    final List<MenuTrack> musicAdds = new ArrayList<MenuTrack>();
    final List<String> musicRemoves = new ArrayList<String>();
    final List<DebugKeys.Key> debugAdds = new ArrayList<DebugKeys.Key>();
    final List<Integer> debugRemoves = new ArrayList<Integer>();
    final List<VersionedDefaults.Entry> defaults = new ArrayList<VersionedDefaults.Entry>();
    final List<PresentationOption> options = new ArrayList<PresentationOption>();

    Contribution(GameVersion version) {
        this.version = version;
    }

    public GameVersion version() {
        return this.version;
    }

    /** @return the mod ids that registered this version (normally one) */
    public List<String> owners() {
        return Collections.unmodifiableList(this.owners);
    }

    void addOwner(String modId) {
        if (!this.owners.contains(modId)) {
            this.owners.add(modId);
        }
    }

    // ---- Panorama ----

    /**
     * The six faces of this version's title-screen panorama, in vanilla's order ({@code panorama_0.png} to
     * {@code panorama_5.png}: front, right, back, left, top, bottom). The backport ships them in its own
     * assets; Core never ships a vanilla panorama.
     */
    public Contribution panorama(ResourceLocation... faces) {
        if (faces.length != 6) {
            throw new IllegalArgumentException(this.version + ": a panorama has six faces, not " + faces.length);
        }
        this.panorama = faces.clone();
        return this;
    }

    /**
     * The six faces from one path prefix: {@code panorama("villagerbackport:textures/gui/title/background/panorama_")}
     * means {@code panorama_0.png} to {@code panorama_5.png} there.
     */
    public Contribution panorama(String prefix) {
        ResourceLocation base = new ResourceLocation(prefix);
        ResourceLocation[] faces = new ResourceLocation[6];
        for (int i = 0; i < 6; i++) {
            faces[i] = new ResourceLocation(base.getNamespace(), base.getPath() + i + ".png");
        }
        return panorama(faces);
    }

    /**
     * This version's {@code panorama_overlay.png}, the gradient 1.13 and later draw over the panorama in place
     * of 1.12.2's two gradients (drawn stretched over the screen, as a 16 by 128 texture). Without one from the
     * newest version that has it, 1.12.2's gradients stay.
     */
    public Contribution panoramaOverlay(ResourceLocation overlay) {
        this.panoramaOverlay = overlay;
        return this;
    }

    // ---- Splashes ----

    /**
     * Splash texts this version added. {@code {PLAYER}} stands for the player's name in capitals (1.14's
     * "<name> IS YOU"). Splashes that name a Java or OpenGL version are left out, since they are false on
     * 1.12.2, and so are removals of them.
     */
    public Contribution addSplashes(String... splashes) {
        Collections.addAll(this.splashAdds, splashes);
        return this;
    }

    /** Splash texts this version removed (or changed: remove the old text, add the new one). */
    public Contribution removeSplashes(String... splashes) {
        Collections.addAll(this.splashRemoves, splashes);
        return this;
    }

    /** As {@link #addSplashes(String...)}, from a text file in the backport's assets, one splash per line. */
    public Contribution addSplashes(ResourceLocation file) {
        this.splashAddFiles.add(file);
        return this;
    }

    /** As {@link #removeSplashes(String...)}, from a text file in the backport's assets, one splash per line. */
    public Contribution removeSplashes(ResourceLocation file) {
        this.splashRemoveFiles.add(file);
        return this;
    }

    // ---- Options and defaults ----

    /**
     * Registers an option of this version (see {@link PresentationOption}). Its {@code since} version becomes
     * this one unless it set its own.
     */
    public Contribution option(PresentationOption option) {
        if (option.since() == null) {
            option.since(this.version.toString());
        }
        this.options.add(option);
        Presentation.registerOption(option);
        return this;
    }

    /**
     * A vanilla setting whose default this version changed, by its key and value as vanilla's options.txt
     * writes them ({@code gameOptionDefault("autoJump", "false")}, {@code ("gamma", "0.5")}). Core writes it
     * once per instance, the first time the title screen opens with this version installed, and never again:
     * a config default never reaches an installed instance by itself, and after that the value is the
     * player's. Client only.
     */
    public Contribution gameOptionDefault(String optionsTxtKey, String value) {
        this.defaults.add(VersionedDefaults.Entry.gameOption(this.version, optionsTxtKey, value));
        return this;
    }

    /** As {@link #gameOptionDefault}, for one of the backports' own options ({@link PresentationOption}). */
    public Contribution optionDefault(PresentationOption option, String value) {
        this.defaults.add(VersionedDefaults.Entry.option(this.version, option, value));
        return this;
    }

    /**
     * Any other changed default, applied once per instance as {@link #gameOptionDefault} is: {@code action} runs
     * once, on the client when the title screen first opens, or on a dedicated server as it starts. {@code key}
     * names it in the instance's record, so it must stay the same across releases.
     */
    public Contribution defaultAction(String key, Runnable action) {
        this.defaults.add(VersionedDefaults.Entry.action(this.version, key, action));
        return this;
    }

    // ---- Loading screen ----

    /**
     * Declares the world-loading screen revision this version introduced. Core already knows vanilla's
     * revisions ({@link LoadingRevision#forVersion}); this is for a revision Core's table puts at another
     * version, and the one declared by the newest version wins over the table when it is newer.
     */
    public Contribution loadingScreen(LoadingRevision revision) {
        this.loadingRevision = revision;
        return this;
    }

    // ---- Menu music ----

    /**
     * A title-screen music track this version added: a sound file by its sound name, as sounds.json names it
     * ({@code "villagerbackport:music/menu/stand_tall"} is {@code assets/villagerbackport/sounds/music/menu/stand_tall.ogg}).
     * Streamed, at full volume, with this weight among the pool (vanilla's tracks weigh 1 each).
     */
    public Contribution addMenuMusic(String soundName, int weight) {
        this.musicAdds.add(new MenuTrack(soundName, weight));
        return this;
    }

    /** A title-screen track this version removed, by its sound name ({@code "minecraft:music/menu/menu1"}). */
    public Contribution removeMenuMusic(String soundName) {
        this.musicRemoves.add(soundName);
        return this;
    }

    // ---- F3 keys ----

    /**
     * An F3 key combination this version added or changed: F3 + {@code keyCode} (an LWJGL 2 key code) runs
     * {@code action} on the client, with no screen open. {@code helpKey} is its line in the F3+Q list (a lang
     * key, as vanilla's {@code "debug.copy_location.help"}). A key vanilla 1.12.2 already has is replaced.
     */
    public Contribution debugKey(int keyCode, String helpKey, DebugKeys.Action action) {
        this.debugAdds.add(new DebugKeys.Key(keyCode, helpKey, action));
        return this;
    }

    /** An F3 key combination this version removed: F3 + {@code keyCode} does nothing from this version on. */
    public Contribution removeDebugKey(int keyCode) {
        this.debugRemoves.add(keyCode);
        return this;
    }

    @Override
    public String toString() {
        return "Contribution[" + this.version + " by " + this.owners + "]";
    }

    /** A menu track: a sound file by its sound name, and its weight. */
    public static final class MenuTrack {
        public final String soundName;
        public final int weight;

        MenuTrack(String soundName, int weight) {
            if (weight <= 0) {
                throw new IllegalArgumentException(soundName + ": weight must be positive");
            }
            this.soundName = soundName;
            this.weight = weight;
        }
    }
}
