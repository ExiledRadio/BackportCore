package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Patches P70 and P71 (docs/PATCHES.md): two 1.16 options that act inside render code no Forge event reaches (moved
 * out of Nether Update Backport, its P3 and P4, in Core 1.4.7). Each passes one value through a hook in
 * {@code presentation.versions.Look116}: a static call taking the value and returning it scaled, with no jump and no
 * new local, so the class's frames stay valid.
 *
 * <ul>
 *   <li><b>P70</b>, {@code EntityRenderer.setupCameraTransform}: the portal wobble strength
 *       {@code f1 = prevTimeInPortal + (timeInPortal - prevTimeInPortal) * partialTicks} becomes
 *       {@code Look116.portalWobble(...)} before it is stored (Distortion Effects, 1.16.2).</li>
 *   <li><b>P71</b>, {@code RenderGlobal.setupTerrain}: the argument of its one {@code Entity.setRenderDistanceWeight}
 *       call becomes {@code Look116.entityDistance(...)} (Entity Distance, 1.16).</li>
 * </ul>
 * An older Nether Update Backport jar patches the same two places itself (its coremod sorts before Core's): where its
 * hook call is already there, or its transformer is on the class path, Core's patch is left out, so the value is
 * never scaled twice.
 */
public final class DistortionTransformer implements IClassTransformer {

    static final String P70 = "P70";
    static final String P71 = "P71";
    private static final String HOOKS = "com/exiledradio/backportcore/presentation/versions/Look116";
    private static final String RENDERER = "net.minecraft.client.renderer.EntityRenderer";
    private static final String GLOBAL = "net.minecraft.client.renderer.RenderGlobal";
    /** Nether Update Backport's own copy, before 1.1.2: its transformer and its hook class. */
    static final String LEGACY_TRANSFORMER = "com/exiledradio/netherupdatebackport/asm/LookTransformer.class";
    private static final String LEGACY_HOOKS = "com/exiledradio/netherupdatebackport/lookfeel/LookHooks";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (RENDERER.equals(transformedName)) {
            return portalWobble(basicClass, legacyPresent());
        }
        if (GLOBAL.equals(transformedName)) {
            return entityDistance(basicClass, legacyPresent());
        }
        return basicClass;
    }

    /** @return true while an older Nether Update Backport jar, which patches these itself, is on the class path */
    static boolean legacyPresent() {
        ClassLoader loader = DistortionTransformer.class.getClassLoader();
        return loader != null && loader.getResource(LEGACY_TRANSFORMER) != null;
    }

    private static boolean callsLegacy(MethodNode m) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode && LEGACY_HOOKS.equals(((MethodInsnNode) i).owner)) {
                return true;
            }
        }
        return false;
    }

    static byte[] portalWobble(byte[] bytes, boolean legacy) {
        String what = "distortion effects (EntityRenderer.setupCameraTransform)";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "setupCameraTransform", "func_78479_a", "(FI)V");
        if (m == null) {
            Asm.skipped(P70, what, "method not found");
            return bytes;
        }
        if (legacy || callsLegacy(m)) {
            Asm.skipped(P70, what, "an older Nether Update Backport jar patches it");
            return bytes;
        }
        // The one FSTORE that ends the expression starting at the first read of prevTimeInPortal.
        AbstractInsnNode store = null;
        int reads = 0;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof FieldInsnNode && i.getOpcode() == Opcodes.GETFIELD && isPrevTimeInPortal((FieldInsnNode) i)) {
                reads++;
                if (reads == 1) {
                    for (AbstractInsnNode j = i.getNext(); j != null; j = j.getNext()) {
                        if (j.getOpcode() == Opcodes.FSTORE) {
                            store = j;
                            break;
                        }
                        if (j instanceof MethodInsnNode || j.getOpcode() < 0 && !(j instanceof LineNumberNode)) {
                            break;
                        }
                    }
                }
            }
        }
        if (reads != 2 || !(store instanceof VarInsnNode)) {
            Asm.skipped(P70, what, "not of the expected shape (prevTimeInPortal read " + reads + " times)");
            return bytes;
        }
        m.instructions.insertBefore(store, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "portalWobble", "(F)F", false));
        Asm.applied(P70, what);
        return Asm.write(node);
    }

    private static boolean isPrevTimeInPortal(FieldInsnNode f) {
        return Asm.named(f.name, "prevTimeInPortal", "field_71080_cy") && "F".equals(f.desc);
    }

    static byte[] entityDistance(byte[] bytes, boolean legacy) {
        String what = "entity distance (RenderGlobal.setupTerrain)";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "setupTerrain", "func_174970_a",
                "(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V");
        if (m == null) {
            Asm.skipped(P71, what, "method not found");
            return bytes;
        }
        if (legacy || callsLegacy(m)) {
            Asm.skipped(P71, what, "an older Nether Update Backport jar patches it");
            return bytes;
        }
        List<MethodInsnNode> calls = Asm.calls(m, Opcodes.INVOKESTATIC, "net/minecraft/entity/Entity",
                "setRenderDistanceWeight", "func_184227_b", "(D)V");
        if (calls.size() != 1) {
            Asm.skipped(P71, what, calls.size() + " setRenderDistanceWeight calls, not one");
            return bytes;
        }
        m.instructions.insertBefore(calls.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "entityDistance", "(D)D", false));
        Asm.applied(P71, what);
        return Asm.write(node);
    }

    /** For the self-test: runs a patch on raw class bytes (as when no older jar is there), keeping the recorded states. */
    public static byte[] patchForTest(String transformedName, byte[] bytes) {
        String p70 = Asm.STATE.get(P70);
        String p71 = Asm.STATE.get(P71);
        byte[] out = RENDERER.equals(transformedName) ? portalWobble(bytes, false) : entityDistance(bytes, false);
        restore(P70, p70);
        restore(P71, p71);
        return out;
    }

    private static void restore(String patch, String state) {
        if (state == null) {
            Asm.STATE.remove(patch);
        } else {
            Asm.STATE.put(patch, state);
        }
    }
}
