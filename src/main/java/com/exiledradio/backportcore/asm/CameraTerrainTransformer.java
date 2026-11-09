package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

/**
 * Patch P18 (docs/PATCHES.md): in third person, the terrain's visibility search starts at the camera, as 1.14's
 * {@code WorldRenderer.setupTerrain} starts it at {@code ActiveRenderInfo.getBlockPos()}, instead of at the
 * player's eyes.
 *
 * <p>One change to {@code RenderGlobal.setupTerrain}: the block it builds from the view entity's eye height (the
 * method's one {@code getEyeHeight()} call, then {@code new BlockPos(DDD)}) passes through
 * {@code pose.client.ClientHooks.terrainStart(BlockPos)}, which returns it unchanged in first person. The hook
 * takes and returns the value already on the stack, reads no local and adds no branch, so the class keeps its
 * frames.
 */
public final class CameraTerrainTransformer implements IClassTransformer {

    static final String PATCH = "P18";
    private static final String WHAT = "terrain visibility from the camera (RenderGlobal.setupTerrain)";
    private static final String TARGET = "net.minecraft.client.renderer.RenderGlobal";
    private static final String BLOCK_POS = "net/minecraft/util/math/BlockPos";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode setup = Asm.method(node, "setupTerrain", "func_174970_a",
                "(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V");
        if (setup == null) {
            Asm.skipped(PATCH, WHAT, "setupTerrain not found");
            return basicClass;
        }
        List<MethodInsnNode> eyes = Asm.calls(setup, Opcodes.INVOKEVIRTUAL, null, "getEyeHeight", "func_70047_e", "()F");
        MethodInsnNode init = eyes.size() == 1 ? nextBlockPos(eyes.get(0)) : null;
        if (init == null) {
            Asm.skipped(PATCH, WHAT, "not vanilla's shape (" + eyes.size() + " getEyeHeight calls)");
            return basicClass;
        }
        setup.instructions.insert(init, new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.CLIENT_HOOKS, "terrainStart",
                "(L" + BLOCK_POS + ";)L" + BLOCK_POS + ";", false));
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }

    /** @return the {@code BlockPos(DDD)} constructor the eye height goes into, if it is the next call */
    private static MethodInsnNode nextBlockPos(AbstractInsnNode from) {
        for (AbstractInsnNode insn = from.getNext(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                return call.getOpcode() == Opcodes.INVOKESPECIAL && BLOCK_POS.equals(call.owner)
                        && "<init>".equals(call.name) && "(DDD)V".equals(call.desc) ? call : null;
            }
        }
        return null;
    }
}
