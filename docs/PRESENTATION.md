# Presentation: each version's look and feel

The look of the game follows the newest installed backport, as vanilla's did at that version. Backport Core
owns every single-slot piece (the panorama, the world-loading screen, the options screens, the splash list,
the menu music, the F3 keys, the fog) and builds each from what the installed backports contribute, so they
never fight. Package `com.exiledradio.backportcore.presentation`; the client-only parts are in its `client`
subpackage. Since Core 1.1.0.

With no backport installed, the newest version is 1.12.2 and the game looks exactly as 1.12.2 does: every
hook calls vanilla's own code.

## What each backport calls

## The Look & Feel switches (Core 1.4.0)

Each part of the look resolves at its own version, `Presentation.newest(LookArea)`: the newest installed version,
stopped at Core's **Newest Look** (`lookAndFeel.newestLook`: Latest or a version), or 1.12.2 while **Use the Newer Look
and Feel** (`lookAndFeel.newerLook`) or that part's own switch is off. `Presentation.newest()` and `isAtLeast(version)`
are the whole look's (the master switch and Newest Look); `installedNewest()` is the newest installed version whatever
the switches, for content. A dedicated server ignores the switches: each player chooses their own look.

| `LookArea` | Switch | What resolves by it |
|---|---|---|
| `LOADING` | `loadingScreens` | `loadingRevision()`: the world-loading screen, the backports' drawers too |
| `STARTUP` | `startupScreen` | the startup screen, its logos, Monochrome Logo |
| `TITLE` | `titleScreen` | panorama, overlay, motion, logo (P29), fade-in, Accessibility button, Panorama Scroll Speed |
| `SPLASHES` | `splashes` | the splash list, Hide Splash Texts |
| `MENUS` | `menus` | menu backgrounds, button text, `widgets.png` and other GUI textures, pause and death screens, world backups, multiplayer warning, 60 fps menus |
| `OPTIONS` | `optionsScreens` | Accessibility, Mouse, Controls and Key Binds screens, the options on them |
| `VIDEO` | `videoSettings` | Video Settings' layout, removed entries, its options |
| `CHAT` | `chat` | command suggestions, the chat options |
| `DEBUG` | `debug` | F3 keys, `DebugText`, Debug Options, debug key binds |
| `TOASTS` | `toasts` | toast textures, Music Toast |
| `INVENTORY` | `inventory` | creative order, slot highlights (P23), container and recipe book textures |
| `MUSIC` | `music` | menu music, `GameMusic`, the Music and Sounds options, 1.14's sound channels |
| `SKY` | `sky` | `FogPipeline` |
| `WORDING` | `wording` | `vanillaText` |
| `TEXTURES` | `textures.modernTextures` | the texture layers, and other replaced assets |

A replaced asset belongs to a part by its path (`LookArea.ofAsset`). An option belongs to its screen's part (Video
Settings, Chat, Music and Sounds, else the options screens) unless it names one with `option.area(LookArea.X)`. Code
that only shows while an option `isShown()` follows its part.

A backport's own drawing asks `Presentation.isAtLeast(LookArea.LOADING, "1.20.5")` (its part) or `isAtLeast("1.20.5")`
(only the master switch and Newest Look). A backport's `@Config` boolean that only changes the look joins the Look & Feel
page with `@Setting.Look("music")` (sections: loading, title, menus, video, chat, debug, toasts, inventory, music, world,
backports); the family's existing ones are listed by name in `settings.LookFeelPage.KNOWN`.

Once, from pre-init or init, on both sides (everything on a `Contribution` is plain data):

```java
Contribution v = Presentation.version("1.14");   // the release your backport brings
```

Then add your version's pieces to `v`. Every method is chainable; leave out what your version did not change.

