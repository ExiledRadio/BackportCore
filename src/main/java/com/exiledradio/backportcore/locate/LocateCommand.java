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
 * {@code /locate}, today's command in place of 1.12.2's ({@code CommandLocate}, replaced under the same name). Only
 * today's two forms: 1.12.2's names ({@code /locate Stronghold}) and 1.13-1.18's bare ids are gone, as in today's game.
 *
 * <ul>
 *   <li>{@code /locate structure <id>} and {@code /locate structure #<tag>}: today's form, over every structure the
 *       installed backports register with {@link StructureLocate}. Today's reply: "The nearest minecraft:shipwreck
 *       is at [x, ~, z] (n blocks away)", the coordinates green, a click puts {@code /tp @s x ~ z} in the chat box.
 *       A tag names what it found: "#minecraft:village (minecraft:village_plains)".</li>
 *   <li>{@code /locate biome <id>} and {@code /locate biome #<tag>}: today's biome form over {@link BiomeLocate}'s ids
 *       ("The nearest minecraft:cherry_grove is at [x, y, z] (n blocks away)", with the height and the distance in
 *       three dimensions, as today's).</li>
 *   <li>{@code /locate poi <id>} and {@code /locate poi #<tag>}: today's point of interest form over {@link PoiLocate}
 *       ("The nearest minecraft:home is at [x, ~, z] (n blocks away)"), 256 blocks around the source.</li>
 * </ul>
 * Search and position are today's ({@link StructureLocate}, {@link BiomeLocate}). The reply is sent only to the one who asked,
 * following the {@code sendCommandFeedback} rule, as today's {@code sendSuccess(..., false)}.
 */
public final class LocateCommand extends CommandBase {

    static final String STRUCTURE = "structure";
    static final String BIOME = "biome";
    static final String POI = "poi";
    /** Today's registry name for point of interest types. */
    static final String POI_REGISTRY = "minecraft:point_of_interest_type";
    /** Today's registry name for biomes, as its "not found" messages name it. */
    static final String BIOME_REGISTRY = "minecraft:worldgen/biome";

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
        if (args.length == 2 && BIOME.equalsIgnoreCase(args[0])) {
            locateBiome(server, sender, level, from, args[1]);
            return;
        }
        if (args.length == 2 && POI.equalsIgnoreCase(args[0])) {
            locatePoi(server, sender, level, args[1]);
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    /** Today's {@code locateBiome}: ResourceOrTagArgument's messages for an unknown id or tag. */
    private static void locateBiome(MinecraftServer server, ICommandSender sender, WorldServer world, BlockPos from, String key)
            throws CommandException {
        boolean tag = key.startsWith("#");
        if (!StructureLocate.isIdLike(key) || !BiomeLocate.known(key)) {
            String bare = tag ? key.substring(1) : key;
            throw new CommandException(tag ? "argument.resource_tag.not_found" : "argument.resource.not_found",
                    StructureLocate.isIdLike(key) ? StructureLocate.normalize(bare) : bare, BIOME_REGISTRY);
        }
        String printable = StructureLocate.normalize(key);
        long start = System.nanoTime();
        BiomeLocate.Found found = BiomeLocate.nearest(world, from, printable);
        if (found == null) {
            throw new CommandException("commands.locate.biome.not_found", printable);
        }
        String name = tag ? printable + " (" + found.id + ")" : printable;
        log(name, start);
        reply(server, sender, from, found.pos, name, true, "commands.locate.biome.success");
    }

    /**
     * Today's {@code locatePoi}: from the block the source is in ({@code BlockPos.containing}), 256 blocks around it
     * ({@link PoiLocate}); the distance shown is horizontal and the height "~", as the structure form's.
     */
    private static void locatePoi(MinecraftServer server, ICommandSender sender, WorldServer world, String key)
            throws CommandException {
        boolean tag = key.startsWith("#");
        if (!StructureLocate.isIdLike(key) || !PoiLocate.known(key)) {
            String bare = tag ? key.substring(1) : key;
            throw new CommandException(tag ? "argument.resource_tag.not_found" : "argument.resource.not_found",
                    StructureLocate.isIdLike(key) ? StructureLocate.normalize(bare) : bare, POI_REGISTRY);
        }
        String printable = StructureLocate.normalize(key);
        BlockPos from = new BlockPos(sender.getPositionVector());
        long start = System.nanoTime();
        PoiLocate.Found found = PoiLocate.nearest(world, from, printable);
        if (found == null) {
            throw new CommandException("commands.locate.poi.not_found", printable);
        }
        String name = tag ? printable + " (" + found.id + ")" : printable;
        log(name, start);
        reply(server, sender, from, found.pos, name, false, "commands.locate.poi.success");
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

    private static void reply(MinecraftServer server, ICommandSender sender, BlockPos from, BlockPos found, String name) {
        reply(server, sender, from, found, name, false, "commands.locate.structure.success");
    }

    /**
     * Today's {@code showLocateResult}: for a structure the horizontal distance and "~" for the height, for a biome
     * ({@code includeY}) the distance in three dimensions and the height found.
     */
    private static void reply(MinecraftServer server, ICommandSender sender, BlockPos from, BlockPos found, String name,
                              boolean includeY, String successKey) {
        double dx = found.getX() - from.getX();
        double dy = found.getY() - from.getY();
        double dz = found.getZ() - from.getZ();
        int distance = includeY ? MathHelper.floor(MathHelper.sqrt((float) (dx * dx + dy * dy + dz * dz)))
                : MathHelper.floor(MathHelper.sqrt((float) (dx * dx + dz * dz)));
        String y = includeY ? String.valueOf(found.getY()) : "~";
        ITextComponent coordinates = new TextComponentString("[")
                .appendSibling(new TextComponentTranslation("chat.coordinates", found.getX(), y, found.getZ()))
                .appendText("]");
        coordinates.getStyle()
                .setColor(TextFormatting.GREEN)
                .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + found.getX() + " " + y + " " + found.getZ()))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentTranslation("chat.coordinates.tooltip")));
        sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, distance);
        if (acceptsSuccess(server, sender)) {
            sender.sendMessage(new TextComponentTranslation(successKey, name, coordinates, distance));
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

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> options = new ArrayList<String>();
            options.add(STRUCTURE);
            options.add(BIOME);
            options.add(POI);
            return getListOfStringsMatchingLastWord(args, options);
        }
        if (args.length == 2 && STRUCTURE.equalsIgnoreCase(args[0])) {
            return completeIds(args[1]);
        }
        if (args.length == 2 && BIOME.equalsIgnoreCase(args[0])) {
            return completeIds(args[1], BiomeLocate.ids(), BiomeLocate.tags());
        }
        if (args.length == 2 && POI.equalsIgnoreCase(args[0])) {
            return completeIds(args[1], PoiLocate.ids(), PoiLocate.tags());
        }
        return Collections.emptyList();
    }

    /**
     * Today's resource-location completion ({@code SharedSuggestionProvider.filterResources}), fitted to 1.12.2's Tab.
     * Today's game shows a list to pick from; 1.12.2's client instead writes the common prefix of everything the
     * server sends back (sorted), then cycles through it. So the matches are tiered, each tier only when the one
     * before has none:
     * <ol>
     *   <li>ids whose path starts with what is typed ({@code mon} -> {@code minecraft:monument}), or with a ':'
     *       typed, whose full name does;</li>
     *   <li>ids it matches after a '_' ({@code plains} -> {@code minecraft:village_plains}), when their common prefix
     *       still holds what was typed ({@code portal} -> {@code minecraft:ruined_portal}; {@code pyramid} would
     *       become a bare {@code minecraft:}, so it gets nothing);</li>
     *   <li>ids whose namespace starts with it.</li>
     * </ol>
     * Today's game lists namespace matches beside path matches, so {@code m} there lists every id; here that would
     * turn {@code m} into {@code minecraft:}. Tags only once a '#' is typed: the client sorts what it gets, so on an
     * empty word a tag would always be the first thing Tab writes.
     */
    static List<String> completeIds(String typed) {
        return completeIds(typed, StructureLocate.ids(), StructureLocate.tags());
    }

    /** {@link #completeIds(String)} over any sorted ids and tags (the biome form's). */
    static List<String> completeIds(String typed, List<String> ids, List<String> tags) {
        String lower = typed.toLowerCase(Locale.ROOT);
        boolean tag = lower.startsWith("#");
        String body = tag ? lower.substring(1) : lower;
        boolean full = body.indexOf(':') >= 0;
        List<String> options = tag ? tags : ids;
        if (body.isEmpty()) {
            return options;
        }
        List<String> start = new ArrayList<String>();
        List<String> word = new ArrayList<String>();
        List<String> namespace = new ArrayList<String>();
        for (String option : options) {
            String id = tag ? option.substring(1) : option;
            int colon = id.indexOf(':');
            String target = full ? id : "minecraft".equals(id.substring(0, colon)) ? id.substring(colon + 1) : null;
            if (target != null && target.startsWith(body)) {
                start.add(option);
            } else if (target != null && matchesSubStr(body, target)) {
                word.add(option);
            } else if (!full && id.substring(0, colon).startsWith(body)) {
                namespace.add(option);
            }
        }
        if (!start.isEmpty()) {
            return start;
        }
        if (!word.isEmpty()) {
            String common = org.apache.commons.lang3.StringUtils.getCommonPrefix(word.toArray(new String[0]));
            String commonId = tag ? common.substring(Math.min(1, common.length())) : common;
            String commonTarget = full ? commonId : commonId.substring(commonId.indexOf(':') + 1);
            return word.size() == 1 || matchesSubStr(body, commonTarget) ? word : new ArrayList<String>();
        }
        return namespace;
    }

    /** Today's {@code SharedSuggestionProvider.matchesSubStr}: at the start, or right after a '_'. */
    static boolean matchesSubStr(String input, String s) {
        for (int i = 0; !s.startsWith(input, i); i++) {
            i = s.indexOf('_', i);
            if (i < 0) {
                return false;
            }
        }
        return true;
    }
}
