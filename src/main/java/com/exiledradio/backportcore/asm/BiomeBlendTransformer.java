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
 * Patch P68 (docs/PATCHES.md): 1.13's Biome Blend. {@code BiomeColorHelper.getColorAtPos} (private, client only) walks
 * {@code getAllInBoxMutable(pos.add(-1, 0, -1), pos.add(1, 0, 1))} and divides each channel's sum by 9. The four
 * {@code ICONST_M1}/{@code ICONST_1} arguments of the two {@code BlockPos.add(int, int, int)} calls become
 * {@code BiomeBlend.low()} and {@code BiomeBlend.high()}, and the three {@code BIPUSH 9} before an {@code IDIV} become
 * {@code BiomeBlend.count()}: the same stack, so the frames stay valid. P44 (underground biome colors) changes the
 * {@code getBiome} call of the same method, which this one leaves alone.
 *
 * <p>Moved from Update Aquatic Backport's {@code asm/BiomeBlendPatch} (its P6) in Core 1.4.7. While an older Update
 * Aquatic jar that still has that transformer is on the class path, this one changes nothing (the method is patched
 * once, by the old jar, whose own option copy follows Core's).
 */
public final class BiomeBlendTransformer implements IClassTransformer {

    static final String PATCH = "P68";
    private static final String WHAT = "Biome Blend (BiomeColorHelper.getColorAtPos)";
    private static final String TARGET = "net.minecraft.world.biome.BiomeColorHelper";
    private static final String HOOKS = "com/exiledradio/backportcore/presentation/versions/BiomeBlend";
    /** The older Update Aquatic jar's own patch and hook. */
    static final String LEGACY_PATCH = "com/exiledradio/updateaquaticbackport/asm/BiomeBlendPatch.class";
    private static final String LEGACY_HOOKS = "com/exiledradio/updateaquaticbackport/water/BiomeBlend";
    private static final String BLOCK_POS = "net/minecraft/util/math/BlockPos";
    private static final String ADD_DESC = "(III)L" + BLOCK_POS + ";";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) {
            return bytes;
        }
        ClassLoader loader = BiomeBlendTransformer.class.getClassLoader();
        if (loader != null && loader.getResource(LEGACY_PATCH) != null) {
            Asm.skipped(PATCH, WHAT, "an older Update Aquatic Backport jar patches it itself");
            return bytes;
        }
        ClassNode node = Asm.read(bytes);
        MethodNode method = Asm.method(node, "getColorAtPos", "func_180285_a",
                "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;"
                        + "Lnet/minecraft/world/biome/BiomeColorHelper$ColorResolver;)I");
        if (method == null) {
            Asm.skipped(PATCH, WHAT, "method not found");
            return bytes;
        }
        List<AbstractInsnNode> lows = new ArrayList<AbstractInsnNode>();
        List<AbstractInsnNode> highs = new ArrayList<AbstractInsnNode>();
        List<AbstractInsnNode> nines = new ArrayList<AbstractInsnNode>();
        int adds = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) insn).owner.equals(LEGACY_HOOKS)) {
                Asm.skipped(PATCH, WHAT, "already patched by an older Update Aquatic Backport jar");
                return bytes;
            }
            if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.owner.equals(BLOCK_POS) && Asm.named(call.name, "add", "func_177982_a") && call.desc.equals(ADD_DESC)) {
                    adds++;
                    // x, y, z: the three instructions before the call.
                    AbstractInsnNode z = call.getPrevious();
                    AbstractInsnNode y = z == null ? null : z.getPrevious();
                    AbstractInsnNode x = y == null ? null : y.getPrevious();
                    if (x != null && y.getOpcode() == Opcodes.ICONST_0 && x.getOpcode() == z.getOpcode()) {
                        if (x.getOpcode() == Opcodes.ICONST_M1) {
                            lows.add(x);
                            lows.add(z);
                        } else if (x.getOpcode() == Opcodes.ICONST_1) {
                            highs.add(x);
                            highs.add(z);
                        }
                    }
                }
            } else if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == 9
                    && insn.getNext() != null && insn.getNext().getOpcode() == Opcodes.IDIV) {
                nines.add(insn);
            }
        }
        if (adds != 2 || lows.size() != 2 || highs.size() != 2 || nines.size() != 3) {
            Asm.skipped(PATCH, WHAT, "not 1.12.2's 3x3 average (adds " + adds + ", corners " + lows.size() + "/" + highs.size()
                    + ", divisors " + nines.size() + ")");
            return bytes;
        }
        for (AbstractInsnNode insn : lows) {
            method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "low", "()I", false));
        }
        for (AbstractInsnNode insn : highs) {
            method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "high", "()I", false));
        }
        for (AbstractInsnNode insn : nines) {
            method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "count", "()I", false));
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
