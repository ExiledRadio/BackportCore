package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Patch P67 (docs/PATCHES.md): a render chunk moved to a new position is rebuilt, as in 1.18.2. As the camera moves,
 * {@code ViewFrustum.updateChunkPositions} moves render chunks, and 1.12.2's {@code RenderChunk.setPosition} empties
 * one ({@code stopCompileTask}: {@code CompiledChunk.DUMMY}) without marking it for a rebuild; 1.18.2's
 * {@code RenderChunk.setOrigin} calls {@code reset()}, which also sets {@code dirty = true}. Right after the one
 * {@code stopCompileTask()} call, {@code this.needsUpdate = true} (the field alone, as 1.18.2 sets {@code dirty}
 * and leaves {@code playerChanged}). A class whose shape differs is left as it is, with one line in the log.
 */
public final class RenderChunkMoveTransformer implements IClassTransformer {

    static final String PATCH = "P67";
    private static final String TARGET = "net.minecraft.client.renderer.chunk.RenderChunk";
    private static final String OWNER = "net/minecraft/client/renderer/chunk/RenderChunk";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET.equals(transformedName)) {
            return basicClass;
        }
        String what = "moved render chunks rebuilt (RenderChunk.setPosition)";
        ClassNode node = Asm.read(basicClass);
        MethodNode m = Asm.method(node, "setPosition", "func_189562_a", "(III)V");
        if (m == null) {
            Asm.skipped(PATCH, what, "method not found");
            return basicClass;
        }
        String field = null;
        for (FieldNode f : node.fields) {
            if ("Z".equals(f.desc) && Asm.named(f.name, "needsUpdate", "field_178593_n")) {
                field = f.name;
            }
        }
        List<MethodInsnNode> calls = Asm.calls(m, Opcodes.INVOKEVIRTUAL, OWNER, "stopCompileTask", "func_178585_h", "()V");
        if (field == null || calls.size() != 1) {
            Asm.skipped(PATCH, what, field == null ? "needsUpdate field not found" : calls.size() + " stopCompileTask calls");
            return basicClass;
        }
        InsnList mark = new InsnList();
        mark.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mark.add(new InsnNode(Opcodes.ICONST_1));
        mark.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, field, "Z"));
        m.instructions.insert(calls.get(0), mark);
        Asm.applied(PATCH, what);
        return Asm.write(node);
    }
}
