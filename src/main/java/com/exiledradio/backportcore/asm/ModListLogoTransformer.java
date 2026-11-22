package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Patch P45 (docs/PATCHES.md): Forge's mod list draws a mod's logo in color in a world too. {@code GuiModList.Info}'s
 * {@code drawHeader} draws the logo with no color of its own, right after {@code GuiScrollingList} drew the in-world
 * gradient, whose vertex colors leave OpenGL's current color near black while {@code GlStateManager} believes it
 * white. {@code drawHeader} now starts with {@code PresentationHooks.modListHeader()}, which sets white for real.
 */
public final class ModListLogoTransformer implements IClassTransformer {

    static final String PATCH = "P45";
    private static final String WHAT = "mod list logo color (GuiModList.Info.drawHeader)";
    private static final String TARGET = "net.minecraftforge.fml.client.GuiModList$Info";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode m = Asm.method(node, "drawHeader", "drawHeader", "(IILnet/minecraft/client/renderer/Tessellator;)V");
        if (m == null) {
            Asm.skipped(PATCH, WHAT, "method not found");
            return basicClass;
        }
        m.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/exiledradio/backportcore/presentation/client/PresentationHooks", "modListHeader", "()V", false));
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
