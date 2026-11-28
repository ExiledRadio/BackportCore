package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Patches P84 and P85 (docs/PATCHES.md), {@code BlockPistonBase}. Each changes the owner of one call, an instance
 * call becoming a static call of {@code mechanics/PistonRules} with the same arguments, the receiver first:
 * <ul>
 *   <li>P84 {@code canPush}: {@code block.hasTileEntity(state)} (Forge's; vanilla's {@code block.hasTileEntity()})
 *       becomes {@code PistonRules.tileEntityStopsPiston}, so a registered block with a tile entity can move;</li>
 *   <li>P85 {@code doMove}: the drop of each block the piston destroys ({@code dropBlockAsItemWithChance}, Forge's;
 *       vanilla's {@code dropBlockAsItem}) becomes {@code PistonRules.destroyedByPiston}, which drops it and adds
 *       1.21.5's breaking effects.</li>
 * </ul>
 */
public final class PistonTransformer implements IClassTransformer {

    private static final String TARGET = "net.minecraft.block.BlockPistonBase";
    private static final String HOOKS = "com/exiledradio/backportcore/mechanics/PistonRules";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String STATE = "Lnet/minecraft/block/state/IBlockState;";
    private static final String WORLD = "Lnet/minecraft/world/World;";
    private static final String POS = "Lnet/minecraft/util/math/BlockPos;";
    private static final String FACING = "Lnet/minecraft/util/EnumFacing;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        boolean changed = canPush(node);
        changed |= doMove(node);
        return changed ? Asm.write(node) : basicClass;
    }

    private static boolean canPush(ClassNode node) {
        String patch = "P84";
        String what = "pistons and registered tile-entity blocks (BlockPistonBase.canPush)";
        MethodNode m = Asm.method(node, "canPush", "func_185646_a", "(" + STATE + WORLD + POS + FACING + "Z" + FACING + ")Z");
        if (m == null) {
            Asm.skipped(patch, what, "method not found");
            return false;
        }
        List<MethodInsnNode> forge = Asm.calls(m, Opcodes.INVOKEVIRTUAL, BLOCK, "hasTileEntity", "hasTileEntity", "(" + STATE + ")Z");
        List<MethodInsnNode> vanilla = Asm.calls(m, Opcodes.INVOKEVIRTUAL, BLOCK, "hasTileEntity", "func_149716_u", "()Z");
        if (forge.size() + vanilla.size() != 1) {
            Asm.skipped(patch, what, "unexpected shape (" + forge.size() + " + " + vanilla.size() + " hasTileEntity calls)");
            return false;
        }
        String desc = forge.isEmpty() ? "(Lnet/minecraft/block/Block;)Z" : "(Lnet/minecraft/block/Block;" + STATE + ")Z";
        MethodInsnNode call = forge.isEmpty() ? vanilla.get(0) : forge.get(0);
        m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tileEntityStopsPiston", desc, false));
        Asm.applied(patch, what);
        return true;
    }

    private static boolean doMove(ClassNode node) {
        String patch = "P85";
        String what = "blocks a piston destroys show their breaking (BlockPistonBase.doMove)";
        MethodNode m = Asm.method(node, "doMove", "func_176319_a", "(" + WORLD + POS + FACING + "Z)Z");
        if (m == null) {
            Asm.skipped(patch, what, "method not found");
            return false;
        }
        List<MethodInsnNode> forge = Asm.calls(m, Opcodes.INVOKEVIRTUAL, BLOCK, "dropBlockAsItemWithChance", "func_180653_a",
                "(" + WORLD + POS + STATE + "FI)V");
        List<MethodInsnNode> vanilla = Asm.calls(m, Opcodes.INVOKEVIRTUAL, BLOCK, "dropBlockAsItem", "func_176226_b",
                "(" + WORLD + POS + STATE + "I)V");
        if (forge.size() + vanilla.size() != 1) {
            Asm.skipped(patch, what, "unexpected shape (" + forge.size() + " + " + vanilla.size() + " drop calls)");
            return false;
        }
        String desc = forge.isEmpty() ? "(Lnet/minecraft/block/Block;" + WORLD + POS + STATE + "I)V"
                : "(Lnet/minecraft/block/Block;" + WORLD + POS + STATE + "FI)V";
        MethodInsnNode call = forge.isEmpty() ? vanilla.get(0) : forge.get(0);
        m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "destroyedByPiston", desc, false));
        Asm.applied(patch, what);
        return true;
    }
}
