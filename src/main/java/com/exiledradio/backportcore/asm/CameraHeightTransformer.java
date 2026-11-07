package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Patch P5 (docs/PATCHES.md): the camera glides to a new eye height, as 1.14's {@code ActiveRenderInfo}
 * eases it over a few ticks, instead of jumping (into a swim or a crawl, out of one, and when sneaking).
 *
 * <p>One change to {@code EntityRenderer.orientCamera}: its one {@code entity.getEyeHeight()} call, which
 * places the camera in first and third person, becomes
 * {@code pose.client.ClientHooks.cameraEyeHeight(entity, partialTicks)}. Picking blocks and mobs keeps the
 * real eye height, as in 1.14.
 */
public final class CameraHeightTransformer implements IClassTransformer {

    static final String PATCH = "P5";
    private static final String WHAT = "camera eye height (EntityRenderer.orientCamera)";
    private static final String TARGET = "net.minecraft.client.renderer.EntityRenderer";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode orient = Asm.method(node, "orientCamera", "func_78467_g", "(F)V");
        if (orient == null) {
            Asm.skipped(PATCH, WHAT, "orientCamera(F)V not found");
            return basicClass;
        }
        List<MethodInsnNode> eyes = Asm.calls(orient, Opcodes.INVOKEVIRTUAL, null, "getEyeHeight", "func_70047_e", "()F");
        if (eyes.size() != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + eyes.size() + " getEyeHeight calls)");
            return basicClass;
        }
        // The entity is already on the stack; the hook takes it and the partial tick instead.
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.FLOAD, 1));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.CLIENT_HOOKS, "cameraEyeHeight",
                "(Lnet/minecraft/entity/Entity;F)F", false));
        orient.instructions.insertBefore(eyes.get(0), hook);
        orient.instructions.remove(eyes.get(0));
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
