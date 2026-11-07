package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch P1 (docs/PATCHES.md): a living entity's movement in water, 1.13's way for players.
 *
 * <p>Three additions to {@code EntityLivingBase.travel}, each a call into {@code pose.MovementHooks}, which
 * returns vanilla's own value for every entity no backport rule claims:
 * <ol>
 *   <li>at the start: {@code MovementHooks.beforeTravel(this)} - where 1.14's {@code PlayerEntity.travel}
 *       turns a swimming player's vertical motion toward the look before moving;</li>
 *   <li>after the one {@code getWaterSlowDown()} call: {@code MovementHooks.waterSlowDown(value, this)} -
 *       1.13's 0.9 slowdown while sprinting;</li>
 *   <li>the water branch's {@code motionY -= 0.02D} (the second of the method's two {@code 0.02D}
 *       constants; the first is lava's) takes its constant from {@code MovementHooks.waterGravity(this)} -
 *       1.13's lighter pull, and none while sprinting.</li>
 * </ol>
 */
public final class WaterTravelTransformer implements IClassTransformer {

    static final String PATCH = "P1";
    private static final String WHAT = "water travel (EntityLivingBase.travel)";
    private static final String TARGET = "net.minecraft.entity.EntityLivingBase";
    private static final String ELB = "net/minecraft/entity/EntityLivingBase";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        MethodNode travel = Asm.method(node, "travel", "func_191986_a", "(FFF)V");
        if (travel == null) {
            Asm.skipped(PATCH, WHAT, "travel(FFF)V not found");
            return basicClass;
        }
        List<MethodInsnNode> slowdown = Asm.calls(travel, Opcodes.INVOKEVIRTUAL, null, "getWaterSlowDown", "func_189749_co", "()F");
        List<LdcInsnNode> gravity = new ArrayList<LdcInsnNode>();
        for (AbstractInsnNode insn : travel.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && Double.valueOf(0.02D).equals(((LdcInsnNode) insn).cst)) {
                gravity.add((LdcInsnNode) insn);
            }
        }
        if (slowdown.size() != 1 || gravity.size() != 2
                || travel.instructions.indexOf(gravity.get(1)) < travel.instructions.indexOf(slowdown.get(0))) {
            Asm.skipped(PATCH, WHAT, "unexpected shape (" + slowdown.size() + " getWaterSlowDown calls, "
                    + gravity.size() + " 0.02D constants)");
            return basicClass;
        }

        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.HOOKS, "beforeTravel", "(L" + ELB + ";)V", false));
        travel.instructions.insert(head);

        InsnList slow = new InsnList();
        slow.add(new VarInsnNode(Opcodes.ALOAD, 0));
        slow.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.HOOKS, "waterSlowDown", "(FL" + ELB + ";)F", false));
        travel.instructions.insert(slowdown.get(0), slow);

        InsnList pull = new InsnList();
        pull.add(new VarInsnNode(Opcodes.ALOAD, 0));
        pull.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Asm.HOOKS, "waterGravity", "(L" + ELB + ";)D", false));
        LdcInsnNode water = gravity.get(1);
        travel.instructions.insertBefore(water, pull);
        travel.instructions.remove(water);

        Asm.applied(PATCH, WHAT);
        return Asm.write(node);
    }
}
