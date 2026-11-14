# Presentation: what to look for in game

The self-tests check the logic and that every hook runs; only you can judge the look. No backport registers
its version yet (each one's "look and feel" stream does that), so there are two ways to look:

*   **Now, in the dev client**: `gradlew runClient -Pselftest` in this repository. The self-test mod registers
    a dummy "1.14" (splashes) and "1.16" (a panorama of six flat colored squares, numbered 0 to 5, and a few
    test options). The run checks itself and closes after about 30 seconds; to look around, comment out the
    `mc.shutdown()` in `selftest/ClientPresentationTests`.
*   **Later, in a real instance**: once a backport's stream registers its version, with that backport and
    Backport Core 1.1.0 installed.

## Backport Core alone (no backport installed)

Everything must look exactly as 1.12.2:

- [ ] The title screen's panorama is 1.12.2's, blurred, nodding and turning as before; the white fade at the
      top and the dark fade at the bottom are there.
- [ ] Splashes are 1.12.2's own.
- [ ] Options still has "Snooper Settings..."; Controls still has Invert Mouse, Sensitivity, Touchscreen and
      Auto-Jump above the key list.
- [ ] Loading a world shows "Loading world" and "Building terrain" on dirt, no grid.
- [ ] F3+Q lists 1.12.2's ten key lines; F3+F changes the render distance.

## Title screen (any backport installed)

- [ ] The panorama is the newest installed backport's: sharp, not blurred, turning slowly all the way
      round (a full turn takes about three minutes) and nodding gently, as in 1.13 and later.
- [ ] Faces meet without seams or a wrong face (front, right, back, left, top, bottom). If one face is
      rotated or swapped, note which: the face order or orientation is then wrong.
- [ ] The panorama fills the screen at any window shape; resize the window and check nothing stretches.
- [ ] With a backport that ships `panorama_overlay.png`, its gradient replaces 1.12.2's two fades; without
      one, 1.12.2's fades stay.
- [ ] Opening the title screen again (leaving a world) restarts the turn, as vanilla's does.
- [ ] `presentation.panorama` in `config/backportcore.cfg`: `1.12.2` shows 1.12.2's own faces drawn the new
      way; an installed version such as `1.14` shows that one; `ROTATE` shows a different one on each start.
- [ ] With a 1.19.3 or later backport: Accessibility Settings has Panorama Scroll Speed, and 0% stops the turn.
- [ ] Splashes include the installed versions' additions (for 1.14: "Rainbow turtle?", your name in capitals
      followed by "IS YOU"), and not their removals ("Made by Notch!" is gone with 1.14 installed).
- [ ] With a 1.20.3 or later backport: Hide Splash Texts in Accessibility hides the yellow text.
- [ ] Title music: tracks a backport added play now and then, removed ones never do; the pause between
      tracks feels as before.

## Options screens (a 1.14 or later backport installed)

- [ ] Options: "Accessibility Settings..." sits where "Snooper Settings..." was.
- [ ] Accessibility Settings: title at the top, Narrator, Subtitles, Chat Text Opacity (or Chat Opacity),
      Auto-Jump in two columns, the backports' accessibility options among them in their version's order,
      Done below. Narrator is greyed out on a system without a narrator, as in Chat Settings.
- [ ] Controls: "Mouse Settings..." and Auto-Jump in the first row; the key list below is unchanged.
- [ ] Mouse Settings: Sensitivity, Invert Mouse, Touchscreen and the backports' mouse options; the slider
      drags smoothly; Done returns to Controls.
- [ ] A backport's Video option sits in the Video Settings list where it names (for example after Clouds),
      and scrolls with the list.
- [ ] With OptiFine: Video Settings has Core's layout with an OptiFine section (Shaders..., Quality..., Details...,
      Performance..., Animations..., Other...); each opens OptiFine's screen and its Done comes back; Texture
      Filtering, Anisotropic Filtering, Show Vignette, Chunk Fade Time and Biome Blend are not on it; OptiFine's
      tooltips show over its settings; nothing in the log about Video Settings.
- [ ] Chat, Music and Sounds, Skin Customization: added options continue the two columns and Done moves
      down with them; nothing overlaps at GUI scale Auto and at 1, 2 and 3, in a small window too.
- [ ] Every toggle, slider and cycle button flips, drags or steps with the click sound vanilla's make, and
      keeps its value after restarting the game (`config/backportcore/options.txt`).

## World-loading screen

Create a new world and load an existing one, each with these installed:

- [ ] 1.13 backport only: "Loading world", then "Preparing spawn area: N%" counting up to 100.
- [ ] 1.14 or 1.15 backport: dirt, a percentage above the middle, and below it a square grid of tiny cells
      that fills in from black: dark blue (generated), green (decorated), light grey, then white in the
      middle. The finished grid has a white core with a thin colored ring and a black border, as 1.14.1's.
- [ ] 1.16 backport: first "Reading world data..." near the top for a moment (it can be very short on a
      fast machine), then the same grid.
- [ ] Loading an existing world: the grid turns white almost at once.
- [ ] Leaving a world ("Saving world") and any other mod's progress screens look as before.

## Versioned defaults

- [ ] On the first start with a backport that changed a default (for example Auto-Jump off from 1.19.4), the
      setting has the new value. Change it back, restart: your choice stays.
      `config/backportcore/applied_defaults.txt` lists what was applied; deleting a line applies it again.

## F3 keys

- [ ] F3+Q lists the newest installed version's keys in alphabetical order, with Esc and F4 after the
      letters when a backport has them.
- [ ] A key a backport added does its thing (for 1.13: F3+C copies your location); a key a later version
      removed does nothing.
- [ ] Holding F3+C for ten seconds still crashes the game on purpose, as in 1.12.2.

## Fog

- [ ] With the backports that add fog steps: underwater, nether and overworld fog look as in their version,
      and nothing flickers when walking into and out of water or lava.
