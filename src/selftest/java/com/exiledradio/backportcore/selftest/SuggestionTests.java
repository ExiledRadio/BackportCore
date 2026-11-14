package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.presentation.CommandSuggestionModel;
import com.exiledradio.backportcore.presentation.CommandSuggestionModel.Page;
import com.exiledradio.backportcore.presentation.CommandSuggestionModel.Suggestions;
import com.exiledradio.backportcore.presentation.CommandUsageNetwork;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketTabComplete;
import net.minecraft.network.play.server.SPacketTabComplete;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Command suggestions (presentation.CommandSuggestionModel, CommandUsageNetwork): what is asked, how 1.12.2's answers
 * become 1.13's list (ranges, the "/" taken off, sorting, client commands merged), accepting a suggestion in the
 * middle of the text, the preview, the usage line's text, paging, the usage answer on a real server and the packets'
 * round trips (Core's and vanilla's tab-complete packets).
 */
final class SuggestionTests {

    private SuggestionTests() {
    }

    static <T extends IMessage> T roundTrip(IMessage in, T out) {
        ByteBuf buf = Unpooled.buffer();
        in.toBytes(buf);
        out.fromBytes(buf);
        check("packet " + in.getClass().getSimpleName() + " read to its end", buf.readableBytes() == 0, buf.readableBytes());
        return out;
    }

