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
 * Patch P37 (docs/PATCHES.md): 1.13's on-ground spawn spot holds no water.
 *
 * <p>Forge's {@code WorldEntitySpawner.canCreatureTypeSpawnBody} ends its on-ground branch with
 * {@code isValidEmptySpawnBlock(state at pos) && isValidEmptySpawnBlock(state above)}. Each of the two results passes
 * through a hook after the call ({@code GroundSpawnHooks.feet} and {@code head}, with the method's world and position),
 * which also asks that the block holds no water. The stack is the same afterwards, so the frames stay valid; the calls
 * stay in place for other coremods and mixins.
 */
public final class GroundSpawnTransformer implements IClassTransformer {

    static final String PATCH = "P37";
    private static final String WHAT = "no ground spawns in water-holding blocks (WorldEntitySpawner.canCreatureTypeSpawnBody)";
    private static final String TARGET = "net.minecraft.world.WorldEntitySpawner";
    private static final String OWNER = "net/minecraft/world/WorldEntitySpawner";
    private static final String HOOK = "com/exiledradio/backportcore/worldgen/GroundSpawnHooks";
    private static final String DESC = "(Lnet/minecraft/entity/EntityLiving$SpawnPlacementType;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Z";
    private static final String VALID_DESC = "(Lnet/minecraft/block/state/IBlockState;)Z";
    private static final String HOOK_DESC = "(ZLnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        // Forge's own method: the same name in every environment.
        MethodNode method = Asm.method(node, "canCreatureTypeSpawnBody", "canCreatureTypeSpawnBody", DESC);
        if (method == null) {
            Asm.skipped(PATCH, WHAT, "canCreatureTypeSpawnBody not found");
            return basicClass;
        }
        List<MethodInsnNode> calls = Asm.calls(method, Opcodes.INVOKESTATIC, OWNER, "isValidEmptySpawnBlock", "func_185331_a", VALID_DESC);
        if (calls.size() != 2) {
            Asm.skipped(PATCH, WHAT, calls.size() + " isValidEmptySpawnBlock calls, not 2");
            return basicClass;
        }
        hook(method, calls.get(0), "feet");
        hook(method, calls.get(1), "head");
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }

    private static void hook(MethodNode method, MethodInsnNode call, String hookName) {
        InsnList after = new InsnList();
        after.add(new VarInsnNode(Opcodes.ALOAD, 1));
        after.add(new VarInsnNode(Opcodes.ALOAD, 2));
        after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, hookName, HOOK_DESC, false));
        method.instructions.insert(call, after);
    }
}
