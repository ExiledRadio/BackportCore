package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Patches P73 to P76 (docs/PATCHES.md): version-feel pieces Core took over from the backports in 1.4.7, each passing
 * one vanilla value through {@code presentation.versions.LookHooks} (a static call taking the value and returning it,
 * with no jump, no new local and no new frame).
 *
 * <ul>
 *   <li><b>P73</b> (1.19.4's Damage Tilt), {@code EntityRenderer.hurtCameraEffect}: its one {@code 14.0F} (the tilt in
 *       degrees) becomes {@code damageTilt(14.0F)}.</li>
 *   <li><b>P74</b> (1.19.4's Glint Speed and Strength on items), {@code RenderItem.renderEffect}: each of its two
 *       {@code Minecraft.getSystemTime()} results through {@code glintTime}, each of its two {@code -8372020} colors
 *       through {@code glintColor}.</li>
 *   <li><b>P75</b> (the same on armor and elytra), {@code LayerArmorBase.renderEnchantedGlint}: its clock
 *       {@code ticksExisted + partialTicks} through {@code glintTicks} before it is stored, and the glint color's three
 *       constants {@code 0.38F, 0.19F, 0.608F} through {@code glintComponent}.</li>
 *   <li><b>P76</b> (1.20.5's Ctrl + pick block), {@code Minecraft.storeTEInStack}: its result through
 *       {@code pickedBlock} before each return.</li>
 * </ul>
 * P73 to P75 were Wild Update Backport's P1 to P3 and P76 was Trails &amp; Tales Backport's P1: while an older jar of
 * that backport, which still patches the method itself, is on the class path, the patch is left to it (the method is
 * never patched twice). A target of another shape is left alone (logged), so vanilla's behavior stays.
 */
public final class LookVersionsTransformer implements IClassTransformer {

    private static final String HOOKS = "com/exiledradio/backportcore/presentation/versions/LookHooks";
    static final String RENDERER = "net.minecraft.client.renderer.EntityRenderer";
    static final String ITEM = "net.minecraft.client.renderer.RenderItem";
    static final String ARMOR = "net.minecraft.client.renderer.entity.layers.LayerArmorBase";
    static final String MINECRAFT = "net.minecraft.client.Minecraft";
    private static final String STACK = "Lnet/minecraft/item/ItemStack;";

    /** Wild Update Backport's own transformer of P73 to P75, in its releases before Core 1.4.7. */
    static final String WILD_UPDATE_COPY = "com.exiledradio.wildupdatebackport.asm.LookTransformer";
    /** Trails &amp; Tales Backport's own transformer of P76, in its releases before Core 1.4.7. */
    static final String TRAILS_COPY = "com.exiledradio.trailsandtalesbackport.asm.PickBlockTransformer";

    private static final Map<String, Boolean> PRESENT = new ConcurrentHashMap<String, Boolean>();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (RENDERER.equals(transformedName)) {
            return older(WILD_UPDATE_COPY, "P73") ? basicClass : damageTilt(basicClass);
        }
        if (ITEM.equals(transformedName)) {
            return older(WILD_UPDATE_COPY, "P74") ? basicClass : itemGlint(basicClass);
        }
        if (ARMOR.equals(transformedName)) {
            return older(WILD_UPDATE_COPY, "P75") ? basicClass : armorGlint(basicClass);
        }
        if (MINECRAFT.equals(transformedName)) {
            return older(TRAILS_COPY, "P76") ? basicClass : pickBlock(basicClass);
        }
        return basicClass;
    }

    /**
     * @return true (and the patch reported skipped) while an older backport jar that still has its own copy of the patch
     *         is on the class path. Looked up as a resource, never loaded; the coremods' jars are on the launch class
     *         loader's path before any Minecraft class is transformed.
     */
    private static boolean older(String className, String patch) {
        Boolean known = PRESENT.get(className);
        if (known == null) {
            ClassLoader loader = LookVersionsTransformer.class.getClassLoader();
            known = loader != null && loader.getResource(className.replace('.', '/') + ".class") != null;
            PRESENT.put(className, known);
        }
        if (known) {
            Asm.STATE.put(patch, "left to " + className);
            Asm.LOG.info("{}: an older backport jar patches this method itself ({}); Core leaves it to that jar.", patch, className);
        }
        return known;
    }

    private static byte[] damageTilt(byte[] bytes) {
        String what = "damage tilt (EntityRenderer.hurtCameraEffect)";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "hurtCameraEffect", "func_78482_e", "(F)V");
        List<LdcInsnNode> tilt = m == null ? null : Asm.constants(m, 14.0F);
        if (tilt == null || tilt.size() != 1) {
            Asm.skipped("P73", what, m == null ? "method not found" : tilt.size() + " constants 14.0");
            return bytes;
        }
        m.instructions.insert(tilt.get(0), hook("damageTilt", "(F)F"));
        Asm.applied("P73", what);
        return Asm.write(node);
    }

    private static byte[] itemGlint(byte[] bytes) {
        String what = "item glint (RenderItem.renderEffect)";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "renderEffect", "func_191966_a", "(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V");
        if (m == null) {
            Asm.skipped("P74", what, "method not found");
            return bytes;
        }
        List<MethodInsnNode> clocks = Asm.calls(m, Opcodes.INVOKESTATIC, "net/minecraft/client/Minecraft", "getSystemTime", "func_71386_F", "()J");
        List<AbstractInsnNode> colors = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof LdcInsnNode && Integer.valueOf(-8372020).equals(((LdcInsnNode) i).cst)) {
                colors.add(i);
            }
        }
        if (clocks.size() != 2 || colors.size() != 2) {
            Asm.skipped("P74", what, clocks.size() + " clocks, " + colors.size() + " colors");
            return bytes;
        }
        for (AbstractInsnNode c : clocks) {
            m.instructions.insert(c, hook("glintTime", "(J)J"));
        }
        for (AbstractInsnNode c : colors) {
            m.instructions.insert(c, hook("glintColor", "(I)I"));
        }
        Asm.applied("P74", what);
        return Asm.write(node);
    }

    private static byte[] armorGlint(byte[] bytes) {
        String what = "armor glint (LayerArmorBase.renderEnchantedGlint)";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "renderEnchantedGlint", "func_188364_a",
                "(Lnet/minecraft/client/renderer/entity/RenderLivingBase;Lnet/minecraft/entity/EntityLivingBase;"
                        + "Lnet/minecraft/client/model/ModelBase;FFFFFFF)V");
        if (m == null) {
            Asm.skipped("P75", what, "method not found");
            return bytes;
        }
        // The clock: the first FSTORE, which must follow the read of ticksExisted.
        AbstractInsnNode store = null;
        boolean ticks = false;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof FieldInsnNode && i.getOpcode() == Opcodes.GETFIELD
                    && Asm.named(((FieldInsnNode) i).name, "ticksExisted", "field_70173_aa")) {
                ticks = true;
            }
            if (i.getOpcode() == Opcodes.FSTORE) {
                store = i;
                break;
            }
        }
        List<LdcInsnNode> r = Asm.constants(m, 0.38F);
        List<LdcInsnNode> g = Asm.constants(m, 0.19F);
        List<LdcInsnNode> b = Asm.constants(m, 0.608F);
        if (!ticks || store == null || r.size() != 1 || g.size() != 1 || b.size() != 1) {
            Asm.skipped("P75", what, "unexpected shape");
            return bytes;
        }
        m.instructions.insertBefore(store, hook("glintTicks", "(F)F"));
        for (AbstractInsnNode c : new AbstractInsnNode[] {r.get(0), g.get(0), b.get(0)}) {
            m.instructions.insert(c, hook("glintComponent", "(F)F"));
        }
        Asm.applied("P75", what);
        return Asm.write(node);
    }

    private static byte[] pickBlock(byte[] bytes) {
        String what = "Ctrl + pick block (Minecraft.storeTEInStack)";
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "storeTEInStack", "func_184119_a", "(" + STACK + "Lnet/minecraft/tileentity/TileEntity;)" + STACK);
        if (m == null) {
            Asm.skipped("P76", what, "method not found");
            return bytes;
        }
        List<AbstractInsnNode> returns = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.ARETURN) {
                returns.add(insn);
            }
        }
        if (returns.isEmpty()) {
            Asm.skipped("P76", what, "no return");
            return bytes;
        }
        for (AbstractInsnNode ret : returns) {
            m.instructions.insertBefore(ret, hook("pickedBlock", "(" + STACK + ")" + STACK));
        }
        Asm.applied("P76", what);
        return Asm.write(node);
    }

    private static MethodInsnNode hook(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
    }
}