| Piece | Call | Notes |
|---|---|---|
| Panorama | `v.panorama("modid:textures/gui/title/background/panorama_")` | Six faces, `panorama_0.png` to `_5.png` (front, right, back, left, top, bottom, as vanilla's), shipped in your jar. Core never ships a vanilla panorama. |
| Panorama overlay | `v.panoramaOverlay(new ResourceLocation("modid", "textures/gui/title/background/panorama_overlay.png"))` | 1.13+'s gradient over the panorama, drawn as a 16 by 128 texture stretched to the screen. Without one, 1.12.2's two gradients stay. |
| Splashes | `v.addSplashes("...", ...)`, `v.removeSplashes("...")`, or `addSplashes(ResourceLocation)` / `removeSplashes(ResourceLocation)` (one splash per line) | A changed splash is a removal plus an addition. `{PLAYER}` is the player's name in capitals (1.14's "IS YOU"). Splashes naming a Java or OpenGL version are ignored, both ways. |
| Options | `v.option(new ToggleOption("modid:name", "options.key", OptionScreen.VIDEO, true))` | Also `SliderOption(id, label, screen, min, max, step, default)` and `CycleOption(id, label, screen, names, valueKeys, default)`. `since` defaults to your version; `.until("1.17")` hides it from that version; `.after("RENDER_CLOUDS")` places it after a vanilla entry (its `GameSettings.Options` name) or another option's id. Read the value with `get()`; `onChange(...)` hears changes. |
| Changed defaults | `v.gameOptionDefault("autoJump", "false")`, `v.optionDefault(option, "0.5")`, `v.defaultAction("key", runnable)` | Applied once per instance, then the player's (below). |
| Loading screen | `v.loadingScreen(LoadingRevision.X)` | Only for a revision Core's table does not put at your version; Core already knows vanilla's. |
| Menu music | `v.addMenuMusic("modid:music/menu/track", 1)`, `v.addMenuMusic(name, 1, 0.4F)`, `v.removeMenuMusic("minecraft:music/menu/menu1")`, `v.removeMenuMusic("music/game/wending")` | Sound names as sounds.json writes them; the `.ogg` sits under `assets/<modid>/sounds/`. Streamed, weighted, at the volume given (1.2.0; full by default). A removal without a namespace removes that path whichever mod plays it (1.2.0). |
| F3 keys | `v.debugKey(Keyboard.KEY_C, "debug.copy_location.help", () -> {...; return true;})`, `v.removeDebugKey(Keyboard.KEY_F)` | LWJGL 2 key codes. Register the action from client code (it runs on the client thread, no screen open). `PresentationHooks.debugFeedback("key", args)` prints vanilla's yellow "[Debug]:" line. |

| Vanilla assets | `v.replaceAsset("textures/gui/widgets.png", new ResourceLocation("modid", "textures/gui/widgets.png"))`, and the shorthands `v.titleLogo(rl)` (`minecraft.png`) and `v.startupLogo("textures/gui/title/mojangstudios.png", rl)` | Core's own resource pack serves `minecraft:<path>` from the newest version that replaces it, above vanilla's default pack (a player's resource packs still win). Read from the backport's jar. |
| Vanilla text | `v.vanillaText(new ResourceLocation("modid", "vanilla_text"))` | `assets/modid/vanilla_text/<code>.lang`, vanilla's keys only. Composed into `minecraft:lang/<code>.lang`, oldest version first, so the newest wording wins whatever the mod load order. Keep vanilla keys out of the mod's own `lang/`. |
| Title Accessibility icon (1.14) | `v.titleAccessibilityIcon(rl)` | Vanilla's 32 by 64 `accessibility.png`. The button shows from 1.14 with the newest icon. |
| Multiplayer warning (1.15.2) | `v.multiplayerWarning("multiplayerWarning.header", "multiplayerWarning.message", "multiplayerWarning.check")` | Shown before the server list from the title screen until dismissed for good (`backportcore:skip_multiplayer_warning`). The wording is whatever the newest version's text says. |
| Creative order | `v.creativeOrder(new ResourceLocation("modid", "creative_order.txt"))` | One `modid:name` or `modid:name@meta` per line; every tab is sorted by the newest file, unnamed items after. No backport uses it: their items' places come from `creative.CreativePlacement` (`docs/CREATIVE_TABS.md`), and a file here would sort over them. |
| Command suggestions (1.13) | `v.commandSuggestions(autoSuggest)`, `v.commandSuggestionLook(look)` | Turns on 1.13's suggestion list in the chat box (`presentation.client.ChatSuggestions`). `autoSuggest` is the version's "Command Suggestions" `ToggleOption`, registered with `option` too; off, the list shows on Tab. `SuggestionLook` is the list's colors and rows, plain data, so a later version can draw it its own way (1.13.2's by default). |
| Option polish | `option.tooltip("options.x.tooltip")`, `option.alsoOn(OptionScreen.VIDEO)`, `slider.zeroKey("options.off")`, `slider.wholeLabel("options.chat.delay", "options.chat.delay_none")` | 1.16's tooltips (wrapped at 200 pixels), one option on two screens (one value), "OFF" at zero, and labels that are one sentence. |
Client-only registrations, from the backport's client proxy:

