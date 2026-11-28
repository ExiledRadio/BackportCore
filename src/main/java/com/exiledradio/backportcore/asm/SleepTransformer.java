package com.exiledradio.backportcore.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patches P86 and P87 (docs/PATCHES.md): 1.17's {@code playersSleepingPercentage}, {@code mechanics/SleepRules}.
 * <ul>
 *   <li>P86 {@code WorldServer.areAllPlayersAsleep} starts with
 *       {@code if (SleepRules.decides(this)) return SleepRules.enoughDeepSleeping(this);} (one branch, an
 *       {@code F_SAME} frame at the head, where the only local is {@code this});</li>
 *   <li>P87 {@code WorldServer.updateAllPlayersSleepingFlag}: first line {@code SleepRules.sleepersChanged(this)}.</li>
 * </ul>
 */
public final class SleepTransformer implements IClassTransformer {

    private static final String TARGET = "net.minecraft.world.WorldServer";
    private static final String HOOKS = "com/exiledradio/backportcore/mechanics/SleepRules";
    private static final String WORLD = "(Lnet/minecraft/world/WorldServer;)";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET.equals(transformedName) || basicClass == null) {
            return basicClass;
        }
        ClassNode node = Asm.read(basicClass);
        boolean changed = false;
        MethodNode asleep = Asm.method(node, "areAllPlayersAsleep", "func_73056_e", "()Z");
        if (asleep == null) {
            Asm.skipped("P86", "sleep percentage (WorldServer.areAllPlayersAsleep)", "method not found");
        } else {
            LabelNode vanilla = new LabelNode();
            InsnList head = new InsnList();
            head.add(new VarInsnNode(Opcodes.ALOAD, 0));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "decides", WORLD + "Z", false));
            head.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
            head.add(new VarInsnNode(Opcodes.ALOAD, 0));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "enoughDeepSleeping", WORLD + "Z", false));
            head.add(new InsnNode(Opcodes.IRETURN));
            head.add(vanilla);
            head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            asleep.instructions.insert(head);
            Asm.applied("P86", "sleep percentage (WorldServer.areAllPlayersAsleep)");
            changed = true;
        }
        MethodNode flag = Asm.method(node, "updateAllPlayersSleepingFlag", "func_72854_c", "()V");
        if (flag == null) {
            Asm.skipped("P87", "sleeping players announced (WorldServer.updateAllPlayersSleepingFlag)", "method not found");
        } else {
            InsnList head = new InsnList();
            head.add(new VarInsnNode(Opcodes.ALOAD, 0));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "sleepersChanged", WORLD + "V", false));
            flag.instructions.insert(head);
            Asm.applied("P87", "sleeping players announced (WorldServer.updateAllPlayersSleepingFlag)");
            changed = true;
        }
        return changed ? Asm.write(node) : basicClass;
    }
}
