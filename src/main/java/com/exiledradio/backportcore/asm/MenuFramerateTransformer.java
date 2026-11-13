package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch P22 (docs/PATCHES.md): menus without a world run at 60 frames a second from 1.21.2
 * ({@code FramerateLimitTracker}'s out-of-level menu limit); 1.12.2's {@code Minecraft.getLimitFramerate} holds them
 * at 30. Its constant 30 goes through {@code PresentationHooks.menuFramerate(int)}.
 */
public final class MenuFramerateTransformer implements IClassTransformer {

    static final String PATCH = "P22";
    private static final String WHAT = "menu frame rate (Minecraft.getLimitFramerate)";
    private static final String TARGET = "net.minecraft.client.Minecraft";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode limit = Asm.method(node, "getLimitFramerate", "func_90020_K", "()I");
        if (limit == null) {
            Asm.skipped(PATCH, WHAT, "getLimitFramerate()I not found");
            return basicClass;
        }
        List<AbstractInsnNode> thirty = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : limit.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == 30) {
                thirty.add(insn);
            }
        }
        if (thirty.size() != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + thirty.size() + " constants 30)");
            return basicClass;
        }
        limit.instructions.insert(thirty.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS,
                "menuFramerate", "(I)I", false));
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
