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
 * Patch P2 (docs/PATCHES.md): the client player's sprint rules, 1.13's way.
 *
 * <p>One addition to {@code EntityPlayerSP.onLivingUpdate}: right before its {@code super.onLivingUpdate()}
 * call (after vanilla's sprint, flight and elytra checks, before the player moves),
 * {@code pose.client.ClientHooks.beforeLivingUpdate(this)}. A backport's rules decide sprinting there
 * the way 1.14's {@code ClientPlayerEntity.livingTick} does: starting in water, not stopping at a wall while
 * swimming, not flying off mid-swim, sinking while sneaking in water.
 */
public final class PlayerSprintTransformer implements IClassTransformer {

    static final String PATCH = "P2";
    private static final String WHAT = "client sprint rules (EntityPlayerSP.onLivingUpdate)";
    private static final String TARGET = "net.minecraft.client.entity.EntityPlayerSP";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode update = Asm.method(node, "onLivingUpdate", "func_70636_d", "()V");
        if (update == null) {
            Asm.skipped(PATCH, WHAT, "onLivingUpdate()V not found");
            return basicClass;
        }
        List<MethodInsnNode> sup = Asm.calls(update, Opcodes.INVOKESPECIAL, node.superName, "onLivingUpdate", "func_70636_d", "()V");
        if (sup.size() != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + sup.size() + " super.onLivingUpdate calls)");
            return basicClass;
        }
        // The receiver of the super call is already on the stack; the hook takes its own copy.
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.CLIENT_HOOKS, "beforeLivingUpdate",
                "(Lnet/minecraft/client/entity/EntityPlayerSP;)V", false));
        update.instructions.insertBefore(sup.get(0), hook);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
