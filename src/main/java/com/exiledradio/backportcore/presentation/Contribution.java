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
    final java.util.Map<String, ResourceLocation> assets = new java.util.LinkedHashMap<String, ResourceLocation>();
    final List<ResourceLocation> texts = new ArrayList<ResourceLocation>();
    @Nullable
    ResourceLocation accessibilityIcon;
    @Nullable
    String[] multiplayerWarning;
    @Nullable
    ResourceLocation creativeOrder;

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
        return addMenuMusic(soundName, weight, 1.0F);
    }

    /**
     * As {@link #addMenuMusic(String, int)}, at a volume (0 to 1) as sounds.json gives it: 1.21.6's menu tracks are
     * {@code music.menu} entries at 0.4. Since Core 1.2.0.
     */
    public Contribution addMenuMusic(String soundName, int weight, float volume) {
        this.musicAdds.add(new MenuTrack(soundName, weight, volume));
        return this;
    }

    /**
     * A title-screen track this version removed, by its sound name ({@code "minecraft:music/menu/menu1"}). A name
     * without a namespace ({@code "music/game/wending"}, since Core 1.2.0) removes that path whichever mod plays it,
     * so a version can drop a track another backport added under its own id.
     */
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

    // ---- Vanilla assets and text ----

    /**
     * One of vanilla's own assets as this version shipped it: {@code replaceAsset("textures/gui/title/minecraft.png",
     * new ResourceLocation("netherupdatebackport", "textures/gui/title/minecraft.png"))}. Core serves {@code
     * minecraft:<path>} from the newest installed version that replaces it, through its own resource pack above
     * vanilla's (a player's resource packs still win, as in vanilla). The file is read from the backport's jar.
     * Core never ships a vanilla asset.
     *
     * <p>The title logo ({@link #titleLogo}), the startup screen's logo ({@link #startupLogo}) and the button
     * texture ({@code textures/gui/widgets.png}, 1.15's highlight) are all this.
     */
    public Contribution replaceAsset(String vanillaPath, ResourceLocation source) {
        this.assets.put(vanillaPath, source);
        return this;
    }

    /** This version's title logo: {@code replaceAsset("textures/gui/title/minecraft.png", logo)} (1.16 redrew it). */
    public Contribution titleLogo(ResourceLocation logo) {
        return replaceAsset("textures/gui/title/minecraft.png", logo);
    }

    /**
     * This version's startup-screen logo, by vanilla's own file name at that version: 1.14's
     * {@code textures/gui/title/mojang.png}, 1.16's {@code textures/gui/title/mojangstudios.png}. Which layout the
     * screen has (1.14's white screen and bar, 1.16's red one) is Core's table ({@link StartupRevision}).
     */
    public Contribution startupLogo(String vanillaPath, ResourceLocation logo) {
        return replaceAsset(vanillaPath, logo);
    }

    /**
     * This version's rewording of vanilla's own text: a folder in the backport's assets holding {@code <code>.lang}
     * files ({@code new ResourceLocation("netherupdatebackport", "vanilla_text")} is {@code
     * assets/netherupdatebackport/vanilla_text/en_us.lang} and so on), each with vanilla's keys only. Core loads
     * every installed version's files into {@code minecraft}'s language, oldest version first, so where two
     * versions reword one key the newest wins, whatever order the mods load in. Keep vanilla's keys out of the
     * mod's own {@code lang/} folder, which would decide by load order instead.
     */
    public Contribution vanillaText(ResourceLocation folder) {
        this.texts.add(folder);
        return this;
    }

    // ---- Title screen and menus ----

    /**
     * The icon of 1.14's Accessibility button on the title screen (19w13a), vanilla's {@code
     * textures/gui/accessibility.png}: a 32 by 64 texture, the plain icon at the top and the hovered one 20 pixels
     * down. The button shows from 1.14 on, with the newest version's icon that has one.
     */
    public Contribution titleAccessibilityIcon(ResourceLocation icon) {
        this.accessibilityIcon = icon;
        return this;
    }

    /**
     * The multiplayer warning screen (1.15.2's "Online play is not rated", reworded in 1.16) shown before the
     * server list, from the title screen, until "Do not show this screen again" is ticked: its header, message and
     * check-box lang keys (vanilla's are {@code multiplayerWarning.header}, {@code .message}, {@code .check}).
     * Newest version wins.
     */
    public Contribution multiplayerWarning(String headerKey, String messageKey, String checkKey) {
        this.multiplayerWarning = new String[] {headerKey, messageKey, checkKey};
        return this;
    }

    /**
     * This version's creative inventory order: a text file in the backport's assets, one item per line, as
     * {@code modid:name} or {@code modid:name@meta}, in the order the version lists them (any tab). Every tab is
     * sorted by it, items not in it keeping their own order after those that are. The newest version with a file
     * wins.
     */
    public Contribution creativeOrder(ResourceLocation file) {
        this.creativeOrder = file;
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
        /** 0 to 1, as sounds.json's {@code volume} (since Core 1.2.0). */
        public final float volume;

        MenuTrack(String soundName, int weight, float volume) {
            if (weight <= 0) {
                throw new IllegalArgumentException(soundName + ": weight must be positive");
            }
            this.soundName = soundName;
            this.weight = weight;
            this.volume = Math.max(0.0F, Math.min(1.0F, volume));
        }
    }
}
