package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Patch P52 (docs/PATCHES.md): the sound device and Directional Audio (1.18, 1.19). Paulscode's
 * {@code LibraryLWJGLOpenAL.init()} opens OpenAL with {@code AL.create()} inside a {@code try} that catches
 * {@code LWJGLException}; that one call becomes {@code presentation.client.SoundDevices.createAL()} (no arguments, no
 * result, throws {@code LWJGLException}), so the handler and the frames stay as they were. The compatibility check
 * ({@code libraryCompatible}, its own {@code AL.create()} and {@code AL.destroy()}) is left alone.
 */
public final class SoundDeviceTransformer implements IClassTransformer {

    static final String PATCH = "P52";
    private static final String WHAT = "sound device and HRTF (LibraryLWJGLOpenAL.init)";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!"paulscode.sound.libraries.LibraryLWJGLOpenAL".equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode m = Asm.method(node, "init", "init", "()V");
        if (m == null) {
            Asm.skipped(PATCH, WHAT, "method not found");
            return basicClass;
        }
        // Any owner: LWJGL 2's org/lwjgl/openal/AL, or lwjglx's where Cleanroom has already moved the reference.
        List<MethodInsnNode> calls = Asm.calls(m, Opcodes.INVOKESTATIC, null, "create", "create", "()V");
        if (calls.size() != 1 || !calls.get(0).owner.endsWith("/openal/AL")) {
            Asm.skipped(PATCH, WHAT, calls.size() + " AL.create() calls");
            return basicClass;
        }
        m.instructions.set(calls.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/exiledradio/backportcore/presentation/client/SoundDevices", "createAL", "()V", false));
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
