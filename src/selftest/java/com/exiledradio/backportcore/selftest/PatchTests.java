package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
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
    };

    private PatchTests() {
    }

    static void schedule() {
        then(1, "patches in the running game", world -> {
            check("P1 applied when EntityLivingBase loaded (dev, beside Fluidlogged API)", "applied".equals(Asm.state("P1")), Asm.state("P1"));
            check("coremod is registered with FML", isTransformerRegistered(), "");
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
        then(1, "patches on SRG names", world -> {
            File jar = new File(SRG_JAR);
            if (!check("Forge's SRG jar in the Gradle cache", jar.isFile(), SRG_JAR)) {
                return;
            }
            try (ZipFile zip = new ZipFile(jar)) {
                for (Object[] c : CLASSES) {
                    String name = (String) c[0];
                    ZipEntry entry = zip.getEntry(name.replace('.', '/') + ".class");
                    if (!check("SRG bytes of " + name, entry != null)) {
                        continue;
                    }
                    byte[] raw = readAll(zip.getInputStream(entry));
                    byte[] out = transformer((String) c[1]).transform(name, name, raw);
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
                        || ((MethodInsnNode) insn).owner.startsWith("com/exiledradio/backportcore/worldgen/"))
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