| Piece | Call | Notes |
|---|---|---|
| Fog | `FogPipeline.add("1.13", "underwater visibility", step)` | `FogPipeline.Step` has `colors`, `density` and `distance`, each optional. Steps run oldest version first, each seeing what the ones before did. |
| Loading screen drawer | `LoadingScreens.register(LoadingRevision.PANORAMA_1_20_5, drawer)` | For the revisions Core does not draw (1.20.5, 1.21, 1.21.9). |

| Debug text (F3) | `DebugText.add("1.15", "no chunk updates", lines -> ...)` | Each step edits `lines.left` and `lines.right` in place, oldest version first, before other mods add their lines (Forge's `RenderGameOverlayEvent.Text`). |
| In-game music | `GameMusic.add("1.13", "under water", s -> { if (...) s.choose(new GameMusic.Track(event, 12000, 24000, false)); })` | The choice starts as 1.12.2's (`getAmbientMusicType`); each version's step may change it. Core's ticker (1.16.5's `MusicManager`, each track saying whether it cuts off another) replaces vanilla's once a step exists. |
| Sky and clouds | `FogPipeline.add(version, name, step)` with `sky(Sky)` / `clouds(Clouds)` | Patches P15 and P16: the sky dome, the fog's blend with the sky, both cloud passes and the camera-above-clouds test. |
Core resolves everything lazily, so the order backports load in does not matter. The game's one resource load at
startup runs before the backports' pre-init, so Core reads the languages again (and any replaced texture already
loaded) once every mod has loaded. The version that ships 1.13's underwater music hands Core its event
(`GameMusic.setUnderWater`), so later versions' music rules can ask for it.

Since Core 1.2.0:

| Piece | Call | Notes |
|---|---|---|
| Per-value tooltips | `cycle.valueTooltips("key.a.tooltip", "key.b.tooltip")` | One per value; null falls back to `tooltip`. |
| Blurred panorama | `PanoramaRenderer.renderBlurred(screen, PanoramaRenderer.MENU_BLUR)` (client) | 1.20.5's menu panorama, by 1.12.2's `shaders/post/blur.json`; sharp where shaders cannot run. |
| Sunrise | `FogPipeline.add(version, name, step)` with `sunrise(Sunrise)` | Patch P24: the glow's RGBA (a copy), or null for none. |
| Cloud timing | a step returning true from `cloudsFollowWorldTime()` | Patch P25: clouds drift by world time. |
| Slot highlight sprites | `v.replaceAsset("textures/gui/sprites/container/slot_highlight_back.png", rl)` and `_front` | Patch P23 draws them from 1.21.2 once both are supplied. |
| Music toast sprites | `v.replaceAsset("textures/gui/sprites/toast/now_playing.png", rl)`, `icon/music_notes.png` | The toast shows once they are supplied; names from `music.<path>` lang keys (1.12.2's tracks by their 1.20.5 names, in Core's lang). |

Core's own options from 1.21.x (shown from the version that added them): Music Frequency, Music Toast and UI
(Music and Sounds, 1.21.6), Attack/Destroy and Use Item/Place Block Hold or Toggle (Controls, 1.21.9), Cloud Distance
(Video Settings, 1.21.6), and 1.21.11's Preset, See-Through Leaves, Weather Effect Radius, Texture Filtering,
Anisotropic Filtering, Show Vignette and Chunk Fade Time (below). Menus without a world run at 60 frames a second
from 1.21.2 (P22).

| Piece | Call | Notes |
|---|---|---|
| A vanilla asset's version | `contribution.replacedAssets()` | The assets that version replaces, by path. |
| A slider's value text | `slider.valueText(v -> ...)` | 1.21.11's "Anisotropic Filtering: 4x" shows 2 to the saved value. |
| An option's name | `option.path()` | The id without its namespace. Options named after vanilla's (`toggle_crouch`) follow vanilla's moves between screens, and the presets set them. |

