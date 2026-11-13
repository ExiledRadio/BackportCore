package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patch P21 (docs/PATCHES.md): the backports' candles influence Quark's matrix enchanting. At the start of
 * {@code vazkii.quark.oddities.tile.TileMatrixEnchanter.getEnchantPowerAt(World, BlockPos)}, a call to {@code
 * compat.QuarkCandles.influence(this.influences, world, pos)}, which adds a dyed candle's influences the way Quark
 * adds its own candle's. Quark only knows its own candle block (it compares the block by identity), and Backport
 * Core hides that block beside the backports' candles, so without this the feature is lost.
 */
public final class QuarkCandlesTransformer implements IClassTransformer {

    static final String PATCH = "P21";
    private static final String WHAT = "dyed candles influence Quark's matrix enchanting (TileMatrixEnchanter.getEnchantPowerAt)";
    private static final String TARGET = "vazkii.quark.oddities.tile.TileMatrixEnchanter";
    private static final String OWNER = "vazkii/quark/oddities/tile/TileMatrixEnchanter";
    private static final String DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)F";
    static final String HOOK = "com/exiledradio/backportcore/compat/QuarkCandles";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        return patch(basicClass);
    }

    public static byte[] patch(byte[] basicClass) {
        ClassNode node = Asm.read(basicClass);
        MethodNode method = Asm.method(node, "getEnchantPowerAt", "getEnchantPowerAt", DESC);
        if (method == null) {
            Asm.skipped(PATCH, WHAT, "getEnchantPowerAt(World, BlockPos) not found");
            return basicClass;
        }
        boolean hasField = false;
        for (org.objectweb.asm.tree.FieldNode f : node.fields) {
            if ("influences".equals(f.name) && "Ljava/util/Map;".equals(f.desc)) {
                hasField = true;
            }
        }
        if (!hasField) {
            Asm.skipped(PATCH, WHAT, "field influences not found");
            return basicClass;
        }
        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "influences", "Ljava/util/Map;"));
        call.add(new VarInsnNode(Opcodes.ALOAD, 1));
        call.add(new VarInsnNode(Opcodes.ALOAD, 2));
        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "influence",
                "(Ljava/util/Map;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V", false));
        method.instructions.insert(call);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
