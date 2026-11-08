package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Patch P17 (docs/PATCHES.md): changed chunks near the camera are rebuilt on the chunk builder threads instead of
 * on the render thread, unless the player changed them. Which ones are built at once follows current vanilla's
 * "Chunk Builder" setting of 1.18.2 on ({@code CoreConfig.rendering.blockUpdates}).
 *
 * <p>In {@code RenderGlobal.setupTerrain}, the one {@code 768.0D} (the squared distance under which 1.12.2 builds a
 * changed chunk at once) passes through {@code render.client.ChunkRebuildHooks.nearbyDistanceSq}, and the result of
 * the one {@code renderChunk.needsImmediateUpdate()} call there and the one in {@code updateChunks} passes through
 * {@code ChunkRebuildHooks.needsImmediateUpdate(boolean)}. Each hook takes one value and returns one of the same
 * type, reads no local and adds no branch, so the class keeps its frames. The original constant and calls stay
 * where they were, so another mod's mixin that targets them (a redirect or constant change) still finds them.
 *
 * <p>Only {@code RenderGlobal} is touched, a client-only class a dedicated server never loads; this class names
 * client types only as strings. OptiFine, Celeritas, Vintagium and Nothirium replace {@code setupTerrain} (and thread
 * the rebuilds themselves); their shape is not vanilla's, so the patch is skipped with one log line.
 */
public final class ChunkRebuildsTransformer implements IClassTransformer {

    static final String PATCH = "P17";
    private static final String WHAT = "threaded chunk rebuilds (RenderGlobal.setupTerrain, updateChunks)";
    private static final String TARGET = "net.minecraft.client.renderer.RenderGlobal";
    private static final String HOOKS = "com/exiledradio/backportcore/render/client/ChunkRebuildHooks";
    private static final String RENDER_CHUNK = "net/minecraft/client/renderer/chunk/RenderChunk";
    private static final Double NEAR = 768.0D;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode setup = Asm.method(node, "setupTerrain", "func_174970_a",
                "(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V");
        MethodNode update = Asm.method(node, "updateChunks", "func_174967_a", "(J)V");
        if (setup == null || update == null) {
            Asm.skipped(PATCH, WHAT, "setupTerrain or updateChunks not found");
            return basicClass;
        }
        int near = Asm.constants(setup, NEAR).size();
        List<MethodInsnNode> setupCalls = immediateCalls(setup);
        List<MethodInsnNode> updateCalls = immediateCalls(update);
        if (near != 1 || setupCalls.size() != 1 || updateCalls.size() != 1) {
            Asm.skipped(PATCH, WHAT, "not vanilla's shape (" + near + " 768.0 constants, " + setupCalls.size()
                    + " + " + updateCalls.size() + " needsImmediateUpdate calls; OptiFine and the Sodium ports replace it)");
            return basicClass;
        }
        setup.instructions.insert(Asm.constants(setup, NEAR).get(0),
                new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "nearbyDistanceSq", "(D)D", false));
        setup.instructions.insert(setupCalls.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "needsImmediateUpdate", "(Z)Z", false));
        update.instructions.insert(updateCalls.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "needsImmediateUpdate", "(Z)Z", false));
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }

    private static List<MethodInsnNode> immediateCalls(MethodNode method) {
        return Asm.calls(method, Opcodes.INVOKEVIRTUAL, RENDER_CHUNK, "needsImmediateUpdate", "func_188281_o", "()Z");
    }
}