    static void schedule() {
        then(1, "suggestion model", world -> {
            check("suggest: a command asks the text before the cursor", "/gi".equals(CommandSuggestionModel.requestFor("/give", 3)));
            check("suggest: chat is not asked", CommandSuggestionModel.requestFor("hello", 3) == null);
            check("suggest: nothing at the cursor's start", CommandSuggestionModel.requestFor("/give", 0) == null);

            Suggestions names = CommandSuggestionModel.fromCompletions("/g", "/g", null, new String[] {"/give", "/gamerule", "/Gamemode"});
            check("suggest: command names lose their slash and start after it", names.start == 1 && names.end == 2, names.start + "," + names.end);
            check("suggest: sorted as Brigadier sorts (ignoring case)", names.texts.equals(Arrays.asList("Gamemode", "gamerule", "give")), names.texts);
            check("suggest: accepting a command name", "/give".equals(names.apply(2, "/g")) && names.cursorAfter(2) == 5,
                    names.apply(2, "/g") + " " + names.cursorAfter(2));

            Suggestions args = CommandSuggestionModel.fromCompletions("/give Dev minecraft:st", "/give Dev minecraft:st", null,
                    new String[] {"minecraft:stone", "minecraft:stick"});
            check("suggest: an argument's range is its word", args.start == 10 && args.end == 22, args.start + "," + args.end);
            check("suggest: accepting replaces the word", "/give Dev minecraft:stick".equals(args.apply(0, args.input)), args.apply(0, args.input));

            String text = "/tp Al 1 2 3";
            Suggestions mid = CommandSuggestionModel.fromCompletions(text, "/tp Al", null, new String[] {"Alice", "Alex"});
            check("suggest: in the middle of the text", "/tp Alex 1 2 3".equals(mid.apply(0, text)) && mid.cursorAfter(0) == 8,
                    mid.apply(0, text) + " " + mid.cursorAfter(0));
            check("suggest: no preview when accepting changes what follows",
                    CommandSuggestionModel.previewSuffix(text, mid.apply(0, text)) == null);
            check("suggest: the preview is the rest", "ve".equals(CommandSuggestionModel.previewSuffix("/gi", "/give")));

            Suggestions merged = CommandSuggestionModel.fromCompletions("/", "/", new String[] {"§7/mycmd§r", "§7/give§r"},
                    new String[] {"/give", "/help", ""});
            check("suggest: client commands merged, colors off, no duplicates",
                    merged.texts.equals(Arrays.asList("give", "help", "mycmd")), merged.texts);
            check("suggest: no answer is an empty list", CommandSuggestionModel.fromCompletions("/x", "/x", null, (String[]) null).isEmpty());

            Suggestions players = CommandSuggestionModel.names("hi al", Arrays.asList("Alex", "bob", "ALan"));
            check("suggest: chat suggests players by the last word", players.start == 3 && players.texts.equals(Arrays.asList("ALan", "Alex")),
                    players.start + " " + players.texts);

            check("suggest: a command's name once typed out", "give".equals(CommandSuggestionModel.commandName("/give ", 6)));
            check("suggest: no name while it is typed", CommandSuggestionModel.commandName("/give", 5) == null);
            check("suggest: no name with the cursor in it", CommandSuggestionModel.commandName("/give Dev", 3) == null);
            check("suggest: usage after the name", "<player> <item>".equals(CommandSuggestionModel.usageArguments("give", "/give <player> <item>")));
            check("suggest: a usage of another shape stays whole", "/teleport x".equals(CommandSuggestionModel.usageArguments("tp", "/teleport x")));
            check("suggest: a bare usage shows nothing", CommandSuggestionModel.usageArguments("give", "/give") == null);

            List<String> many = new ArrayList<String>();
            for (int i = 0; i < 25; i++) {
                many.add(String.format("c%02d", i));
            }
            Page page = new Page(new Suggestions("/", 1, 1, many), "/", 10);
            check("page: 10 rows of 25, the first selected", page.visible() == 10 && page.selected() == 0 && page.offset() == 0
                    && !page.moreAbove() && page.moreBelow());
            page.cycle(-1);
            check("page: up from the first wraps to the last", page.selected() == 24 && page.offset() == 15 && page.moreAbove() && !page.moreBelow(),
                    page.selected() + "/" + page.offset());
            page.cycle(1);
            check("page: down from the last wraps to the first", page.selected() == 0 && page.offset() == 0, page.selected() + "/" + page.offset());
            for (int i = 0; i < 10; i++) {
                page.cycle(1);
            }
            check("page: the list scrolls with the selection", page.selected() == 10 && page.offset() == 1, page.selected() + "/" + page.offset());
            page.scroll(1.0);
            check("page: the wheel up scrolls back", page.offset() == 0, page.offset());
            page.scroll(-100.0);
            check("page: the wheel stops at the end", page.offset() == 15, page.offset());
            check("page: the selection applies to the text it opened over", "/c10".equals(page.applied()), page.applied());
            Page few = new Page(new Suggestions("/", 1, 1, Arrays.asList("a", "b", "c")), "/", 10);
            few.scroll(-5.0);
            check("page: a short list never scrolls", few.visible() == 3 && few.offset() == 0 && !few.moreBelow());
        });

        then(1, "suggestion packets", world -> {
            CommandUsageNetwork.Ask ask = roundTrip(new CommandUsageNetwork.Ask("give"), new CommandUsageNetwork.Ask());
            check("packet: usage question", "give".equals(ask.name));
            CommandUsageNetwork.Answer answer = roundTrip(new CommandUsageNetwork.Answer("give", "commands.give.usage"),
                    new CommandUsageNetwork.Answer());
            check("packet: usage answer", "give".equals(answer.name) && "commands.give.usage".equals(answer.usage));
            roundTrip(new CommandUsageNetwork.Hello(), new CommandUsageNetwork.Hello());

            // Vanilla's own packets, as the client builds them (its constructor and getMatches are client-only, so the
            // fields are set and read directly here).
            PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
            BlockPos pos = new BlockPos(1, 64, -3);
            CPacketTabComplete question = new CPacketTabComplete();
            ObfuscationReflectionHelper.setPrivateValue(CPacketTabComplete.class, question, "/give Dev minecraft:st", "field_149420_a");
            ObfuscationReflectionHelper.setPrivateValue(CPacketTabComplete.class, question, pos, "field_179710_b");
            ObfuscationReflectionHelper.setPrivateValue(CPacketTabComplete.class, question, false, "field_186990_b");
            question.writePacketData(buf);
            CPacketTabComplete read = new CPacketTabComplete();
            read.readPacketData(buf);
            check("packet: tab-complete question keeps the text and the looked-at block",
                    "/give Dev minecraft:st".equals(read.getMessage()) && pos.equals(read.getTargetBlock()) && !read.hasTargetBlock());
            PacketBuffer buf2 = new PacketBuffer(Unpooled.buffer());
            new SPacketTabComplete(new String[] {"/give", "/gamemode"}).writePacketData(buf2);
            SPacketTabComplete answer2 = new SPacketTabComplete();
            answer2.readPacketData(buf2);
            String[] matches = ObfuscationReflectionHelper.getPrivateValue(SPacketTabComplete.class, answer2, "field_149632_a");
            check("packet: tab-complete answer", Arrays.equals(matches, new String[] {"/give", "/gamemode"}));
        });

        then(1, "suggestion usage on the server", world -> {
            MinecraftServer server = world.getMinecraftServer();
            EntityPlayerMP player = PoseTests.player(world, "usage");
            check("usage: a command anyone may use", "commands.help.usage".equals(CommandUsageNetwork.usageFor(server, player, "help")),
                    CommandUsageNetwork.usageFor(server, player, "help"));
            check("usage: none for a command the player may not use", CommandUsageNetwork.usageFor(server, player, "give").isEmpty());
            check("usage: none for no such command", CommandUsageNetwork.usageFor(server, player, "nosuchcommand").isEmpty());
            check("usage: an alias finds its command", "commands.help.usage".equals(CommandUsageNetwork.usageFor(server, player, "?")),
                    CommandUsageNetwork.usageFor(server, player, "?"));
            server.getPlayerList().addOp(player.getGameProfile());
            try {
                check("usage: an operator gets /give's", "commands.give.usage".equals(CommandUsageNetwork.usageFor(server, player, "give")),
                        CommandUsageNetwork.usageFor(server, player, "give"));
            } finally {
                server.getPlayerList().removeOp(player.getGameProfile());
            }
        });
    }
}
