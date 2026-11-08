# Presentation: each version's look and feel

The look of the game follows the newest installed backport, as vanilla's did at that version. Backport Core
owns every single-slot piece (the panorama, the world-loading screen, the options screens, the splash list,
the menu music, the F3 keys, the fog) and builds each from what the installed backports contribute, so they
never fight. Package `com.exiledradio.backportcore.presentation`; the client-only parts are in its `client`
subpackage.

With no backport installed, the newest version is 1.12.2 and the game looks exactly as 1.12.2 does.

## What each backport calls

Once, from pre-init or init (both sides; everything is plain data):

```java
Contribution v = Presentation.version("1.14");   // the release your backport brings
```

Then add your version's pieces to `v`. Every method is chainable; leave out what your version did not change.

| Piece | Call | Notes |
|---|---|---|
| Panorama | `v.panorama("modid:textures/gui/title/background/panorama_")` | Six faces, `panorama_0.png` to `_5.png`, shipped in your jar. Core never ships a vanilla panorama. |
| Panorama overlay | `v.panoramaOverlay(new ResourceLocation("modid", "textures/gui/title/background/panorama_overlay.png"))` | 1.13+'s gradient over the panorama. Without one, 1.12.2's two gradients stay. |
| Splashes | `v.addSplashes("...", ...)`, `v.removeSplashes("...")`, or the `ResourceLocation` forms (one splash per line) | A change is a removal plus an addition. `{PLAYER}` is the player's name in capitals. Java and OpenGL splashes are ignored. |
| Options | `v.option(new ToggleOption("modid:name", "options.key", OptionScreen.VIDEO, true))` | Also `SliderOption`, `CycleOption`. `since` defaults to your version; `.until("1.17")`, `.after("RENDER_CLOUDS")`. Read the value with `get()`. |
| Changed defaults | `v.gameOptionDefault("autoJump", "false")`, `v.optionDefault(option, "0.5")`, `v.defaultAction("key", runnable)` | Applied once per instance, then the player's. |
| Loading screen | `v.loadingScreen(LoadingRevision.X)` | Only for a revision Core's table does not put at your version (it already knows vanilla's). |
| Menu music | `v.addMenuMusic("modid:music/menu/track", 1)`, `v.removeMenuMusic("minecraft:music/menu/menu1")` | Sound names as sounds.json writes them; the `.ogg` sits under `sounds/`. |
| F3 keys | `v.debugKey(Keyboard.KEY_C, "debug.copy_location.help", () -> {...; return true;})`, `v.removeDebugKey(code)` | LWJGL 2 key codes. Register the action from client code. |
| Fog and sky | `FogPipeline.add("1.13", "underwater fog", step)` (client) | Steps run in version order. |

Core resolves everything lazily, so registration order between backports does not matter.

## How each piece resolves

* **Newest version**: `Presentation.newest()`, the highest registered version (`GameVersion`, compared
  number by number: 1.14 = 1.14.0 < 1.14.1 < 1.15).
* **Single-slot pieces** come from the newest installed version that has one: the panorama (unless Core's
  `presentation.panorama` setting says otherwise), the overlay.
* **Lists** start from 1.12.2's and apply every installed version's additions and removals, oldest first:
  splashes, menu music, F3 keys, defaults. A version's removal takes effect only when that version is
  installed.
* **Loading screen**: vanilla's revision at the newest version (`LoadingRevision.forVersion`): 1.13's text,
  1.14's chunk grid, 1.16's "Reading world data..." then the grid. 1.20.5, 1.21 and 1.21.9 are hooks
  (`client.LoadingScreens.register`); until a drawer is registered, Core draws 1.16's.
* **Options** show while the newest version is at or after their `since` (and before `until`), on their
  screen, or on its fallback when that screen does not exist at the newest version (`OptionScreen.resolve`):
  Accessibility (1.14) falls back to Chat, Mouse Settings (1.14) to Controls; Key Binds and Online (1.18)
  and Font (1.20.5) are not built yet and fall back to Controls and Options.
