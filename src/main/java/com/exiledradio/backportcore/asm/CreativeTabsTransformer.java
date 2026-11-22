package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch P47 (docs/PATCHES.md): 1.19.3's creative tabs ({@code creative.client.ModernTabs}). In
 * {@code GuiContainerCreative}:
 * <ul>
 *   <li>{@code initGui}: its one {@code CREATIVE_TAB_ARRAY.length} becomes {@code CreativeScreenHooks.tabCount(array)}
 *       and its one {@code CREATIVE_TAB_ARRAY[i]} becomes {@code openTab(array, i)} (an instruction replaced by a
 *       static call taking and leaving the same stack);</li>
 *   <li>{@code drawScreen} and {@code drawGuiContainerBackgroundLayer}: their one {@code Arrays.copyOfRange} each (the
 *       page's tabs) becomes {@code tabsOnPage} with the same arguments;</li>
 *   <li>{@code drawGuiContainerBackgroundLayer}: its one {@code getTabPage()} (whether the open tab is on the page shown)
 *       becomes {@code pageOf(tab)}, the page the hooks give it;</li>
 *   <li>{@code renderToolTip}: its one {@code Item.getCreativeTab()} (the search tab's tooltip) becomes
 *       {@code searchTooltipTab(item)};</li>
 *   <li>{@code isMouseOverTab}, {@code renderCreativeInventoryHoveringText} and {@code drawTab}: the whole body becomes
 *       one call to the hook of that name, which runs 1.12.2's own code until 1.19.3's tabs are on. The new bodies are
 *       straight-line, so they need no stack map frames.</li>
 * </ul>
 * Every shape is checked first: one mismatch and the class is left as it was.
 */
public final class CreativeTabsTransformer implements IClassTransformer {

    static final String PATCH = "P47";
    private static final String WHAT = "1.19.3's creative tabs (GuiContainerCreative)";
    private static final String TARGET = "net.minecraft.client.gui.inventory.GuiContainerCreative";
    static final String HOOKS = "com/exiledradio/backportcore/creative/client/CreativeScreenHooks";
    private static final String GUI = "net/minecraft/client/gui/inventory/GuiContainerCreative";
    private static final String TABS = "net/minecraft/creativetab/CreativeTabs";
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String TAB_DESC = "L" + TABS + ";";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        return patch(basicClass);
    }

    static byte[] patch(byte[] basicClass) {
        ClassNode node = Asm.read(basicClass);
        MethodNode init = Asm.method(node, "initGui", "func_73866_w_", "()V");
        MethodNode draw = Asm.method(node, "drawScreen", "func_73863_a", "(IIF)V");
        MethodNode background = Asm.method(node, "drawGuiContainerBackgroundLayer", "func_146976_a", "(FII)V");
        MethodNode tooltip = Asm.method(node, "renderToolTip", "func_146285_a", "(Lnet/minecraft/item/ItemStack;II)V");
        MethodNode over = Asm.method(node, "isMouseOverTab", "func_147049_a", "(" + TAB_DESC + "II)Z");
        MethodNode hover = Asm.method(node, "renderCreativeInventoryHoveringText", "func_147052_b", "(" + TAB_DESC + "II)Z");
        MethodNode tab = Asm.method(node, "drawTab", "func_147051_a", "(" + TAB_DESC + ")V");
        if (init == null || draw == null || background == null || tooltip == null || over == null || hover == null || tab == null) {
            Asm.skipped(PATCH, WHAT, "a method is missing");
            return basicClass;
        }
        List<AbstractInsnNode> lengths = afterArray(init, Opcodes.ARRAYLENGTH);
        List<AbstractInsnNode> loads = afterArray(init, -1);
        String copyDesc = "([Ljava/lang/Object;II)[Ljava/lang/Object;";
        int drawCopies = Asm.calls(draw, Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOfRange", "copyOfRange", copyDesc).size();
        int backgroundCopies = Asm.calls(background, Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOfRange", "copyOfRange", copyDesc).size();
        int tabCalls = Asm.calls(tooltip, Opcodes.INVOKEVIRTUAL, ITEM, "getCreativeTab", "func_77640_w", "()" + TAB_DESC).size();
        // Forge's getTabPage keeps its name in production.
        int pages = Asm.calls(background, Opcodes.INVOKEVIRTUAL, TABS, "getTabPage", "getTabPage", "()I").size();
        if (lengths.size() != 1 || loads.size() != 1 || drawCopies != 1 || backgroundCopies != 1 || tabCalls != 1 || pages != 1) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + lengths.size() + " tab counts, " + loads.size() + " tab loads, " + drawCopies + "+"
                    + backgroundCopies + " page copies, " + tabCalls + " tooltip tab calls, " + pages + " selected-tab pages)");
            return basicClass;
        }
        init.instructions.set(lengths.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tabCount", "([" + TAB_DESC + ")I", false));
        init.instructions.set(loads.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "openTab", "([" + TAB_DESC + "I)" + TAB_DESC, false));
        for (MethodNode m : new MethodNode[] {draw, background}) {
            for (MethodInsnNode call : Asm.calls(m, Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOfRange", "copyOfRange", copyDesc)) {
                m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tabsOnPage", copyDesc, false));
            }
        }
        for (MethodInsnNode call : Asm.calls(background, Opcodes.INVOKEVIRTUAL, TABS, "getTabPage", "getTabPage", "()I")) {
            background.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "pageOf", "(" + TAB_DESC + ")I", false));
        }
        for (MethodInsnNode call : Asm.calls(tooltip, Opcodes.INVOKEVIRTUAL, ITEM, "getCreativeTab", "func_77640_w", "()" + TAB_DESC)) {
            tooltip.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "searchTooltipTab", "(L" + ITEM + ";)" + TAB_DESC, false));
        }
        replaceBody(over, "isMouseOverTab", "(L" + GUI + ";" + TAB_DESC + "II)Z", true, Opcodes.IRETURN);
        replaceBody(hover, "renderCreativeInventoryHoveringText", "(L" + GUI + ";" + TAB_DESC + "II)Z", true, Opcodes.IRETURN);
        replaceBody(tab, "drawTab", "(L" + GUI + ";" + TAB_DESC + ")V", false, Opcodes.RETURN);
        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }

    /**
     * @return the instructions right after each {@code GETSTATIC CreativeTabs.CREATIVE_TAB_ARRAY} that are
     * {@code opcode}, or (opcode -1) each {@code AALOAD} right after that field and an {@code ILOAD}
     */
    private static List<AbstractInsnNode> afterArray(MethodNode method, int opcode) {
        List<AbstractInsnNode> out = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.GETSTATIC) {
                continue;
            }
            FieldInsnNode f = (FieldInsnNode) insn;
            if (!TABS.equals(f.owner) || !Asm.named(f.name, "CREATIVE_TAB_ARRAY", "field_78032_a")) {
                continue;
            }
            AbstractInsnNode next = next(insn);
            if (opcode >= 0) {
                if (next != null && next.getOpcode() == opcode) {
                    out.add(next);
                }
            } else if (next instanceof VarInsnNode && next.getOpcode() == Opcodes.ILOAD) {
                AbstractInsnNode load = next(next);
                if (load != null && load.getOpcode() == Opcodes.AALOAD) {
                    out.add(load);
                }
            }
        }
        return out;
    }

    private static AbstractInsnNode next(AbstractInsnNode insn) {
        AbstractInsnNode n = insn.getNext();
        while (n != null && n.getOpcode() < 0) {
            n = n.getNext();
        }
        return n;
    }

    /** The whole body becomes {@code return Hooks.name(this, tab[, mouseX, mouseY])}. */
    private static void replaceBody(MethodNode method, String hook, String desc, boolean mouse, int returnOp) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        if (mouse) {
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        }
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, desc, false));
        method.instructions.add(new InsnNode(returnOp));
    }
}
