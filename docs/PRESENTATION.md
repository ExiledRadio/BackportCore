# Presentation: each version's look and feel

The look of the game follows the newest installed backport, as vanilla's did at that version. Backport Core
owns every single-slot piece (the panorama, the world-loading screen, the options screens, the splash list,
the menu music, the F3 keys, the fog) and builds each from what the installed backports contribute, so they
never fight. Package `com.exiledradio.backportcore.presentation`; the client-only parts are in its `client`
subpackage. Since Core 1.2.0.

With no backport installed, the newest version is 1.12.2 and the game looks exactly as 1.12.2 does: every
hook calls vanilla's own code.

## What each backport calls

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
| Menu music | `v.addMenuMusic("modid:music/menu/track", 1)`, `v.removeMenuMusic("minecraft:music/menu/menu1")` | Sound names as sounds.json writes them; the `.ogg` sits under `assets/<modid>/sounds/`. Streamed, full volume, weighted. |
| F3 keys | `v.debugKey(Keyboard.KEY_C, "debug.copy_location.help", () -> {...; return true;})`, `v.removeDebugKey(Keyboard.KEY_F)` | LWJGL 2 key codes. Register the action from client code (it runs on the client thread, no screen open). `PresentationHooks.debugFeedback("key", args)` prints vanilla's yellow "[Debug]:" line. |

| Vanilla assets | `v.replaceAsset("textures/gui/widgets.png", new ResourceLocation("modid", "textures/gui/widgets.png"))`, and the shorthands `v.titleLogo(rl)` (`minecraft.png`) and `v.startupLogo("textures/gui/title/mojangstudios.png", rl)` | Core's own resource pack serves `minecraft:<path>` from the newest version that replaces it, above vanilla's default pack (a player's resource packs still win). Read from the backport's jar. |
| Vanilla text | `v.vanillaText(new ResourceLocation("modid", "vanilla_text"))` | `assets/modid/vanilla_text/<code>.lang`, vanilla's keys only. Composed into `minecraft:lang/<code>.lang`, oldest version first, so the newest wording wins whatever the mod load order. Keep vanilla keys out of the mod's own `lang/`. |
| Title Accessibility icon (1.14) | `v.titleAccessibilityIcon(rl)` | Vanilla's 32 by 64 `accessibility.png`. The button shows from 1.14 with the newest icon. |
| Multiplayer warning (1.15.2) | `v.multiplayerWarning("multiplayerWarning.header", "multiplayerWarning.message", "multiplayerWarning.check")` | Shown before the server list from the title screen until dismissed for good (`backportcore:skip_multiplayer_warning`). The wording is whatever the newest version's text says. |
| Creative order | `v.creativeOrder(new ResourceLocation("modid", "creative_order.txt"))` | One `modid:name` or `modid:name@meta` per line; every tab is sorted by the newest file, unnamed items after. No backport uses it: their items' places come from `creative.CreativePlacement` (`docs/CREATIVE_TABS.md`), and a file here would sort over them. |
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
  * Chat, Music and Sounds, Skin Customization: the two-column grid continues; Done moves down.
  * Accessibility (1.14, falls back to Chat) and Mouse Settings (1.14, falls back to Controls): Core's own
    screens in 1.14's layout, with vanilla's settings that moved there (narrator, subtitles, chat opacity,
    Auto-Jump; sensitivity, invert mouse, touchscreen) and the backports' options.
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
## What is not covered yet

* The 1.20 title logo layout (its logo file can be replaced; the 1.20 layout is not drawn).
* Forge's own loading screen during the game's start stays Forge's; Core's startup screen shows as each load
  ends. 1.12.2 loads resources in one blocking call, so the bar cannot move during a reload.
* The world list's search bar (1.13.1): its list, title and focus are private to `GuiWorldSelection`, so it
  would mean replacing the screen.
* Optimize World (the Flattening's converter), Command Suggestions (no Brigadier), Raw Input (LWJGL 2), Fabulous
  graphics, Biome Blend: no 1.12.2 counterpart.
* Text Background "Everywhere" reaches the chat input box, not name tags or subtitles.
* Key Binds, Online and Font Settings screens.
## Testing

`gradlew runServer -Pselftest` (delete `eclipse/world` first) checks the registry, the panorama choice, the
spawn-area grid of the server's own start, option values, defaults, splashes, music, F3 keys and the step
order, with a dummy "1.14" and "1.16" from the self-test mod. `gradlew runClient -Pselftest` checks the
client side in a dev client (logs `CLIENT SELFTEST DONE pass=N fail=M`, then closes): the patches at the title
screen, the dummy panorama drawn, the splash and music pools, the defaults, every options screen opened in
code, the F3 hook, and a world created to see the loading screen draw 1.16's revision. No clicks or keys are
sent to the window. `docs/PRESENTATION_CHECKLIST.md` lists what to look at in game.
