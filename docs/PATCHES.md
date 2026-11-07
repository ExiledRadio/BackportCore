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
*   A patch only adds calls into the library's hook classes (`pose/MovementHooks`, `pose/client/ClientHooks`).
    Each hook does exactly what vanilla 1.12.2 does (returns vanilla's value, or nothing) until a backport
    has registered rules with `pose.Poses` and they are on. With no backport installed, the patched game
    plays as vanilla.
*   A transformer that does not find the exact shape it expects changes nothing, logs
    `P<n> ...: <why>; patch skipped, vanilla behavior kept.`, and records why (`asm.Asm.state`). It never
    half-patches a method. A patched class logs `P<n> ...: patched.`
*   Only straight-line code is added (no new branches or locals), so the class keeps its stack map frames;
    the writer only recomputes maximum stack sizes.
*   Each patch is read against 1.14.4's decompiled code for its numbers; the hooks' Javadoc names the 1.14.4
    method each part follows.

**Tested** (`gradlew runServer -Pselftest`, `selftest/PatchTests`): P1 reports itself applied when vanilla
loads `EntityLivingBase` in the dev server; the coremod is registered with the class loader; the bytes of
all five target classes, put through the dev class loader's whole transformer chain (Fluidlogged API's
included; Forge's side check left out, since the client classes would be refused on a server), carry exactly
the hook calls listed below, still carry Fluidlogged API's own hooks where it has them (`travel`,
`EntityPlayerSP.onLivingUpdate`), and every method of each class passes ASM's stack and type check; and the
same five classes from Forge's SRG-named jar (`forge-1.12.2-14.23.5.2847-srgBin.jar`, the names a real game
runs with) are patched the same way and verify. The behavior each patch enables is tested by the pose tests
here and by the backports that use it.

## P1. Water travel

*   **Class**: `net.minecraft.entity.EntityLivingBase`, method `travel(FFF)V` (`func_191986_a`).
    **Transformer**: `asm/WaterTravelTransformer`. **Hooks**: `pose/MovementHooks`. **Since**: 1.2.0.
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
    which calls each `PlayerMovement.beforeClientMove`. **Since**: 1.2.0.
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
    `afterAngles`. **Since**: 1.2.0.
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
    `pose/client/ClientHooks.applyRotations`. **Since**: 1.2.0.
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
    **Since**: 1.2.0.
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
