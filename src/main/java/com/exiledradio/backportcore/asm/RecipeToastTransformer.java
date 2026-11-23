package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Patch P66 (docs/PATCHES.md): 1.13's recipe icon on the "New Recipes Unlocked!" toast. {@code RecipeToast.draw}
 * calls {@code PresentationHooks.recipeToastIcon(GuiToast)} right before it draws the recipe's output, so the crafting
 * table is drawn small in the corner behind it, as 1.13 draws a crafting recipe's toast. The call takes the toast GUI
 * from local 1 and leaves the stack as it was. A class whose shape differs is left as it is, with one line in the log.
 */
public final class RecipeToastTransformer implements IClassTransformer {

    static final String PATCH = "P66";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !"net.minecraft.client.gui.toasts.RecipeToast".equals(transformedName)) {
            return basicClass;
        }
        String what = "recipe toast icon (RecipeToast.draw)";
        org.objectweb.asm.tree.ClassNode node = Asm.read(basicClass);
        MethodNode m = Asm.method(node, "draw", "func_193653_a",
                "(Lnet/minecraft/client/gui/toasts/GuiToast;J)Lnet/minecraft/client/gui/toasts/IToast$Visibility;");
        if (m == null) {
            Asm.skipped(PATCH, what, "method not found");
            return basicClass;
        }
        List<MethodInsnNode> calls = Asm.calls(m, Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/RenderItem",
                "renderItemAndEffectIntoGUI", "func_184391_a", "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;II)V");
        if (calls.size() != 1) {
            Asm.skipped(PATCH, what, calls.size() + " item draws");
            return basicClass;
        }
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/exiledradio/backportcore/presentation/client/PresentationHooks",
                "recipeToastIcon", "(Lnet/minecraft/client/gui/toasts/GuiToast;)V", false));
        m.instructions.insertBefore(calls.get(0), hook);
        Asm.applied(PATCH, what);
        return Asm.write(node);
    }
}
