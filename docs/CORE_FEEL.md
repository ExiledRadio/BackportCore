# Version feel in Core (1.4.7)

Each backport used to bring its version's look and feel itself, so a backport installed alone lacked every
earlier version's (Caves & Cliffs alone: no 1.13 command suggestions, no 1.16 F3 + F4). Since 1.4.7 Core owns
the pieces that are not tied to one backport's content and applies them up to the newest installed version
(`Presentation.core(version)`, `presentation/versions/LookNNN`). Music and panoramas stay in the backports.

Option ids are the backports' own (`updateaquaticbackport:auto_suggestions` ...), so saved values carry over.

## Older backport jars

A new Core with an older backport jar that still registers its copy:

- an option with the id of one Core owns follows Core's (`PresentationOption.mirror`): one button, one value;
- an F3 text, fog or music step of the same version and name is left out (`OrderedSteps`);
- F3 keys, splashes, assets, wording and the other single-slot pieces resolve to the same thing;
- event handlers and class patches: Core leaves its own copy off while the older jar's class is on the class
  path (`LegacyCopies`).

## What moved

| Backport | Moved to Core | Stays in the backport |
|---|---|---|
| Update Aquatic (1.13) | Biome Blend (+ its patch), Command Suggestions, F3 + C, F3 + I, 1.13 F3 text and packet rates, furnace recipe book (vanilla furnace), recipe_book/recipe_button sprites, splashes, snooper default, wording | panorama + overlay, underwater fog (UA water), underwater music |
| Village & Pillage (1.14) | Scroll Sensitivity, Discrete Scrolling (+ hotbar scroll), F3 + N (1.14.1), F3 + Esc, F3 server chunk line, Accessibility icon, mojang.png startup logo, splashes, wording | panorama + overlay; the 1.14.x version declarations |
| Buzzy Bees (1.15) | Sneak/Sprint Hold/Toggle (+ toggle keys), widgets.png, checkbox.png, multiplayer warning, window title, F3 chunk-updates text, startup logo, Accessibility icon, splashes, wording | panorama + overlay |
| Nether Update (1.16) | Entity Distance, Distortion Effects, FOV Effects (+ nausea overlay and patches), F3 + F4 switcher, F3 + N, F3 text, Create New World redirect, title logo, startup logo, widgets, checkbox, Accessibility icon, multiplayer warning, splashes, wording | panorama + overlay, music; the 1.16.x version declarations |
| Caves & Cliffs (1.17, 1.18) | Hide Lightning Flashes, Autosave Indicator, clouds at 192, 1.18.1 fog, F3 block and chunk rows, gamma default, title logo, startup logo, widgets, checkbox, Accessibility icon, multiplayer warning, splashes, wording | panoramas + overlay, music |
| Wild Update (1.19) | Damage Tilt, Glint Speed, Glint Strength (+ patches), F3 allocation rate, F3 + F removal, 1.19.3 creative tab sprites, auto-jump default, title logo, startup logo, widgets, checkbox, Accessibility icon, multiplayer warning, splashes, wording | panorama + overlay, music, Darkness Pulsing (the Darkness effect is Wild Update's) |
| Trails & Tales (1.20) | Menu Background Blur, 1.20.5 loading screen and dimension screens, F3 + 1 and F3 + 2, Back buttons, Ctrl + scroll GUI Scale, window icon, shield blocking pose, title logos (Minceraft, edition), menu_background.png, splashes, wording | panorama + overlay, music and its pack, the brush pose (the brush is Trails & Tales'), the pick-block patch if it is tied to its blocks |
| Tricky Trials (1.21) | Reduce FPS When, High Contrast Block Outlines, Invert Mouse X, Sprint Window, Save Unsent Chats (+ their handlers), air bubbles (+ pop sound), rarity tooltips, 1.21.x fog steps, loading screen, F3 entity line, slot highlight and toast sprites, startup logo, splashes, wording | panorama + overlay, music, spawner sounds |
| Game Drops (26.x) | nothing: it is the newest backport, so its pieces apply only with it installed anyway | everything |

Final details per backport are in the CHANGELOG of 1.4.7 and each backport's release.
