package com.exiledradio.backportcore.locate;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.CommandBlockBaseLogic;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code /locate}, today's command in place of 1.12.2's ({@code CommandLocate}, replaced under the same name).
 *
 * <ul>
 *   <li>{@code /locate structure <id>} and {@code /locate structure #<tag>}: today's form, over every structure the
 *       installed backports register with {@link StructureLocate}. Today's reply: "The nearest minecraft:shipwreck
 *       is at [x, ~, z] (n blocks away)", the coordinates green, a click puts {@code /tp @s x ~ z} in the chat box.
 *       A tag names what it found: "#minecraft:village (minecraft:village_plains)".</li>
 *   <li>{@code /locate <Name>}: 1.12.2's names (Stronghold, Monument, Mansion, EndCity, Fortress, Temple,
 *       Mineshaft) keep working, as does any other name 1.12.2's search knows (other mods' structures). Each
 *       one 1.12.2 shares with today's game is searched as today's id. {@code Village} still works as it did,
 *       for the habit, but is only listed while no backport registers today's village types: then
 *       {@code #minecraft:village} is the way to ask.</li>
 *   <li>1.13-1.18's bare ids ({@code /locate shipwreck}) also work, unlisted.</li>
 * </ul>
 * Search and position are today's ({@link StructureLocate}). The reply is sent only to the one who asked,
 * following the {@code sendCommandFeedback} rule, as today's {@code sendSuccess(..., false)}.
 */
public final class LocateCommand extends CommandBase {

    static final String STRUCTURE = "structure";

    /** 1.12.2's names, and today's id or tag each one is searched as (null: 1.12.2's own search). */
    private static final String[][] LEGACY = {
            {"Stronghold", "minecraft:stronghold"},
            {"Monument", "minecraft:monument"},
            {"Village", "#minecraft:village"},
            {"Mansion", "minecraft:mansion"},
            {"EndCity", "minecraft:end_city"},
            {"Fortress", "minecraft:fortress"},
            {"Temple", null},
            {"Mineshaft", "minecraft:mineshaft"}};

    @Override
    public String getName() {
        return "locate";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.backportcore.locate.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        World world = sender.getEntityWorld();
        if (!(world instanceof WorldServer)) {
            throw new CommandException("commands.locate.structure.not_found", args.length > 1 ? args[1] : "");
        }
        WorldServer level = (WorldServer) world;
        BlockPos from = sender.getPosition();

        if (args.length == 2 && STRUCTURE.equalsIgnoreCase(args[0])) {
            String key = args[1];
            if (!StructureLocate.isIdLike(key) || !StructureLocate.known(key)) {
                throw new CommandException("commands.locate.structure.invalid", key);
            }
            String printable = StructureLocate.normalize(key);
            StructureLocate.Found found = search(level, from, printable, printable);
            String name = printable.startsWith("#") && !found.id.equals(printable) ? printable + " (" + found.id + ")" : printable;
            reply(server, sender, from, found.pos, name);
            return;
        }
        if (args.length != 1) {
            throw new WrongUsageException(getUsage(sender));
        }

        String typed = args[0];
        String[] legacy = legacy(typed);
        if (legacy != null && legacy[1] != null && StructureLocate.known(legacy[1])) {
            StructureLocate.Found found = search(level, from, legacy[1], legacy[0]);
            reply(server, sender, from, found.pos, legacy[0]);
            return;
        }
        if (legacy == null && StructureLocate.isIdLike(typed) && StructureLocate.known(typed)) {
            // 1.13 to 1.18 took the bare id.
            String printable = StructureLocate.normalize(typed);
            StructureLocate.Found found = search(level, from, printable, printable);
            reply(server, sender, from, found.pos, printable);
            return;
        }
        // 1.12.2's own search, by the name as 1.12.2 spells it (its names are case-sensitive).
        String name = legacy != null ? legacy[0] : typed;
        long start = System.nanoTime();
        BlockPos pos = level.findNearestStructure(name, from, false);
        if (pos == null) {
            throw new CommandException("commands.locate.structure.not_found", name);
        }
        log(name, start);
        reply(server, sender, from, pos, name);
    }

    private static StructureLocate.Found search(WorldServer world, BlockPos from, String key, String printable)
            throws CommandException {
        long start = System.nanoTime();
        StructureLocate.Found found;
        try {
            found = StructureLocate.nearest(world, from, key);
        } catch (StructureLocate.Unavailable why) {
            ITextComponent reason = why.reason();
            reason.getStyle().setColor(TextFormatting.RED);
            throw new CommandException("%s", reason);
        }
        if (found == null) {
            throw new CommandException("commands.locate.structure.not_found", printable);
        }
        log(printable, start);
        return found;
    }

    private static void log(String name, long start) {
        BackportCore.LOGGER.info("Locating element {} took {} ms", name, (System.nanoTime() - start) / 1000000L);
    }

    /** Today's {@code showLocateResult} for a structure: horizontal distance, "~" for the height. */
    private static void reply(MinecraftServer server, ICommandSender sender, BlockPos from, BlockPos found, String name) {
        int dx = found.getX() - from.getX();
        int dz = found.getZ() - from.getZ();
        int distance = MathHelper.floor(MathHelper.sqrt((float) (dx * dx + dz * dz)));
        ITextComponent coordinates = new TextComponentString("[")
                .appendSibling(new TextComponentTranslation("chat.coordinates", found.getX(), "~", found.getZ()))
                .appendText("]");
        coordinates.getStyle()
                .setColor(TextFormatting.GREEN)
                .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + found.getX() + " ~ " + found.getZ()))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentTranslation("chat.coordinates.tooltip")));
        sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, distance);
        if (acceptsSuccess(server, sender)) {
            sender.sendMessage(new TextComponentTranslation("commands.locate.structure.success", name, coordinates, distance));
        }
    }

    /** Today's {@code CommandSource.acceptsSuccess}: the console always, a command block if it tracks output, else the game rule. */
    private static boolean acceptsSuccess(MinecraftServer server, ICommandSender sender) {
        if (sender instanceof MinecraftServer) {
            return true;
        }
        if (sender instanceof CommandBlockBaseLogic) {
            return ((CommandBlockBaseLogic) sender).shouldTrackOutput();
        }
        World world = server.getWorld(0);
        return world == null || world.getGameRules().getBoolean("sendCommandFeedback");
    }

    /** @return the {name, target} row for a 1.12.2 name, any case, or null */
    @Nullable
    static String[] legacy(String typed) {
        for (String[] row : LEGACY) {
            if (row[0].equalsIgnoreCase(typed)) {
                return row;
            }
        }
        return null;
    }

    /** @return the 1.12.2 names tab completion lists: all but Village once today's village types are registered */
    static List<String> listedLegacyNames() {
        List<String> names = new ArrayList<String>();
        for (String[] row : LEGACY) {
            if ("Village".equals(row[0]) && !StructureLocate.members(row[1]).isEmpty()) {
                continue;
            }
            names.add(row[0]);
        }
        return names;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> options = new ArrayList<String>();
            options.add(STRUCTURE);
            options.addAll(listedLegacyNames());
            return getListOfStringsMatchingLastWord(args, options);
        }
        if (args.length == 2 && STRUCTURE.equalsIgnoreCase(args[0])) {
            return completeIds(args[1]);
        }
        return Collections.emptyList();
    }

    /**
     * Today's resource-location completion: every id and tag whose full name, or whose path where no namespace
     * is typed, starts with what is typed.
     */
    static List<String> completeIds(String typed) {
        List<String> options = new ArrayList<String>(StructureLocate.ids());
        options.addAll(StructureLocate.tags());
        String lower = typed.toLowerCase(Locale.ROOT);
        boolean tag = lower.startsWith("#");
        String body = tag ? lower.substring(1) : lower;
        List<String> out = new ArrayList<String>();
        for (String option : options) {
            boolean optionTag = option.startsWith("#");
            if (tag != optionTag && !lower.isEmpty()) {
                continue;
            }
            String full = optionTag ? option.substring(1) : option;
            String path = full.substring(full.indexOf(':') + 1);
            if (full.startsWith(body) || (body.indexOf(':') < 0 && path.startsWith(body))) {
                out.add(option);
            }
        }
        return out;
    }
}
