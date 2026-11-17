package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch P33 (docs/PATCHES.md): 1.14's crouching eyes. 1.12.2's {@code EntityPlayer.getEyeHeight} lowers the eyes
 * 0.08 when the sneak key is down or the player is 1.65 tall; 1.14 sets them by pose, 1.27 when crouching, key or
 * no key (a player held crouched under a 1.5-block ceiling has the key up).
 *
 * <p>The method's first instruction hands the player to {@code pose.MovementHooks.eyesFor(this)}, and each
 * {@code FRETURN} first passes the value through {@code MovementHooks.eyeHeight(value)}, which returns it unchanged
 * unless a backport has turned on the short crouch ({@code PoseRules.shortCrouch}) and the player is in its 1.5
 * box. {@code this} is read only at the start: Mojang's shipped class marks it unusable at the return (its frame
 * there is {@code [top, float]}).
 */
public final class EyeHeightTransformer implements IClassTransformer {

    static final String PATCH = "P33";
    private static final String WHAT = "crouching eye height (EntityPlayer.getEyeHeight)";
    private static final String TARGET = "net.minecraft.entity.player.EntityPlayer";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode eyes = Asm.method(node, "getEyeHeight", "func_70047_e", "()F");
        if (eyes == null) {
            Asm.skipped(PATCH, WHAT, "getEyeHeight()F not found");
            return basicClass;
        }
        List<AbstractInsnNode> returns = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : eyes.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.FRETURN) {
                returns.add(insn);
            }
        }
        if (returns.isEmpty()) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (no FRETURN)");
            return basicClass;
        }
        InsnList start = new InsnList();
        start.add(new VarInsnNode(Opcodes.ALOAD, 0));
        start.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.HOOKS, "eyesFor",
                "(Lnet/minecraft/entity/player/EntityPlayer;)V", false));
        eyes.instructions.insert(start);
        for (AbstractInsnNode ret : returns) {
            // The value is on the stack: the hook takes it and returns the value to use.
            eyes.instructions.insertBefore(ret, new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.HOOKS, "eyeHeight", "(F)F", false));
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
