package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Patch P28 (docs/PATCHES.md): 1.21.11's Chunk Fade. {@code ChunkRenderContainer.preRenderChunk} (each chunk, each
 * layer, as it is drawn) starts with {@code PresentationHooks.chunkFadePre(chunk)}, and {@code RenderGlobal.renderBlockLayer}
 * calls {@code chunkFadeEnd()} right after its {@code renderContainer.renderChunkLayer(layer)}.
 */
public final class ChunkFadeTransformer implements IClassTransformer {

    static final String PATCH = "P28";
    private static final String WHAT = "chunk fade (ChunkRenderContainer.preRenderChunk, RenderGlobal.renderBlockLayer)";
    private static final String CONTAINER = "net.minecraft.client.renderer.ChunkRenderContainer";
    private static final String GLOBAL = "net.minecraft.client.renderer.RenderGlobal";
    private static final String CHUNK = "net/minecraft/client/renderer/chunk/RenderChunk";
    private static int done;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        boolean container = CONTAINER.equals(transformedName);
        if (!container && !GLOBAL.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        if (container) {
            MethodNode pre = Asm.method(node, "preRenderChunk", "func_178003_a", "(L" + CHUNK + ";)V");
            if (pre == null) {
                Asm.skipped(PATCH, WHAT, "preRenderChunk not found");
                return basicClass;
            }
            InsnList head = new InsnList();
            head.add(new VarInsnNode(Opcodes.ALOAD, 1));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "chunkFadePre", "(L" + CHUNK + ";)V", false));
            pre.instructions.insert(head);
        } else {
            MethodNode layer = Asm.method(node, "renderBlockLayer", "func_174982_a", "(Lnet/minecraft/util/BlockRenderLayer;)V");
            if (layer == null) {
                Asm.skipped(PATCH, WHAT, "renderBlockLayer(BlockRenderLayer) not found");
                return basicClass;
            }
            List<MethodInsnNode> calls = Asm.calls(layer, Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/ChunkRenderContainer",
                    "renderChunkLayer", "func_178001_a", "(Lnet/minecraft/util/BlockRenderLayer;)V");
            if (calls.size() != 1) {
                Asm.skipped(PATCH, WHAT, "unexpected shape (" + calls.size() + " renderChunkLayer calls)");
                return basicClass;
            }
            layer.instructions.insert(calls.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.PRESENTATION_HOOKS, "chunkFadeEnd", "()V", false));
        }
        if (++done == 2) {
            Asm.applied(PATCH, WHAT);
        }
        return Asm.write(node);
    }
}
