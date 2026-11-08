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
    `presentation/client/PresentationHooks`, `worldgen/WorldgenHooks`).
    Each hook does exactly what vanilla 1.12.2 does (returns vanilla's value, or nothing) until a backport
    has registered rules with `pose.Poses` and they are on, or (P6, P7) registered a presentation version
    with `presentation.Presentation`, or (P8, P9) a backport of 1.13 or later is installed. With no backport
    installed, the patched game plays as vanilla.
*   P6, P7 and P9 only change the owner of an existing call (an instance call becomes a static call with the
    same arguments); the hook calls the original method when it keeps vanilla's behavior. P8 replaces a whole
    method body with one call, and its hook runs 1.12.2's own loop line for line.
*   A transformer that does not find the exact shape it expects changes nothing, logs
    `P<n> ...: <why>; patch skipped, vanilla behavior kept.`, and records why (`asm.Asm.state`). It never
    half-patches a method. A patched class logs `P<n> ...: patched.`
*   Only straight-line code is added (no new branches or locals), so the class keeps its stack map frames;
    the writer only recomputes maximum stack sizes.
*   Each patch is read against 1.14.4's decompiled code for its numbers; the hooks' Javadoc names the 1.14.4
    method each part follows.

**Tested** (`gradlew runServer -Pselftest`, `selftest/PatchTests`): P1 reports itself applied when vanilla
loads `EntityLivingBase` in the dev server; the coremod is registered with the class loader; the bytes of
all nine target classes, put through the dev class loader's whole transformer chain (Fluidlogged API's
included; Forge's side check left out, since the client classes would be refused on a server), carry exactly
the hook calls listed below, still carry Fluidlogged API's own hooks where it has them (`travel`,
`EntityPlayerSP.onLivingUpdate`), and every method of each class passes ASM's stack and type check; and the
same nine classes from Forge's SRG-named jar (`forge-1.12.2-14.23.5.2847-srgBin.jar`, the names a real game
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
    `drawGradient`. **Since**: 1.2.0.
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
    **Since**: 1.2.0.
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
    **Since**: 1.2.0 (Update Aquatic Backport's P5 before).
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
    **Since**: 1.2.0 (Update Aquatic Backport's P4 before).
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
