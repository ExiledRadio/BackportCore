package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patch P35 (docs/PATCHES.md): 1.14.3's torches on glass panes and iron bars. {@code BlockPane} inherits Forge's
 * {@code Block.canPlaceTorchOnTop}, which turns panes away (their top is {@code CENTER_SMALL}). The patch gives
 * {@code BlockPane} its own override, {@code return BlockRules.torchOnPane(this, state, world, pos)}, which answers as
 * the inherited method does until a backport switches the change on ({@code mechanics.BlockRules}).
 */
public final class PaneTorchTransformer implements IClassTransformer {

    static final String PATCH = "P35";
    private static final String WHAT = "torches on panes and bars (BlockPane.canPlaceTorchOnTop)";
    private static final String TARGET = "net.minecraft.block.BlockPane";
    private static final String DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        for (MethodNode m : node.methods) {
            if ("canPlaceTorchOnTop".equals(m.name) && DESC.equals(m.desc)) {
                Asm.skipped(PATCH, WHAT, "BlockPane already overrides canPlaceTorchOnTop");
                return basicClass;
            }
        }
        // Forge's own method: the same name in development and production.
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "canPlaceTorchOnTop", DESC, null, null);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/exiledradio/backportcore/mechanics/BlockRules",
                "torchOnPane", "(Lnet/minecraft/block/Block;" + DESC.substring(1), false));
        m.instructions.add(new InsnNode(Opcodes.IRETURN));
        m.maxStack = 4;
        m.maxLocals = 4;
        node.methods.add(m);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
