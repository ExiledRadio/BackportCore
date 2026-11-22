package com.exiledradio.backportcore.gamerules;

import com.exiledradio.backportcore.BackportCore;

import io.netty.buffer.ByteBuf;
import net.minecraft.command.CommandGameRule;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 26.3's in-world game rules: the client asks for the server's rules ({@code ServerboundClientCommandPacket}'s
 * {@code REQUEST_GAMERULE_VALUES}), the server answers with each rule's value ({@code ClientboundGameRuleValuesPacket}),
 * and the client sends back the ones changed ({@code ServerboundSetGameRulePacket}). The server answers and applies
 * only for a player who may use {@code /gamerule} (permission level 2), and applies each value as {@code /gamerule}
 * does ({@code setOrCreateGameRule}, then {@code notifyGameRuleChange}). Since Core 1.4.4.
 */
public final class GameRulesNetwork {

    public static final String CHANNEL = "backportcore_rules";
    static final int MAX_RULES = 1024;
    static final int MAX_TEXT = 256;

    private static SimpleNetworkWrapper channel;
    /** The client's answer handler (the open Game Rules screen), called on the network thread. */
    @Nullable
    static volatile Consumer<Values> clientAnswer;
    /** For the self-test: rules the server set from this channel. */
    public static volatile int applied;

    private GameRulesNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(AskHandler.class, Ask.class, 0, Side.SERVER);
        channel.registerMessage(ValuesHandler.class, Values.class, 1, Side.CLIENT);
        channel.registerMessage(SetHandler.class, Set.class, 2, Side.SERVER);
    }

    /** Client side: asks the server for its rules; {@code answer} gets them (on the network thread). */
    public static void ask(Consumer<Values> answer) {
        clientAnswer = answer;
        channel.sendToServer(new Ask());
    }

    /** Client side: no screen waits for an answer any more. */
    public static void forget() {
        clientAnswer = null;
    }

    /** Client side: sends the changed rules (1.12.2 names and values). */
    public static void send(Map<String, String> changed) {
        if (!changed.isEmpty()) {
            channel.sendToServer(new Set(changed));
        }
    }

    /** @return whether {@code player} may change game rules (as {@code /gamerule}: level 2) */
    public static boolean permitted(EntityPlayerMP player) {
        return player.canUseCommand(2, "gamerule");
    }

    /** Server side: the rules the screen edits, as the server has them. */
    public static Values values(MinecraftServer server) {
        Values out = new Values();
        out.allowed = true;
        GameRules rules = server.getWorld(0).getGameRules();
        for (String id : rules.getRules()) {
            Boolean numeric = GameRuleTable.numeric(rules, id);
            if (numeric != null && id.length() <= MAX_TEXT && out.ids.size() < MAX_RULES) {
                out.ids.add(id);
                out.values.add(rules.getString(id));
                out.numeric.add(numeric);
            }
        }
        return out;
    }

    /**
     * Server side: sets each rule {@code /gamerule} would, after the same checks (an existing rule, a boolean rule
     * only true or false, a number in 26.3's bounds for a rule the table knows).
     *
     * @return how many were set
     */
    public static int apply(MinecraftServer server, Map<String, String> changed) {
        GameRules rules = server.getWorld(0).getGameRules();
        int n = 0;
        for (Map.Entry<String, String> e : changed.entrySet()) {
            String id = e.getKey();
            String value = e.getValue();
            if (!rules.hasRule(id)) {
                continue;
            }
            Boolean numeric = GameRuleTable.numeric(rules, id);
            if (numeric == null) {
                continue;
            }
            GameRuleTable.Rule rule = GameRuleTable.unknown(id, numeric, value);
            if (rule.numeric != numeric || !rule.valid(rule.shown(value))) {
                continue;
            }
            if (rules.getString(id).equals(value)) {
                continue;
            }
            rules.setOrCreateGameRule(id, value);
            CommandGameRule.notifyGameRuleChange(rules, id, server);
            n++;
        }
        applied += n;
        return n;
    }

    // ---- messages -------------------------------------------------------------------------------------------------

    /** Client to server: send me your rules. */
    public static final class Ask implements IMessage {
        @Override
        public void fromBytes(ByteBuf buf) {
        }

        @Override
        public void toBytes(ByteBuf buf) {
        }
    }

    /** Server to client: whether the player may edit, and each rule's value. */
    public static final class Values implements IMessage {
        public boolean allowed;
        public final List<String> ids = new ArrayList<String>();
        public final List<String> values = new ArrayList<String>();
        public final List<Boolean> numeric = new ArrayList<Boolean>();

        @Override
        public void fromBytes(ByteBuf buf) {
            this.allowed = buf.readBoolean();
            int n = buf.readInt();
            if (n < 0 || n > MAX_RULES) {
                throw new IllegalArgumentException("too many game rules: " + n);
            }
            for (int i = 0; i < n; i++) {
                this.ids.add(read(buf));
                this.values.add(read(buf));
                this.numeric.add(buf.readBoolean());
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(this.allowed);
            buf.writeInt(this.ids.size());
            for (int i = 0; i < this.ids.size(); i++) {
                ByteBufUtils.writeUTF8String(buf, this.ids.get(i));
                ByteBufUtils.writeUTF8String(buf, this.values.get(i));
                buf.writeBoolean(this.numeric.get(i));
            }
        }
    }

    /** Client to server: the changed rules. */
    public static final class Set implements IMessage {
        public final Map<String, String> changed = new LinkedHashMap<String, String>();

        public Set() {
        }

        Set(Map<String, String> changed) {
            this.changed.putAll(changed);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int n = buf.readInt();
            if (n < 0 || n > MAX_RULES) {
                throw new IllegalArgumentException("too many game rules: " + n);
            }
            for (int i = 0; i < n; i++) {
                String id = read(buf);
                this.changed.put(id, read(buf));
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.changed.size());
            for (Map.Entry<String, String> e : this.changed.entrySet()) {
                ByteBufUtils.writeUTF8String(buf, e.getKey());
                ByteBufUtils.writeUTF8String(buf, e.getValue());
            }
        }
    }

    static String read(ByteBuf buf) {
        String s = ByteBufUtils.readUTF8String(buf);
        if (s.length() > MAX_TEXT) {
            throw new IllegalArgumentException("game rule text too long");
        }
        return s;
    }

    public static final class AskHandler implements IMessageHandler<Ask, IMessage> {
        @Override
        public IMessage onMessage(Ask message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            final MinecraftServer server = player.getServer();
            if (server == null) {
                return null;
            }
            server.addScheduledTask(() -> {
                Values answer = permitted(player) ? values(server) : new Values();
                channel.sendTo(answer, player);
            });
            return null;
        }
    }

    public static final class ValuesHandler implements IMessageHandler<Values, IMessage> {
        @Override
        public IMessage onMessage(Values message, MessageContext ctx) {
            Consumer<Values> answer = clientAnswer;
            if (answer != null) {
                answer.accept(message);
            }
            return null;
        }
    }

    public static final class SetHandler implements IMessageHandler<Set, IMessage> {
        @Override
        public IMessage onMessage(final Set message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            final MinecraftServer server = player.getServer();
            if (server == null) {
                return null;
            }
            server.addScheduledTask(() -> {
                if (!permitted(player)) {
                    BackportCore.LOGGER.warn("Game rules: {} may not change game rules; ignored", player.getName());
                    return;
                }
                apply(server, message.changed);
            });
            return null;
        }
    }
}
