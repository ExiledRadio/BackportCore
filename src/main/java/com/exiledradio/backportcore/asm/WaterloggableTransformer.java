package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Patch P19 (docs/PATCHES.md): with Fluidlogged API installed, every class that lists {@code waterlog.Waterloggable}
 * among its interfaces also gets {@code waterlog.fluidlogged.FluidloggedWaterloggable}, Fluidlogged API's {@code
 * IFluidloggable} answered from the block's own choices. A family block names no Fluidlogged API type, so it loads
 * without Fluidlogged API; with it, it is an {@code IFluidloggable} exactly as if it had said so.
 *
 * <p>Only the interface list changes: no method, no frame. Whether Fluidlogged API is installed is read once, on the
 * first class that needs the answer, from the class loader (its coremod jar is on the class path by then). Without
 * it, nothing is changed.
 */
public final class WaterloggableTransformer implements IClassTransformer {

    static final String PATCH = "P19";
    private static final String WHAT = "Fluidlogged API waterlogging for Waterloggable blocks";
    static final String WATERLOGGABLE = "com/exiledradio/backportcore/waterlog/Waterloggable";
    static final String BRIDGE = "com/exiledradio/backportcore/waterlog/fluidlogged/FluidloggedWaterloggable";
    private static final String FLUIDLOGGABLE = "git/jbredwards/fluidlogged_api/api/block/IFluidloggable.class";

    private static final byte[] NEEDLE = WATERLOGGABLE.getBytes(StandardCharsets.UTF_8);

    /** null until first asked. */
    private static Boolean fluidlogged;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !contains(basicClass, NEEDLE)) {
            return basicClass;
        }
        ClassReader reader = new ClassReader(basicClass);
        String[] interfaces = reader.getInterfaces();
        if (!Arrays.asList(interfaces).contains(WATERLOGGABLE) || Arrays.asList(interfaces).contains(BRIDGE)) {
            return basicClass;
        }
        if (!fluidlogged()) {
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] ifaces) {
                String[] more = Arrays.copyOf(ifaces, ifaces.length + 1);
                more[ifaces.length] = BRIDGE;
                // A generic class's signature lists its interfaces too; keep the two in step for reflection.
                super.visit(version, access, name, signature == null ? null : signature + "L" + BRIDGE + ";", superName, more);
            }
        }, 0);
        Asm.STATE.put(PATCH, "applied");
        Asm.LOG.debug("{} {}: {}", PATCH, WHAT, transformedName);
        return writer.toByteArray();
    }

    private static synchronized boolean fluidlogged() {
        if (fluidlogged == null) {
            fluidlogged = Launch.classLoader != null && Launch.classLoader.findResource(FLUIDLOGGABLE) != null;
            Asm.LOG.info("{} {}: {}.", PATCH, WHAT, fluidlogged ? "Fluidlogged API found, blocks hold water"
                    : "Fluidlogged API not installed, blocks stay dry");
            if (!fluidlogged) {
                Asm.STATE.put(PATCH, "Fluidlogged API not installed");
            }
        }
        return fluidlogged;
    }

    private static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0, end = hay.length - needle.length; i <= end; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
