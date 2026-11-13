package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Patch P24 (docs/PATCHES.md): the sunrise and sunset color goes through {@code FogPipeline}'s sunrise steps
 * ({@code PresentationHooks.sunriseColors}), so a version can change it (1.21.11: rain and thunder dim it). Both of
 * 1.12.2's readers of {@code WorldProvider.calcSunriseSunsetColors} are swapped: {@code EntityRenderer.updateFogColor}
 * (the fog's blend toward the sunrise) and {@code RenderGlobal.renderSky} (the glow itself).
 */
public final class SunriseTransformer implements IClassTransformer {

    static final String PATCH = "P24";
    private static final String WHAT = "sunrise color (EntityRenderer.updateFogColor, RenderGlobal.renderSky)";
    private static final String RENDERER = "net.minecraft.client.renderer.EntityRenderer";
    private static final String GLOBAL = "net.minecraft.client.renderer.RenderGlobal";
    private static int done;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        boolean renderer = RENDERER.equals(transformedName);
        if (!renderer && !GLOBAL.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode method = renderer ? Asm.method(node, "updateFogColor", "func_78466_h", "(F)V")
                : Asm.method(node, "renderSky", "func_174976_a", "(FI)V");
        if (method == null) {
            Asm.skipped(PATCH, WHAT, (renderer ? "updateFogColor" : "renderSky") + " not found");
            return basicClass;
        }
        int swapped = Asm.swapCalls(method, Opcodes.INVOKEVIRTUAL, "net/minecraft/world/WorldProvider", "calcSunriseSunsetColors",
                "func_76560_a", "(FF)[F", "sunriseColors", "(Lnet/minecraft/world/WorldProvider;FF)[F");
        if (swapped != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + swapped + " calls in " + transformedName + ")");
            return basicClass;
        }
        if (++done == 2) {
            Asm.applied(PATCH, WHAT);
        }
        return Asm.write(node);
    }
}
