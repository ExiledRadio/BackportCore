package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patch P34 (docs/PATCHES.md): map decorations newer than 1.12.2 drawn from their own sprites
 * ({@code map.MapIcons}). Forge's {@code MapDecoration.render(int)}, which the map renderer asks first for every
 * decoration and which always answers false, becomes {@code return MapIcons.render(this, index)}: false (vanilla
 * draws from its sheet) for every type but the ones added through {@code MapIcons}.
 *
 * <p>The method is client only; on a dedicated server Forge's side check may already have removed it, and the
 * class is left alone.
 */
public final class MapIconTransformer implements IClassTransformer {

    static final String PATCH = "P34";
    private static final String WHAT = "newer map decorations (MapDecoration.render)";
    private static final String TARGET = "net.minecraft.world.storage.MapDecoration";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        return patch(basicClass);
    }

    static byte[] patch(byte[] basicClass) {
        ClassNode node = Asm.read(basicClass);
        // Forge's own method: the same name in development and production.
        MethodNode render = Asm.method(node, "render", "render", "(I)Z");
        if (render == null) {
            Asm.STATE.put(PATCH, "no render(I)Z (a dedicated server)");
            return basicClass;
        }
        if ((render.access & Opcodes.ACC_STATIC) != 0 || render.instructions.size() > 12) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (not Forge's one-line render)");
            return basicClass;
        }
        InsnList body = new InsnList();
        body.add(new VarInsnNode(Opcodes.ALOAD, 0));
        body.add(new VarInsnNode(Opcodes.ILOAD, 1));
        body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/exiledradio/backportcore/map/MapIcons", "render",
                "(Lnet/minecraft/world/storage/MapDecoration;I)Z", false));
        body.add(new InsnNode(Opcodes.IRETURN));
        render.instructions.clear();
        render.instructions.add(body);
        render.tryCatchBlocks.clear();
        if (render.localVariables != null) {
            render.localVariables.clear();
        }
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
