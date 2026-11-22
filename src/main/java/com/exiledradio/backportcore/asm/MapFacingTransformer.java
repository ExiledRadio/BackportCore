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
 * Patch P43 (docs/PATCHES.md): a player off the map keeps their facing (26.3). {@code MapData.updateDecorations}
 * starts with {@code MapFacing.enter(world, name, x, z, rotation)} (the parameters, read at the head), and its one
 * {@code new MapDecoration(type, x, y, rotation)} is passed through {@code MapFacing.facing}, which gives an off-map
 * or off-limits player marker the rotation an on-map one gets instead of 1.12.2's 0. Same stack shape.
 */
public final class MapFacingTransformer implements IClassTransformer {

    static final String PATCH = "P43";
    private static final String WHAT = "off-map player facing (MapData.updateDecorations)";
    private static final String TARGET = "net.minecraft.world.storage.MapData";
    private static final String DESC = "(Lnet/minecraft/world/storage/MapDecoration$Type;Lnet/minecraft/world/World;Ljava/lang/String;DDD)V";
    private static final String HOOK = "com/exiledradio/backportcore/map/MapFacing";
    private static final String DECORATION = "net/minecraft/world/storage/MapDecoration";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode m = Asm.method(node, "updateDecorations", "func_191095_a", DESC);
        if (m == null) {
            Asm.skipped(PATCH, WHAT, "method not found");
            return basicClass;
        }
        List<MethodInsnNode> news = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.INVOKESPECIAL && DECORATION.equals(((MethodInsnNode) insn).owner)
                    && "<init>".equals(((MethodInsnNode) insn).name)) {
                news.add((MethodInsnNode) insn);
            }
        }
        if (news.size() != 1) {
            Asm.skipped(PATCH, WHAT, news.size() + " MapDecoration constructions, expected 1");
            return basicClass;
        }
        m.instructions.insert(news.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "facing",
                "(L" + DECORATION + ";)L" + DECORATION + ";", false));
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 2));
        head.add(new VarInsnNode(Opcodes.ALOAD, 3));
        head.add(new VarInsnNode(Opcodes.DLOAD, 4));
        head.add(new VarInsnNode(Opcodes.DLOAD, 6));
        head.add(new VarInsnNode(Opcodes.DLOAD, 8));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "enter", "(Lnet/minecraft/world/World;Ljava/lang/String;DDD)V", false));
        m.instructions.insert(head);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
