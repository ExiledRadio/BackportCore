package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Patch P9 (docs/PATCHES.md): a village starts by the biome the newest installed version reads.
 *
 * <p>{@code MapGenVillage.canSpawnStructureAtCoords} asks
 * {@code world.getBiomeProvider().areBiomesViable(chunkX * 16 + 8, chunkZ * 16 + 8, 0, VILLAGE_SPAWN_BIOMES)}:
 * the 1:4 cell at the chunk's block (8, 8). Its one {@code areBiomesViable} call changes owner: it becomes
 * {@code worldgen.WorldgenHooks.villageBiomes(provider, x, z, radius, biomes)}, which asks vanilla's method
 * until a backport of 1.13 or later is installed. Subclasses that call this method (Village &amp; Pillage's
 * villages) get it too.
 */
public final class VillageBiomeTransformer implements IClassTransformer {

    static final String PATCH = "P9";
    private static final String WHAT = "village biome point (MapGenVillage.canSpawnStructureAtCoords)";
    private static final String TARGET = "net.minecraft.world.gen.structure.MapGenVillage";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode method = Asm.method(node, "canSpawnStructureAtCoords", "func_75047_a", "(II)Z");
        if (method == null) {
            Asm.skipped(PATCH, WHAT, "canSpawnStructureAtCoords(II)Z not found");
            return basicClass;
        }
        List<MethodInsnNode> calls = Asm.calls(method, Opcodes.INVOKEVIRTUAL, "net/minecraft/world/biome/BiomeProvider",
                "areBiomesViable", "func_76940_a", "(IIILjava/util/List;)Z");
        if (calls.size() != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + calls.size() + " areBiomesViable calls)");
            return basicClass;
        }
        method.instructions.set(calls.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.WORLDGEN_HOOKS, "villageBiomes",
                "(Lnet/minecraft/world/biome/BiomeProvider;IIILjava/util/List;)Z", false));
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
