# Bytecode patches

The family's standing rule (2026-11-07): closest to vanilla, and bytecode patching is allowed where vanilla
parity needs it. Backport Core has its own small coremod, `asm/BackportCorePlugin` (no MixinBooter), with
one transformer per patch. Each patch below says what it changes, why no event or API could do it, how it
fails safe, and how it is tested.

**How the coremod is found.** The release jar's manifest names it (`FMLCorePlugin:
com.exiledradio.backportcore.asm.BackportCorePlugin`, `FMLCorePluginContainsFMLMod: true`), so FML loads
the plugin and then the jar as an ordinary mod. The dev jar carries the same two lines, which is how FG2's
GradleStart finds the coremod on a backport's `runServer`/`runClient` classpath. This repository's own run
tasks name it with `-Dfml.coreMods.load`.

**Shared rules.**

*   Sorting index 1500: after FML's deobfuscation (so methods carry SRG names in a real game and MCP names in
    the dev environment; every transformer matches both) and after Fluidlogged API's transformers (1401),
    so these see each method as Fluidlogged API leaves it. None of the call sites patched here is one
    Fluidlogged API changes; each patch was checked with Fluidlogged API's transformers in the chain.
*   A patch only adds calls into the library's hook classes (`pose/MovementHooks`, `pose/client/ClientHooks`,
    `presentation/client/PresentationHooks`, `worldgen/WorldgenHooks`, `render/client/ChunkRebuildHooks`, `creative/client/CreativeScreenHooks`,
    and from P50 `presentation/client/NotificationTime`, `QuitShortcuts` and `SoundDevices`).
    Each hook does exactly what vanilla 1.12.2 does (returns vanilla's value, or nothing) until a backport
    has registered rules with `pose.Poses` and they are on, or (P6, P7) registered a presentation version
    with `presentation.Presentation`, or (P8, P9) a backport of 1.13 or later is installed, or (P10, P11) one of 1.16. With no backport
    installed, the patched game plays as vanilla.
*   P6, P7 and P9 only change the owner of an existing call (an instance call becomes a static call with the
    same arguments); the hook calls the original method when it keeps vanilla's behavior. P8, P10 and P11 replace a
    whole method body with one call, and their hooks run 1.12.2's own code line for line.
*   A transformer that does not find the exact shape it expects changes nothing, logs
    `P<n> ...: <why>; patch skipped, vanilla behavior kept.`, and records why (`asm.Asm.state`). It never
    half-patches a method. A patched class logs `P<n> ...: patched.`
*   Only straight-line code is added (no new branches or locals), so the class keeps its stack map frames;
    the writer only recomputes maximum stack sizes.
*   Each patch is read against 1.14.4's decompiled code for its numbers; the hooks' Javadoc names the 1.14.4
    method each part follows.

**Tested** (`gradlew runServer -Pselftest`, `selftest/PatchTests`): P1 reports itself applied when vanilla
loads `EntityLivingBase` in the dev server; the coremod is registered with the class loader; the bytes of
all target classes, put through the dev class loader's whole transformer chain (Fluidlogged API's
included; Forge's side check left out, since the client classes would be refused on a server), carry exactly
the hook calls listed below, still carry Fluidlogged API's own hooks where it has them (`travel`,
`EntityPlayerSP.onLivingUpdate`), and every method of each class passes ASM's stack and type check; and the
same classes from Forge's SRG-named jar (Minecraft's; Forge's own classes are not in it, and FML remaps them to the same SRG names at runtime) (`forge-1.12.2-14.23.5.2847-srgBin.jar`, the names a real game
runs with) are patched the same way and verify. The behavior each patch enables is tested by the pose tests
here and by the backports that use it.

## P1. Water travel

*   **Class**: `net.minecraft.entity.EntityLivingBase`, method `travel(FFF)V` (`func_191986_a`).
    **Transformer**: `asm/WaterTravelTransformer`. **Hooks**: `pose/MovementHooks`. **Since**: 1.1.0.
*   **Vanilla 1.14**: `PlayerEntity.travel` turns a swimming player's vertical motion toward the look before
    moving; `LivingEntity.travel` slows a sprinting entity in water by 0.9 instead of 0.8 and pulls down by
    1/16 of gravity (0.005, none while sprinting) instead of 0.02.
*   **Why a patch**: 1.12.2 fires no event inside `travel`. A player tick event runs before or after the whole
    move, so a change made there lands a tick late (the vertical pull and the look) or has to undo the
    0.8 and 0.02 after the fact without knowing what collisions changed. Doing it in place is the only way to
    get 1.14's numbers exactly.
*   **What changes** (three additions, nothing removed except one constant):
    1.  first line: `MovementHooks.beforeTravel(this)`;
    2.  after the method's one `getWaterSlowDown()` call: the value goes through
        `MovementHooks.waterSlowDown(value, this)` (Depth Strider then adjusts it, as in both versions);
    3.  the water branch's `motionY -= 0.02D`: its constant becomes `MovementHooks.waterGravity(this)`. It
        is the second of the method's two `0.02D` constants (the first is lava's), and it must come after the
        `getWaterSlowDown` call.
*   **Who it reaches**: players only, and only while a backport's `PlayerMovement` is registered and its
    rules are on. Every other entity gets vanilla's values.
*   **Fails safe**: method missing, or not exactly one `getWaterSlowDown` call and two `0.02D` constants in
    that order: skipped; water movement stays 1.12.2's.
*   **Other mods**: Fluidlogged API redirects the `isInWater`, `isOffsetPositionInLiquid` and `move` calls in
    this method; this patch touches none of them and leaves its redirections in place (tested). A mod that
    rewrites the water branch's constants is detected by the counts and left alone.
*   **Tested**: in the dev server, a player in water moved by vanilla's `travel` gets the test movement's
    slowdown (0.5) and pull (0), and with the hooks at their defaults vanilla's 0.8 and 0.02; a pig in the
    same water never reaches the player movement and keeps 0.8 and 0.02; with the rules off the movement is
    not called. Update Aquatic tests its swimming numbers through this patch.

## P2. The client player's sprint rules

*   **Class**: `net.minecraft.client.entity.EntityPlayerSP`, method `onLivingUpdate()V` (`func_70636_d`).
    **Transformer**: `asm/PlayerSprintTransformer`. **Hook**: `pose/client/ClientHooks.beforeLivingUpdate`,
    which calls each `PlayerMovement.beforeClientMove`. **Since**: 1.1.0.
*   **Vanilla 1.14**: `ClientPlayerEntity.livingTick` lets a sprint start in water with the eyes under, keeps
    a swim going on any forward input and through bumping a wall, ends it on leaving the water, never toggles
    creative flight mid-swim, and sinks a sneaking player in water.
*   **Why a patch**: 1.12.2's sprint checks sit inside `onLivingUpdate`, between the input event and the
    move, with no event after them. Fluidlogged API also adds, at the start of the flight check right after
    them, a hook that stops any sprint in water (unless one particular other swimming mod is installed), so
    without a later hook a player could never swim.
*   **What changes**: one call, `ClientHooks.beforeLivingUpdate(this)`, right before the method's one
    `super.onLivingUpdate()` call: after vanilla's sprint, flight and elytra checks (and Fluidlogged API's
    hook), before the player moves. A backport decides the sprint again there from the state the tick began
    with.
*   **Fails safe**: method missing or not exactly one `super.onLivingUpdate()`: skipped; sprinting stays
    1.12.2's (and with Fluidlogged API installed, no sprint survives in water).
*   **Other mods**: Fluidlogged API's own hook in this method is kept and runs first (tested in the
    transformer chain).
*   **Tested**: transformer chain and SRG names as above (a dedicated server never loads the class). Not
    exercised in a running client.

## P3. Swimming limbs on every biped model

*   **Class**: `net.minecraft.client.model.ModelBiped`, method
    `setRotationAngles(FFFFFFLnet/minecraft/entity/Entity;)V` (`func_78087_a`). **Transformer**:
    `asm/BipedAnglesTransformer`. **Hooks**: `pose/client/ClientHooks.beforeAngles`, `afterHead`,
    `afterAngles`. **Since**: 1.1.0.
*   **Vanilla 1.14**: `BipedModel.setRotationAngles` blends the head toward -45 degrees and the arms and legs
    into the swim stroke by the swim animation, for the player model and the armor drawn on it alike, and
    draws the model crouched by pose, not by the sneak key.
*   **Why a patch**: replacing the player renderers' model would leave armor, and every mod's own biped
    layer, standing upright on a swimming body. No event fires between the model's angles and its drawing.
*   **What changes** (three calls):
    1.  first line: `beforeAngles(this, entity)` remembers the head's last angle (1.14 blends from it) and,
        for a player in the swimming pose, clears `isSneak`;
    2.  right after the head's angles, before the first statement that touches `bipedBody` (where 1.14
        blends the head, so a swing and a bow still aim from the blended head): `afterHead(this, entity,
        headPitch)`;
    3.  before the method's last `copyModelAngles` call (the head onto the headwear, its last line):
        `afterAngles(this, entity, limbSwing)` - backports' player arm poses (`pose.client.PlayerModelHook`),
        then 1.14's stroke. `ModelPlayer` copies the limbs onto its outer layers after this, as vanilla does.
*   **Who it reaches**: only a model drawn for the player of the current third-person render
    (`RenderPlayerEvent.Pre` to `Post`). The first-person arm, mobs and every other use of the model are
    untouched.
*   **Fails safe**: method missing, no `copyModelAngles` call, or the body statement not where expected:
    skipped; players are drawn standing (the box, eyes and camera still follow the pose).
*   **Tested**: transformer chain and SRG names. Not seen in a running client.

## P4. The body laid along the swim

*   **Class**: `net.minecraft.client.renderer.entity.RenderPlayer`, method
    `applyRotations(Lnet/minecraft/client/entity/AbstractClientPlayer;FFF)V` (`func_77043_a`, not its
    bridge). **Transformer**: `asm/PlayerRotationsTransformer`. **Hook**:
    `pose/client/ClientHooks.applyRotations`. **Since**: 1.1.0.
*   **Vanilla 1.14**: `PlayerRenderer.applyRotations` turns the body by `-90 - pitch` in water (flat, -90, out
    of it, crawling), blended by the swim animation, and moves it so it lies where the box is; a sneaking
    player in the swimming pose is not lowered.
*   **Why a patch**: a render event wraps the whole renderer, name tag included, so turning the body there
    turns the name tag with it. Inside `applyRotations` only the model and its layers are affected.
*   **What changes**: after the second (last) `super.applyRotations` call, the branch for a player neither
    sleeping nor gliding: `ClientHooks.applyRotations(player, partialTicks)`.
*   **Fails safe**: method missing or not exactly two `super.applyRotations` calls: skipped; players are drawn
    upright.
*   **Tested**: transformer chain and SRG names. Not seen in a running client.

## P5. The camera eases to a new eye height

*   **Class**: `net.minecraft.client.renderer.EntityRenderer`, method `orientCamera(F)V` (`func_78467_g`).
    **Transformer**: `asm/CameraHeightTransformer`. **Hook**: `pose/client/ClientHooks.cameraEyeHeight`.
    **Since**: 1.1.0.
*   **Vanilla 1.14**: `ActiveRenderInfo.interpolateHeight` moves the camera half way to the eye height each
    tick and draws between the last two heights, so starting a swim or a crawl lowers the view smoothly
    instead of snapping it 1.2 blocks.
*   **Why a patch**: the camera event comes before vanilla's own eye-height translation and the third-person
    distance check, both of which use the raw eye height read in this method.
*   **What changes**: the method's one `entity.getEyeHeight()` call becomes
    `ClientHooks.cameraEyeHeight(entity, partialTicks)`. Aiming at blocks and mobs keeps the real eye
    height, as in 1.14.
*   **Who it reaches**: the view entity, while a backport's pose rules are on; otherwise vanilla's value.
*   **Fails safe**: method missing or not exactly one `getEyeHeight` call: skipped; the camera snaps as in
    1.12.2.
*   **Tested**: transformer chain and SRG names. Not seen in a running client.

## P6. The title screen panorama

*   **Class**: `net.minecraft.client.gui.GuiMainMenu`, method `drawScreen(IIF)V` (`func_73863_a`).
    **Transformer**: `asm/TitlePanoramaTransformer`. **Hooks**: `presentation/client/PresentationHooks.renderSkybox`,
    `drawGradient`. **Since**: 1.1.0.
*   **Vanilla 1.13**: the title screen draws its panorama sharp (no blur), through an 85 degree perspective at
    the window's shape, turning a full circle (`RenderSkyboxCube`, `RenderSkybox`), and draws
    `panorama_overlay.png` over it in place of 1.12.2's two gradients.
*   **Why a patch**: `renderSkybox` is private and draws through a 256 by 256 blurred texture; no event comes
    between it and the gradients, and replacing the whole title screen class would break every mod that
    checks for it.
*   **What changes**: three call sites change owner, nothing else: the one `renderSkybox(mouseX, mouseY,
    partialTicks)` call and the two `drawGradientRect` calls become static calls to the hooks, with the menu
    as the first argument.
*   **Who it reaches**: the title screen, once any backport is installed (a 1.13 or later version is
    registered). With none, both hooks call vanilla's own methods. The overlay replaces the gradients only
    when an installed version registered one.
*   **Fails safe**: method missing, or not exactly one `renderSkybox` and two `drawGradientRect` calls:
    skipped; the panorama is 1.12.2's, blurred, with the chosen faces not shown.
*   **Tested**: transformer chain and SRG names (`selftest/PatchTests`); the client self-test
    (`runClient -Pselftest`) reports the hook drawing the dummy version's faces at the main menu.

## P7. F3 key combinations

*   **Class**: `net.minecraft.client.Minecraft`, method `runTickKeyboard()V` (`func_184118_az`).
    **Transformer**: `asm/DebugKeysTransformer`. **Hook**: `presentation/client/PresentationHooks.processKeyF3`.
    **Since**: 1.1.0.
*   **Vanilla**: each version adds, changes and removes F3 combinations (1.13 F3+C and F3+I, 1.16 F3+F4,
    1.17 F3+L, 1.19 removes F3+F), and F3+Q lists the current ones.
*   **Why a patch**: `processKeyF3` is private and runs before Forge's key event; by the time a mod hears the
    key, vanilla has already acted on it, so a key cannot be changed or removed from outside.
*   **What changes**: the one `this.processKeyF3(key)` call changes owner: it becomes
    `PresentationHooks.processKeyF3(this, key)`. Nothing else in the method moves.
*   **Who it reaches**: every F3 combination, once an installed version changes the key map
    (`presentation.DebugKeys`); until then the hook calls vanilla's own `processKeyF3` for every key.
    F3+C's held-for-ten-seconds crash is outside this method and unchanged.
*   **Fails safe**: method missing or not exactly one `processKeyF3` call: skipped; 1.12.2's keys only.
*   **Tested**: transformer chain and SRG names; the client self-test calls the hook with the dummy
    version's added and removed keys.

## P8. The strongholds' and spawn point's biome search

*   **Class**: `net.minecraft.world.biome.BiomeProvider`, method `findBiomePosition(IIILjava/util/List;Ljava/util/Random;)`
    (`func_180630_a`). **Transformer**: `asm/BiomeSearchTransformer`. **Hook**: `worldgen/WorldgenHooks.findBiomePosition`.
    **Since**: 1.1.0 (Update Aquatic Backport's P5 before).
*   **Vanilla 1.13+**: the search keeps one matching 1:4 cell of the square, replacing the kept one with chance
    `1 / (n + 1)`, and counts `n` for every matching cell (1.13's `OverworldBiomeProvider.findBiomePosition`;
    1.14's `findBiome` and 1.15's and 1.16's `findBiomeHorizontal` walk the same cells in the same order for
    these squares). 1.12.2 counts only the cells it keeps, so it draws different random numbers. The search
    places all 128 strongholds (one shared random, so one difference moves every later stronghold) and starts
    the spawn point.
*   **Why a patch**: `MapGenStronghold` and `WorldServer.createSpawnPosition` call the method directly; only the
    method itself reaches both, and the other world types' providers that inherit it.
*   **What changes**: the method's whole body becomes `return WorldgenHooks.findBiomePosition(this, x, z, range,
    biomes, random)`. The hook runs 1.12.2's own loop, line for line (reading the provider's `genBiomes`), until
    the newest installed backport is 1.13 or later, then 1.13's (over `getBiomesForGeneration` of the same
    square). The new body is straight-line; the old body's frames, locals and handlers go with it.
*   **Fails safe**: method missing: skipped; 1.12.2's pick.
*   **Tested**: transformer chain and SRG names; vanilla oracles: the same 128 strongholds and spawn biome
    search as 1.13.2, 1.14.4, 1.15.2 and 1.16.5's own code over 6 seeds, and 1.12.2's with Core alone.

## P9. The villages' biome point

*   **Class**: `net.minecraft.world.gen.structure.MapGenVillage`, method `canSpawnStructureAtCoords(II)Z`
    (`func_75047_a`). **Transformer**: `asm/VillageBiomeTransformer`. **Hook**: `worldgen/WorldgenHooks.villageBiomes`.
    **Since**: 1.1.0 (Update Aquatic Backport's P4 before).
*   **Vanilla**: 1.12.2 reads the 1:4 cell at block (8, 8) of the candidate chunk
    (`areBiomesViable(x * 16 + 8, z * 16 + 8, 0, VILLAGE_SPAWN_BIOMES)`); 1.13 to 1.15 read the block-scale biome
    at (9, 0, 9) (`ChunkGenerator.createStructures`), 1.16 the 1:4 cell at (4x + 2, 4z + 2). The grid and biome
    list are unchanged, so only villages near biome edges move; that is enough to differ from the seed maps.
*   **Why a patch**: replacing the village generator through `InitMapGenEvent` would take the slot Village &
    Pillage's villages need (they replace exactly vanilla's class). The patch keeps vanilla's class, and
    subclasses that call this method get the point too.
*   **What changes**: the one `INVOKEVIRTUAL BiomeProvider.areBiomesViable(IIILjava/util/List;)Z` changes owner:
    it becomes `INVOKESTATIC WorldgenHooks.villageBiomes`, same arguments with the provider first. For radius 0
    and a newest version of 1.13 or later the hook reads `StructurePlacements.biomeAt`; otherwise it calls
    `areBiomesViable` unchanged.
*   **Fails safe**: method missing, or not exactly one such call: skipped; 1.12.2's point.
*   **Tested**: transformer chain and SRG names; vanilla oracles: the same village start chunks as each
    version's own code over 6 seeds, blocks -4096..4095.

## P10 and P11. The biome layers' neighbor rules of 1.16

*   **Classes**: `net.minecraft.world.gen.layer.GenLayer`, static `biomesEqualOrMesaPlateau(II)Z` (`func_151616_a`,
    P10), and `net.minecraft.world.gen.layer.GenLayerBiomeEdge`, private `canBiomesBeNeighbors(II)Z`
    (`func_151634_b`, P11). **Transformer**: `asm/LayerRulesTransformer`. **Hooks**: `worldgen/WorldgenHooks.similarBiomes`,
    `canBeNeighbors`. **Since**: 1.1.0.
*   **Vanilla 1.16**: biomes became data, so the layers stopped asking biome objects. 1.16.5's `Layers.isSame`
    looks both ids up in one fixed category table, where the badlands plateaus have a category of their own;
    1.15's (and 1.12.2's) test asks the biome category (class) and treats a plateau specially only from the
    plateau's side, so a badlands cell counted as "like" a plateau beside it. And 1.16.5's
    `BiomeEdgeLayer.checkEdge` keeps every mountain cell, where 1.15 turned mountains beside a biome of another
    temperature into mountain edge. Both are rare: no difference inside 1,024 blocks of 0, 0 on the six test
    seeds, but the badlands plateaus 18,000 blocks out move strongholds (found through `strongholdtrace`, see
    `seedcheck/OverworldDump`).
*   **Why a patch**: both are asked by vanilla's own layer classes (hills, biome edge, shore), which Village &
    Pillage's 1.14 stack reuses; only the methods themselves reach every caller.
*   **What changes**: each whole body becomes one call to its hook, which runs 1.12.2's code line for line
    until a 1.16 backport is installed (read once per world, when its biome map is made).
*   **Fails safe**: method missing: skipped; 1.12.2's (= 1.15's) rules.
*   **Tested**: transformer chain and SRG names; the vanilla oracle: 1.16.5's strongholds on all six seeds,
    and 1.16.5's 1:4 map 18,000 blocks out (seed 171).

## Frames in production classes (read before writing a patch)

Mojang's shipped classes carry ProGuard's stack map frames, which mark a local as unused (TOP) once the original
code stops reading it. The coremod writes with `COMPUTE_MAXS` only, so those frames stay. Inserted code may
therefore read a local only where the original code still reads it later, or at method entry; anything else
fails JVM verification in a real game while every dev run passes (dev runs use javac-compiled Minecraft, whose
frames keep every parameter). P12 to P16 read no local at all: each only changes the owner of an existing call
or passes an existing constant through a hook of the same type.

## P12. Button label colors

*   **Classes**: `net.minecraft.client.gui.GuiButton` and Forge's `net.minecraftforge.fml.client.config.GuiButtonExt`,
    method `drawButton(Lnet/minecraft/client/Minecraft;IIF)V` (`func_191745_a`). **Transformer**:
    `asm/ScreenPatchesTransformer`. **Hook**: `presentation/client/PresentationHooks.buttonTextColor`. **Since**: 1.1.0.
*   **Vanilla 1.15**: a button's label is white whether hovered or not (1.15's `AbstractButton`: 0xFFFFFF when
    active), where 1.12.2 to 1.14 draw it light gray (0xE0E0E0) and yellow when hovered (0xFFFFA0). The hover
    outline itself is `textures/gui/widgets.png`, which the 1.15 backport ships (`Contribution.replaceAsset`).
*   **Why a patch**: the colors are constants inside `drawButton`; every screen's buttons draw through it.
*   **What changes**: the two constants `14737632` and `16777120` each pass through `buttonTextColor(int)`, which
    returns them unchanged until the newest installed version is 1.15 or later. The disabled color and a
    button's own `packedFGColour` are left alone.
*   **Fails safe**: method missing, or not exactly one of each constant: skipped; 1.12.2's colors.
*   **Tested**: transformer chain (both classes) and SRG names (GuiButton); the client self-test.

## P13. The chat's drawing

*   **Classes**: `net.minecraft.client.gui.GuiIngame.renderGameOverlay(F)V` (`func_175180_a`) and Forge's
    `net.minecraftforge.client.GuiIngameForge.renderChat(II)V`. **Hook**: `PresentationHooks.drawChat`. **Since**: 1.1.0.
*   **Vanilla 1.14 and 1.16**: Text Background Opacity (the chat's background no longer follows its text's
    opacity), Line Spacing and Chat Delay's "[+N pending lines]" (1.16.5's `ChatComponent.render`).
*   **Why a patch**: `GuiNewChat.drawChat` reads its own private fields and fixed numbers; Forge has no chat
    rendering event. Changing the callers rather than `drawChat` itself keeps other mods' changes to
    `drawChat` working whenever Core's chat is not in use.
*   **What changes**: the one `drawChat(updateCounter)` call in each method becomes
    `PresentationHooks.drawChat(chat, updateCounter)`, which calls vanilla's `drawChat` before 1.14 and Core's
    `presentation/client/ChatRenderer` from 1.14 on.
*   **Fails safe**: method missing or not exactly one call: skipped; 1.12.2's chat.
*   **Tested**: transformer chain and SRG names; the client self-test (chat drawn, delay queue).

## P14. The chat screen

*   **Class**: `net.minecraft.client.gui.GuiChat`, `drawScreen(IIF)V` and `mouseClicked(III)V`. **Hooks**:
    `PresentationHooks.chatComponent`, `chatBoxColor`. **Since**: 1.1.0.
*   **Vanilla 1.14 and 1.16**: the component under the mouse is found with the line spacing (1.16.5's
    `getClickedComponentStyleAt`), and the input box takes the text background opacity when Text Background is
    "Everywhere" (1.14.4's `Options.getBackgroundColor`).
*   **What changes**: both `getChatComponent(x, y)` calls change owner; the input box's `Integer.MIN_VALUE`
    passes through `chatBoxColor(int)`.
*   **Fails safe**: not exactly one call in each method and one box color: skipped.
*   **Tested**: transformer chain and SRG names.

## P15 and P16. Sky color and cloud height

*   **Classes**: `net.minecraft.client.renderer.RenderGlobal` (`renderSky(FI)V`, `renderClouds(FIDDD)V`,
    `renderCloudsFancy(FIDDD)V`; P15) and `net.minecraft.client.renderer.EntityRenderer` (`updateFogColor(F)V`,
    `renderWorldPass(IFJ)V`; P16). **Hooks**: `PresentationHooks.skyColor`, `cloudHeight`, `cloudLayer`. **Since**: 1.1.0.
*   **Vanilla**: later versions change the sky and the clouds (1.18 raises the clouds from 128 to 192); the fog
    pipeline (`presentation/client/FogPipeline`) applies each version's step in order.
*   **Why a patch**: Forge has events for the fog, not for the sky color or the cloud height.
*   **What changes**: P15: `renderSky`'s `world.getSkyColor(entity, partialTicks)` and the two cloud passes'
    `provider.getCloudHeight()` keep their call and pass its result through `skyColor(Vec3d, float)` and
    `cloudHeight(float)` right after it (since 1.4.1: a mixin redirecting the call, such as SRParasites', still finds
    it). P16: `updateFogColor`'s `getSkyColor` result goes through the same hook (the fog's
    blend with the sky follows the same color), and `renderWorldPass`'s two `128.0D` (whether the camera is below
    the clouds, which decides if they draw before or after the terrain) pass through `cloudLayer(double)`,
    which returns 128.0 until a step moves the clouds.
*   **Who it reaches**: nothing changes until a backport adds a step with `sky` or `clouds`.
*   **Fails safe**: any method missing or a count other than one `getSkyColor`, one `getCloudHeight` per pass
    and two `128.0D`: skipped for that class; vanilla's sky and clouds.
*   **Tested**: transformer chain and SRG names; the client self-test's dummy step (clouds +64) sees both.

## P17. Threaded chunk rebuilds

*   **Class**: `net.minecraft.client.renderer.RenderGlobal`, `setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V`
    (`func_174970_a`) and `updateChunks(J)V` (`func_174967_a`). **Transformer**: `asm/ChunkRebuildsTransformer`.
    **Hooks**: `render/client/ChunkRebuildHooks.nearbyDistanceSq`, `needsImmediateUpdate`. **Config**:
    `rendering.blockUpdates` ("Chunk Builder": `NONE`, `PLAYER_AFFECTED`, `NEARBY`; default `PLAYER_AFFECTED`); from 1.4.5
    also Video Settings' Chunk Builder (`backportcore:prioritize_chunk_updates`, from 1.18.2), the same setting, which
    decides while it is shown (`ChunkRebuildHooks.mode`). **Since**: 1.1.0.
*   **Vanilla**: 1.12.2 rebuilds every changed render chunk whose center is within sqrt(768) blocks of the camera,
    and every chunk the player dirtied (`needsImmediateUpdate`, set by a block change with flag 8, which
    `PlayerControllerMP` uses for the player's own breaking and placing; a change the server sends uses flags 3),
    on the render thread in the same frame. 1.15.2 and 1.16.5 still do exactly that (the `768.0D` test is in both
    jars' `LevelRenderer.setupRender`). 1.18.2 made it the "Chunk Builder" video setting (`prioritizeChunkUpdates`;
    1.19.2's `LevelRenderer.compileChunks` read here): None = "Threaded", every rebuild on the builder threads,
    the default; Player Affected = "Semi Blocking", only the player's own changes at once; Nearby = "Fully
    Blocking", 1.12.2's rule. `rendering.blockUpdates` takes the same three values. Its default is
    `PLAYER_AFFECTED`, not vanilla's `NONE`: it removes the measured stall below and keeps the player's own
    breaking free of the one-frame hole `NONE` can show (vanilla's own tooltip says so); `NONE` is one word away.
*   **Why a patch**: the rule is inline in `setupTerrain`, and Forge's only switch (`alwaysSetupTerrainOffThread`
    in forge.cfg) also threads the player's own changes. Measured 2026-11-08 on the user's test instance with a
    spark client profile, swimming through and breaking a kelp forest with Update Aquatic and Fluidlogged API
    3.3.3: `ChunkRenderDispatcher.updateChunkNow` from `setupTerrain` was 9.8% of client time (about half of it
    Fluidlogged API's liquid extended state; the family's own code 0.12%). A kelp column breaks one block a tick
    from the server, so 1.12.2's rule rebuilt a chunk on the render thread every tick for a second (about 40 fps).
*   **What changes**: `setupTerrain`'s one `768.0D` passes through `nearbyDistanceSq(double)`, which returns it
    for `NEARBY` and -1.0 otherwise (no squared distance is below it). The result of the one
    `renderChunk.needsImmediateUpdate()` call in `setupTerrain` and the one in `updateChunks` passes through
    `needsImmediateUpdate(boolean)`, which keeps it except `false` for `NONE`. The constant and the calls
    themselves stay, so another mod's mixin aimed at them still finds its target. Forge's
    `alwaysSetupTerrainOffThread` is still checked first, as before. The setting is read on every call, so a
    change in the config screen takes effect at once.
*   **Who it reaches**: every client with Core installed, with no backport needed: it is a renderer rule, not a
    version's content. Servers never load `RenderGlobal`; the transformer names client classes only as strings.
*   **Fails safe**: either method missing, or not exactly one `768.0D` and one `needsImmediateUpdate` call in each:
    skipped with one log line; 1.12.2's rule. OptiFine, Celeritas, Vintagium and Nothirium replace `setupTerrain`
    and thread the rebuilds themselves. Where the method no longer has vanilla's shape the patch is skipped. A
    mixin that overwrites the method later in the chain simply drops the hooks.
*   **Tested**: transformer chain and SRG names (`PatchTests`); a dedicated-server boot; the client self-test
    (P17 applied, the distance rule per mode). Frame rate by hand: see the 1.1.0 handover.

## P18. Third person's terrain starts at the camera

*   **Class**: `net.minecraft.client.renderer.RenderGlobal`, `setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V`
    (`func_174970_a`). **Transformer**: `asm/CameraTerrainTransformer`. **Hook**:
    `pose/client/ClientHooks.terrainStart`. **Since**: 1.1.0.
*   **Vanilla 1.14**: `WorldRenderer.setupTerrain` starts its search for visible render chunks at the camera's
    own block (`ActiveRenderInfo.getBlockPos()`), and redoes the search whenever the camera moves or turns.
    1.12.2 starts at the view entity's eyes and redoes it only when the entity moves or turns. In third person in
    a tunnel the two differ: when the eyes' chunk opens only behind the player and the player looks ahead,
    1.12.2 draws that one chunk and nothing else, so the chunk the camera sits in (4 blocks back) is missing and
    the camera looks out through the stone at the sky. Crawling and swimming through one-block tunnels hit this
    all the time; the user's report was "F5 while prone, the camera is stuck in a block". The camera's own
    distance check (`orientCamera`'s eight rays) was already right: it starts at the eased low eye height (P5),
    and its numbers are 1.14's `ActiveRenderInfo.calcCameraDistance`.
*   **Why a patch**: the start block is a local inside `setupTerrain`, built and used in the same few lines.
*   **What changes**: the block built from the eye height (the method's one `getEyeHeight()` call, then
    `new BlockPos(DDD)`) passes through `terrainStart(BlockPos)`. In third person it returns the camera's block;
    the camera is the origin of the modelview `ActiveRenderInfo.updateRenderInfo` read just before (its private
    `MODELVIEW`, `field_178812_b`), relative to the entity's interpolated position. In every view, a camera that
    moved since the last frame marks the terrain dirty (`setDisplayListEntitiesDirty()`), so pressing F5 while
    lying still redoes the search, as 1.14 does. First person keeps the eyes' block. The hook takes and returns
    the value already on the stack: no local is read and no branch added, so the shipped frames stay valid.
*   **Who it reaches**: the view entity, while a backport's pose rules are on (as P5); otherwise vanilla's block.
*   **Fails safe**: method missing, or not exactly one `getEyeHeight` call followed by `BlockPos(DDD)`: skipped;
    1.12.2's search. The matrix field missing: one warning, and the eyes' block. OptiFine, Celeritas, Vintagium and
    Nothirium replace `setupTerrain`; where the method no longer has vanilla's shape the patch is skipped.
*   **Tested**: transformer chain and SRG names (`PatchTests`); the frame verifier over the SRG client class; the
    client self-test (`ClientCameraTests`, `runClient -Pselftest`): crawling, then swimming, in a one-block
    tunnel across a chunk border, back and front view at 56 looks each, the view never starts inside a block and
    keeps its 4 blocks along the open tunnel; back view draws the camera's chunk (1.12.2's search drew 1 render
    chunk there, P18's 95).

## P19. Waterloggable blocks become Fluidlogged API's

*   **What**: with Fluidlogged API installed, every class that lists `waterlog.Waterloggable` among its
    interfaces also gets `waterlog.fluidlogged.FluidloggedWaterloggable`, an `IFluidloggable` whose default
    methods answer Fluidlogged API from the block's `waterNeed`, `canHoldWater` and `onWaterChanged`.
    Subclasses inherit it. Without Fluidlogged API (its `IFluidloggable` class not on the class path) nothing
    is changed.
*   **Why a patch**: Fluidlogged API asks `instanceof IFluidloggable`. A block that implemented it in source
    could not load without Fluidlogged API, and the user wants every backport to load and play without it.
    Its `FluidloggableEvent` answers only whether a fluid may enter, not the fill/drain callbacks.
*   **Shape**: only the class's interface list (and its generic signature, when it has one) changes. No
    method, no frame. A class already listing the bridge is left alone.
*   **Fails safe**: with no Fluidlogged API the blocks stay plain `Waterloggable` and dry.
*   **Tested**: `WaterTests` checks that a `Waterloggable` test block and a subclass of one are
    `IFluidloggable`, and every waterlogging test runs through the bridge.

## P20. Fluidlogged API water outside y 0-255

*   **What**: before each return of `net.minecraft.world.chunk.Chunk(World, int, int)`, a call to
    `waterlog/fluidlogged/SectionFluidStates.install(this)`. In a Caves & Cliffs Backport extended world
    (`WorldHeights.isCavesAndCliffsExtended`, y -64 to 319) it replaces the chunk's Fluidlogged API store, still
    empty, with Core's: one Fluidlogged API container per 16-block section, keyed by signed section y (y packed in
    4 bits at the section's base, as Fluidlogged API's own Cubic Chunks cubes). Every other world keeps
    Fluidlogged API's store. Only applied when Fluidlogged API and Caves & Cliffs Backport are both installed.
*   **Why a patch**: Fluidlogged API 3.3.3's chunk store (`FluidStateCapabilityVanilla`) packs a position into
    a char with y in eight bits, so water below 0 or above 255 lands on another height or nowhere. Its store is
    made by `PluginChunk$Hooks.createCapabilityInstance`, which it calls right after the constructor's
    `super()`, before `this.world` is set, so a patch there cannot tell which world the chunk is in; the end of
    the constructor is the first place that can. No event comes between.
*   **Saving and sync**: the store writes Fluidlogged API's own save format (version 2), y 0-255 in its `data`
    list (so the world opened without extended height keeps that water) and the rest under
    `backportcore:extended`; it reads Fluidlogged API's own saves too. Fluidlogged API's chunk-watch packet
    carries only `getContainer(0)`, so Core sends every other non-empty section in the same packet type (with its
    section y, as for Cubic Chunks) on `ChunkWatchEvent.Watch`. Per-position updates already go by y.
*   **Shape**: two instructions (`aload 0`, `invokestatic`) before a `RETURN`: nothing on the stack, no new
    branch, so the frames stay valid.
*   **Fails safe**: constructor not found, or no return: skipped, Fluidlogged API's store everywhere (water
    outside 0-255 is then lost, as before). A chunk whose store is not Fluidlogged API's plain one (Cubic
    Chunks), or that already holds water, is left alone.
*   **Tested**: `PatchTests` (dev and SRG `Chunk`: one call before each return, the class verifies);
    `DeepWaterTests`: the store, its save in both formats and the sync packets in every run; in an extended world
    (`runServer -Pselftest -PwithMods=<Caves & Cliffs Backport dev jar> -PselftestOnly=deepwater,water,patches`,
    level-type `cavesandcliffs`) waterlogged blocks at y -60 and 300, the `Waterlogging` helpers there, the
    chunk's capability NBT into a new chunk, and over two runs a save and reload. `selftest/deepwater/DeepWater`
    (`gradlew deepWaterJar -Pselftest`) runs the place / reload check on another mod's dev server.
*   **Not covered**: generation in a `ChunkPrimer`. Fluidlogged API's primer indexes as `ChunkPrimer` does and
    its `generateFluidStates` copies y 0-255 only, so `Waterlogging.setWater(primer, ...)` ignores any other y
    (it used to write another column's water); set such water after the chunk exists.

## P21. Dyed candles influence Quark's matrix enchanting

*   **What**: at the start of `vazkii.quark.oddities.tile.TileMatrixEnchanter.getEnchantPowerAt(World, BlockPos)`
    (Quark, with its Oddities add-on), a call to `compat/QuarkCandles.influence(this.influences, world, pos)`. A
    block named `<color>_candle` by any mod but Quark adds the influences Quark's config gives that color, up to its
    "Influencing Max", only while "Influencing Enabled" is on: what Quark does for its own candle.
*   **Why a patch**: Quark compares the block with its own candle by identity and has no API for others. Backport
    Core hides Quark's candle beside the backports' candles (`duplicate/QuarkPairs`), so without this the feature
    is lost.
*   **Rest of the stand-in** (no patch): the backports' candles give Quark's "Enchantment Power" (0.5 by default)
    through `getEnchantPowerBonus` while Quark's candles are on, 0 without Quark as in 1.18; with Quark's tallow on,
    string over two tallow makes "Candles Crafted" plain candles; the candles carry the ore name `blockCandle`.
*   **Shape**: five instructions at the head (`aload 0`, `getfield influences`, `aload 1`, `aload 2`,
    `invokestatic`), nothing left on the stack, no new branch.
*   **Fails safe**: method or field not found: skipped, Quark unchanged. Any reflection failure in the hook turns it
    off for the session.
*   **Tested**: G:/VBPTest/family-all (`FamilyProbe -Dfamilyprobe.candles`), all backports + Quark r1.6-179 and
    QuarkRotN r1.6-192 + Quark Oddities: patched; a red and a blue candle by the table add Fire Aspect, Flame,
    Efficiency, Sharpness, Lure and Power; power counts; without Quark power is 0 and there is no tallow recipe.
    RLCraft and Dregora ship Quark without Oddities, so there only the power and the recipe apply.

## P22. Menus without a world at 60 frames a second

*   **What**: in `Minecraft.getLimitFramerate`, the constant 30 (a menu with no world open) goes through
    `PresentationHooks.menuFramerate(int)`: 60 once a backport of 1.21.2 or later is installed (its
    `FramerateLimitTracker` limit for out-of-level menus), 30 otherwise.
*   **Why a patch**: 1.12.2 holds the frame rate inside `runGameLoop` with no event; a mod can only slow it further.
*   **Shape**: one `invokestatic (I)I` after the single `bipush 30`.
*   **Fails safe**: method not found or not exactly one constant 30: skipped, 1.12.2's 30.
*   **Tested**: client self-test (`P22 applied`).

## P23. 1.21.2's slot highlight

*   **What**: `GuiContainer.drawScreen`'s `drawGradientRect` (the white square over the hovered slot) becomes
    `PresentationHooks.slotHighlight(gui, ...)`. From 1.21.2, when a backport supplies the two sprites
    (`textures/gui/sprites/container/slot_highlight_back.png` and `_front.png` through `replaceAsset`), it draws the
    back one behind the item already drawn (depth-tested at z 0) and the front one over it, 4 pixels out from the
    slot; otherwise 1.12.2's square (Forge's `GuiUtils.drawGradientRect`).
*   **Why a patch**: the square is drawn inside `drawScreen`'s slot loop after the item; no event comes between.
*   **Shape**: the one `invokevirtual drawGradientRect` swapped for an `invokestatic` with the receiver first.
*   **Fails safe**: not exactly one call: skipped, vanilla's square.
*   **Tested**: client self-test (`P23 applied`). How it looks: not seen.

## P24. The sunrise glow through the fog pipeline

*   **What**: both readers of `WorldProvider.calcSunriseSunsetColors` (`EntityRenderer.updateFogColor` and
    `RenderGlobal.renderSky`) call `PresentationHooks.sunriseColors`, which runs `FogPipeline.Step.sunrise` on a
    copy of the array (1.12.2 shares one). Tricky Trials Backport's 1.21.11 step dims it in rain and thunder.
*   **Why a patch**: the color is read straight from the provider; a provider replacement would fight every mod
    that replaces providers.
*   **Shape**: in each method, `sunriseColors([FF)[F` (with `FLOAD 1`, partialTicks) inserted after the one
    `invokevirtual`, which stays in place for mixins (SRParasites redirects it; Core 1.3.0 to 1.4.0 swapped it and
    crashed it). The hook returns the array it was given when no step exists.
*   **Fails safe**: per class, skipped when the call is not found once.
*   **Tested**: client self-test (`P24 applied`); the 1.21.11 math in Tricky Trials' self-test.

## P25. Clouds drift by world time

*   **What**: every read of `RenderGlobal.cloudTickCounter` in `renderClouds` and `renderCloudsFancy` goes through
    `PresentationHooks.cloudTicks(int)`: the world's total time once a step at the newest version asks for it
    (`FogPipeline.Step.cloudsFollowWorldTime`, 1.21.11), else the client's own count. Every player then sees the
    same clouds.
*   **Shape**: `invokestatic (I)I` after each `getfield cloudTickCounter` (three reads, Forge's `IRenderHandler`
    call included).
*   **Fails safe**: fewer than two reads found: skipped.
*   **Tested**: client self-test (`P25 applied`).

## P26. Cloud Distance

*   **What**: `RenderGlobal.renderClouds` starts with `PresentationHooks.cloudsBegin()`; the fast clouds' loop bounds
    (`-256` and `256`, both axes) go through `cloudFast(int)` and `renderCloudsFancy`'s cell loops (`-3` to `4`, both
    axes, cells of 96 blocks) through `cloudFancy(int)`. From 1.21.6 (Cloud Distance, 2 to 128 chunks, default 128),
    the clouds reach the cloud distance, their far plane is moved out to 1.5 times it, and they fade from the camera
    to it (1.21.6's `FogCloudsEnd`: linear fog from 0 to the distance in place of the render distance's), except under
    water, in lava, while blind, or in a world with its own cloud renderer. Before 1.21.6 every hook returns 1.12.2's
    numbers.
*   **1.21.2's clouds (since 1.4.5)**: right after `cloudsBegin()`, `if (PresentationHooks.modernClouds(partialTicks,
    x, y, z)) return;`. From 1.21.2 (the sky's look) `presentation.client.ModernClouds` draws 26.3's `CloudRenderer`
    mesh instead: a 12 by 4 by 12 box per cloud texel, only the faces that can be seen (top or bottom by the camera's
    side, a side where the next cell is empty and only on the camera's side, inward faces within one cell), in six
    display lists (one per direction, 26.3's face shades times the cloud color), a depth pass then a color pass.
    1.12.2 drew a wall at every texel edge, inside clouds too, and from below they showed through the undersides as a
    grid of lines, worse with a far Cloud Distance. 1.12.2's clouds stay with the sky look off or before 1.21.2, for a
    world with its own cloud renderer, and while an OptiFine shader pack is loaded.
*   **Why a patch**: the bounds are constants in the loops and the far plane is set by `EntityRenderer.renderCloudsCheck`;
    no event comes between.
*   **Shape**: at the head an `invokestatic ()V`, then the parameters, an `invokestatic (FDDD)Z`, `ifeq` past a `return`
    (an `F_SAME` frame); `invokestatic (I)I` after four `sipush` and four loop constants.
*   **Fails safe**: methods not found or not exactly four of each constant: skipped, 1.12.2's clouds.
*   **Cost**: 1.12.2 builds its clouds on the CPU each frame. Fancy clouds at 128 chunks are 45 by 45 cells (1.12.2: 8
    by 8); the Fast and Fancy presets set 32 and 64.
*   **Tested**: client self-test (`P26 applied`, the hooks keep 1.12.2's numbers below 1.21.6); the video shots
    (`BACKPORTCORE_VIDEOSHOTS=1`) draw 1.21.2's clouds from below, above, inside and Fast, and 1.12.2's with the sky
    look off (`video-clouds-*.png`).

## P27. Weather Effect Radius

*   **What**: `EntityRenderer.renderRainSnow` sets its radius to 5 and to 10 with Fancy graphics; both constants go
    through `PresentationHooks.weatherRadius(int)`, which gives 1.21.11's Weather Effect Radius (3 to 10 blocks).
*   **Why a patch**: the radius is a local of the method.
*   **Shape**: `invokestatic (I)I` after the `iconst_5` and the `bipush 10` stored into the same local.
*   **Fails safe**: not exactly one of each into one local: skipped.
*   **Tested**: client self-test (`P27 applied`). How it looks: not seen.

## P28. Chunk Fade

*   **What**: `ChunkRenderContainer.preRenderChunk` starts with `PresentationHooks.chunkFadePre(chunk)`, and
    `RenderGlobal.renderBlockLayer(BlockRenderLayer)` calls `chunkFadeEnd()` right after its
    `renderContainer.renderChunkLayer(layer)`. 1.21.11's terrain shader mixes a section with the fog color by its
    visibility (`ChunkVisibility`, 0 to 1 over Chunk Fade Time after it is first drawn). 1.12.2's fixed pipeline has no
    such input, so a chunk still fading gets one fog coordinate for the frame (`GL_FOG_COORDINATE_SOURCE` set to
    `GL_FOG_COORDINATE`): the fog it would have at its center times its visibility, for linear, exp and exp2 fog alike.
    Chunks that are done, and everything after the layer, use depth fog again.
*   **Why a patch**: the chunks are drawn one by one inside `VboRenderList`/`RenderList`; no event per chunk.
*   **Shape**: `aload 1; invokestatic` at the head of `preRenderChunk`; one `invokestatic ()V` after the one call.
*   **Fails safe**: per class, skipped when the method or the call is not found. The hook does nothing without OpenGL
    1.4, with OptiFine (its shaders own the terrain), with fog off, or with Chunk Fade at None.
*   **Tested**: client self-test (`P28 applied`, the fog-coordinate math). How it looks: not seen.

## P29. The 1.20 title logo layout

*   **What**: in `GuiMainMenu.drawScreen`, the logo's seven `drawTexturedModalRect` calls become
    `PresentationHooks.titleLogoRect(menu, ...)`, the edition's `drawModalRectWithCustomSizedTexture` becomes
    `titleEdition(...)`, and the splash's `90` (added to half the width) and `70.0F` go through `splashX(int)` and
    `splashY(float)`. In 1.20's layout (`VanillaRevisions.TitleLogo.LOGO_1_20`, below) the first piece draws
    `minecraft.png` as one 256 by 44 piece of 256 by 64 at width / 2 - 128, the other pieces draw nothing, `edition.png`
    is 128 by 14 of 128 by 16 at width / 2 - 64, y 67, and the splash is at width / 2 + 123, 69. Minceraft (1.12.2's
    one-in-ten-thousand roll) is `minceraft.png` when the same backports supply it.
*   **When**: the newest installed version that replaces `textures/gui/title/minecraft.png` is 1.20 or later, so is the
    newest that replaces `textures/gui/title/edition.png`, and `minecraft.png` is served by Core's pack (a resource
    pack of the player's is drawn 1.12.2's way). Otherwise every hook draws what 1.12.2 draws.
*   **Why a patch**: the logo is drawn in the middle of `drawScreen` between the panorama and the splash.
*   **Shape**: seven `invokevirtual` swapped for `invokestatic` with the receiver first, one static call's owner
    changed, `invokestatic` after the two constants.
*   **Fails safe**: not exactly seven pieces, one edition, one `bipush 90; iadd` and one `70.0F`: skipped.
*   **Tested**: client self-test (`P29 applied`, 1.12.2's layout kept without 1.20 textures). The 1.20 layout: not seen.

## P30. Debug key binds

*   **What**: `Minecraft.runTickKeyboard` names F3 and C by number. Its three `Keyboard.isKeyDown(61)` (the modifier),
    its `key == 61` (the debug screen's toggle, on release) and its two `Keyboard.isKeyDown(46)` (the crash) go
    through `PresentationHooks.debugModifierKey`, `debugOverlayKey` and `debugCrashKey`. From 1.21.11 they are key
    binds on the Key Binds screen (category "Debug", `DebugBindings`), as are each of the F3 map's combinations;
    P7's hook turns a rebound combination's key back into the one the map knows.
*   **Why a patch**: the numbers are constants in the keyboard loop.
*   **Shape**: `invokestatic (I)I` after six `bipush` constants.
*   **Fails safe**: not exactly three, one and two: skipped, F3 and C fixed as in 1.12.2.
*   **Tested**: client self-test (`P30 applied`, keys pass through before 1.21.11).

## P31. 1.20.5's menu backgrounds

*   **Classes**: `net.minecraft.client.gui.GuiScreen`, methods `drawBackground(I)V` (`func_146278_c`, the dirt) and
    `drawWorldBackground(I)V` (`func_146270_b`, the gradient over the world); `net.minecraft.client.gui.GuiSlot`, method
    `drawScreen(IIF)V` (`func_148128_a`, every list). **Transformer**: `asm/MenuBackgroundTransformer`. **Hooks**:
    `PresentationHooks.menuDirt`, `menuWorldGradient`, `listBackground`, `listOverlay`, `listMaxScroll`, which call
    `presentation/client/MenuBackground`. **Since**: 1.3.2.
*   **Vanilla 1.20.5** (24w09a, `Screen.renderBackground`, `AbstractSelectionList.renderWidget`): no dirt anywhere. Outside a
    world a menu is the panorama, blurred, under `menu_background.png`; inside one the world, blurred, under
    `inworld_menu_background.png` (lighter than 1.12.2's gradient, the pause menu's look). Container, book and sign screens
    keep the gradient (`renderTransparentBackground`). A list draws `menu_list_background.png` over its area, its entries
    clipped to it, and a header and a footer separator on its edges, with no dirt above or below and no shadows.
*   **Why a patch**: every screen draws its background through these two methods and every list through `drawScreen`, in
    the middle of the screen's own drawing; no event comes between the dirt and what is drawn over it, and the lists' dirt
    is drawn after their entries.
*   **What changes**: call owners only. `drawBackground`'s one `tessellator.draw()` (the dirt quad, built but not yet drawn)
    becomes `menuDirt(tessellator)`; `drawWorldBackground`'s one `drawGradientRect` becomes `menuWorldGradient(screen,
    ...)`; in `GuiSlot.drawScreen` Forge's one `drawContainerBackground(tessellator)`, the two `overlayBackground(...)` and
    the one `getMaxScroll()` become `listBackground(slot, tessellator)`, `listOverlay(slot, ...)` and `listMaxScroll(slot)`.
    Until a 1.20.5 or later backport is installed, each hook draws exactly what 1.12.2 does (the dirt quad is drawn, the
    gradient and the list's protected methods are called by reflection). From 1.20.5: the dirt quad is dropped and the
    screen's background drawn in its place (once a frame: the panorama outside a world, then the blur, then the menu
    texture); the list's hook draws the screen's background first if the screen drew none, the list texture (a list class
    that overrides `drawContainerBackground` keeps its own), and turns on a scissor over the list; the first overlay turns it
    off and draws the separators; the second turns color writes off for 1.12.2's two shadow quads; `getMaxScroll` turns
    them back on before the scroll bar.
*   **Who it reaches**: every `GuiScreen` and `GuiSlot`, vanilla's and every mod's that uses them. Forge's
    `GuiScrollingList` (the mod list) is its own class and keeps 1.12.2's look.
*   **Fails safe**: per class. `GuiScreen` without exactly one draw and one gradient, or `GuiSlot` without exactly one
    container background, two overlays and one `getMaxScroll`: that class is skipped (`P31` or `P31-list`). A list drawn
    inside a scissor the screen already set keeps 1.12.2's own clipping. Color writes and the scissor are also reset at
    the start of every frame, should a list's drawing throw. Without shaders or framebuffers the blur is left out.
*   **Tested**: server self-test (`PatchTests`): the dev chain carries the five hooks and verifies; on the SRG jar
    `GuiScreen` is patched the same way, and `GuiSlot` (vanilla's bytes, without Forge's `drawContainerBackground`) is
    left unchanged. Client: `BACKPORTCORE_MENUSHOTS=1 runClient -Pselftest` (`selftest/ClientMenuShots`) opens Options,
    Video Settings, Language, the world list, Resource Packs and Multiplayer outside a world and the pause menu, Video
    Settings and the inventory inside one, checks what was drawn and saves each (`screenshots/menu-*.png`).

## P32. Narrator Hotkey

*   **Class**: `net.minecraft.client.Minecraft`, method `dispatchKeypresses()V` (`func_152348_aa`).
    **Transformer**: `asm/MenuBackgroundTransformer`. **Hook**: `PresentationHooks.narratorCtrl`. **Since**: 1.3.2.
*   **Vanilla 1.20.2** (23w31a): Accessibility Settings' Narrator Hotkey (on by default); off, Ctrl + B (Cmd + B) no longer
    toggles the narrator.
*   **Why a patch**: 1.12.2 tests `key == 48 && GuiScreen.isCtrlKeyDown()` inside `dispatchKeypresses`, before any event a
    mod could cancel.
*   **What changes**: the method's one `GuiScreen.isCtrlKeyDown()` becomes `PresentationHooks.narratorCtrl()`, which
    answers the same unless Core's `backportcore:narrator_hotkey` is shown (a 1.20.2 or later backport is installed) and
    off. Nothing else in the method moves.
*   **Fails safe**: not exactly one `isCtrlKeyDown` call: skipped; Ctrl + B always toggles the narrator.
*   **Tested**: server self-test (`PatchTests`, dev and SRG); client: the option shown and off turns the hotkey away.

## P33. Crouching eye height

*   **Class**: `net.minecraft.entity.player.EntityPlayer`, method `getEyeHeight()F` (`func_70047_e`).
    **Transformer**: `asm/EyeHeightTransformer`. **Hooks**: `pose/MovementHooks.eyesFor`, `MovementHooks.eyeHeight`.
    **Since**: 1.4.0.
*   **Vanilla 1.14** (19w12a): a crouching player is 1.5 blocks tall with eyes at 1.27 (`PlayerEntity`'s CROUCHING
    size and `getStandingEyeHeight`), by pose: a player held crouched under a 1.5-block ceiling has those eyes with the
    sneak key up. 1.12.2 is 1.65 tall sneaking and lowers the eyes 0.08 (to 1.54) only for the key or a 1.65 height.
*   **Why a patch**: 1.12.2 has no eye-height event, and `getEyeHeight` reads the sneak key, which a forced crouch does
    not press.
*   **What changes**: the method's first instruction hands the player to `eyesFor` (kept per thread), and each
    `FRETURN` passes the value through `eyeHeight`, which returns 1.27 for a player the pose engine has in the short
    crouch's 1.5 box (`Poses.shortCrouch()`, `Pose.SNEAKING`), else the value unchanged. `this` is read only at the
    start: Mojang's shipped class marks it unusable at the return (frame `[top, float]`).
*   **Who it reaches**: players, while a backport's `PoseRules.shortCrouch()` is on; otherwise vanilla's value.
*   **Fails safe**: method missing or no `FRETURN`: skipped; eyes are 1.12.2's (1.54 with the key, 1.62 held crouched).
*   **Tested**: transformer chain and SRG names; `PoseTests` "1.14's short crouch" (sizes, eyes, the 1.5 gap, held
    crouched, the 1.65 control); client: `ClientCrouchTests` (camera easing to 1.27, walking into a 1.5 gap with the
    integrated server agreeing, held crouched at sneaking speed and drawn crouching, standing up outside).

## P34. Map decorations from their own sprites

*   **Class**: `net.minecraft.world.storage.MapDecoration`, Forge's method `render(I)Z` (client only).
    **Transformer**: `asm/MapIconTransformer`. **Hook**: `map/MapIcons.render`. **Since**: 1.4.0.
*   **Vanilla 1.14 to 1.21.5**: village, swamp hut, jungle temple and trial chambers maps carry their own icons. 1.12.2
    draws decorations from a 4 x 4 sheet already holding ten (Update Aquatic Backport's red X makes eleven).
*   **Why a patch**: Forge asks `MapDecoration.render(index)` before drawing each decoration ("true to skip vanilla"),
    but the renderer makes plain `MapDecoration`s from the map's data, so no subclass can answer.
*   **What changes**: the method's body (Forge's `return false`) becomes `return MapIcons.render(this, index)`. It
    answers false for every type but those added through `MapIcons.get`, which `map/client/MapIconRenderer` draws
    where vanilla would, from `backportcore:textures/map/decorations/<name>.png` (today's 8 x 8 sprites).
*   **Fails safe**: method missing (a dedicated server, or a vanilla jar) or not Forge's short body: left alone; an
    added type then draws as whatever the sheet holds at its number.
*   **Tested**: dev transformer chain; on the SRG (vanilla) jar it leaves the class alone; `MechanicsTests` "map icons".

## P35. Torches on panes and bars

*   **Class**: `net.minecraft.block.BlockPane`; adds an override of Forge's `canPlaceTorchOnTop`.
    **Transformer**: `asm/PaneTorchTransformer`. **Hook**: `mechanics/BlockRules.torchOnPane`. **Since**: 1.4.0.
*   **Vanilla 1.14.3** (pre3): torches and redstone torches stand on glass panes and iron bars.
*   **Why a patch**: `BlockPane` inherits Forge's `Block.canPlaceTorchOnTop`, which turns its `CENTER_SMALL` top away;
    a vanilla class can't be given an override any other way.
*   **What changes**: `BlockPane` gets `canPlaceTorchOnTop(state, world, pos)` returning
    `BlockRules.torchOnPane(this, state, world, pos)`: true while a backport has `BlockRules.torchesOnPanes` on,
    otherwise the inherited method's answer, line for line. Subclasses that override it keep their own.
*   **Fails safe**: `BlockPane` already overrides the method (another coremod): skipped; panes keep 1.12.2's rule.
*   **Tested**: transformer chain and SRG names; `MechanicsTests` "torches on panes and bars".

## P36. Spawn lists for late creature types

*   **Class**: `net.minecraft.world.biome.Biome`, method `getSpawnableList(EnumCreatureType)`.
    **Transformer**: `asm/SpawnListSwitchTransformer`. **Hook**: `worldgen/SpawnListHooks.switchIndex`. **Since**: 1.4.4.
*   **Not a vanilla change**: a crash fix. The method's `switch` reads `Biome$1`'s table, sized to the creature
    types that exist when it first runs. A type `EnumHelper.addCreatureType` adds after that has an ordinal past the
    end, and every call with it throws `ArrayIndexOutOfBoundsException` instead of taking the default branch
    (Forge's `modSpawnableLists`). Seen with Alex's Mobs (Legacy) 1.7.0, which adds `AM_WATER_AMBIENT` in pre-init
    after C&C's and UA's constructors had already made the table (their `SwitchTables.grow` initializes `Biome$1`),
    and Alex's Mobs' own widener only looks at `Biome`'s member classes, not the anonymous `Biome$1`.
*   **Why a patch**: which mod adds its type when, and which mod first runs the switch, changes from pack to pack;
    growing the table at some later point still loses to a type added after it.
*   **What changes**: the switch's one `IALOAD` (`GETSTATIC <int[]>`, `type.ordinal()`, `IALOAD`) becomes
    `SpawnListHooks.switchIndex(table, ordinal)`: the table's entry, or 0 (the default branch) past its end. The
    stack is the same before and after, so the frames stay valid; vanilla types read the same entries as before.
*   **Fails safe**: method not found, or no such table read in it (another coremod already replaced the switch):
    skipped.
*   **Tested**: transformer chain and SRG names; `PatchTests` "P36" adds a creature type after the switch ran and
    asks plains for its list.

## P37. No ground spawns in blocks that hold water

*   **Class**: `net.minecraft.world.WorldEntitySpawner`, method `canCreatureTypeSpawnBody` (Forge's).
    **Transformer**: `asm/GroundSpawnTransformer`. **Hooks**: `worldgen/GroundSpawnHooks.feet`, `head`. **Since**: 1.4.4.
*   **Vanilla change**: 1.13's `isValidEmptySpawnBlock` also asks for an empty fluid state, so a mob that spawns on the
    ground never spawns in a waterlogged block. 1.12.2 asks only that the feet and head blocks are not liquid material;
    kelp and seagrass (UA's `OCEAN_PLANT`/`SEA_GRASS`) and blocks Fluidlogged API waterlogs are not, so they passed.
    Chunk generation (`performWorldGenSpawning`) places every listed animal with only this check, never
    `getCanSpawnHere`: Lycanites' bobekos (cold biomes, which include cold oceans) spawned on the sea floor in kelp and
    floated up.
*   **Why a patch**: the check is a static method every spawn path (generation, the spawning cycle, other mods) calls;
    `LivingSpawnEvent.CheckSpawn` cannot tell a ground mob from a modded swimmer that leaves its placement unregistered.
*   **What changes**: the two `isValidEmptySpawnBlock` results of the on-ground branch pass through
    `GroundSpawnHooks.feet(valid, world, pos)` and `head(valid, world, pos)`: false when the block (or the one above)
    holds water (`Waterlogging.hasWater`). The calls stay in place; the stack is the same, so the frames stay valid.
    `IN_WATER` and placement types with their own predicate (UA's polar bears on ice) are unchanged.
*   **Fails safe**: method not found, or not exactly two `isValidEmptySpawnBlock` calls in it: skipped.
*   **Tested**: transformer chain and SRG names; `PatchTests` "P37" (grass floor; dry, waterlogged feet, waterlogged
    head; in-water unchanged).

## P38. The server loop at the target tick rate (`/tick`)

*   **Class**: `net.minecraft.server.MinecraftServer`, methods `run` and `updateTimeLightAndEntities`.
    **Transformer**: `asm/TickTransformer`. **Hooks**: `command/tick/TickHooks.msPerTick`, `runTick`, `functions`.
    **Since**: 1.4.4.
*   **Vanilla change**: 1.20.3's `/tick rate` and `/tick sprint`. 26.3's loop waits `nanosecondsPerTick()` between
    ticks, and while sprinting runs ticks with no wait at all; functions do not run while the game is frozen.
    1.12.2's loop waits a constant 50 ms.
*   **Why a patch**: the 50 ms are constants inside the loop; no event can change how long it waits.
*   **What changes**: `run`'s four `LDC 50L` become `TickHooks.msPerTick()` (the current tick's length in whole
    milliseconds, carrying the remainder so 30 per second averages 33.3 ms), and its two `this.tick()` calls become
    `TickHooks.runTick(this)`: one tick a slot, several when a tick is shorter than a millisecond, and while
    sprinting, ticks back to back for up to 50 ms with that wall time as the slot (so nothing is caught up after a
    sprint). `updateTimeLightAndEntities`' `getFunctionManager().update()` goes through `TickHooks.functions`. The
    stack is the same before and after and no local is read, so the frames stay valid. With `/tick` off, or at 20
    per second, every hook does what the constant and the call did.
*   **Fails safe**: methods not found, or not exactly four `50L`, two `tick()` and one function update: skipped,
    and `/tick rate` and `/tick sprint` change nothing (freezing still works).
*   **Tested**: transformer chain and SRG names (`PatchTests`); `CommandTests` measures 8 and 100 per second, the
    whole-millisecond slots at 30 per second, and a 400-tick sprint.

## P39. Freezing (`/tick freeze`), both sides

*   **Classes**: `net.minecraft.world.World` (`updateEntityWithOptionalForce`, `updateEntities`) and
    `net.minecraft.world.WorldServer` (`tick`). **Transformer**: `asm/TickTransformer`. **Hooks**:
    `TickHooks.skipEntity`, `updateTile`, `frozenWorldTick`. **Since**: 1.4.4.
*   **Vanilla change**: while frozen (and not stepping), 26.3's `ServerLevel.tick` skips the world border, weather,
    time, block and fluid ticks, random ticks, spawning, raids and block events; entities skip their tick unless they
    are players or carry one (`TickRateManager.isEntityFrozen`), and block entities skip theirs (`Level.tickBlockEntities`).
    The client's level does the same with the client's copy of the state.
*   **Why a patch**: 1.12.2 has no event before an entity's or a block entity's tick that can cancel it, and none
    that can stop the world's own tick.
*   **What changes**: `WorldServer.tick` and `World.updateEntityWithOptionalForce` start with
    `if (hook(...)) return;` (one branch with an `F_SAME` frame at the head, where the locals are exactly the
    parameters); while frozen, `frozenWorldTick` still ticks the chunk provider (unloading) and the player chunk map
    (chunks reach players), as 26.3 does. `updateEntities`' `ITickable.update()` call goes through `updateTile`.
*   **Fails safe**: methods not found, or not exactly one `ITickable.update` call: skipped.
*   **Tested**: transformer chain and SRG names; `CommandTests` freezes a pig, a burning furnace and the clock,
    steps them exactly 3 ticks, and checks a pig carrying a player keeps ticking.

## P40. Freezing on the client

*   **Classes**: `WorldClient` (`tick`), `Minecraft` (`runTick`), `RenderManager` (`renderEntityStatic`),
    `TileEntityRendererDispatcher` (`render(TileEntity, float, int)`). **Transformer**: `asm/TickTransformer`.
    **Hooks**: `command/tick/client/ClientTicks`. **Since**: 1.4.4.
*   **Vanilla change**: 26.3's client follows the server's tick state: while frozen its clock, particles and ambient
    particles (`animateTick`) wait, and `DeltaTracker` gives frozen entities and block entities a partial tick of 1,
    so they draw where their last tick left them instead of jittering between ticks.
*   **Why a patch**: the time setters, particle ticks and the renderers' partial tick are inside these methods.
*   **What changes**: `WorldClient.tick`'s `setTotalWorldTime` and `setWorldTime`, and `runTick`'s
    `ParticleManager.updateEffects()` and `WorldClient.doVoidFogParticles(...)`, become static hooks with the same
    stack; the two renderers start with `partialTicks = hook(arg, partialTicks)` (the parameters read on the first
    line). The timer's tick length follows the server's rate when it is slower than 20 per second
    (`Minecraft.getTickTargetMillis`), through reflection, not a patch, set at the start of every frame
    (`RenderTickEvent`) as 26.3's `DeltaTracker.Timer.advanceGameTime` asks for it each frame; with no world it is
    always 50 ms. As in 26.3, the whole client tick runs at that rate, the player and screen ticks included; only
    freezing exempts players. 1.12.2 reads a screen's mouse and keys once a tick (`runTick`'s `handleInput`), 26.3
    every frame, so while the tick is longer than 50 ms an open screen also gets `handleInput` every frame (same
    event, no patch).
*   **Fails safe**: any method not found, or not exactly one of each call: skipped, and the client draws frozen
    things as moving.
*   **Tested**: transformer chain and SRG names; `ClientTickTests` (rate sync, timer, screen input every frame
    while slowed, the title screen at 50 ms on its first frame, the player ticking on both sides while frozen,
    frozen pig and clock, partial ticks, a step, reset on leaving).

## P41. A jukebox's comparator signal for discs added after 1.12.2

*   **Class**: `net.minecraft.block.BlockJukebox`, method `getComparatorInputOverride`.
    **Transformer**: `asm/JukeboxSignalTransformer`. **Hook**: `mechanics/JukeboxSignal.has`, `signal`. **Since**: 1.4.4.
*   **Vanilla change**: since 1.21 each song carries its own `comparator_output` (`JukeboxSong`; 26.3's Bounce gives
    8). 1.12.2 counts from the disc's item id (`id + 1 - id of record_13`), right for its own twelve discs only, and a
    modded disc gives a number far above 15.
*   **Why a patch**: the signal is the block's own method, and no Forge event or interface lets an item answer it.
*   **What changes**: the method starts with `if (JukeboxSignal.has(world, pos)) return JukeboxSignal.signal(world, pos);`
    (one branch, an `F_SAME` frame at the head, where the locals are exactly the parameters). A disc answers when its
    item class has a public `int comparatorSignal()` (found by name, so a backport needs no Core type); 0 to 15. Every
    other disc, and an empty jukebox, keep 1.12.2's answer.
*   **Fails safe**: method not found: skipped, and every disc gives 1.12.2's signal.
*   **Tested**: transformer chain and SRG names; `GdRequestTests` (13 and wait as before, a test disc with 8, empty 0).

## P42. Borderless fullscreen

*   **Class**: `net.minecraft.client.Minecraft`, method `toggleFullscreen()V` (`func_71352_k`).
    **Transformer**: `asm/FullscreenTransformer`. **Hook**: `presentation/client/BorderlessFullscreen.borderlessToggle`.
    **Since**: 1.4.4.
*   **Vanilla change**: 26.1 added Exclusive Fullscreen (Video Settings, Display); off, Fullscreen is a borderless window
    the size of the monitor. 26.3 made off the default. 1.12.2's fullscreen is always exclusive (`Display.setFullscreen`).
*   **Why a patch**: F11, the Fullscreen button and the start with `fullScreen:true` in options.txt all go through
    `toggleFullscreen`, which changes the monitor's mode itself; no event comes before it. LWJGL 2 makes a window
    undecorated only when it makes one, so the toggle has to make a new window of the monitor's size with
    `org.lwjgl.opengl.Window.undecorated` set (`Display.setDisplayMode` with another size makes a new native window and
    keeps the GL context).
*   **What changes**: the method starts with `if (BorderlessFullscreen.borderlessToggle(this)) return;` (one branch, an
    `F_SAME` frame at the head, where the only local is `this`). The hook answers false (vanilla's toggle runs) unless
    the newest look is 26.1 or later with Exclusive Fullscreen off, or the game is leaving Core's borderless window. It
    then does what vanilla's method does around the mode: the `fullscreen` flags, the game's size (`resize`), VSync, a
    frame; leaving puts back the window's size (`tempDisplayWidth`/`Height`), place and resizability (Forge's MC-68754
    fix).
*   **Fails safe**: method not found, or not exactly one `Display.setFullscreen(Z)V` call in it (a launcher's or
    Cleanroom's own method): skipped, and Fullscreen is 1.12.2's. Under Cleanroom's LWJGL 3 the option is not shown and
    the hook always answers false. An exception inside the hook is logged, the undecorated property is cleared and
    vanilla's toggle does not run on top of it (the next F11 starts again from the window LWJGL has).
*   **Tested**: transformer chain and SRG names (`PatchTests`); the client self-test (`ClientGdLookTests`): P42 applied,
    false below 26.1, and a real round trip on the desktop's monitor: in, an undecorated window of the monitor's size at
    0, 0 with LWJGL not in exclusive mode; out, the game's window size again, decorated and resizable.
*   **1.4.5**: the same hook enters exclusive fullscreen at the mode Fullscreen Resolution (26.3's Exclusive
    Fullscreen Mode) names, in place of 1.12.2's desktop mode (`FullscreenModes.enter`: the `fullscreen` flags,
    `Display.setDisplayMode`, `Display.setFullscreen(true)`, then the same size, VSync and frame steps); with Current,
    or a mode the monitor no longer lists, it answers false as before. Leaving is vanilla's.
    On Cleanroom (LWJGL 3 through lwjglx) the borderless window is lwjglx's own `Display.setBorderless(boolean)`
    (GLFW's windowed mode over the target monitor), found by reflection; `VanillaRevisions.borderlessPossible` is true
    there when it exists. Before 1.4.5 Core stepped aside on Cleanroom and F11 changed the monitor's mode.
    With OptiFine, `Config.checkDisplayMode` makes the first fullscreen frame exclusive at its own Fullscreen Mode;
    on entering the borderless window Core sets its `fullscreenModeChecked` (true) and `desktopModeChecked` (false)
    by reflection, as OptiFine does after its own check, so the window stays borderless.

## P44. Grass, foliage and water colors under ground

*   **Class**: `net.minecraft.world.biome.BiomeColorHelper` (client), method `getColorAtPos`.
    **Transformer**: `asm/BiomeColorTransformer`. **Hook**: `worldgen/UndergroundBiomes.colorBiome`. **Since**: 1.4.4.
*   **Vanilla change**: since 1.18 biomes are three-dimensional and the 3 by 3 color blend reads the biome at the block,
    so grass, leaves and water in an underground biome (26.2's sulfur caves) take its colors.
*   **Why a patch**: the blend asks `IBlockAccess.getBiome`, the column's biome; no Forge event hands over the position
    with the biome (`BiomeEvent.GetGrassColor` has the biome only).
*   **What changes**: the one `IBlockAccess.getBiome(pos)` call becomes `UndergroundBiomes.colorBiome(access, pos)`, same
    stack: the client's underground span there if a backport registered one, else the access's answer. A client
    holding no spans takes one map check per call.
*   **Fails safe**: method or call not found: skipped, and colors stay the column's.
*   **Tested**: transformer chain and SRG names; Game Drops Backport's client test (sulfur caves grass `#aba64f` and
    water `#34bf89` at a registered span, the column's color above it).

## P43. An off-map player marker keeps its facing

*   **Class**: `net.minecraft.world.storage.MapData`, method `updateDecorations` (`func_191095_a`).
    **Transformer**: `asm/MapFacingTransformer`. **Hook**: `map/MapFacing.enter`, `facing`. **Since**: 1.4.4.
*   **Vanilla change**: 26.3's `MapItemSavedData.playerDecorationTypeAndRotation` gives `player_off_map` and
    `player_off_limits` the same `calculateRotation` as an on-map player (the Nether's spin included) and draws them
    from pointed sprites; 1.12.2 to 26.2 set their rotation to 0 and draw round dots.
*   **Why a patch**: the rotation is a local of this private method, chosen in the off-map branch.
*   **What changes**: the method starts with `MapFacing.enter(world, name, x, z, rotation)` (parameters read at the
    head, no branch), and its one `new MapDecoration(type, x, y, rotation)` is passed through
    `MapFacing.facing(decoration)` (same stack shape), which rebuilds an off-map or off-limits player marker with
    1.12.2's on-map rotation (`shouldMapSpin` for the spin). Only while a backport registers 26.3
    (`Presentation.installedNewest()`); otherwise the decoration is returned unchanged. On the client, P34's
    `MapIcons.render` draws the two types from `backportcore:textures/map/decorations/player_off_map.png` and
    `player_off_limits.png` (26.3's) under the same condition.
*   **Fails safe**: method not found, or not exactly one `MapDecoration` construction: skipped, and off-map markers
    stay 1.12.2's.
*   **Tested**: transformer chain and SRG names; `GdRequestTests` (off-limits and off-map rotations east and west, an
    on-map marker unchanged, the sprites with and without 26.3).

## P45. Mod list logos in a world

*   **Class**: Forge's `net.minecraftforge.fml.client.GuiModList$Info`, method `drawHeader`.
    **Transformer**: `asm/ModListLogoTransformer`. **Hook**: `presentation/client/PresentationHooks.modListHeader`.
    **Since**: 1.4.4.
*   **Bug fixed**: Forge's own. Opened from the pause menu, the mod list drew every logo black: `GuiScrollingList`
    draws the in-world gradient with vertex colors, which leave OpenGL's current color near black after
    `GlStateManager.resetColor()`, and `drawHeader` draws the logo with no color of its own. From the title screen the
    dirt branch's `color(1, 1, 1, 1)` happened to be applied, so logos showed there.
*   **What changes**: `drawHeader` starts with `modListHeader()`: `resetColor()` then `color(1, 1, 1, 1)`, so white is
    set for real. Nothing else is drawn differently.
*   **Fails safe**: method not found: skipped, and logos stay black in a world.
*   **Tested**: `ClientMenuShots` (`menu-modlist.png` from the title screen, `menu-ingame-modlist.png` in a world).

## P46. Simulation Distance

*   **Classes**: `net.minecraft.world.World` (`updateEntityWithOptionalForce`, `updateEntities`) and
    `net.minecraft.world.WorldServer` (`tickUpdates`, `updateBlocks`). **Transformer**: `asm/SimulationTransformer`.
    **Hooks**: `simulation/SimulationDistance.entityArea`, `tileLoaded`, `scheduledTickLoaded`, `randomTickChunks`.
    **Since**: 1.4.5.
*   **Vanilla change**: 1.18 split Simulation Distance from Render Distance. 26.3's `DistanceManager` gives each player's
    chunk a ticket at level `31 - simulationDistance` that rises by one a chunk: entities, scheduled block and fluid
    ticks and spawning run where the level is 31 or lower (`inEntityTickingRange`), block entities and random ticks
    where it is 32 or lower (`inBlockTickingRange`). 1.12.2 ticks everything loaded (entities with a 32-block loaded
    margin, random ticks within 128 blocks of a player).
*   **Why a patch**: 1.12.2 has no event before an entity's, a block entity's or a scheduled tick that can hold it back
    (Forge's `EntityEvent.CanUpdate` can only let an entity in an unloaded area tick), and the random tick loop takes
    its chunks from `PlayerChunkMap` with no hook. Natural spawning needs none: `WorldEvent.PotentialSpawns` is
    cancelled outside the distance (`SimulationDistance.Events`).
*   **What changes**: four calls, each exactly once in its method. In `updateEntityWithOptionalForce` the result of the
    private `isAreaLoaded(IIIIIIZ)Z` (reached only for a forced update of an entity that is not a player) passes
    through `entityArea(Z, World, Entity)`: false outside the distance, after the entity's despawn check (26.3's
    `checkDespawn` runs before its range test), so the method returns as for an unloaded area. In `updateEntities`
    the `isBlockLoaded(BlockPos, Z)` before a block entity's tick becomes `tileLoaded`; in `tickUpdates` the
    `isAreaLoaded(BlockPos, BlockPos)` becomes `scheduledTickLoaded` (false: 1.12.2 schedules the tick again for the
    next tick, as for an unloaded area); in `updateBlocks` Forge's `getPersistentChunkIterable(Iterator)` becomes
    `randomTickChunks`, which still does the loop's lighting work (`enqueueRelightChecks`, `onTick`) for chunks outside
    and hands only the chunks inside to thunder, ice and snow and random ticks. Spawn chunks and chunks with Forge
    chunk tickets always tick. With no distance set (the option hidden, a dedicated server without a 1.18 backport)
    every hook answers as vanilla. Client worlds are never limited.
*   **Fails safe**: a method missing, or not exactly one of its call: that class is left alone and the patch is
    reported skipped (if `World` was patched and `WorldServer` not, entities and block entities are limited and
    block and random ticks are not).
*   **Tested**: transformer chain and SRG names (`PatchTests`); `SimulationTests` (distance 3, no players: a pig, a
    burning furnace and a pressed button at the spawn tick, the same far away hold until the distance is off; the
    square edges at 4 and 5 chunks with a stand-in player; spawning denied outside; server.properties);
    `ClientVideoShots` (the option reaches the game's own server).

## P47. 1.19.3's creative tabs

*   **Class**: `net.minecraft.client.gui.inventory.GuiContainerCreative` (`initGui`, `drawScreen`,
    `drawGuiContainerBackgroundLayer`, `renderToolTip`, `isMouseOverTab`, `renderCreativeInventoryHoveringText`,
    `drawTab`). **Transformer**: `asm/CreativeTabsTransformer`. **Hooks**: `creative/client/CreativeScreenHooks`.
    **Since**: 1.4.5.
*   **Vanilla change**: 1.19.3 (22w42a) replaced the creative tabs (`CreativeModeTabs`): Building Blocks, Colored Blocks,
    Natural Blocks, Functional Blocks, Redstone Blocks, Saved Hotbars and Search on top, Tools & Utilities, Combat,
    Food & Drinks, Ingredients, Spawn Eggs, Operator Utilities and the Survival Inventory below. Seven tabs to a row,
    26 pixels wide and 27 apart (`getTabX`: `27 * column`, or `imageWidth - 27 * (7 - column) + 1` aligned right), where
    1.12.2 has six 28 wide (`28 * column + column`, or `xSize - 28 * (6 - column) + 2`). The icon sits 5 pixels in, not 6.
*   **Why a patch**: the screen places every tab by 1.12.2's arithmetic in three protected methods and picks a page's tabs
    by index range in two others, with no event; 1.19.3's first page holds 13 or 14 tabs where 1.12.2's holds 12. A
    subclass of the screen cannot reach its private `setCurrentCreativeTab` or the static `selectedTabIndex` that every
    other mod's code reads.
*   **What changes**: in `initGui` the one `CREATIVE_TAB_ARRAY.length` (the page count) becomes `tabCount(array)` and the
    one `CREATIVE_TAB_ARRAY[i]` (the tab it opens on) `openTab(array, i)`, each an instruction replaced by a static call
    taking and leaving the same stack; in `drawScreen` and `drawGuiContainerBackgroundLayer` Forge's one
    `Arrays.copyOfRange` (the page's tabs) becomes `tabsOnPage` with the same arguments, and in the background the one
    `getTabPage()` (is the open tab on the page shown) `pageOf(tab)`; in `renderToolTip` the one
    `Item.getCreativeTab()` (the search tab's tooltip line) becomes `searchTooltipTab(item)`; the whole bodies of
    `isMouseOverTab`, `renderCreativeInventoryHoveringText` and `drawTab` become one call each to the hook of that name.
    Until 1.19.3's tabs are on (`ModernTabs.active`: the inventory part at 1.19.3 or later) every hook runs 1.12.2's code
    line for line, with Core's eleven added tabs (made at load complete, after every mod's) on no page and left out of
    the count. A tab another mod makes after Core's keeps the place it would have had without them (`compact`). The
    tab sprites are the backport's `tabs.png` (Wild Update's, 1.19.3's); with only an older sheet the hooks draw 1.12.2's
    sprites cut to 26 pixels.
*   **Fails safe**: a method missing, or not exactly one of each changed instruction: the class is left alone, the patch
    is reported skipped, and `ModernTabs.install` adds no tabs, so the screen stays 1.12.2's.
*   **Other mods**: the three replaced bodies drop any earlier transformer's changes to them (none known); a mod that
    replaces the screen keeps its own. Items keep their 1.12.2 tabs, so `getSubItems`, the search tab and JEI are as
    before.
*   **Tested**: transformer chain and SRG names (`PatchTests`; vanilla's unpaged screen in the SRG jar is left alone);
    `ClientMenuShots` (the first page's 13 and 14 tabs, each tab's first item and screenshot, books, eggs, every 1.12.2
    stack in a 1.19.3 tab, search, a late mod tab on page 2, the inventory part off).

## P50. Notification Time

*   **Classes**: `net.minecraft.client.gui.toasts.GuiToast$ToastInstance` (`render`), `net.minecraft.client.gui.GuiSubtitleOverlay`
    (`renderSubtitles`), `net.minecraft.client.gui.GuiIngame` (`updateTick`). **Transformer**:
    `asm/NotificationTimeTransformer`. **Hooks**: `presentation/client/NotificationTime`. **Since**: 1.4.5. Reported as
    `P50-toasts`, `P50-subtitles` and `P50-highlight`, one per class.
*   **Vanilla change**: 1.19.4 added Notification Time (Accessibility Settings, 0.5x to 10x): toasts stay
    `5000.0 * getNotificationDisplayTimeMultiplier()` ms (a system toast its own time times it), subtitles
    `3000 * notificationDisplayTime`, and the held item's name `(int) (40.0 * notificationDisplayTime)` ticks (26.3's
    `ToastManager`, `SubtitleOverlay`, `Hud.tick`).
*   **Why a patch**: 1.12.2's toasts decide when to hide from the time `ToastInstance.render` hands them, with no event; the
    subtitle overlay's 3000 ms and the highlight's 40 ticks are constants in methods with no hook.
*   **What changes**: the long handed to `IToast.draw(GuiToast, long)` passes through `toastTime(long)`, which divides it
    by the multiplier, so every toast (any mod's) stays that much longer; its own timings inside (an advancement toast's
    "Advancement Made!" for 1.5 s) stretch with it. The one `3000L` and the one `3000.0F` in `renderSubtitles` pass through
    `subtitleTime` and `subtitleFade`, and the `40` stored into `remainingHighlightTicks` through `highlightTicks`. Each is
    a value in and the same type out. While the option does not show (before 1.19.4, or the options' part of the look
    off) every hook returns vanilla's value.
*   **Fails safe**: per class, a method missing or not exactly one of its site: that class is left alone and reported
    skipped.
*   **Tested**: transformer chain and SRG names (`PatchTests`); `ClientMenuShots` (`OptionsRowsShots`: applied in the
    client, the hooks at 2x and 1x).

## P51. Quit Shortcuts

*   **Class**: `net.minecraft.client.Minecraft` (`runGameLoop`). **Transformer**: `asm/QuitShortcutsTransformer`. **Hook**:
    `presentation/client/QuitShortcuts`. **Since**: 1.4.5.
*   **Vanilla change**: 26.3 added Quit Shortcuts (Controls, on by default). Off, `Window.onQuitRequested` ignores a quit
    while `InputQuirks.isQuitShortcutDown()`: Alt and F4 held on Windows and Linux, Cmd and Q on macOS.
*   **Why a patch**: 1.12.2 shuts down as soon as `Display.isCloseRequested()` answers true, at the top of
    `runGameLoop`, before any event. LWJGL 2 reports Alt + F4 and the close button alike.
*   **What changes**: the one `isCloseRequested()` result (any owner: LWJGL 2's `Display`, or lwjglx's where Cleanroom has
    moved the reference) passes through `closeRequested(boolean)`, which answers false while the option is off and the
    shortcut's keys are down (`Keyboard.isKeyDown`, from the same frame's `Display.update`). LWJGL 2's flag is cleared when
    read; lwjglx's is GLFW's `glfwWindowShouldClose`, which stays set, so the hook clears it with
    `glfwSetWindowShouldClose(window, false)`.
*   **Fails safe**: not exactly one call: the class is left alone, and quitting is 1.12.2's.
*   **Tested**: transformer chain and SRG names (`PatchTests`); `ClientMenuShots` (applied; with the option off and no keys
    held a close still closes). Alt + F4 on a real window was not tried by hand.

## P52. The sound device and Directional Audio

*   **Class**: `paulscode.sound.libraries.LibraryLWJGLOpenAL` (`init`), Paulscode's OpenAL library (FML's class loader
    transforms it; it is not in Forge's SRG jar, so only the dev chain checks it). **Transformer**:
    `asm/SoundDeviceTransformer`. **Hook**: `presentation/client/SoundDevices`. **Since**: 1.4.5.
*   **Vanilla change**: 1.18 added Device (Music & Sounds: System Default or an OpenAL device; a change reloads
    the sound engine), and 1.19 Directional Audio (OpenAL Soft's HRTF, `ALC_HRTF_SOFT` when the context is
    made).
*   **Why a patch**: Paulscode opens OpenAL with a bare `AL.create()` (the default device, no attributes) on its own
    thread; nothing else can pick the device, and a second `AL.create` throws.
*   **What changes**: that one `AL.create()` (inside its `try` for `LWJGLException`) becomes `SoundDevices.createAL()`, which
    throws the same. LWJGL 2: `AL.create(device, 44100, 60, false)` opens the chosen device (the default when it fails),
    and for HRTF the context is made again with `ALC_HRTF_SOFT` where the device has `ALC_SOFT_HRTF` (LWJGL 2's OpenAL
    Soft has not, so Directional Audio is not shown there). Cleanroom: lwjglx's `AL.create` always opens the default
    device, so the hook moves it with LWJGL 3's `alcReopenDeviceSOFT` or resets it with HRTF (`alcResetDeviceSOFT`).
    Paulscode's compatibility check (`libraryCompatible`, its own `AL.create()` and `AL.destroy()`) is left alone.
*   **Fails safe**: not exactly one `AL.create()` in `init`: left alone, sound opens as 1.12.2's. In the hook, any failure
    to open the chosen device or turn HRTF on keeps the default device and context.
*   **Tested**: transformer chain (`PatchTests`, dev only); `ClientMenuShots` (applied; the devices listed; the sound
    system restarted on the first listed device and back).
## P61. A focused text field's white frame

*   **Class**: `net.minecraft.client.gui.GuiTextField`, method `drawTextBox`. **Transformer**:
    `asm/ScreenPatchesTransformer`. **Hook**: `presentation/client/PresentationHooks.textFieldFrame`. **Since**: 1.4.5.
*   **Vanilla change**: 1.16 (`EditBox.renderButton`) draws a focused text field's frame white (`-1`); 1.12.2's is grey
    (`-6250336`) focused or not. 26.3's `text_field` and `text_field_highlighted` sprites are those two frames on black.
*   **Why a patch**: the frame is one `drawRect` inside `drawTextBox`, which every screen's text field calls; no event.
*   **What changes**: `this` is loaded before the frame's color constant and the two go through
    `textFieldFrame(field, color)`, which answers white for a focused field once 1.16 or later is the newest
    (`VanillaRevisions.textFieldFocusFrame`), else the color. One stack slot more, no local.
*   **Fails safe**: method missing or not exactly one frame color: skipped, the frame stays grey.
*   **Tested**: transformer chain and SRG names (`PatchTests`); `ClientMenuShots` (the hook focused and not, the Add
    Server screen's focused Server Name field in `menu-text-field.png`).

## P62. A slider's handle under the mouse

*   **Classes**: `mouseDragged` of `net.minecraft.client.gui.GuiOptionSlider`, `GuiSlider`, `GuiScreenOptionsSounds$Button`
    and Forge's `net.minecraftforge.fml.client.config.GuiSlider`. **Transformer**: `asm/ScreenPatchesTransformer`.
    **Hook**: `presentation/client/PresentationHooks.sliderHandleRow`. **Since**: 1.4.5.
*   **Vanilla change**: 1.14 (`AbstractSliderButton.renderBg`) draws the handle from the hovered button's row of
    `widgets.png` (86) while the mouse is on the slider; 1.12.2 always draws row 66. 26.3's `slider_handle` and
    `slider_handle_highlighted` sprites are those rows of the family's `widgets.png`, pixel for pixel.
*   **Why a patch**: each slider class draws its own handle in `mouseDragged`; no event.
*   **What changes**: `this` is loaded before each `bipush 66` (two in the vanilla classes, one in Forge's, which draws
    the handle in one box) and the two go through `sliderHandleRow(button, row)`: 86 while `isMouseOver()` once 1.14 or
    later is the newest (`VanillaRevisions.sliderHandleHover`), else the row. Core's own sliders call the hook.
*   **Fails safe**: method missing or not the expected number of rows: that class is skipped, its handle stays 1.12.2's.
*   **Tested**: transformer chain and SRG names (`PatchTests`, Forge's class in the dev chain only); `ClientMenuShots`
    (the hook hovered).
## P57. The active effects beside the inventory

*   **Class**: `net.minecraft.client.renderer.InventoryEffectRenderer`, `drawScreen(IIF)V`. **Transformer**:
    `asm/ScreenPatchesTransformer`. **Hook**: `PresentationHooks.inventoryEffects` (`InventoryEffects.draw`). **Since**:
    1.4.5.
*   **Vanilla change**: 1.18 (`EffectRenderingInventoryScreen`) draws the effects in a column right of the inventory,
    120 or 32 wide, and no longer moves the inventory; 1.21.11 (`EffectsInInventory`) fits each background to its text.
*   **Why a patch**: the drawing is the private `drawActivePotionEffects()`, at `guiLeft - 124`, called from
    `drawScreen` before the survival inventory's tooltip. Forge's `PotionShiftEvent` (cancelled by Core from 1.18)
    stops the shift but not the drawing.
*   **What changes**: the one call to `drawActivePotionEffects()` becomes a static call with the screen as its argument.
    Before 1.18 the hook calls the private method; from 1.18 it draws Core's column, calling Forge's
    `Potion.renderInventoryEffect` for each effect as 1.12.2 does.
*   **Fails safe**: not exactly one call: skipped; 1.12.2's effects.
*   **Tested**: transformer chain and SRG names; `ClientMenuShots` (four effects beside a centered survival inventory,
    each as wide as its text; compact in the creative inventory).

## P58. The recipe book's search box

*   **Class**: `net.minecraft.client.gui.recipebook.GuiRecipeBook`, `render(IIF)V`. **Hook**:
    `PresentationHooks.recipeSearchBox` (`RecipeBookLook`). **Since**: 1.4.5.
*   **Vanilla change**: 1.16 shows "Search..." (`gui.recipebook.search_hint`, gray italics) in the empty, unfocused box;
    1.20.5 puts the box at `yo + 13`, 81 wide (1.12.2's `j + 14`, 80).
*   **Why a patch**: the box is a private field drawn in the middle of `render`, under the tabs and the page.
*   **What changes**: the one `searchBar.drawTextBox()` becomes a static call with the box. The hook moves a new box once
    (1.20.5), draws it, then the hint (1.16). Core's furnace books call the same hook.
*   **Fails safe**: not exactly one `drawTextBox` call: skipped; 1.12.2's box.
*   **Tested**: transformer chain and SRG names; `ClientMenuShots` (the hint drawn, the box at 1.20.5's place).

## P59. Subtitles and Text Background

*   **Class**: `net.minecraft.client.gui.GuiSubtitleOverlay`, `renderSubtitles`. **Hook**:
    `PresentationHooks.subtitleBoxColor`. **Since**: 1.4.5.
*   **Vanilla change**: 1.14's `Options.getBackgroundColor`: with Text Background set to Everywhere, the subtitle box
    takes the Text Background Opacity; with Chat Only it keeps its own (`0xCC000000`).
*   **What changes**: the one `0xCC000000` constant passes through the hook (as P14's chat box).
*   **Fails safe**: not exactly one constant: skipped.
*   **Tested**: transformer chain and SRG names; `ClientMenuShots` (a subtitle drawn through the hook, both settings).

## P60. The profiler pie at the GUI scale

*   **Class**: `net.minecraft.client.Minecraft`, `runGameLoop()V`. **Hook**: `PresentationHooks.profilerPie`
    (`FrameCharts.profilerPie`). **Since**: 1.4.5.
*   **Vanilla change**: 1.20.2 (`ProfilerPieChart`) draws F3 + 1's pie at the GUI scale, 105 across, 10 from the bottom
    or above the F3 + 2 or F3 + 3 charts, on a dark box with the node's path wrapped; 1.12.2's is at window pixels,
    160 across.
*   **Why a patch**: `displayDebugInfo` is private and called once a frame after the GUI, with no event.
*   **What changes**: the one call to `displayDebugInfo(elapsed)` becomes a static call with the game as first argument.
    Before 1.20.2 the hook calls the private method.
*   **Fails safe**: not exactly one call: skipped; 1.12.2's pie.
*   **Tested**: transformer chain and SRG names; `ClientMenuShots` (the pie's box above the charts).

## P65. 26.1's lightmap

*   **Class**: `net.minecraft.client.renderer.EntityRenderer`, `updateLightmap(F)V`. **Transformer**:
    `asm/LightmapTransformer`. **Hook**: `PresentationHooks.modernLightmap` (`presentation/client/ModernLightmap`,
    the formula in `presentation/LightmapColors`). **Since**: 1.4.5.
*   **Vanilla change**: 26.1 rewrote the lightmap (26.3's `lightmap.fsh` and `LightmapRenderStateExtractor`): the same
    brightness curve as 1.12.2 (`level / (4 - 3 level)`), with block light tinted by `visual/block_light_tint`
    (`#FFD88C`) and mixed toward white by `0.9 (2 level - 1)^2`, sky light colored by `visual/sky_light_color` and
    scaled by `visual/sky_light_factor` (the day timeline: white and 1 by day, `#7A7AFF` and 0.24 at night; rain and
    thunder blend toward the night's by 5/16 and 0.527), each dimension's `visual/ambient_light_color` added under
    both (Overworld `#0A0A0A`, Nether `#302821`, End `#3F473F`; the Nether and the End have no sky light), Night
    Vision as a floor of `#999999`, Darkness subtracted before the clamp, then the Brightness option's curve. 1.12.2
    multiplied its own ambient term into the curve and drew the End with a fixed formula.
*   **Why a patch**: the lightmap is computed inside the private `updateLightmap`, called from `renderWorld` each frame
    the flag is set; no event comes between the flag and the upload.
*   **What changes**: at the head, `if (PresentationHooks.modernLightmap(this, partialTicks)) return;` (one branch past a
    `return`, an `F_SAME` frame). From 26.1 (the sky's look, `VanillaRevisions.modernLightmap`) the hook fills
    `lightmapColors` with 26.1's colors, uploads them and clears the flag; otherwise 1.12.2's method runs. Other mods'
    dimensions: `isNether()` takes the Nether's values, the End's provider the End's, any other with sky light the
    Overworld's timeline set by its own sun (`getCelestialAngle`) and weather, one without sky light the Overworld's
    ambient light only. Forge's `WorldProvider.getLightmapColors` is still called for every cell, after the boss
    darkening and before the clamp, as 1.12.2 calls it. Wild Update's Darkness (which fills 1.12.2's lightmap itself
    before each frame) is read from `DarknessClient.factor`; while it is above 0 a highest-priority render tick listener
    fills 26.1's lightmap first with 26.3's Darkness steps, so Wild Update finds nothing to do.
*   **OptiFine**: its `updateLightmap` starts with the same update check, then its Custom Colors, then 1.12.2's loop;
    the hook goes in ahead of all of it. It steps aside (one log line per reason) while a shader pack is loaded and where
    Custom Colors has a lightmap for the dimension (`lightmap/world<id>.png`), so OptiFine's method runs. Dynamic
    Lights changes the light levels, not the lightmap, and works with either.
*   **Fails safe**: method missing, not starting with `aload 0; getfield lightmapUpdateNeeded` then `ifeq` (the dev
    build, OptiFine) or `ifne` (the released game, over a `return`), or a frame at
    its head: skipped, 1.12.2's lightmap. The hook keeps 1.12.2's when the renderer's fields cannot be read.
*   **Tested**: transformer chain and SRG names (`PatchTests`); `LookFeelTests` checks all 256 cells against a
    transcription of `lightmap.fsh` for nine cases (Overworld noon, midnight, dusk in rain, dawn in thunder, Night
    Vision, Darkness, boss fog, Nether, End), within 1/255, and 26.3's values by hand; `ClientVideoShots` shoots each
    of the Overworld by day and night, a torch-lit room, the Nether and the End with 26.1's lightmap and with 1.12.2's
    (`video-light-*-26.1.png`, `video-light-*-1.12.2.png`).
