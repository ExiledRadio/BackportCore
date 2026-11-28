package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Patch P79 (docs/PATCHES.md): gliding while looking straight up no longer turns the position into NaN.
 *
 * <p>{@code EntityLivingBase.travel}'s elytra branch adds a climb while the pitch is below 0, dividing by the look's
 * horizontal length {@code d6} with no test:
 * <pre>
 *     if (f &lt; 0.0F) {                      // 1.16.5: if (f &lt; 0.0F &amp;&amp; d1 &gt; 0.0D)
 *         double d10 = d8 * -sin(f) * 0.04D;
 *         this.motionX -= vec3d.x * d10 / d6;
 *         this.motionZ -= vec3d.z * d10 / d6;
 *     }</pre>
 * Vanilla's sine table never gives a cosine of exactly 0 at pitch -90, so {@code d6} stays a tiny non-zero number.
 * OptiFine's Fast Math table is rounded, and there {@code cos(90)} is exactly 0: at pitch -90 with any horizontal
 * speed the motion becomes {@code 0 / 0}, the position NaN, and the server kicks the player with "Invalid move player
 * packet received". 1.16.5 (and every version since) tests {@code d1 > 0} there, as the other two divisions by it in
 * the branch already do in 1.12.2.
 *
 * <p>The patch adds that test: after the branch's {@code fload f; fconst_0; fcmpg; ifge END}, a
 * {@code dload d6; dconst_0; dcmpl; ifle END} to the same label. It only changes what happens when {@code d6} is 0
 * (or NaN), where 1.12.2's answer is NaN; every other flight is vanilla's to the bit.
 */
public final class ElytraLookUpTransformer implements IClassTransformer {

    static final String PATCH = "P79";
    private static final String WHAT = "elytra looking straight up (EntityLivingBase.travel)";
    private static final String TARGET = "net.minecraft.entity.EntityLivingBase";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode travel = Asm.method(node, "travel", "func_191986_a", "(FFF)V");
        if (travel == null) {
            Asm.skipped(PATCH, WHAT, "travel(FFF)V not found");
            return basicClass;
        }
        List<MethodInsnNode> looks = Asm.calls(travel, Opcodes.INVOKEVIRTUAL, null, "getLookVec", "func_70040_Z",
                "()Lnet/minecraft/util/math/Vec3d;");
        if (looks.size() != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + looks.size() + " getLookVec calls)");
            return basicClass;
        }
        // After the look: f = this.rotationPitch * 0.017453292F (fstore), then d6 = Math.sqrt(x * x + z * z) (dstore).
        int pitch = -1;
        int horizontal = -1;
        AbstractInsnNode at = looks.get(0);
        for (AbstractInsnNode insn = at.getNext(); insn != null; insn = insn.getNext()) {
            if (pitch < 0 && insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.GETFIELD
                    && Asm.named(((FieldInsnNode) insn).name, "rotationPitch", "field_70125_A")) {
                AbstractInsnNode store = real(real(real(insn)));
                if (store != null && store.getOpcode() == Opcodes.FSTORE) {
                    pitch = ((VarInsnNode) store).var;
                }
            } else if (pitch >= 0 && insn instanceof MethodInsnNode && "java/lang/Math".equals(((MethodInsnNode) insn).owner)
                    && "sqrt".equals(((MethodInsnNode) insn).name)) {
                AbstractInsnNode store = real(insn);
                if (store != null && store.getOpcode() == Opcodes.DSTORE) {
                    horizontal = ((VarInsnNode) store).var;
                }
                break;
            }
        }
        if (pitch < 0 || horizontal < 0) {
            Asm.skipped(PATCH, WHAT, "the pitch or the look's horizontal length was not found");
            return basicClass;
        }
        // The branch: fload f; fconst_0; fcmpg; ifge END, the first test of f after the look.
        JumpInsnNode climb = null;
        for (AbstractInsnNode insn = at.getNext(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.FLOAD && ((VarInsnNode) insn).var == pitch) {
                AbstractInsnNode zero = real(insn);
                AbstractInsnNode cmp = real(zero);
                AbstractInsnNode jump = real(cmp);
                if (zero != null && zero.getOpcode() == Opcodes.FCONST_0 && cmp != null && cmp.getOpcode() == Opcodes.FCMPG
                        && jump != null && jump.getOpcode() == Opcodes.IFGE) {
                    climb = (JumpInsnNode) jump;
                    break;
                }
            }
        }
        if (climb == null) {
            Asm.skipped(PATCH, WHAT, "the pitch < 0 test was not found");
            return basicClass;
        }
        AbstractInsnNode next = real(climb);
        if (next != null && next.getOpcode() == Opcodes.DLOAD && ((VarInsnNode) next).var == horizontal) {
            Asm.skipped(PATCH, WHAT, "the horizontal length is already tested there");
            return basicClass;
        }
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.DLOAD, horizontal));
        guard.add(new InsnNode(Opcodes.DCONST_0));
        guard.add(new InsnNode(Opcodes.DCMPL));
        guard.add(new JumpInsnNode(Opcodes.IFLE, climb.label));
        travel.instructions.insert(climb, guard);

        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }

    /** The next real instruction after this one, past labels, line numbers and frames; null at the end. */
    private static AbstractInsnNode real(AbstractInsnNode insn) {
        if (insn == null) {
            return null;
        }
        AbstractInsnNode next = insn.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }
}
