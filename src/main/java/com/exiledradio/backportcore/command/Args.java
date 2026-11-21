package com.exiledradio.backportcore.command;

import net.minecraft.command.CommandException;

/**
 * Brigadier's argument types that today's commands use, parsed from one 1.12.2 command word, with 26.3's error
 * messages (vanilla keys, translated by Mojang's files).
 */
public final class Args {

    private Args() {
    }

    /** Brigadier's {@code StringReader.readFloat} prefix: digits, '.', '-'. */
    private static int numberEnd(String word) {
        int i = 0;
        while (i < word.length()) {
            char c = word.charAt(i);
            if (c >= '0' && c <= '9' || c == '.' || c == '-') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    /** {@code FloatArgumentType.floatArg(min, max)}. */
    public static float floatArg(String word, float min, float max) throws CommandException {
        if (word.isEmpty() || numberEnd(word) != word.length()) {
            throw new CommandException(word.isEmpty() ? "parsing.float.expected" : "parsing.float.invalid", word);
        }
        float value;
        try {
            value = Float.parseFloat(word);
        } catch (NumberFormatException e) {
            throw new CommandException("parsing.float.invalid", word);
        }
        if (value < min) {
            throw new CommandException("argument.float.low", min, value);
        }
        if (value > max) {
            throw new CommandException("argument.float.big", max, value);
        }
        return value;
    }

    /** {@code IntegerArgumentType.integer()}. */
    public static int intArg(String word) throws CommandException {
        try {
            return Integer.parseInt(word);
        } catch (NumberFormatException e) {
            throw new CommandException(word.isEmpty() ? "parsing.int.expected" : "parsing.int.invalid", word);
        }
    }

    /** {@code BoolArgumentType.bool()}. */
    public static boolean boolArg(String word) throws CommandException {
        if ("true".equals(word)) {
            return true;
        }
        if ("false".equals(word)) {
            return false;
        }
        throw new CommandException(word.isEmpty() ? "parsing.bool.expected" : "parsing.bool.invalid", word);
    }

    /** {@code TimeArgument.time(minimum)}: a number of ticks, or of seconds ({@code s}) or days ({@code d}). */
    public static int time(String word, int minimum) throws CommandException {
        int end = numberEnd(word);
        String number = word.substring(0, end);
        if (number.isEmpty()) {
            throw new CommandException("parsing.float.expected");
        }
        float value;
        try {
            value = Float.parseFloat(number);
        } catch (NumberFormatException e) {
            throw new CommandException("parsing.float.invalid", number);
        }
        String unit = word.substring(end);
        int factor = "d".equals(unit) ? 24000 : "s".equals(unit) ? 20 : "t".equals(unit) || unit.isEmpty() ? 1 : 0;
        if (factor == 0) {
            throw new CommandException("argument.time.invalid_unit");
        }
        int ticks = Math.round(value * (float) factor);
        if (ticks < minimum) {
            throw new CommandException("argument.time.tick_count_too_low", minimum, ticks);
        }
        return ticks;
    }

    /** {@code RangeArgument.intRange()}: {@code n}, {@code a..b}, {@code a..} or {@code ..b}; null bounds are open. */
    public static Integer[] intRange(String word) throws CommandException {
        if (word.isEmpty()) {
            throw new CommandException("argument.range.empty");
        }
        int dots = word.indexOf("..");
        Integer min;
        Integer max;
        if (dots < 0) {
            min = rangeInt(word);
            max = min;
        } else {
            String a = word.substring(0, dots);
            String b = word.substring(dots + 2);
            min = a.isEmpty() ? null : rangeInt(a);
            max = b.isEmpty() ? null : rangeInt(b);
            if (min == null && max == null) {
                throw new CommandException("argument.range.empty");
            }
        }
        if (min != null && max != null && min > max) {
            throw new CommandException("argument.range.swapped");
        }
        return new Integer[] {min, max};
    }

    private static Integer rangeInt(String text) throws CommandException {
        if (text.indexOf('.') >= 0) {
            throw new CommandException("argument.range.ints");
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            throw new CommandException("parsing.int.invalid", text);
        }
    }
}
