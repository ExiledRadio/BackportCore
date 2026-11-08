package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Patch P7 (docs/PATCHES.md): every F3 key combination goes through the presentation module's key map, so a
 * backport can add, change and remove combinations as its version did (1.13's F3+C and F3+I, 1.16's F3+F4,
 * 1.19's removal of F3+F).
 *
 * <p>One call site in {@code Minecraft.runTickKeyboard} changes owner: its one {@code this.processKeyF3(key)}
 * call becomes {@code presentation.client.PresentationHooks.processKeyF3(this, key)}, which calls vanilla's
 * own method for every key until an installed version changes the map.
 */
public final class DebugKeysTransformer implements IClassTransformer {

    static final String PATCH = "P7";
    private static final String WHAT = "F3 key combinations (Minecraft.runTickKeyboard)";
    private static final String TARGET = "net.minecraft.client.Minecraft";
    private static final String MC = "net/minecraft/client/Minecraft";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode keyboard = Asm.method(node, "runTickKeyboard", "func_184118_az", "()V");
        if (keyboard == null) {
            Asm.skipped(PATCH, WHAT, "runTickKeyboard()V not found");
            return basicClass;
        }
        List<MethodInsnNode> f3 = Asm.calls(keyboard, Opcodes.INVOKESPECIAL, MC, "processKeyF3", "func_184122_c", "(I)Z");
        if (f3.size() != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + f3.size() + " processKeyF3 calls)");
            return basicClass;
        }
        keyboard.instructions.set(f3.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "processKeyF3",
                "(L" + MC + ";I)Z", false));
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
