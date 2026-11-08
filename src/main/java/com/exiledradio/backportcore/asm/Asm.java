package com.exiledradio.backportcore.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the transformers share: reading and writing classes, and matching a method by its development
 * (MCP) or production (SRG) name. In production FML has already renamed every Minecraft member to its SRG
 * name when these transformers run (they sort after its deobfuscation transformer), and class names are
 * the readable ones in both.
 */
public final class Asm {

    static final Logger LOG = LogManager.getLogger("Backport Core");

    static final String HOOKS = "com/exiledradio/backportcore/pose/MovementHooks";
    static final String CLIENT_HOOKS = "com/exiledradio/backportcore/pose/client/ClientHooks";
    static final String PRESENTATION_HOOKS = "com/exiledradio/backportcore/presentation/client/PresentationHooks";
    static final String WORLDGEN_HOOKS = "com/exiledradio/backportcore/worldgen/WorldgenHooks";

    /** Each patch's state by its id ("P1".."P9"): "applied", or why it was skipped. Read by the self-tests. */
    private static final Map<String, String> STATE = new ConcurrentHashMap<String, String>();

    private Asm() {
    }

    /** @return "applied", the reason the patch was skipped, or null if its class has not loaded */
    public static String state(String patch) {
        return STATE.get(patch);
    }

    static void applied(String patch, String what) {
        STATE.put(patch, "applied");
        LOG.info("{} {}: patched.", patch, what);
    }

    static void skipped(String patch, String what, String why) {
        STATE.put(patch, why);
        LOG.warn("{} {}: {}; patch skipped, vanilla behavior kept.", patch, what, why);
    }

    static boolean named(String actual, String mcp, String srg) {
        return mcp.equals(actual) || srg.equals(actual);
    }

    static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    /** Writes a class whose patches only add straight-line code: the existing frames stay valid. */
    static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    /** @return the one non-bridge method with this name and descriptor, or null */
    static MethodNode method(ClassNode node, String mcp, String srg, String desc) {
        MethodNode found = null;
        for (MethodNode m : node.methods) {
            if (named(m.name, mcp, srg) && desc.equals(m.desc) && (m.access & Opcodes.ACC_BRIDGE) == 0) {
                if (found != null) {
                    return null;
                }
                found = m;
            }
        }
        return found;
    }

    /** @return every call in the method with this opcode, name and descriptor (any owner when owner is null) */
    static List<MethodInsnNode> calls(MethodNode method, int opcode, String owner, String mcp, String srg, String desc) {
        List<MethodInsnNode> out = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode && insn.getOpcode() == opcode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if ((owner == null || owner.equals(call.owner)) && named(call.name, mcp, srg) && desc.equals(call.desc)) {
                    out.add(call);
                }
            }
        }
        return out;
    }
}
