package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Patch P51 (docs/PATCHES.md): 26.3's Quit Shortcuts. {@code Minecraft.runGameLoop} starts with
 * {@code if (Display.isCreated() && Display.isCloseRequested()) this.shutdown();}; the {@code isCloseRequested()} result
 * goes through {@code presentation.client.QuitShortcuts.closeRequested(boolean)}, which answers false for a close that
 * Alt + F4 (Cmd + Q on macOS) asked for while the option is off. The call stays in place (a boolean in, a boolean out).
 */
public final class QuitShortcutsTransformer implements IClassTransformer {

    static final String PATCH = "P51";
    private static final String WHAT = "quit shortcuts (Minecraft.runGameLoop)";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!"net.minecraft.client.Minecraft".equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode m = Asm.method(node, "runGameLoop", "func_71411_J", "()V");
        if (m == null) {
            Asm.skipped(PATCH, WHAT, "method not found");
            return basicClass;
        }
        // Any owner: LWJGL 2's org/lwjgl/opengl/Display, or lwjglx's where Cleanroom has already moved the reference.
        List<MethodInsnNode> calls = Asm.calls(m, Opcodes.INVOKESTATIC, null, "isCloseRequested", "isCloseRequested", "()Z");
        if (calls.size() != 1) {
            Asm.skipped(PATCH, WHAT, calls.size() + " isCloseRequested calls");
            return basicClass;
        }
        m.instructions.insert(calls.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/exiledradio/backportcore/presentation/client/QuitShortcuts", "closeRequested", "(Z)Z", false));
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
