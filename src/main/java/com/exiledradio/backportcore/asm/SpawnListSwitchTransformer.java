package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch P36 (docs/PATCHES.md): {@code Biome.getSpawnableList} survives a creature type added after its switch first ran.
 *
 * <p>The method switches on its {@code EnumCreatureType}: {@code Biome$1.$SwitchMap[type.ordinal()]}, a table javac
 * sizes to the enum's values when the switch first runs. A type that {@code EnumHelper.addCreatureType} adds later has
 * an ordinal past the table's end, and the lookup throws {@code ArrayIndexOutOfBoundsException} instead of taking the
 * default branch (Forge's {@code modSpawnableLists}). Which mod adds its type when, and which mod first runs the switch,
 * changes from pack to pack, so the lookup itself changes: each {@code IALOAD} of such a table becomes
 * {@code SpawnListHooks.switchIndex(table, ordinal)}, the entry, or 0 (the default branch) past the end.
 */
public final class SpawnListSwitchTransformer implements IClassTransformer {

    static final String PATCH = "P36";
    private static final String WHAT = "creature type switch (Biome.getSpawnableList)";
    private static final String TARGET = "net.minecraft.world.biome.Biome";
    private static final String HOOK = "com/exiledradio/backportcore/worldgen/SpawnListHooks";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode method = Asm.method(node, "getSpawnableList", "func_76747_a",
                "(Lnet/minecraft/entity/EnumCreatureType;)Ljava/util/List;");
        if (method == null) {
            Asm.skipped(PATCH, WHAT, "getSpawnableList(EnumCreatureType) not found");
            return basicClass;
        }
        List<AbstractInsnNode> loads = switchLoads(method);
        if (loads.isEmpty()) {
            // Another coremod or mixin already replaced the switch: nothing left that can overrun.
            Asm.skipped(PATCH, WHAT, "no creature type switch table read in getSpawnableList");
            return basicClass;
        }
        for (AbstractInsnNode load : loads) {
            method.instructions.set(load, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "switchIndex", "([II)I", false));
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }

    /** Every {@code GETSTATIC <int[]>; ...; EnumCreatureType.ordinal(); IALOAD}: the switch's table lookup. */
    static List<AbstractInsnNode> switchLoads(MethodNode method) {
        List<AbstractInsnNode> found = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.IALOAD) {
                continue;
            }
            AbstractInsnNode ordinal = previous(insn);
            if (!(ordinal instanceof MethodInsnNode) || ordinal.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"ordinal".equals(((MethodInsnNode) ordinal).name)
                    || !"net/minecraft/entity/EnumCreatureType".equals(((MethodInsnNode) ordinal).owner)) {
                continue;
            }
            AbstractInsnNode table = previous(previous(ordinal));
            if (table instanceof FieldInsnNode && table.getOpcode() == Opcodes.GETSTATIC && "[I".equals(((FieldInsnNode) table).desc)) {
                found.add(insn);
            }
        }
        return found;
    }

    /** The real instruction before this one, skipping labels, line numbers and frames. */
    private static AbstractInsnNode previous(AbstractInsnNode insn) {
        AbstractInsnNode p = insn == null ? null : insn.getPrevious();
        while (p != null && p.getOpcode() < 0) {
            p = p.getPrevious();
        }
        return p;
    }
}