A backport that copies one of vanilla's options names it after vanilla's own (`<modid>:inactivity_fps_limit`,
`<modid>:toggle_crouch`, `<modid>:entity_distance_scaling`): Core places it by that name on 1.21.11's Video Settings,
moves it as vanilla moved it, and the graphics presets set it.

## How each piece resolves

* **Newest version**: `Presentation.newest()`, the highest registered version (`GameVersion`, compared
  number by number: 1.14 = 1.14.0 < 1.14.1 < 1.15). `Presentation.isAtLeast("1.16")` asks about it.
* **Panorama**: Core's `presentation.panorama` setting (`config/backportcore.cfg`): `LATEST` (default, the
  newest installed version with faces), a version (`1.14`, or `1.12.2` for 1.12.2's own faces), or `ROTATE`
  (a different one each launch, never last launch's). Once any backport is installed the title screen draws
  it as 1.13 does (patch P6): sharp, an 85 degree view, a full slow turn, restarting with each new title
  screen. 1.19.3's Panorama Scroll Speed (Core's own option, shown from 1.19.3) scales the turn.
* **Overlay**: the newest installed version's that has one.
* **Lists** start from 1.12.2's and apply every installed version's removals and additions, oldest first:
  splashes, menu music, F3 keys, defaults. A version's removal takes effect only when that version is
  installed, and a later version can bring a removed item back.
* **Loading screen**: vanilla's revision at the newest version (`LoadingRevision.forVersion`): 1.13's
  "Preparing spawn area: N%", 1.14's chunk-status grid (43 by 43, 1.14.4's colors, `LoadingStatus`), 1.16's
  "Reading world data..." then the grid. 1.20.5, 1.21 and 1.21.9 are hooks; until a drawer is registered,
  Core draws 1.16's. 1.12.2 generates a chunk in fewer steps than 1.14, so the grid shows generated, populated,
  ready-to-light and full (`SpawnProgress`); the percentage is 1.12.2's own spawn-area count.
