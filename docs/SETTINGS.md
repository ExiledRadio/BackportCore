# Settings screen

One screen for every backport's settings: Mod Options › Backport Core › Config. Every installed family mod is
on it with no code. Each backport keeps its own row in Forge's mod list (so the list's search finds it), and its
Config button opens this screen on its own page.

Code: `settings/` (common) and `settings/client/`.

## What a backport gets for free

Core reads the `@Config` classes Forge already knows (`ConfigManager.getModConfigClasses`) and Forge's own
`Property` objects. Nothing about the `.cfg` file changes: same keys, same comments, same values.

- Each category is a feature row. Its `enabled` boolean is the row's on/off switch.
- A category with one setting and no `enabled` shows that setting on the row itself.
- Other settings sit under the row. Features view: rows closed. All: rows open. Changed: only non-default values.
- `@Config.RequiresMcRestart` shows a Restart badge. `@Config.RequiresWorldRestart` shows Rejoin and locks the
  setting while a world is open.
- `@Config.RangeInt` with a span of 1000 or less is a slider. Other numbers and strings are text fields, red
  while invalid.
- Enums cycle (Shift cycles back). Arrays open the list editor.
- Names come from the field's lang key (Forge's `@Config.LangKey`, else `modid.category.field`). Without one,
  the field name is made readable: `rebuildUntradedVillagers` shows as "Rebuild untraded villagers".
- The row line is `<langKey>.short` when the mod writes one. Descriptions come from `<langKey>.tooltip` (what `build_langs.py config` writes), else the `@Config.Comment`.
  The row shows the first sentence; the info line shows two lines; Shift shows all of it.
- An enum value's words come from `<langKey>.<value in lower case>`, else the constant made readable.

## Annotations (optional, `settings/Setting.java`)

| Annotation | On | Does |
|---|---|---|
| `@Setting.Feature(icon = "minecraft:kelp")` | category field | The row's item icon (`id` or `id@meta`). |
| `@Setting.Feature(toggle = "demandPricing")` | category field | Another boolean is the row's switch. |
| `@Setting.Client` | setting | Client badge. Each player sets it; on a server it stays local. |
| `@Setting.Server` | setting | Server badge. The default for an unmarked setting. |
| `@Setting.NewChunks` | setting | New chunks badge: only affects terrain generated later. |
| `@Setting.Vanilla("2")` | setting | Vanilla's value, where the default differs. Shown in the info line and on sliders. |
| `@Setting.ListOf(Setting.Kind.BLOCK)` | array | The list editor checks ids against that registry and suggests matches. Without it, Core guesses from the defaults. |

A mod outside the family registers with `BackportSettings.register(modid)` (or names its classes) in
pre-init or init.

## Look & Feel (Core 1.4.0; part of the Family page since 1.4.5)

On the Family page (Backport Core's), after Core's own settings; Core's look switches show only there
(`LookFeelPage.familyPage()`). It holds no settings of its own: each row is a setting of another page (the same
Forge property), listed when it is Core's `lookAndFeel`, `textures.modernTextures` or `presentation.panorama`, carries
`@Setting.Look(section)`, or is in `LookFeelPage.KNOWN`. The master switch heads it; the sections start open. A look
setting is a Client setting wherever it shows. A row shows the versions its part comes from; with the master switch
off, the others are dimmed and keep their values.

## Update notices (Core 1.4.5)

`settings/Updates.newer(modid)`: Forge's update check first (each family mod's `@Mod(updateJSON)`, the files in this
repo's `updates/<modid>.json`, read once at startup unless Forge's `version_checking` config is off); when it has no
answer, `Family.Member.latest`. The Family page lists every outdated family mod, a mod's page its own, and the side list
shows "Update: x". A row opens the mod's CurseForge page.

**On every release of a family mod:** set its version in `updates/<modid>.json` (both promos and a line under
`1.12.2`) and push Core's `main`, then set `latest` in `Family.MEMBERS` for the next Core build.

## Servers

- Single player and LAN host: everything edits the local files, on the client thread, as Forge's own config
  screen does.
- On a remote server, Server settings show the server's values. The client asks when the screen opens
  (channel `backportcore_set`). Operators (permission level 2, as `/gamerule`) can change them: the server checks
  each value, saves its `.cfg`, fires the config-changed events and answers in chat. Every change is logged with
  the player's name. A Restart setting changed this way applies when the server restarts.
- A server without Core never answers; the screen says so after 3 seconds and locks Server settings.

## Saving

Changes are staged until Done (or Esc). Core sets Forge's properties, calls `ConfigManager.sync`, then posts
`OnConfigChangedEvent` and `PostConfigChangedEvent`, so each mod's existing handler still runs. The title and
pause screens say "Restart the game to apply N changes." while any saved Restart setting differs from the value
the game started with.

## Tests

- `gradlew runServer -Pselftest`: `SettingsTests` (page building, value checks, apply, non-operator refused).
- `BACKPORTCORE_SETTINGSSHOTS=1 gradlew runClient -Pselftest`: opens the screen in code and saves
  `eclipse/screenshots/settings-*.png`. Put backport `-dev` jars in `eclipse/mods` for their pages.
