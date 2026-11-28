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
 * Patches P80-P83 (docs/PATCHES.md): the vibration sources 1.12.2 fires no event for. Each adds one straight-line
 * call into {@code gameevent/GameEventHooks}:
 * <ul>
 *   <li>P80 {@code Entity.updateFallState}: first line {@code GameEventHooks.landed(this, onGround, state)};</li>
 *   <li>P81 {@code BlockCauldron.fillWithRain}: its one {@code World.setBlockState(pos, state, flags)} becomes
 *       {@code GameEventHooks.rainFill(world, pos, state, flags)} (same stack, same result);</li>
 *   <li>P82 {@code World.destroyBlock}: first line {@code GameEventHooks.blockDestroyed(this, pos)};</li>
 *   <li>P83 {@code EntityLivingBase.resetActiveHand}: first line {@code GameEventHooks.stoppedUsing(this)}.</li>
 * </ul>
 */
public final class GameEventSourcesTransformer implements IClassTransformer {

    static final String HOOKS = "com/exiledradio/backportcore/gameevent/GameEventHooks";
    private static final String STATE = "Lnet/minecraft/block/state/IBlockState;";
    private static final String POS = "Lnet/minecraft/util/math/BlockPos;";
    private static final String WORLD = "Lnet/minecraft/world/World;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        if ("net.minecraft.entity.Entity".equals(transformedName)) {
            return head(basicClass, "P80", "non-living entities landing (Entity.updateFallState)", "updateFallState", "func_184231_a",
                    "(DZ" + STATE + POS + ")V", "landed", "(Lnet/minecraft/entity/Entity;Z" + STATE + ")V",
                    Opcodes.ALOAD, 0, Opcodes.ILOAD, 3, Opcodes.ALOAD, 4);
        }
        if ("net.minecraft.world.World".equals(transformedName)) {
            return head(basicClass, "P82", "blocks destroyed (World.destroyBlock)", "destroyBlock", "func_175655_b",
                    "(" + POS + "Z)Z", "blockDestroyed", "(" + WORLD + POS + ")V", Opcodes.ALOAD, 0, Opcodes.ALOAD, 1);
        }
        if ("net.minecraft.entity.EntityLivingBase".equals(transformedName)) {
            return head(basicClass, "P83", "stopping an item's use (EntityLivingBase.resetActiveHand)", "resetActiveHand", "func_184602_cy",
                    "()V", "stoppedUsing", "(Lnet/minecraft/entity/EntityLivingBase;)V", Opcodes.ALOAD, 0);
        }
        if ("net.minecraft.block.BlockCauldron".equals(transformedName)) {
            return rainFill(basicClass);
        }
        return basicClass;
    }

    /** Puts a hook call (its arguments loaded as the opcode/local pairs given) before the method's first instruction. */
    private static byte[] head(byte[] bytes, String patch, String what, String mcp, String srg, String desc, String hook, String hookDesc,
                               int... loads) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, mcp, srg, desc);
        if (m == null) {
            Asm.skipped(patch, what, "method not found");
            return bytes;
        }
        if (!Asm.calls(m, Opcodes.INVOKESTATIC, HOOKS, hook, hook, hookDesc).isEmpty()) {
            Asm.skipped(patch, what, "hook already present");
            return bytes;
        }
        InsnList head = new InsnList();
        for (int i = 0; i < loads.length; i += 2) {
            head.add(new VarInsnNode(loads[i], loads[i + 1]));
        }
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, hookDesc, false));
        m.instructions.insert(head);
        Asm.applied(patch, what);
        return Asm.write(node);
    }

    private static byte[] rainFill(byte[] bytes) {
        String patch = "P81";
        String what = "cauldrons filling with rain (BlockCauldron.fillWithRain)";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "fillWithRain", "func_176224_k", "(" + WORLD + POS + ")V");
        if (m == null) {
            Asm.skipped(patch, what, "method not found");
            return bytes;
        }
        List<MethodInsnNode> sets = Asm.calls(m, Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "setBlockState", "func_180501_a",
                "(" + POS + STATE + "I)Z");
        if (sets.size() != 1) {
            Asm.skipped(patch, what, "unexpected shape (" + sets.size() + " setBlockState calls)");
            return bytes;
        }
        m.instructions.set(sets.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "rainFill", "(" + WORLD + POS + STATE + "I)Z", false));
        Asm.applied(patch, what);
        return Asm.write(node);
    }
}
