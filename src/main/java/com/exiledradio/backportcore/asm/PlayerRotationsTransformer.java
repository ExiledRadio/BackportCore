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
 * Patch P4 (docs/PATCHES.md): a swimming or crawling player's body drawn lying along the look, as 1.14's
 * {@code PlayerRenderer.applyRotations}.
 *
 * <p>One addition to {@code RenderPlayer.applyRotations}: after the {@code super.applyRotations} call of its
 * last branch (neither sleeping nor gliding, the branch 1.14 adds the swim turn to),
 * {@code pose.client.ClientHooks.applyRotations(player, partialTicks)}. The turn sits inside the renderer's
 * own matrix, so the name tag, the shadow and everything drawn outside the model stay where vanilla puts
 * them.
 */
public final class PlayerRotationsTransformer implements IClassTransformer {

    static final String PATCH = "P4";
    private static final String WHAT = "player body turn (RenderPlayer.applyRotations)";
    private static final String TARGET = "net.minecraft.client.renderer.entity.RenderPlayer";
    private static final String DESC = "(Lnet/minecraft/client/entity/AbstractClientPlayer;FFF)V";
    private static final String SUPER_DESC = "(Lnet/minecraft/entity/EntityLivingBase;FFF)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode rotations = Asm.method(node, "applyRotations", "func_77043_a", DESC);
        if (rotations == null) {
            Asm.skipped(PATCH, WHAT, "applyRotations not found");
            return basicClass;
        }
        List<MethodInsnNode> sup = Asm.calls(rotations, Opcodes.INVOKESPECIAL, node.superName, "applyRotations", "func_77043_a", SUPER_DESC);
        if (sup.size() != 2) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + sup.size() + " super.applyRotations calls)");
            return basicClass;
        }
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.FLOAD, 4));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.CLIENT_HOOKS, "applyRotations",
                "(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V", false));
        rotations.instructions.insert(sup.get(1), hook);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
