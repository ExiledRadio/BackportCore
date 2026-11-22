package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.asm.ExtendedFluidStatesTransformer;
import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.util.EnumHelper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * docs/PATCHES.md: every patch applies, in this dev environment alongside Fluidlogged API's transformers, and
 * on the SRG-named classes a real game runs (Forge's own srgBin jar from the Gradle cache).
 *
 * <p>The client classes are never loaded on a dedicated server, so their bytes go through the class loader's
 * transformer chain by hand here (every transformer except Forge's side check, which would refuse them);
 * the result is checked for the hook calls and for a consistent stack in every method.
 */
final class PatchTests {

    private static final String SRG_JAR = System.getProperty("user.home")
            + "/.gradle/caches/minecraft/net/minecraftforge/forge/1.12.2-14.23.5.2847/stable/39/forge-1.12.2-14.23.5.2847-srgBin.jar";


    /** class, patch, hook names that must appear, each with its count */
    private static final Object[][] CLASSES = {
            {"net.minecraft.entity.EntityLivingBase", "P1", new String[] {"beforeTravel", "waterSlowDown", "waterGravity"}},
            {"net.minecraft.client.entity.EntityPlayerSP", "P2", new String[] {"beforeLivingUpdate"}},
            {"net.minecraft.client.model.ModelBiped", "P3", new String[] {"beforeAngles", "afterHead", "afterAngles"}},
            {"net.minecraft.client.renderer.entity.RenderPlayer", "P4", new String[] {"applyRotations"}},
            {"net.minecraft.client.renderer.EntityRenderer", "P5", new String[] {"cameraEyeHeight"}},
            {"net.minecraft.client.gui.GuiMainMenu", "P6", new String[] {"renderSkybox", "drawGradient*2"}},
            {"net.minecraft.client.Minecraft", "P7", new String[] {"processKeyF3"}},
            {"net.minecraft.world.biome.BiomeProvider", "P8", new String[] {"findBiomePosition"}},
            {"net.minecraft.world.gen.structure.MapGenVillage", "P9", new String[] {"villageBiomes"}},
            {"net.minecraft.world.gen.layer.GenLayer", "P10", new String[] {"similarBiomes"}},
            {"net.minecraft.world.gen.layer.GenLayerBiomeEdge", "P11", new String[] {"canBeNeighbors"}},
            {"net.minecraft.client.gui.GuiButton", "P12", new String[] {"buttonTextColor*2"}},
            {"net.minecraftforge.fml.client.config.GuiButtonExt", "P12", new String[] {"buttonTextColor*2"}},
            {"net.minecraft.client.gui.GuiIngame", "P13", new String[] {"drawChat"}},
            {"net.minecraftforge.client.GuiIngameForge", "P13", new String[] {"drawChat"}},
            {"net.minecraft.client.gui.GuiChat", "P14", new String[] {"chatComponent*2", "chatBoxColor"}},
            {"net.minecraft.client.renderer.RenderGlobal", "P15", new String[] {"skyColor", "cloudHeight*2"}},
            {"net.minecraft.client.renderer.EntityRenderer", "P16", new String[] {"skyColor", "cloudLayer*2"}},
            {"net.minecraft.client.renderer.RenderGlobal", "P17", new String[] {"nearbyDistanceSq", "needsImmediateUpdate*2"}},
            {"net.minecraft.client.renderer.RenderGlobal", "P18", new String[] {"terrainStart"}},
            {"net.minecraft.client.gui.GuiScreen", "P31", new String[] {"menuDirt", "menuWorldGradient"}},
            {"net.minecraft.client.gui.GuiSlot", "P31", new String[] {"listBackground", "listOverlay*2", "listMaxScroll"}},
            {"net.minecraft.client.Minecraft", "P32", new String[] {"narratorCtrl"}},
            {"net.minecraft.entity.player.EntityPlayer", "P33", new String[] {"eyesFor", "eyeHeight"}},
            {"net.minecraft.world.storage.MapDecoration", "P34", new String[] {"render"}},
            {"net.minecraft.block.BlockPane", "P35", new String[] {"torchOnPane"}},
            {"net.minecraft.world.biome.Biome", "P36", new String[] {"switchIndex"}},
            {"net.minecraft.world.WorldEntitySpawner", "P37", new String[] {"feet", "head"}},
            {"net.minecraft.server.MinecraftServer", "P38", new String[] {"msPerTick*4", "runTick*2", "functions"}},
            {"net.minecraft.world.World", "P39", new String[] {"skipEntity", "updateTile"}},
            {"net.minecraft.world.WorldServer", "P39", new String[] {"frozenWorldTick"}},
            {"net.minecraft.client.multiplayer.WorldClient", "P40", new String[] {"setTotalWorldTime", "setWorldTime"}},
            {"net.minecraft.client.Minecraft", "P40", new String[] {"updateParticles", "animateTick"}},
            {"net.minecraft.client.renderer.entity.RenderManager", "P40", new String[] {"entityPartialTicks"}},
            {"net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher", "P40", new String[] {"tilePartialTicks"}},
            {"net.minecraft.block.BlockJukebox", "P41", new String[] {"has", "signal"}},
            {"net.minecraft.client.Minecraft", "P42", new String[] {"borderlessToggle"}},
            {"net.minecraft.world.biome.BiomeColorHelper", "P44", new String[] {"colorBiome"}},
            {"net.minecraft.world.storage.MapData", "P43", new String[] {"enter", "facing"}},
            {"net.minecraft.world.World", "P46", new String[] {"entityArea", "tileLoaded"}},
            {"net.minecraft.world.WorldServer", "P46", new String[] {"scheduledTickLoaded", "randomTickChunks"}},
            {"net.minecraft.client.gui.inventory.GuiContainerCreative", "P47", new String[] {"tabCount", "openTab", "tabsOnPage*2",
                    "pageOf", "searchTooltipTab", "isMouseOverTab", "renderCreativeInventoryHoveringText", "drawTab"}},
            {"net.minecraft.client.gui.GuiTextField", "P61", new String[] {"textFieldFrame"}},
            {"net.minecraft.client.gui.GuiOptionSlider", "P62", new String[] {"sliderHandleRow*2"}},
            {"net.minecraft.client.gui.GuiSlider", "P62", new String[] {"sliderHandleRow*2"}},
            {"net.minecraft.client.gui.GuiScreenOptionsSounds$Button", "P62", new String[] {"sliderHandleRow*2"}},
            {"net.minecraftforge.fml.client.config.GuiSlider", "P62", new String[] {"sliderHandleRow"}},
    };

    private PatchTests() {
    }

    static void schedule() {
        then(1, "patches in the running game", world -> {
            check("P1 applied when EntityLivingBase loaded (dev, beside Fluidlogged API)", "applied".equals(Asm.state("P1")), Asm.state("P1"));
            check("coremod is registered with FML", isTransformerRegistered(), "");
            // Before the SRG run below, which reports P46 skipped on vanilla's (Forge-less) World.
            check("P46 applied when World and WorldServer loaded", "applied".equals(Asm.state("P46")), Asm.state("P46"));
        });
        then(1, "P36: a creature type added after the spawn list switch ran", world -> {
            check("P36 applied when Biome loaded", "applied".equals(Asm.state("P36")), Asm.state("P36"));
            Biome plains = Biomes.PLAINS;
            plains.getSpawnableList(EnumCreatureType.MONSTER); // the switch has run: its table exists
            int before = EnumCreatureType.values().length;
            EnumCreatureType late = EnumHelper.addCreatureType("BACKPORTCORE_SELFTEST_LATE", IAnimals.class, 0, Material.AIR, true, false);
            check("late type is past the table's end (ordinal " + late.ordinal() + ", " + before + " types when the switch ran)",
                    late.ordinal() >= before, late.ordinal());
            List<Biome.SpawnListEntry> list = null;
            String error = null;
            try {
                list = plains.getSpawnableList(late);
            } catch (RuntimeException e) {
                error = e.toString();
            }
            check("getSpawnableList(late type) returns Forge's empty modded list instead of throwing",
                    list != null && list.isEmpty(), error);
            check("the same list comes back the second time (Forge's modSpawnableLists)", list != null && list == plains.getSpawnableList(late), "");
            check("vanilla types still get their own lists", plains.getSpawnableList(EnumCreatureType.CREATURE) == plains.getSpawnableList(EnumCreatureType.CREATURE)
                    && !plains.getSpawnableList(EnumCreatureType.CREATURE).isEmpty(), "");
        });
        then(1, "P37: no ground spawn in a block that holds water", world -> {
            check("P37 applied when WorldEntitySpawner loaded", "applied".equals(Asm.state("P37")), Asm.state("P37"));
            EntityLiving.SpawnPlacementType ground = EntityLiving.SpawnPlacementType.ON_GROUND;
            BlockPos floor = new BlockPos(4400, 150, 4400);
            BlockPos feet = floor.up();
            BlockPos head = feet.up();
            world.setBlockState(floor, Blocks.GRASS.getDefaultState(), 2);
            world.setBlockState(feet, Blocks.AIR.getDefaultState(), 2);
            world.setBlockState(head, Blocks.AIR.getDefaultState(), 2);
            world.setBlockState(head.up(), Blocks.AIR.getDefaultState(), 2);
            check("control: grass with air above is a ground spawn spot", WorldEntitySpawner.canCreatureTypeSpawnBody(ground, world, feet), "");
            world.setBlockState(feet, TestBlocks.optional.getDefaultState(), 2);
            check("control: the same block dry (not liquid material, not a full cube) is still a spot",
                    !Waterlogging.hasWater(world, feet) && WorldEntitySpawner.canCreatureTypeSpawnBody(ground, world, feet), world.getBlockState(feet));
            Waterlogging.setWater(world, feet, true, 2);
            check("the feet block holds water (kelp, seagrass, waterlogged)", Waterlogging.hasWater(world, feet), world.getBlockState(feet));
            check("water held at the feet: no ground spawn (1.13's fluid check)", !WorldEntitySpawner.canCreatureTypeSpawnBody(ground, world, feet), "");
            world.setBlockState(feet, Blocks.AIR.getDefaultState(), 2);
            Waterlogging.placeWithWater(world, head, TestBlocks.optional.getDefaultState(), 2);
            Waterlogging.setWater(world, head, true, 2);
            check("water held at the head: no ground spawn", Waterlogging.hasWater(world, head)
                    && !WorldEntitySpawner.canCreatureTypeSpawnBody(ground, world, feet), world.getBlockState(head));
            world.setBlockState(head, Blocks.WATER.getDefaultState(), 2);
            check("in-water spawning is unchanged: water over water", world.setBlockState(feet, Blocks.WATER.getDefaultState(), 2)
                    && world.setBlockState(floor, Blocks.WATER.getDefaultState(), 2)
                    && WorldEntitySpawner.canCreatureTypeSpawnBody(EntityLiving.SpawnPlacementType.IN_WATER, world, feet), "");
            for (BlockPos p : new BlockPos[] {floor, feet, head}) {
                world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
            }
        });
        then(1, "P37: chunk generation's animals stay out of a kelp bed", world -> {
            // 16 x 16 columns at y 200: stone, two plant blocks (a kelp column's shape), then water or air.
            int wet = bed(world, 4800, true);
            int dry = bed(world, 4832, false);
            check("generation spawning on the dry bed still places animals (the path works)", dry > 0, dry);
            check("no animal placed in the waterlogged bed", wet == 0, wet);
        });
        then(1, "patches through the dev transformer chain", world -> {
            for (Object[] c : CLASSES) {
                String name = (String) c[0];
                byte[] raw = Launch.classLoader.getClassBytes(name);
                if (!check("dev bytes of " + name, raw != null)) {
                    continue;
                }
                byte[] out = raw;
                for (IClassTransformer t : Launch.classLoader.getTransformers()) {
                    if (t.getClass().getName().endsWith("SideTransformer")) {
                        continue;
                    }
                    out = t.transform(name, name, out);
                }
                verify("dev " + c[1], name, out, (String[]) c[2]);
                if (name.endsWith("EntityPlayerSP")) {
                    check("dev P2 beside Fluidlogged API: its own sprint hook is still there",
                            countCalls(read(out), "canFlyDisableSprint") == 1, countCalls(read(out), "canFlyDisableSprint"));
                }
                if (name.endsWith("EntityLivingBase")) {
                    check("dev P1 beside Fluidlogged API: it still patched travel", countOwner(read(out), "git/jbredwards/fluidlogged_api") > 0, "");
                }
            }
        });
        then(1, "P20 on Chunk (dev and SRG names)", world -> {
            String state = Asm.state("P20");
            boolean cc = Launch.classLoader.findResource("com/exiledradio/cavesandcliffsbackport/height/Heights.class") != null;
            check("P20 " + (cc ? "applied with" : "left out without") + " Caves & Cliffs Backport",
                    cc ? "applied".equals(state) : "Caves & Cliffs Backport not installed".equals(state), state);
            byte[] dev = Launch.classLoader.getClassBytes("net.minecraft.world.chunk.Chunk");
            if (check("dev bytes of Chunk", dev != null)) {
                verifyP20("dev P20", ExtendedFluidStatesTransformer.patch(dev));
            }
            File jar = new File(SRG_JAR);
            if (jar.isFile()) {
                try (ZipFile zip = new ZipFile(jar)) {
                    ZipEntry entry = zip.getEntry("net/minecraft/world/chunk/Chunk.class");
                    if (check("SRG bytes of Chunk", entry != null)) {
                        verifyP20("SRG P20", ExtendedFluidStatesTransformer.patch(readAll(zip.getInputStream(entry))));
                    }
                }
            }
        });
        then(1, "patches on SRG names", world -> {
            File jar = new File(SRG_JAR);
            if (!check("Forge's SRG jar in the Gradle cache", jar.isFile(), SRG_JAR)) {
                return;
            }
            try (ZipFile zip = new ZipFile(jar)) {
                for (Object[] c : CLASSES) {
                    String name = (String) c[0];
                    if (name.startsWith("net.minecraftforge.")) {
                        // Forge's own classes are not in the SRG jar: Forge ships them with Mojang's obfuscated
                        // references, and FML's remapper gives them the same SRG names before these transformers
                        // run. The transformer matches both names; the dev chain above checks the shape.
                        continue;
                    }
                    ZipEntry entry = zip.getEntry(name.replace('.', '/') + ".class");
                    if (!check("SRG bytes of " + name, entry != null)) {
                        continue;
                    }
                    byte[] raw = readAll(zip.getInputStream(entry));
                    byte[] out = transformer((String) c[1]).transform(name, name, raw);
                    if (name.endsWith(".GuiContainerCreative")) {
                        // Forge pages the creative tabs (Arrays.copyOfRange, the tab count); the SRG jar is vanilla's,
                        // which has neither. The patch must leave it alone there; the dev chain above checks the patch.
                        check("SRG P47 on unpaged GuiContainerCreative: left alone (Forge's paging missing)", out == raw, Asm.state("P47"));
                        continue;
                    }
                    if (name.endsWith(".MapDecoration")) {
                        // Forge adds MapDecoration.render(int); the SRG jar is vanilla's, which has none. The patch
                        // must leave it alone there; the dev chain above checks the patch.
                        check("SRG P34 on unpatched MapDecoration: left alone (Forge's render missing)", out == raw,
                                Asm.state("P34"));
                        continue;
                    }
                    if ("P46".equals(c[1])) {
                        // Forge's block entity check (isBlockLoaded(pos, false)) and getPersistentChunkIterable are
                        // Forge's patches; the SRG jar is vanilla's. P46 must leave it alone there, and the SRG names
                        // it looks for must be vanilla's.
                        check("SRG P46 on unpatched " + name + ": left alone (Forge's calls missing)", out == raw, Asm.state("P46"));
                        ClassNode n = read(raw);
                        boolean isWorld = name.endsWith(".World");
                        int srg = 0;
                        for (MethodNode m : n.methods) {
                            if (m.name.equals(isWorld ? "func_72866_a" : "func_72955_a")) {
                                for (AbstractInsnNode insn : m.instructions.toArray()) {
                                    if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).name.equals(isWorld ? "func_175663_a" : "func_175707_a")) {
                                        srg++;
                                    }
                                }
                            }
                        }
                        check("SRG P46: " + (isWorld ? "updateEntityWithOptionalForce calls func_175663_a" : "tickUpdates calls func_175707_a") + " once",
                                srg == 1, srg);
                        continue;
                    }
                    if (name.endsWith(".WorldEntitySpawner")) {
                        // Forge moves the spawn spot check into canCreatureTypeSpawnBody; the SRG jar is vanilla's,
                        // where it is still in canCreatureTypeSpawnAtLocation. P37 must leave it alone there, and
                        // the SRG name it looks for must be the one vanilla's check calls (twice).
                        check("SRG P37 on unpatched WorldEntitySpawner: left alone (Forge's canCreatureTypeSpawnBody missing)",
                                out == raw, Asm.state("P37"));
                        int srgCalls = 0;
                        for (MethodNode m : read(raw).methods) {
                            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                                if (insn instanceof MethodInsnNode && "func_185331_a".equals(((MethodInsnNode) insn).name)) {
                                    srgCalls++;
                                }
                            }
                        }
                        check("SRG P37: vanilla's spot check calls func_185331_a (isValidEmptySpawnBlock) twice", srgCalls == 2, srgCalls);
                        continue;
                    }
                    if (name.endsWith(".GuiSlot")) {
                        // The SRG jar is vanilla's, without Forge's binary patches; P31's list half swaps Forge's
                        // drawContainerBackground call, which a real game has (Forge patches before any transformer).
                        // On vanilla's bytes it must leave the class alone; the dev chain above checks the patch.
                        check("SRG P31 on unpatched GuiSlot: left alone (Forge's drawContainerBackground missing)", out == raw,
                                Asm.state("P31-list"));
                        continue;
                    }
                    check("SRG " + c[1] + " transformed", out != raw, Asm.state((String) c[1]));
                    verify("SRG " + c[1], name, out, (String[]) c[2]);
                }
            }
        });
    }

    private static boolean isTransformerRegistered() {
        for (IClassTransformer t : Launch.classLoader.getTransformers()) {
            if (t.toString().contains("com.exiledradio.backportcore.asm.") || t.getClass().getName().contains("com.exiledradio.backportcore.asm.")) {
                return true;
            }
        }
        return false;
    }

    /** Builds a 16 x 16 bed at (x, 200, x), runs chunk generation's animal spawning over it, and counts what it placed. */
    private static int bed(net.minecraft.world.World world, int x0, boolean water) {
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = x0; z < x0 + 16; z++) {
                world.setBlockState(new BlockPos(x, 200, z), Blocks.STONE.getDefaultState(), 2);
                for (int y = 201; y <= 202; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    world.setBlockState(p, TestBlocks.plant.getDefaultState(), 2);
                    if (water) {
                        Waterlogging.setWater(world, p, true, 2);
                    }
                }
                for (int y = 203; y <= 206; y++) {
                    world.setBlockState(new BlockPos(x, y, z), water ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
        java.util.Random random = new java.util.Random(37L);
        for (int i = 0; i < 400; i++) {
            WorldEntitySpawner.performWorldGenSpawning(world, Biomes.PLAINS, x0, x0, 16, 16, random);
        }
        net.minecraft.util.math.AxisAlignedBB box = new net.minecraft.util.math.AxisAlignedBB(x0, 199, x0, x0 + 16, 210, x0 + 16);
        List<net.minecraft.entity.passive.EntityAnimal> placed = world.getEntitiesWithinAABB(net.minecraft.entity.passive.EntityAnimal.class, box);
        for (net.minecraft.entity.passive.EntityAnimal a : placed) {
            a.setDead();
        }
        return placed.size();
    }

    private static IClassTransformer transformer(String patch) throws Exception {
        String cls;
        switch (patch) {
            case "P1": cls = "WaterTravelTransformer"; break;
            case "P2": cls = "PlayerSprintTransformer"; break;
            case "P3": cls = "BipedAnglesTransformer"; break;
            case "P4": cls = "PlayerRotationsTransformer"; break;
            case "P6": cls = "TitlePanoramaTransformer"; break;
            case "P7": cls = "DebugKeysTransformer"; break;
            case "P8": cls = "BiomeSearchTransformer"; break;
            case "P9": cls = "VillageBiomeTransformer"; break;
            case "P10": case "P11": cls = "LayerRulesTransformer"; break;
            case "P12": case "P13": case "P14": case "P15": case "P16": case "P61": case "P62": cls = "ScreenPatchesTransformer"; break;
            case "P17": cls = "ChunkRebuildsTransformer"; break;
            case "P18": cls = "CameraTerrainTransformer"; break;
            case "P31": case "P32": cls = "MenuBackgroundTransformer"; break;
            case "P33": cls = "EyeHeightTransformer"; break;
            case "P34": cls = "MapIconTransformer"; break;
            case "P35": cls = "PaneTorchTransformer"; break;
            case "P36": cls = "SpawnListSwitchTransformer"; break;
            case "P37": cls = "GroundSpawnTransformer"; break;
            case "P38": case "P39": case "P40": cls = "TickTransformer"; break;
            case "P41": cls = "JukeboxSignalTransformer"; break;
            case "P42": cls = "FullscreenTransformer"; break;
            case "P44": cls = "BiomeColorTransformer"; break;
            case "P43": cls = "MapFacingTransformer"; break;
            case "P46": cls = "SimulationTransformer"; break;
            case "P47": cls = "CreativeTabsTransformer"; break;
            default: cls = "CameraHeightTransformer"; break;
        }
        return (IClassTransformer) Class.forName("com.exiledradio.backportcore.asm." + cls).newInstance();
    }

    private static void verify(String label, String name, byte[] out, String[] hooks) {
        ClassNode node = read(out);
        for (String spec : hooks) {
            // "name*2": that many calls; otherwise exactly one.
            int star = spec.indexOf('*');
            String hook = star < 0 ? spec : spec.substring(0, star);
            int want = star < 0 ? 1 : Integer.parseInt(spec.substring(star + 1));
            check(label + ": " + want + " call(s) to " + hook, countCalls(node, hook) == want, countCalls(node, hook));
        }
        String bad = null;
        for (MethodNode m : node.methods) {
            try {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, m);
            } catch (Exception e) {
                bad = m.name + m.desc + ": " + e.getMessage();
                break;
            }
        }
        check(label + ": every method of " + name + " verifies", bad == null, bad);
    }

    /** Every return of Chunk(World, int, int) calls SectionFluidStates.install first, and the class verifies. */
    private static void verifyP20(String label, byte[] out) {
        ClassNode node = read(out);
        int returns = 0;
        int calls = 0;
        for (MethodNode m : node.methods) {
            if (!m.name.equals("<init>") || !m.desc.equals("(Lnet/minecraft/world/World;II)V")) {
                continue;
            }
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn.getOpcode() == org.objectweb.asm.Opcodes.RETURN) {
                    returns++;
                    AbstractInsnNode before = insn.getPrevious();
                    while (before != null && before.getOpcode() < 0) {
                        before = before.getPrevious();
                    }
                    if (before instanceof MethodInsnNode && ((MethodInsnNode) before).name.equals("install")
                            && ((MethodInsnNode) before).owner.endsWith("waterlog/fluidlogged/SectionFluidStates")) {
                        calls++;
                    }
                }
            }
        }
        check(label + ": install before each of Chunk(World, int, int)'s returns", returns > 0 && calls == returns,
                calls + "/" + returns);
        String bad = null;
        for (MethodNode m : node.methods) {
            try {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, m);
            } catch (Exception e) {
                bad = m.name + m.desc + ": " + e.getMessage();
                break;
            }
        }
        check(label + ": every method of Chunk verifies", bad == null, bad);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static int countCalls(ClassNode node, String hook) {
        int n = 0;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode && hook.equals(((MethodInsnNode) insn).name)
                        && ((((MethodInsnNode) insn).owner.startsWith("com/exiledradio/backportcore/pose/")
                        || ((MethodInsnNode) insn).owner.startsWith("com/exiledradio/backportcore/presentation/")
                        || ((MethodInsnNode) insn).owner.startsWith("com/exiledradio/backportcore/worldgen/")
                        || ((MethodInsnNode) insn).owner.startsWith("com/exiledradio/backportcore/render/")
                        || ((MethodInsnNode) insn).owner.startsWith("com/exiledradio/backportcore/map/")
                        || ((MethodInsnNode) insn).owner.startsWith("com/exiledradio/backportcore/mechanics/")
                        || ((MethodInsnNode) insn).owner.startsWith("com/exiledradio/backportcore/command/")
                        || ((MethodInsnNode) insn).owner.startsWith("com/exiledradio/backportcore/simulation/")
                        || ((MethodInsnNode) insn).owner.startsWith("com/exiledradio/backportcore/creative/"))
                        || ((MethodInsnNode) insn).owner.startsWith("git/jbredwards/"))) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int countOwner(ClassNode node, String ownerPrefix) {
        int n = 0;
        for (MethodNode m : node.methods) {
            if (!m.name.equals("travel") && !m.name.equals("func_191986_a")) {
                continue;
            }
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.startsWith(ownerPrefix)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
        }
        in.close();
        return out.toByteArray();
    }
}
