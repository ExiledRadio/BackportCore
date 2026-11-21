package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patch P41 (docs/PATCHES.md): a jukebox's comparator signal for discs added after 1.12.2.
 * {@code BlockJukebox.getComparatorInputOverride} starts with
 * {@code if (JukeboxSignal.has(world, pos)) return JukeboxSignal.signal(world, pos);} (one branch, an {@code F_SAME}
 * frame at the head, where the locals are exactly the parameters).
 */
public final class JukeboxSignalTransformer implements IClassTransformer {

    static final String PATCH = "P41";
    private static final String WHAT = "jukebox comparator signal (BlockJukebox.getComparatorInputOverride)";
    private static final String TARGET = "net.minecraft.block.BlockJukebox";
    private static final String DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)I";
    private static final String HOOK = "com/exiledradio/backportcore/mechanics/JukeboxSignal";
    private static final String HOOK_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode m = Asm.method(node, "getComparatorInputOverride", "func_180641_l", DESC);
        if (m == null) {
            Asm.skipped(PATCH, WHAT, "method not found");
            return basicClass;
        }
        LabelNode vanilla = new LabelNode();
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 2));
        head.add(new VarInsnNode(Opcodes.ALOAD, 3));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "has", HOOK_DESC + "Z", false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        head.add(new VarInsnNode(Opcodes.ALOAD, 2));
        head.add(new VarInsnNode(Opcodes.ALOAD, 3));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "signal", HOOK_DESC + "I", false));
        head.add(new InsnNode(Opcodes.IRETURN));
        head.add(vanilla);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(head);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
