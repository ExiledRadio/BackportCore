# Screen audit: 26.3 against the family

What a player sees with Game Drops Backport installed (the newest look is 26.3) next to every screen and GUI piece of
vanilla 26.3 that a 1.12.2 player can reach. Audit only: no code changed. 2026-11-22.

## How this was made

* **Vanilla 26.3**: every class under `net/minecraft/client/gui/screens/**` and `components/**` of the unobfuscated
  26.3 client jar (374 classes), plus `Gui`, `Hud`, `contextualbar/*`, the toasts, the boss bar, the chat, the tab
  list, the subtitles, the debug screen, tooltips and the recipe book. Each class's string constants (lang keys,
  sprite names) were dumped with `javap`, and the options screens' option lists read from their bytecode.
* **First version**: the first client jar whose `en_us.json` has the element's lang keys, over 1.13.2, 1.14.4,
  1.15.2, 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.6, 1.21, 1.21.3, 1.21.4, 1.21.5, 1.21.8,
  1.21.10, 1.21.11, 26.1, 26.2 and 26.3 (`G:\MCDownloads`, `.minecraft\versions`). A key first seen in, say, 1.18.2
  means "1.17.2 to 1.18.2"; where the wiki's exact release is well known it is given instead. "1.12.2" means the
  element already exists in 1.12.2.
* **Ours**: Backport Core (`presentation/**`, `settings/**`, `creative/**`, `gamerules/**`, `docs/PRESENTATION.md`),
  every backport's `lookfeel` package and every other `GuiScreen`/HUD hook in the ten repositories (main branches),
  the session notes of the look-and-feel streams, and the menu shots in `eclipse/screenshots/menu-*.png` (taken today
  at 17:15 by the self-test client with the Game Drops dev jar; those shots carry the self-test's dummy 1.14/1.16
  versions, so their title logo and "(1.16 test)" labels are not what an instance shows).
* **Not done**: no fresh in-game pass in the Backport Showcase instance. Where a status could only be read from code,
  the row says "(code)".

