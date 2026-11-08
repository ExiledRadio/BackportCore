package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Patch P3 (docs/PATCHES.md): the swimming and crawling limbs, drawn by every biped model the way 1.14's
 * {@code BipedModel.setRotationAngles} draws them, so the player's armor follows the body.
 *
 * <p>Three additions to {@code ModelBiped.setRotationAngles}, all calls into {@code pose.client.ClientHooks}:
 * <ol>
 *   <li>at the start: {@code beforeAngles(this, entity, limbSwing, headPitch)} - remembers the head's last angle (1.14 blends
 *       from it), and a player in the swimming pose is not drawn crouched, as 1.14 draws by pose rather
 *       than by the sneak key;</li>
 *   <li>right after the head's angles are set (before the first {@code bipedBody} statement, where 1.14
 *       blends the head): {@code afterHead(this)};</li>
 *   <li>before the last {@code copyModelAngles} (the head to the headwear, the method's last line, where
 *       1.14 adds the stroke): {@code afterAngles(this)} - 1.14's swim stroke for arms
 *       and legs, blended in by the player's swim amount.</li>
 * </ol>
 * The parameters are read only at the first line: the shipped (ProGuard) stack-map frames mark headPitch,
 * limbSwing and the entity unusable further down, where the original code no longer reads them, and a later FLOAD there is a
 * VerifyError in a real game. Dev runs recompile Minecraft with javac and never show it.
 * All three do nothing unless the model is being drawn for a player in a third-person render.
 */
public final class BipedAnglesTransformer implements IClassTransformer {

    static final String PATCH = "P3";
    private static final String WHAT = "biped swim angles (ModelBiped.setRotationAngles)";
    private static final String TARGET = "net.minecraft.client.model.ModelBiped";
    private static final String OWNER = "net/minecraft/client/model/ModelBiped";
    private static final String DESC = "(FFFFFFLnet/minecraft/entity/Entity;)V";
    private static final String COPY = "(Lnet/minecraft/client/model/ModelRenderer;Lnet/minecraft/client/model/ModelRenderer;)V";
    private static final String MODEL_ENTITY = "(Lnet/minecraft/client/model/ModelBiped;Lnet/minecraft/entity/Entity;";
    private static final String MODEL_ONLY = "(Lnet/minecraft/client/model/ModelBiped;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode angles = Asm.method(node, "setRotationAngles", "func_78087_a", DESC);
        if (angles == null) {
            Asm.skipped(PATCH, WHAT, "setRotationAngles not found");
            return basicClass;
        }
        List<MethodInsnNode> copies = Asm.calls(angles, Opcodes.INVOKESTATIC, null, "copyModelAngles", "func_178685_a", COPY);
        AbstractInsnNode body = null;
        for (AbstractInsnNode insn : angles.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.GETFIELD && OWNER.equals(((FieldInsnNode) insn).owner)
                    && Asm.named(((FieldInsnNode) insn).name, "bipedBody", "field_78115_e")) {
                body = insn;
                break;
            }
        }
        AbstractInsnNode bodyStart = body == null ? null : body.getPrevious();
        if (copies.isEmpty() || bodyStart == null || bodyStart.getOpcode() != Opcodes.ALOAD || ((VarInsnNode) bodyStart).var != 0) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + copies.size() + " copyModelAngles calls, body statement "
                    + (bodyStart == null ? "missing" : "found") + ")");
            return basicClass;
        }

        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new VarInsnNode(Opcodes.ALOAD, 7));
        head.add(new VarInsnNode(Opcodes.FLOAD, 1));
        head.add(new VarInsnNode(Opcodes.FLOAD, 5));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.CLIENT_HOOKS, "beforeAngles", MODEL_ENTITY + "FF)V", false));
        angles.instructions.insert(head);

        InsnList afterHead = new InsnList();
        afterHead.add(new VarInsnNode(Opcodes.ALOAD, 0));
        afterHead.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.CLIENT_HOOKS, "afterHead", MODEL_ONLY, false));
        angles.instructions.insertBefore(bodyStart, afterHead);

        // The copy's two arguments are already on the stack; the hook leaves them there.
        InsnList tail = new InsnList();
        tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tail.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.CLIENT_HOOKS, "afterAngles", MODEL_ONLY, false));
        angles.instructions.insertBefore(copies.get(copies.size() - 1), tail);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