* **Options** show while the newest version is at or after their `since` (and before `until`), on their
  screen, or on its fallback when that screen does not exist at the newest version (`OptionScreen.resolve`).
  Where they go:
  * Options: the free row above Done. From 1.14, "Accessibility Settings..." takes Snooper Settings' place.
  * Video Settings: the scrolling list, after the entry each names, or at the end.
  * Controls: the row above the key list (two slots from 1.14, when "Mouse Settings..." and Auto-Jump take
    the first row).
  * Chat, Music and Sounds, Skin Customization: the two-column grid continues; what is below it (Done, and on
    Skin Customization OptiFine's "OptiFine Cape...") moves down.
  * Accessibility (1.14, falls back to Chat) and Mouse Settings (1.14, falls back to Controls): Core's own
    screens in 1.14's layout, with vanilla's settings that moved there (narrator, subtitles, chat opacity,
    Auto-Jump; sensitivity, invert mouse, touchscreen) and the backports' options. When they do not all fit above
    Done (the whole family on a small window), they scroll as 1.16's options screens do: a list from y 32 to 32 above
    the bottom, rows 25 tall, Done or Accessibility's footer below.
  * Key Binds and Online (1.18) and Font (1.20.5) are not built yet: their options show on Controls and
    Options.

  Values are saved in `config/backportcore/options.txt` (`id:value` lines, kept for options no installed mod
  registers). Vanilla's own entries keep 1.12.2's order and names; a version's renames belong in its
  backport's lang file.
* **Defaults** apply once per instance, oldest version first (a later version wins for the same setting),
  and are recorded in `config/backportcore/applied_defaults.txt` so they never apply again. A client applies
  them when the title screen first shows (vanilla settings by their options.txt keys, then
  `GameSettings.loadOptions`); a dedicated server applies the non-vanilla ones as it starts.
* **Menu music**: when vanilla's ticker starts `music.menu`, the sound is swapped for a pick from the pool and
  the ticker is told, so vanilla's delays between tracks stay.
* **F3 keys** (patch P7): once a version changes the map, every F3 combination goes through it; F3+Q lists
  the map, letter keys alphabetically, then the others in the order added.

## What Core decides by version (no registration)

Core knows vanilla's own screens at each version (`VanillaRevisions`), as it knows the loading screen's, so a
change one version made stays made when only a later backport is installed:

* **Startup screen** (1.14: white, `mojang.png` 256 by 256, the red bar; 1.16: red `0xEF323D`, the Mojang
  Studios logo in two halves, a white bar; 1.17's Monochrome Logo: black). Drawn when each resource load
  ends (the game's last one at startup, F3+T, resource packs), covering the screen with the bar full for one
  second, then fading for one second over what is beneath. Its logo is the backports' `startupLogo`.
* **Title screen**: 1.13's fade-in on the first start (panorama at once, the rest over the next second); 1.14's
  Accessibility button (with an icon registered).
* **Video Settings**: 1.13 removes 3D Anaglyph (off) and numbers GUI Scale (Auto, 1, 2 ... up to what the window
  fits); 1.14 removes Use VBOs (on).
* **Pause menu** (1.14): 1.14.4's 204-wide grid with Give Feedback and Report Bugs; Forge's Mod Options moves below.
* **Death screen** (1.15): a hardcore death screen's second button reads "Title screen".
* **Buttons** (1.15): white labels, hovered or not (P12). The highlight texture is the 1.15 backport's `widgets.png`.
* **Chat** (P13, P14): Core's own options, 1.14's Text Background Opacity and Text Background, 1.16's Line Spacing
  and Chat Delay (player chat only, "[+N pending lines]"), on Accessibility and Chat Settings in 1.16.5's order.
* **Accessibility Guide** (1.16.4): at the bottom of Accessibility Settings, beside Done.
* **World list** (1.13): Edit World's Make Backup (a zip in `.minecraft/backups`) and Open Backups Folder.
* **Controls** (1.18): Options' "Controls..." opens Core's Controls screen (Mouse Settings... and Key Binds..., then
  Toggle Crouch, Toggle Sprint, Attack/Destroy, Use Item/Place Block and Auto-Jump in 1.21.11's order, then the
  backports' Controls options); "Key Binds..." is 1.12.2's key list with its top rows gone, titled Key Binds, the list
  from y 20. Toggle Crouch and Toggle Sprint (any mod's option of that name) show on Accessibility and Controls.
* **Accessibility** (1.21.9): Auto-Jump, Toggle Crouch and Toggle Sprint are on Controls only, and "Controls..." sits
  beside Narrator. 1.21.11: View Bobbing joins it from Video Settings.
* **Video Settings** (1.21.11, `VideoLayout`, `VideoSections`): three sections under their headers (Display: Max
  Framerate, VSync, Reduce FPS when, GUI Scale, Fullscreen, Brightness; Quality & Performance: the Preset across the
  top, then Render Distance, Smooth Lighting, Clouds, Particles, Mipmap Levels, Entity Shadows, Entity Distance, Menu
  Background Blurriness, Cloud Distance, See-Through Leaves, Texture Filtering, Anisotropic Filtering, Weather Effect
  Radius; Preferences: Autosave Indicator, Show Vignette, Attack Indicator, Chunk Fade Time; any other entry after
  them). Backport options with those names land in their places; 1.12.2's list draws every row 25 pixels tall, so
  the headers sit on rows of their own. Graphics is gone: **See-Through Leaves** is 1.12.2's Fancy graphics (what draws
  leaves see-through; taken from Graphics the first time), and the rest of what Fancy did has its own option.
  * **Preset** (`GraphicsPreset`): a 310-wide slider over Fast, Fancy, Fabulous! and Custom; letting go on one of
    the first three sets 1.21.11's values for the section (render distance 8/16/32, cloud distance 32/64/128, mipmaps
    2/4/4, weather radius 5/10/10, ...), the backports' options of those names included. Changing any of them makes it
    Custom.
  * **Cloud Distance** (from 1.21.6, P26): the clouds reach 2 to 128 chunks and fade out by that distance.
  * **Weather Effect Radius** (P27): rain and snow within 3 to 10 blocks.
  * **Texture Filtering**: None or Anisotropic, and **Anisotropic Filtering** 2x, 4x or 8x (on with Anisotropic only),
    set on the block atlas with OpenGL's anisotropic filtering. 1.21.11's RGSS is a shader 1.12.2 cannot run.
  * **Show Vignette**: off removes it; on draws it with See-Through Leaves off too.
  * **Chunk Fade Time** (P28): chunks drawn for the first time fade in from the fog color, 0 to 2 seconds.
* **Video Settings with OptiFine** (`OptiFineVideoScreen`): OptiFine rewrites `GuiVideoSettings` as a fixed grid of
  its twelve settings and six submenu buttons, with no scrolling list. Core recognizes that by its shape (no list among
  the class's fields, not by any name) and opens a subclass of OptiFine's own screen instead: after OptiFine's
  `initGui`, its buttons move into a scrolling list built as vanilla's, so the screen looks like Core's Video Settings
  without OptiFine (1.21.11's sections when that is the newest version), and OptiFine's buttons keep OptiFine's
  actions.
  * **Display**: Max Framerate (OptiFine's, with its VSync step), Reduce FPS when, GUI Scale, Brightness.
    **Quality & Performance**: the Preset, Render Distance, Smooth Lighting and OptiFine's Smooth Lighting Level,
    Entity Distance, Menu Background Blurriness, Cloud Distance, See-Through Leaves, Weather Effect Radius.
    **Preferences**: Autosave Indicator, Attack Indicator, then OptiFine's Dynamic Lights and Dynamic FOV.
    **OptiFine**: Shaders..., Quality..., Details..., Performance..., Animations... and Other..., which open OptiFine's
    submenus with this screen as their parent (Done there comes back to it). Without 1.21.11 there are no headers:
    the settings and options in two columns, then the six buttons.
  * The settings OptiFine moved off Video Settings (Fullscreen, Clouds, Particles, Mipmap Levels, Entity Shadows) stay
    in OptiFine's submenus. Graphics and View Bobbing are left out as without OptiFine (See-Through Leaves sets
    Graphics; View Bobbing is on Accessibility). Core's option tooltips show as without OptiFine, and OptiFine's
    tooltip manager is given its settings in view, as its own screen does.
  * **Left to OptiFine** (`VideoSections.OPTIFINE_HAS`): where OptiFine has its own setting, Core's is not on the
    screen and does nothing: Texture Filtering and Anisotropic Filtering (OptiFine's Anisotropic Filtering, Quality),
    Show Vignette (its Vignette, Details), Chunk Fade Time (off with OptiFine anyway; OptiFine draws the chunks) and
    Update Aquatic Backport's Biome Blend (its Smooth Biomes, Quality; with Smooth Biomes off, the blend keeps its
    saved radius). OptiFine's Clouds, Cloud Height, Fast Render, Dynamic Lights and mipmap settings have no Core
    counterpart, so nothing is doubled. Cloud Distance and Weather Effect Radius are not OptiFine's (it has neither)
    and stay; their patches (P26, P27) apply with OptiFine.
  * If OptiFine's screen ever lacks Done or a submenu button, or another mod's screen has no list, Core leaves the
    screen as it is and logs one line; nothing throws.
* **F3** (1.21.9, `DebugEntries`, `DebugScreen`): each part of the debug screen is Off, In F3 (In Overlay from 1.21.11)
  or Always, on **Debug Options** (F3 + F6, `DebugOptionsScreen`) with its search box and the Default and Performance
  profiles. 1.12.2's lines are sorted into vanilla's parts (game_version, fps, chunk_render_stats,
  entity_render_stats, particle_render_stats, chunk_source_stats, player_position, player_section_position,
  light_levels, biome, local_difficulty, looking_at_block, memory, system_specs, post_effect; a line no part claims
  belongs to the one above it, or shows with F3 like other mods' lines), keeping 1.12.2's order. The default profile
  shows what 1.21.9's does: version, fps, memory, system specs and position. Always parts show with F3 hidden. The
  renderers are parts too: 3d_crosshair, entity_hitboxes (F3 + B), chunk_borders (F3 + G) and 1.12.2's water,
  heightmap, collision box and solid face renderers. F3's combinations work in menus (not while typing), and the left
  column ends with "To edit: press [F3+F6]". Saved in Core's options file (`backportcore:debug_profile`,
  `backportcore:debug/<part>`).
* **Debug key binds** (1.21.11, `DebugBindings`, P30): F3 (as the modifier and as the screen's toggle), the crash's C
  and each F3 combination of the newest map are key binds in the Key Binds screen's "Debug" category.
* **Title logo** (`VanillaRevisions.TitleLogo`, P29): 1.20's layout when the newest backport that replaces
  `minecraft.png` is 1.20 or later and replaces `edition.png` too (so its textures are 1.20's): `minecraft.png` as one
  256 by 44 piece, `edition.png` 128 by 14 under it, Minceraft from `minceraft.png`, the splash at width / 2 + 123, 69.
  A backport of 1.20 or later registers `replaceAsset` for `textures/gui/title/minecraft.png`, `edition.png` and
  `minceraft.png` with vanilla's own files, and draws nothing itself.
* **F3 + 3, the network charts** (1.20.2, `NetworkCharts`): Core's own key from 1.20.2. On, the debug screen comes on
  and F3 + 2's charts (1.12.2's lagometer) go, and the other way round. The bandwidth received (bottom left; every byte
  the connection receives, counted at the front of its Netty pipeline, logged each tick; not on this game's own server,
  as vanilla) and the ping (bottom right; Core's ping request each tick while shown, `PingNetwork`, answered by a server
  running Core; on a server without Core, the tab list's latency) are 1.20.6's `BandwidthDebugChart` and
  `PingDebugChart`: 240 samples, 60 high, the min, avg and max above, the scale lines, the colors (`DebugCharts`).
  1.12.2's "Debug: Pie [shift] ... FPS [alt] ..." line reads 1.20.2's "Debug charts: [F3+1] Profiler hidden; [F3+2] FPS +
  TPS hidden; [F3+3] Ping hidden".
* **F3 keys with no help line** (1.20.2): `v.debugKey(key, null, action)` puts a key in the map with no F3 + Q line, as
  1.20.2's F3 + 1, 2 and 3 have none.
* **Narrator Hotkey** (1.20.2, P32): Core's own option `backportcore:narrator_hotkey`, last on Accessibility Settings, on
  by default; off, Ctrl + B (Cmd + B) no longer toggles the narrator.
* **Panorama motion** (1.20.5, `VanillaRevisions.panoramaSpinOnly`): the pitch holds at 10 degrees and the view only
  turns, by one spin every screen shares and that never restarts (the title screen's, the menus', the loading screens');
  each screen advances it by the ticks since its own last frame, half a tick for its first (`advancePanoramaTime`).
* **Menu backgrounds** (1.20.5, `MenuBackground`, P31): no dirt anywhere. Outside a world a menu is the panorama and its
  overlay, blurred, under `menu_background.png`; inside one the world as drawn this frame, blurred, under
  `inworld_menu_background.png` (the pause menu among them). Container, book and sign screens keep 1.12.2's gradient,
  as 1.20.5's do. Lists (every `GuiSlot`: the world and server lists, Video Settings, languages, resource packs, key binds,
  other mods' lists) draw `menu_list_background.png` over their area with their entries clipped to it, a header
  separator above and a footer separator below, and no dirt or shadows; a list whose screen draws no background gets the
  screen's behind it. The title screen itself is not blurred, as in vanilla.
  * **Blur**: 1.20.6's `post/blur.json` is six box-blur passes, all at the full radius (its `RadiusMultiplier`s sit
    where `PostChain` never reads them); Core's `backportcore:shaders/post/menu_blur.json` is the same six on 1.12.2's
    `blur` program. The radius is Menu Background Blurriness times 10: the slider of that name a backport registers
    (0 to 100%, Trails & Tales Backport's, or 0 to 10), else 1.20.5's default 5; under 1 there is no blur. Once a frame.
    The graphics presets set it in vanilla's 0 to 10 and convert for a 0 to 100% slider. Without shaders or
    framebuffers, no blur. `PanoramaRenderer.renderBlurred` uses the same six passes.
  * **Textures**: the eight files (`menu_background`, `inworld_menu_background`, `menu_list_background`,
    `inworld_menu_list_background`, `header_separator`, `footer_separator` and their `inworld_` forms, under
    `textures/gui/`) are read when a pack has them (a backport's `replaceAsset`, a player's resource pack). Each of
    1.20.6's is a single color, so without one Core draws that color: black at 64 of 255 for the screens, 112 for lists,
    and the separators a white line at 51 over a black line at 191 (the footer the other way up). A backport needs to
    supply none of them.
## What is not covered yet

* 1.21.11's Fullscreen Resolution (1.12.2 goes fullscreen at the desktop's mode), Biome Blend, Prioritize Chunk
  Updates, Simulation Distance and Improved Transparency (no 1.12.2 counterpart; a backport's option of that name is
  placed and set by the presets), RGSS texture filtering.
* The debug parts 1.12.2's screen has no lines for (tps, gpu_utilization, simple_performance_impactors,
  looking_at_fluid, looking_at_entity, heightmap, sound_mood, entity_spawn_counts, chunk_generation_stats, the
  other visualizers), and 1.21.9's arrangement of the parts into the two columns: 1.12.2's order stays.
* Allow Cursor Changes (LWJGL 2 has no system cursors), and slider arrow keys (1.12.2's screens have no keyboard
  focus).
* Forge's own loading screen during the game's start stays Forge's; Core's startup screen shows as each load
  ends. 1.12.2 loads resources in one blocking call, so the bar cannot move during a reload.
* The world list's search bar (1.13.1): its list, title and focus are private to `GuiWorldSelection`, so it
  would mean replacing the screen.
* Optimize World (the Flattening's converter), Raw Input (LWJGL 2), Fabulous graphics, Biome Blend: no 1.12.2
  counterpart.
* Command suggestions: 1.13's red highlighting of a wrong argument and the colored arguments (1.12.2's commands do not
  describe their arguments), the usage of the argument at the cursor (the whole command's `getUsage` shows instead),
  suggestion tooltips, and the narrator reading the list. A chat screen that does not extend `GuiChat` gets none.
* Text Background "Everywhere" reaches the chat input box, not name tags or subtitles.
* Key Binds, Online and Font Settings screens.
* 1.20.5's menu look on Forge's mod list (`GuiScrollingList`, its own drawing) and on screens that draw dirt themselves
  rather than through `GuiScreen.drawBackground`; 1.20.5's tab headers and header-and-footer layouts (1.12.2's screens
  keep their layouts, only the backgrounds change).
## Command suggestions

On Cleanroom 0.6 and later, which draw their own list in `GuiChat` with no switch, `ChatSuggestions` does nothing. Otherwise, once a version calls `commandSuggestions`, any `GuiChat` (and its subclasses) gets 1.13.2's list, through Forge's screen
events (no patch). The text before the cursor goes to the server in 1.12.2's `CPacketTabComplete` with the looked-at
block, one request at a time, once typing pauses for 80 ms; the answer is read off the connection on its way to
vanilla's handler (which ignores it: its own Tab never ran). Forge's client commands are asked locally. So vanilla and
Forge servers without Core work. The answer becomes Brigadier's form: the word before the cursor is the range, command
names lose their `/`, sorted ignoring case (`CommandSuggestionModel`, plain Java, self-tested on a server).

Keys as 1.13.2: Up and Down move, Tab accepts (again: the next; Shift: the one before), Esc closes the list before the
chat, the wheel scrolls it, a click or hover picks. Plain chat lists the tab list's names on Tab. 1.12.2's Tab
completion (and its comma-separated list in the chat) is off while suggestions are on. With a command's name typed out
and no list showing, its `getUsage` shows grey above the input (the arguments after the name): asked of a server that
said hello on login (`CommandUsageNetwork`, a server running Core), or from Forge's client commands; otherwise none.

## Testing

`gradlew runServer -Pselftest` (delete `eclipse/world` first) checks the registry, the panorama choice, the
spawn-area grid of the server's own start, option values, defaults, splashes, music, F3 keys and the step
order, with a dummy "1.14" and "1.16" from the self-test mod. `gradlew runClient -Pselftest` checks the
client side in a dev client (logs `CLIENT SELFTEST DONE pass=N fail=M`, then closes): the patches at the title
screen, the dummy panorama drawn, the splash and music pools, the defaults, every options screen opened in
code, the F3 hook, and a world created to see the loading screen draw 1.16's revision. No clicks or keys are
sent to the window. `docs/PRESENTATION_CHECKLIST.md` lists what to look at in game.