**Status**: **matches**, **partly**, **missing**, **n/a** (cannot exist on 1.12.2 or needs Mojang's online services).
**Size**: S (a day or less, one class), M (a few days, a screen or a patch), L (a week or more, or a new system).

Already built or in progress elsewhere, so not listed as gaps: the tabbed Create New World screen, 1.19.3's creative
tabs, the 26.2 title icon row, the pause menu icon row, the 26.1 Game Rules screen, the 1.21.11/26.3 Video Settings
rows and the Cleanroom borderless fullscreen.

---

## 1. Title and menus

| Element | 26.3 (first) | Ours | What differs | Size |
|---|---|---|---|---|
| **Multiplayer warning** (`SafetyScreen` / `WarningScreen`) | 26.3 layout (screen 1.15.2) | **partly, wrong layout** | See below. | S |
| Title screen buttons | 26.3 (layout 1.20, icon row 26.2) | partly | Singleplayer, Multiplayer, Minecraft Realms are 26.3's; Forge's Mods takes half of the Realms row, as Forge does on modern versions (keep). Realms reads "Realms" (1.12.2's `menu.online`), 26.3 "Minecraft Realms". | S |
| Copyright line as a link | 1.19.4 | missing | 26.3's "Copyright Mojang AB. Do not distribute!" is a `PlainTextButton` that opens Credits & Attribution (underlined on hover). Ours is plain text that does nothing. | S |
| Version line, bottom left | 1.12.2 | n/a | 26.3 "Minecraft 26.3 (Modded)"; Forge replaces it with its branding lines. Leave to Forge. | – |
| Logo, edition, Minceraft (P29) | 1.20 | matches | Needs a backport to register 1.20's three textures. The dev shot's yellow box is the self-test's dummy, not a fault; check once in the instance. | – |
| Splash, panorama, fade-in, overlay | 26.3 / 1.13 | matches | – | – |
| Accessibility onboarding (first start) | 1.19.4 | missing | 26.3 opens `AccessibilityOnboardingScreen` on the very first start instead of the title screen: "Welcome to Minecraft! Would you like to enable the Narrator or visit the Accessibility Settings?", a Narrator cycle button, "Accessibility Settings...", Continue, over the panorama; the narrator offers itself ("Press enter to enable the narrator"). Then the title screen fades in. | M |
| Credits & Attribution screen | 1.19.4 | missing | Title "Credits & Attribution"; Credits (opens the End credits), Attribution, Licenses (open minecraft.net links through Confirm Link), Done. Reached from Options and the copyright link. | S |
| Credits (`WinScreen`) | 1.12.2 (credits.json 1.17) | **partly (text matches, Core 1.4.5)** | `texts/credits.json` and `postcredits.txt` read as 26.3's `WinScreen` reads them when a backport supplies them (Game Drops does). 26.3's vignette, logo and 256-wide column are not drawn: 1.12.2's screen scrolls the text. | S |
| Startup screen (Mojang Studios) | 1.16 | matches | Core's `StartupScreen`. | – |
| Pause menu | 26.2 / 26.1 | matches | Icon row and World Options... done. The Mods row is Forge's (kept). Server Links (1.21) and Custom Options (1.21.8, server dialogs) need a server that sends them: n/a. | – |
| Death screen | 1.20.5 (title 1.16.2, buttons 1.15) | matches (code) | "You Died!"/"Game Over!", Score in yellow, Respawn and Title Screen with the quit question, buttons enabled after 20 ticks; Buzzy Bees' hardcore buttons. The draft-report icon is Player Reporting: n/a. | – |
| Confirm, alert and message screens | 1.21.6 layout (1.17, 1.19.1 before it) | **matches (Core 1.4.5)** | `MessageScreens`: 1.12.2's own `GuiYesNo` (exact class) laid out as 1.17, 1.19.1 or 1.21.6 (26.3's centered column); "Saving world" when leaving a singleplayer world (1.13 dirt, 1.20.5 and 1.21.11 framed boxes); `GuiScreenWorking` without its lone " 0%" (1.14). Trails & Tales' `Screens1206` still lays out its own world-open questions as 1.20.6's. | – |
| Menu background, list band, separators | 1.20.5 | matches | Core's `MenuBackground` (P31). | – |
| Header and footer layout | 1.20.5 | missing | Every 26.3 options-like screen puts its title in a 33-high header (centered at y 13) and its buttons in a 33-high footer, with the list or grid between the separators. 1.12.2's screens keep their own title heights and button rows; the shots show Options' title at y 20 with buttons far below it, and Language's footer two rows tall. Core's `docs/PRESENTATION.md` lists this as not covered. One shared layout helper, then screen by screen. | L |
| Widgets: buttons | 1.20.2 sprites (art 1.15) | matches | White labels (P12), the 1.15 backport's `widgets.png`. | – |
| Widgets: check box | 1.16, sprites 1.20.2 | **matches (Core 1.4.5)** | `Widgets`: 1.20.2's 17-square box from the `widget/checkbox` sprites (Game Drops supplies 26.3's), label beside it, framed white once clicked. | – |
| Widgets: slider, text field, scroll bar | 1.20.2 sprites | **matches (Core 1.4.5)** | Compared with 26.3's sprites: track, handle, text field and scroll bar are 1.12.2's drawing (with the family's `widgets.png`) pixel for pixel. What differed were states: the handle under the mouse (1.14, P62) and a focused field's white frame (1.16, P61). | – |
| Keyboard focus and navigation | 1.19.4 | n/a | Tab/arrow focus, the white focus frame. 1.12.2 screens have no focus; not worth a rewrite. | – |
| Tooltips (frame and background) | 1.20.5 sprites, per-item style 1.21.2 | partly | Same purple frame on 1.12.2. Missing: tooltips placed below or above a widget (`BelowOrAboveWidgetTooltipPositioner`, menu buttons) and items' own tooltip style (`tooltip_style` component; no vanilla item uses one). Core's options tooltips exist. | S |
| Disconnected screen | 1.21 (layout 1.20) | **matches (Core 1.4.5)** | 1.20's centered column (reason white), 1.21's 200-wide button; "Back to Server List" is the backports' vanilla text. Open Report Directory / Report to Server Admins need 1.21's disconnect reports and server links: n/a. | – |
| Connecting screen | 1.12.2 | matches | "Connecting to the server...", "Logging in...". Transfer (1.20.5) is n/a. | – |
| Out of memory screen | 1.20.5 | **matches (Core 1.4.5)** | Header, 300-wide framed message, Back to Title Screen and Quit Game in the footer; `outOfMemory.*` from Game Drops' vanilla text. | – |
| Ban notices, Realms, telemetry consent, Player Reporting, Social Interactions, Friends (26.2), Dialogs (1.21.6) | 1.16.4 to 26.2 | n/a | Mojang's online services or server-sent dialog packets 1.12.2 servers cannot send. The pause and title icons that would open them already answer with 26.3's "Not available in this version" toast. | – |

### The multiplayer warning, concretely

26.3's `WarningScreen.init` (javap): one vertical `LinearLayout` (8 apart) centered in the whole screen, holding

1. the title, bold, centered (`StringWidget`);
2. the message in a `FittingMultiLineTextWidget` (12 padding around it), `width - 100` wide and at most
   `height - 100` tall, shrunk to the text (`minimizeHeight`), drawn on the `widget/text_field` sprite (a one-pixel
   grey border around black) with the text inside, scrolling with a scroll bar only when it does not fit;
3. "Do not show this screen again" (`Checkbox`, centered);
4. the footer, Proceed and Back.

Ours (`MultiplayerWarningScreen`) is 1.16.5's: the title left-aligned at 25, 30, the text left-aligned at x 25,
wrapped at `width - 50`, 18 pixels a line (double-spaced), from y 70 down, the check box and buttons at fixed offsets
below. The user reports the text drawn on a black box nearly the full width of the screen: nothing in
`MultiplayerWarningScreen` draws a box, so whatever draws it there comes from elsewhere (not traced; check
`MenuBackground` and the Text Background "Everywhere" option first). The 26.3 box is narrower (50 each side), framed
in grey, as tall as the text with 4 pixels inside, single-spaced (9 pixels), and the whole block sits in the middle of
the screen.

## 2. Options

26.3's option lists below are read from each screen's bytecode, in order.

| Element | 26.3 (first) | Ours | What differs | Size |
|---|---|---|---|---|
| Options screen | 26.1 (Online 1.18, Telemetry 1.19.3, Credits 1.19.4) | partly | 26.3: FOV and **Online...** on the first row; then Skin Customization, Music & Sounds, Video Settings, Controls, Language, Chat Settings, Resource Packs, Accessibility Settings, **Telemetry Data**, **Credits & Attribution**; Done in the footer. Ours: Realms Notifications sits where Online... goes; no Telemetry Data (26.3 greys it out with the tooltip "Telemetry is disabled." when telemetry is off, which is what we can show) and no Credits & Attribution; 1.12.2's spacing (the shot: a 60-pixel gap under FOV). In a world 26.3 has no Difficulty (Game Drops removed it). | S |
| Online Options | 1.18 (Friends rows 26.2) | missing | 26.3: Realms (Realms Notifications), Servers (Allow Server Listing), Friends List (26.2) with In-game Notifications and Share Presence, Xbox Settings... Only Realms Notifications and Allow Server Listing mean anything here. Allow Server Listing needs the client-info packet field 1.12.2 lacks: keep it off the screen or show it as a client setting a Core server reads. | S |
| Accessibility Settings | 1.14, 26.3 order | partly | 26.3: Narrator, Show Subtitles, **High Contrast**, Menu Background Blurriness, Text Background Opacity, Background For Chat Only, Chat Text Opacity, Line Spacing, Chat Delay, **Notification Time**, View Bobbing, Distortion Effects, FOV Effects, Darkness Pulsing, Damage Tilt, Glint Speed, Glint Strength, Hide Lightning Flashes, Monochrome Logo, Panorama Scroll Speed, Hide Splash Texts, Narrator Hotkey, **Rotate With Minecarts**, High Contrast Block Outlines; Controls... beside Narrator; Accessibility Guide and Done. Missing: High Contrast (1.19.4, turns on a built-in resource pack of high-contrast GUI sprites; ours would need that pack in 1.12.2's texture layout), Notification Time (1.19.4, multiplies how long toasts stay; Core owns `SystemToasts`, 1.12.2's `GuiToast` needs a patch), Rotate With Minecarts (1.21.3, experimental minecart improvements; the camera turns with the cart). Order not checked in game. | M |
| Mouse Settings | 1.14, 26.3 list | partly | 26.3: Sensitivity, Scroll Sensitivity, Discrete Scrolling, Invert Mouse X, Invert Mouse Y, Allow Cursor Changes. Ours carries 1.12.2's Touchscreen, which 26.3's list does not have (Touchscreen's key is still in the lang file; check whether 26.3 adds it anywhere). Allow Cursor Changes is n/a (LWJGL 2). | S |
| Controls | 1.18, 26.3 list | partly | 26.3: Mouse Settings..., Key Binds..., Toggle Crouch, Toggle Sprint, Attack/Destroy, Use Item/Place Block, Auto-Jump, Sprint Window, **Quit Shortcuts** (26.3), **Right Click Emulation** (26.3, macOS only), Operator Items Tab. Missing the two 26.3 ones. Quit Shortcuts off means Alt + F4 no longer quits: LWJGL 2 reports Alt + F4 and the window's close button alike (`Display.isCloseRequested`), so it needs the key state as well. Right Click Emulation: Ctrl + left click as right click on macOS. | S |
| Key Binds | 1.18 | matches (code) | Core's. Not compared in game: 26.3 marks conflicting keys in red with a tooltip naming the other binding (1.19.4) and has "Reset Keys" in the footer. | S |
| Music & Sounds | 1.21.6 | partly | 26.3: Master, the categories in two columns (Music, Jukebox/Note Blocks, Weather, Blocks, Hostile, Friendly, Players, Ambient, Voice/Speech, UI), **Device** (1.18), Show Subtitles, **Directional Audio** (1.19), Music Frequency, Music Toast. Missing Device (pick the OpenAL output device; LWJGL 2's `ALC10` can list them, Paulscode must be restarted on the chosen one) and Directional Audio (OpenAL Soft's HRTF; LWJGL 2's OpenAL Soft is old and `ALC_SOFT_HRTF` must be asked for when the context is made). | M (Device), L (Directional Audio) |
| Skin Customization | 1.12.2 | matches | Same parts and Main Hand; layout is 1.12.2's grid (see header and footer). | – |
| Chat Settings | 26.3 list | partly | 26.3: Chat, Colors, Web Links, Prompt on Links, Chat Text Opacity, Text Background Opacity, Text Size, Line Spacing, Chat Delay, Width, Focused Height, Unfocused Height, Narrator, Command Suggestions, **Hide Matched Names**, Reduced Debug Info, **Only Show Secure Chat**, Save Unsent Chats. Hide Matched Names (1.16.4) and Only Show Secure Chat (1.19.1) belong to Social Interactions and chat signing: n/a. | – |
| Language | search 1.21.11, Font 1.20.5 | partly | 26.3: a search box ("Search...") under the title, the list, then "Font Settings..." and Done, and the accuracy warning. Ours: no search box; Force Unicode Font is still on this screen (26.3 moved it to Font Settings). Trails & Tales adds Viossa. | S |
| Font Settings | 1.20.5 | missing | Force Unicode Font and Japanese Glyph Variants (1.20.5). Japanese Glyph Variants switches unifont's Japanese forms; 1.12.2's unicode pages have no variants, so only Force Unicode Font would be on it. | S |
| Resource Packs | 1.16 (search 1.21.10) | partly | 26.3: "Select Resource Packs", the drop hint "Drag and drop files into this window to add packs", a search box, two lists "Available" and "Selected" with move arrows on the icons, "Open Pack Folder" and Done; dropping a file copies it in with a confirm screen. Ours: 1.12.2's lists headed "Available Resource Packs" / "Selected Resource Packs", "Open Resource Pack Folder" with "(Place resource pack files here)", no search, no drag and drop (LWJGL 2 has no file drop; n/a). | M |
| Video Settings | 26.3 | matches | Done (see the list at the top). Graphics API (Vulkan, 26.2) and RGSS are n/a. | – |
| Telemetry Data | 1.19.3 | n/a | Mojang's data collection. Only the disabled button on Options is worth showing. | – |
| Unsupported graphics warning | 1.16 | n/a | Fabulous! on a GPU that cannot do it; no Fabulous here. | – |

## 3. World selection and creation

| Element | 26.3 (first) | Ours | What differs | Size |
|---|---|---|---|---|
| Select World | 1.13 (search), 26.3 wording | partly | 26.3: title, a search box under it (1.13; the hint "Search..." from 1.21.11), the list with the play arrow on hover over the icon (`world_list/join`, warnings for newer or experimental worlds), then Play Selected World, Create New World; Edit, Delete, Re-Create, **Back**. Ours: no search (Core's notes: the screen's list is private, so it means replacing the screen), "Cancel" for Back, no hover play arrow. Trails & Tales lists corrupted worlds and opens through 1.20.6's checks. | M |
| Edit World | 1.13 (Reset Icon 1.13, Optimize 1.13) | matches | Core's `WorldBackups`, `OptimizeWorldScreens`. Not compared in game: 26.3 has Reset Icon, Open World Folder, Make Backup, Open Backups Folder, Optimize World, then Save and Cancel, in one column. | S |
| Create New World | 1.19.4 tabs | in progress elsewhere | – | – |
| Experiments, Data Packs, Game Rules | 1.19.4 / 1.13 / 1.16 | n/a / n/a / matches | 1.12.2 has neither experiments nor data packs. | – |
| Superflat customize and presets | 1.12.2 (layout 1.16) | partly (code) | 26.3's layer list (block icon in a slot sprite, Height, Layer Material), Remove Layer, Presets with the share box; 1.12.2's are the same screens in 1.12.2's look. Matches in content. | S |
| Single Biome customize (`CreateBuffetWorldScreen`) | 1.16 | n/a | 1.12.2's Single Biome world type uses its own customize screen; check if any backport touches it. | – |
| Recover world data | 1.20.5 | matches | Trails & Tales' `RecoverWorldDataScreen`. | – |
| Backup confirm, symlink warning, data pack failure | 1.13 / 1.20 / 1.16 | n/a | No data packs, no symlink check on 1.12.2. Backup confirm (before opening an older world) has no trigger: every world is 1.12.2's. | – |
| World loading screen | 1.21.9 | matches | Tricky Trials' 1.21.9 drawer; 26.3's `LevelLoadingScreen` adds the dimension backgrounds (Trails & Tales' `DimensionScreen`). | – |

## 4. Multiplayer

| Element | 26.3 (first) | Ours | What differs | Size |
|---|---|---|---|---|
| Server list | 1.12.2 (sprites 1.20.2) | partly | 26.3: Join Server, Direct Connection, Add Server; Edit, Delete, Refresh, **Back**. Ours: "Direct Connect" and "Cancel" (1.12.2's words). Entries look alike (ping bars, move arrows, join arrow). The wording belongs in a backport's vanilla text. | S |
| Add/Edit Server | 1.12.2 | matches | Name, address, Server Resource Packs. | – |
| Direct Connection | 1.12.2 | matches | Wording "Direct Connection". | S |
| Multiplayer warning | 1.15.2 | partly | Section 1. | S |
| LAN scanning row | 1.12.2 | matches | 26.3 animates the dots the same way. | – |
| Open to LAN | 1.12.2, replaced 26.1 | matches | Game Drops' World Options holds it. | – |
| Code of Conduct, Restrictions, Server Reconfigure, Server Links | 1.20.5 to 1.21 | n/a | Need 1.20.5+ server packets. | – |

## 5. In-game HUD

| Element | 26.3 (first) | Ours | What differs | Size |
|---|---|---|---|---|
| Hearts, armor, food, air | 1.20.2 sprites; frozen 1.17; air bubbles 1.21.4 | partly | Tricky Trials draws 1.21.4's air bubbles. Frozen hearts are Caves & Cliffs' (powder snow, code). Nothing else differs in art except where `icons.png` comes from the texture layers. | – |
| Experience bar and level | 1.12.2 | matches | – | – |
| Locator bar | 1.21.6 | matches | Tricky Trials' `LocatorBarHud`. | – |
| Horse jump bar, camel cooldown | 1.12.2 / 1.20 | matches (code) | Trails & Tales' camel. | – |
| Hotbar, offhand, attack indicator, crosshair | 1.12.2 | matches | – | – |
| Status effect icons (top right) | 1.12.2 (sprites 1.20.2) | matches | Ambient border and the blinking at the end are 1.12.2's. | – |
| Boss bar | 1.12.2 | matches | Same six-color sprites, notches, layout. | – |
| Tab list | 1.12.2 (sprites 1.20.2) | matches (code) | Faces, ping bars, the health objective's hearts as in 1.12.2. | – |
| Subtitles | 1.12.2 (Background for chat only 1.14) | matches | Text Background "Everywhere" reaches subtitles (Core 1.4.5, P59). | – |
| Autosave indicator | 1.18 | matches (code) | Caves & Cliffs' option. | – |
| Title and action bar text, held item name, overlay message | 1.12.2 | matches | – | – |
| Spectator menu and hotbar | 1.12.2 | matches | 26.3 key "Spectator Hotbar" (1.21.10) as a key bind is not checked. | S |
| Demo, debug-day-count overlays | – | n/a | Demo mode only. | – |
| IME pre-edit overlay | 1.20.5 | n/a | LWJGL 2 has no IME pre-edit events. | – |

## 6. Inventories and containers

| Element | 26.3 (first) | Ours | What differs | Size |
|---|---|---|---|---|
| Creative inventory | 1.19.3 tabs, 26.3 order | matches | Done. 26.3's "Operator Utilities" behind Operator Items Tab, as Core has it. | – |
| Survival inventory | 1.12.2 (effects beside it 1.18, fitted to their text 1.21.11; sprites 1.20.2) | matches | Core 1.4.5 (`InventoryEffects`, P57): the column right of the inventory, compact when names do not fit, no shift; creative too. With the recipe book open 1.12.2 hides the effects; 26.3 still draws them when there is room. | – |
| Slot highlight | 1.21.2 | matches | P23 with the sprites supplied. | – |
| Recipe book | 1.12.2 (search hint 1.16, box 1.20.5, 1.21.2 rework) | partly | Core 1.4.5 (`RecipeBookLook`, P58): "Search..." and 1.20.5's box, in the crafting and furnace books. 26.3's crafting book otherwise matches 1.12.2's (tabs, filter, arrows, ghost recipe colors and cycle). 1.21.2's magnifier click too. Missing: 1.21.2's displays (several outputs per button), which 1.12.2's recipes cannot describe. | S |
| Furnace, blast furnace, smoker | 1.13 / 1.14 | matches | Core's book, V&P's `GuiFurnaceVariant`. | – |
| Crafting table, chest, hopper, dispenser, dropper, shulker box | 1.12.2 | matches | Textures from the layers. | – |
| Crafter | 1.21 | matches | Tricky Trials. | – |
| Anvil | 1.12.2 (sprites 1.20.2) | matches | The red X over the result when it cannot be made, "Too Expensive!" as before. | – |
| Enchanting table | 1.12.2 | matches | – | – |
| Beacon | 1.12.2 | matches | – | – |
| Brewing stand | 1.12.2 | matches | – | – |
| Grindstone, loom, cartography table, stonecutter, lectern, smithing | 1.14 / 1.16 / 1.20 | matches | V&P, Nether Update, Trails & Tales. | – |
| Villager trading | 1.14 | matches | V&P's `GuiVillagerMerchant`. | – |
| Horse, donkey, llama | 1.12.2 (slots 1.20.2) | matches (code) | – | – |
| Nautilus | 26.2 | matches | Tricky Trials' `GuiNautilus`. | – |
| Book and quill, written book | 1.12.2 (multi-line edit 1.15, page arrows 1.20.2, sign screen 1.21.6) | **matches (V&P 2.7.4)** | V&P's `GuiBookEditor` already had mouse selection, Shift/drag, Ctrl + A/C/X/V (1.14's editor; the audit misread it). From 1.21.6 signing is 26.3's own screen (`GuiBookSign`). | – |
| Sign, hanging sign | 1.20 (flat 26.2) | **matches** | Trails & Tales draws 26.2's flat sign (done in 1.0.2); 1.12.2's oak and other mods' vanilla woods now find their texture at vanilla's path, which Game Drops supplies. | – |
| Command block, structure block | 1.12.2 (layout 1.13) | partly | 1.13's command block screen has suggestions in the command box; ours is 1.12.2's with no suggestions. Structure block layout unchanged since 1.13 aside from Strict (1.21). | M |
| Jigsaw, test block, test instance | 1.14 / 1.21.5 | n/a | No jigsaw blocks or test framework on 1.12.2 (Core's jigsaw is generation only). | – |
| Statistics | 1.12.2 (sprites 1.20.2) | matches (code) | 26.3's tabs (General, Items, Mobs) are 1.12.2's. | – |
| Advancements | 1.12.2 | matches | Backgrounds from the 1.15 layer. | – |
| Bundle tooltip | 1.21.2 | matches | Tricky Trials. | – |
| Item rarity color | 1.21.2 | matches | Tricky Trials. | – |
| Game mode switcher (F3 + F4) | 1.16 | matches | Nether Update. | – |

## 7. Chat and commands

| Element | 26.3 (first) | Ours | What differs | Size |
|---|---|---|---|---|
| Chat box and messages | 1.12.2, 1.16 options | matches | Core's `ChatRenderer` (P13, P14). | – |
| Command suggestions | 1.13 | partly | Core's `ChatSuggestions`. Missing (Core's notes): the red highlight of a wrong argument, colored arguments, the argument usage at the cursor, suggestion tooltips. 1.12.2 commands do not describe their arguments, so only partly possible. | L |
| Chat drafts, portal keeps chat open | 1.21.9 | matches | Tricky Trials' `ChatDrafts`. | – |
| Chat input hint ("Input message and press Enter to send") | 1.17 | n/a | Narration only; nothing is drawn. | – |
| Queued messages ("[+N pending lines]") | 1.16 | matches | – | – |
| Message indicators, deleted marker, signature tags | 1.19.1 | n/a | Chat signing and reporting. | – |
| Copy to clipboard clicks | 1.15 | matches | Core's `CopyClick`. | – |
| Link confirmation | 1.12.2 | matches | – | – |
| In-bed chat | 1.12.2 | matches | 26.3: "Leave Bed" button under the chat, as 1.12.2. | – |

## 8. Toasts

| Element | 26.3 (first) | Ours | What differs | Size |
|---|---|---|---|---|
| Advancement, recipe, tutorial toasts | 1.12.2 (sprites 1.20.2) | matches (code) | Art from the texture layers; 160 wide as in 1.12.2. Notification Time (below) would change how long they stay. | – |
| System toast | 26.3 | matches | Core's `SystemToasts` is the one drawer (Core 1.4.5): 26.3's ids and five slots, one line before 26.2. Trails & Tales' toasts use it with Core 1.4.5 (its own drawer stays for Core 1.4.4). | – |
| Now Playing (Music Toast) | 1.21.6 | matches | Core. | – |
| Narrator toast | 1.13 | matches | 1.12.2 shows it. | – |
| Smelting recipe toast | 1.13 | matches | Core's `SmeltingToast`. | – |
| Friend toast | 26.2 | n/a | Friends. | – |
| Notification Time | 1.19.4 | missing | Section 2, Accessibility. | M |

## 9. Accessibility

| Element | 26.3 (first) | Ours | What differs | Size |
|---|---|---|---|---|
| Narrator for screens (reads buttons on hover and focus) | 1.14 | missing | 26.3 narrates the screen title, the hovered or focused widget and its usage ("Press Enter to activate"); 1.12.2's narrator reads only chat. Every Core screen would need narration text; hover narration can be done centrally from the button under the mouse. | L |
| High Contrast | 1.19.4 | missing | Section 2. A resource pack of 26.3's high-contrast GUI art in 1.12.2's sheets. | M |
| Accessibility onboarding | 1.19.4 | missing | Section 1. | M |
| Accessibility Guide link | 1.16.4 | matches | – | – |
| High Contrast Block Outlines | 1.21.2 | matches | Tricky Trials. | – |
| Monochrome logo, Panorama Scroll Speed, Hide Splash Texts, Narrator Hotkey | 1.17 to 1.20.3 | matches | Core. | – |
| Darkness Pulsing, Glint Speed/Strength, Damage Tilt, FOV/Distortion Effects, Hide Lightning Flashes | 1.16 to 1.19.4 | matches | Wild Update, Nether Update, Caves & Cliffs. | – |
| Rotate With Minecarts | 1.21.3 | missing | Section 2. | S |

## 10. Debug

| Element | 26.3 (first) | Ours | What differs | Size |
|---|---|---|---|---|
| F3 screen parts, Debug Options (F3 + F6), profiles | 1.21.9 | partly | Core's. Core 1.4.5 adds tps, looking_at_fluid, looking_at_entity, sound_mood (1.21.9), heightmap (1.21.11), detailed_memory (26.1) and 1.21.9's two-column arrangement. Missing (no 1.12.2 source): gpu_utilization, simple_performance_impactors, chunk_generation_stats, sound_cache, entity and block tags; the cave mood needs Nether Update to call `DebugParts.setMood`. C&C's 1.18 noise rows are not done. | S |
| Overlay GUI scale | 26.3 | matches | Core's `DebugScale`. | – |
| Charts F3 + 1/2/3 (profiler pie, FPS, TPS, ping, bandwidth) | 1.20.2 | matches | Core 1.4.5 (`FrameCharts`, P60): 1.20.2's FPS and TPS charts on F3 + 2 in place of the lagometer, and the pie at the GUI scale above the charts. The TPS chart has no task or "other" share (1.12.2 does not measure them) and none for a remote server. | – |
| F3 + Q help, F3 key binds | 1.21.11 | matches | – | – |
| Game mode switcher | 1.16 | matches | Nether Update. | – |
| Crash screen, "Pausing on lost focus" etc. | – | n/a | – | – |

---

## Prioritized gaps

Ordered by how often a player sees them, then by size. "S" items first within a level.

**Seen every session**

1. **Multiplayer warning layout** (S): 26.3's centered column, framed text box `width - 100`, single spacing, check box
   and buttons centered; find what draws the full-width black box.
2. **Header and footer layout** on the options-like screens (L, but the biggest visible difference): a shared 33/33
   layout for Options, Skin Customization, Music & Sounds, Chat, Accessibility, Controls, Key Binds, Language, Resource
   Packs, Select World, Play Multiplayer, Edit World. Start with Options, Language and Resource Packs.
3. **Options screen** (S): Online... in place of Realms Notifications (with an Online Options screen holding it), a
   disabled Telemetry Data button ("Telemetry is disabled."), Credits & Attribution, 26.3's spacing.
4. **26.3 wording on the menus** (S): Back for Cancel on Select World and Play Multiplayer, Direct Connection, Minecraft
   Realms, Open Pack Folder, Available/Selected. A backport's vanilla text, no code.
5. **Select World search box** (M): needs Core to own the world list screen.
6. **Resource Packs** (M): 26.3's two lists with move arrows, headers, search box.
7. **Language search and Font Settings** (S): search box; Force Unicode Font onto a Font Settings screen.

**Seen often**

8. Done in Core 1.4.5: **Survival inventory effects** (M): 26.3's column right of the inventory, without shifting it.
9. Done in Core 1.4.5: **One system toast drawer** (S): Trails & Tales' `SystemToast1205` folded into Core's `SystemToasts`.
10. Done in Core 1.4.5 as far as 1.12.2's recipes allow: **Recipe book** (M): search hint, box, magnifier (section 6).
11. Done in Core 1.4.5: **F3 + 2 charts** (M): 1.20.2's FPS and TPS charts in place of the lagometer.
12. **Mouse Settings** (S): drop Touchscreen if 26.3 really has none (check first). *Done in 1.4.5: 26.2 removed it.*
13. **Copyright link and Credits & Attribution** (S).

**Seen sometimes**

14. **Accessibility onboarding** on the first start (M). *Done in 1.4.5.*
15. **Notification Time** (M), **High Contrast** (M), **Rotate With Minecarts** (S). *Done in 1.4.5 (P50; Game Drops'
    pack; Rotate With Minecarts greyed out as vanilla's without Minecart Improvements).*
16. **Quit Shortcuts** and **Right Click Emulation** on Controls (S). *Done in 1.4.5 (P51).*
17. ~~**Confirm and message screens**~~: done in Core 1.4.5 (`MessageScreens`, 26.3's column from 1.21.6).
18. ~~**Disconnected screen**, **Out of memory**~~: done in Core 1.4.5 (wording from Game Drops' vanilla text).
19. ~~**Sign edit screen**, **book editor**~~: the flat sign was in Trails & Tales 1.0.2 (vanilla woods' textures now from Game Drops); V&P 2.7.4 signs on 26.3's own
20. ~~**Check box and slider/text field/scroll bar**~~: compared with 26.3's sprites; done in Core 1.4.5 (P61, P62, `Widgets`).
21. Done in Core 1.4.5: **Subtitle text background** (S).
22. **Music & Sounds: Device** (M), **Directional Audio** (L). *Done in 1.4.5 (P52); Directional Audio only where
    OpenAL has HRTF (Cleanroom), not LWJGL 2.*
23. Done in Core 1.4.5: **Debug parts** 1.12.2 has no lines for, and 1.21.9's column arrangement (M).
24. **Command suggestions**: argument highlighting and usage at the cursor (L, only partly possible).
25. **Narrator for screens** (L).
26. ~~**End credits**~~: done in Core 1.4.5 (`Credits`, the file from Game Drops).

Not applicable to 1.12.2 (no action): Realms, Friends, Social Interactions, Player Reporting, telemetry, ban notices,
chat signing, server-sent dialogs and links, code of conduct, transfers, experiments, data packs, jigsaw and test
blocks, keyboard focus navigation, IME pre-edit, Allow Cursor Changes, Vulkan, Fabulous!.
