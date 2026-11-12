package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patch P20 (docs/PATCHES.md): Fluidlogged API's water outside y 0-255 in Caves &amp; Cliffs Backport's extended
 * worlds. Before each return of {@code Chunk(World, int, int)}, a call to {@code
 * waterlog.fluidlogged.SectionFluidStates.install(this)}, which gives the chunk of an extended world a store with
 * one container per 16-block section in place of Fluidlogged API's (whose positions keep y in eight bits).
 *
 * <p>Fluidlogged API makes its store in its own hook ({@code PluginChunk$Hooks.createCapabilityInstance}) right after
 * the constructor's {@code super()}, before {@code this.world} is set, so the world is only known at the end of the
 * constructor. Only applied when both Fluidlogged API and Caves &amp; Cliffs Backport are installed; the hook then
 * still leaves every chunk of a world without extended height as Fluidlogged API made it.
 */
public final class ExtendedFluidStatesTransformer implements IClassTransformer {

    static final String PATCH = "P20";
    private static final String WHAT = "per-section Fluidlogged API water in extended worlds (Chunk.<init>)";
    private static final String TARGET = "net.minecraft.world.chunk.Chunk";
    private static final String DESC = "(Lnet/minecraft/world/World;II)V";
    static final String HOOK = "com/exiledradio/backportcore/waterlog/fluidlogged/SectionFluidStates";
    private static final String CC_HEIGHTS = "com/exiledradio/cavesandcliffsbackport/height/Heights.class";
    private static final String FLUIDLOGGED = "git/jbredwards/fluidlogged_api/api/block/IFluidloggable.class";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        if (Launch.classLoader == null || Launch.classLoader.findResource(FLUIDLOGGED) == null) {
            Asm.STATE.put(PATCH, "Fluidlogged API not installed");
            return basicClass;
        }
        if (Launch.classLoader.findResource(CC_HEIGHTS) == null) {
            Asm.STATE.put(PATCH, "Caves & Cliffs Backport not installed");
            Asm.LOG.debug("{} {}: Caves & Cliffs Backport not installed, nothing to do.", PATCH, WHAT);
            return basicClass;
        }
        return patch(basicClass);
    }

    /** The patch itself, whatever is installed (the self-tests run it on Forge's SRG-named class too). */
    public static byte[] patch(byte[] basicClass) {
        ClassNode node = Asm.read(basicClass);
        MethodNode init = Asm.method(node, "<init>", "<init>", DESC);
        if (init == null) {
            Asm.skipped(PATCH, WHAT, "Chunk(World, int, int) not found");
            return basicClass;
        }
        int returns = 0;
        for (AbstractInsnNode insn : init.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "install", "(Lnet/minecraft/world/chunk/Chunk;)V", false));
                init.instructions.insertBefore(insn, call);
                returns++;
            }
        }
        if (returns == 0) {
            Asm.skipped(PATCH, WHAT, "no return in Chunk(World, int, int)");
            return basicClass;
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
