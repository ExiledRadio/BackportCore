package com.exiledradio.backportcore.presentation;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The chat box's command suggestions without the drawing: which text to ask the server about, how 1.12.2's
 * tab-complete answer becomes 1.13's suggestion list (Brigadier's ranges, sorted as Brigadier sorts), what accepting
 * one does to the text, and the list's paging. Plain Java, so the self-tests run it on a server.
 *
 * <p>1.12.2's server answers {@code CPacketTabComplete} with whole words for the word before the cursor: the command
 * names with their {@code /} while that word is the first, otherwise the last argument's candidates. 1.13's list
 * shows the command names without the {@code /} (its range starts after it), so the {@code /} is taken off here.
 */
public final class CommandSuggestionModel {

    private static final Pattern WHITESPACE = Pattern.compile("(\\s+)");

    private CommandSuggestionModel() {
    }

    /** Suggestions for one text: each replaces {@code [start, end)} of the text as it was when they were made. */
    public static final class Suggestions {
        public final String input;
        public final int start;
        public final int end;
        public final List<String> texts;

        public Suggestions(String input, int start, int end, List<String> texts) {
            this.input = input;
            this.start = start;
            this.end = end;
            this.texts = Collections.unmodifiableList(new ArrayList<String>(texts));
        }

        public boolean isEmpty() {
            return this.texts.isEmpty();
        }

        public int size() {
            return this.texts.size();
        }

        /** @return {@code text} with suggestion {@code i} in place of the range (Brigadier's {@code Suggestion.apply}) */
        public String apply(int i, String text) {
            if (this.start > text.length() || this.end > text.length() || this.start > this.end) {
                return text;
            }
            return text.substring(0, this.start) + this.texts.get(i) + text.substring(this.end);
        }

        /** @return the cursor after accepting suggestion {@code i}: the end of the suggestion */
        public int cursorAfter(int i) {
            return this.start + this.texts.get(i).length();
        }
    }

    /**
     * @return the text the server is asked to complete (everything before the cursor), or null when there is nothing
     * to ask: not a {@code /} command, or the cursor at the very start
     */
    @Nullable
    public static String requestFor(String text, int cursor) {
        if (!text.startsWith("/") || cursor < 1 || cursor > text.length()) {
            return null;
        }
        return text.substring(0, cursor);
    }

    /**
     * Turns tab-complete answers into suggestions.
     *
     * @param text the whole text the request was made for
     * @param prefix what was sent: the text before the cursor
     * @param answers the server's matches, and the client's own commands' (Forge's {@code ClientCommandHandler},
     *                which colors them grey); either may be null
     */
    public static Suggestions fromCompletions(String text, String prefix, @Nullable String[]... answers) {
        boolean firstWord = prefix.indexOf(' ') < 0;
        int start = firstWord ? Math.min(1, prefix.length()) : prefix.lastIndexOf(' ') + 1;
        Set<String> texts = new LinkedHashSet<String>();
        if (answers != null) {
            for (String[] list : answers) {
                if (list == null) {
                    continue;
                }
                for (String raw : list) {
                    if (raw == null) {
                        continue;
                    }
                    String s = TextFormatting.getTextWithoutFormattingCodes(raw);
                    if (s == null) {
                        continue;
                    }
                    if (firstWord && s.startsWith("/")) {
                        s = s.substring(1);
                    }
                    if (!s.isEmpty()) {
                        texts.add(s);
                    }
                }
            }
        }
        return new Suggestions(text, start, prefix.length(), sorted(texts));
    }

    /**
     * 1.13's suggestions for chat that is not a command: the online players' names (the tab list) that start with the
     * last word, ignoring case.
     */
    public static Suggestions names(String text, Collection<String> names) {
        int start = lastWordIndex(text);
        String rest = text.substring(start).toLowerCase(Locale.ROOT);
        Set<String> texts = new LinkedHashSet<String>();
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).startsWith(rest)) {
                texts.add(name);
            }
        }
        return new Suggestions(text, start, text.length(), sorted(texts));
    }

    private static List<String> sorted(Set<String> texts) {
        List<String> list = new ArrayList<String>(texts);
        // Brigadier's Suggestions.create sorts this way.
        Collections.sort(list, String::compareToIgnoreCase);
        return list;
    }

    /** @return where the last word starts: after the last run of whitespace (1.13's {@code getLastWordIndex}) */
    public static int lastWordIndex(String text) {
        int i = 0;
        Matcher m = WHITESPACE.matcher(text);
        while (m.find()) {
            i = m.end();
        }
        return i;
    }

    /** @return the grey rest drawn after the typed text, or null when accepting would change what is already typed */
    @Nullable
    public static String previewSuffix(String typed, String accepted) {
        return accepted.startsWith(typed) ? accepted.substring(typed.length()) : null;
    }

    /** @return the command's name once it is typed out (a space follows it and the cursor is past it), or null */
    @Nullable
    public static String commandName(String text, int cursor) {
        if (!text.startsWith("/")) {
            return null;
        }
        int space = text.indexOf(' ');
        if (space <= 1 || cursor <= space) {
            return null;
        }
        return text.substring(1, space).toLowerCase(Locale.ROOT);
    }

    /**
     * @return the usage without its leading {@code /name} (1.13 shows the arguments still to come), or the whole usage
     * when it does not start that way; null for an empty one
     */
    @Nullable
    public static String usageArguments(String name, @Nullable String usage) {
        if (usage == null) {
            return null;
        }
        String u = usage.trim();
        String head = "/" + name;
        if (u.regionMatches(true, 0, head, 0, head.length()) && (u.length() == head.length() || u.charAt(head.length()) == ' ')) {
            u = u.substring(head.length()).trim();
        }
        return u.isEmpty() ? null : u;
    }

    /** The list's selection and scroll (1.13's {@code SuggestionsList} without the drawing). */
    public static final class Page {
        public final Suggestions suggestions;
        /** The text the list was opened over: each suggestion is applied to it. */
        public final String original;
        public final int rows;
        private int offset;
        private int selected;
        /** Set once a suggestion is accepted: the next Tab moves on before accepting. */
        public boolean accepted;

        public Page(Suggestions suggestions, String original, int rows) {
            this.suggestions = suggestions;
            this.original = original;
            this.rows = rows;
            select(0);
        }

        public int offset() {
            return this.offset;
        }

        public int selected() {
            return this.selected;
        }

        /** @return how many rows show */
        public int visible() {
            return Math.min(this.suggestions.size(), this.rows);
        }

        public boolean moreAbove() {
            return this.offset > 0;
        }

        public boolean moreBelow() {
            return this.suggestions.size() > this.offset + visible();
        }

        /** Selects {@code i}, wrapping around either end. */
        public void select(int i) {
            int n = this.suggestions.size();
            if (n == 0) {
                this.selected = 0;
                return;
            }
            if (i < 0) {
                i += n;
            }
            if (i >= n) {
                i -= n;
            }
            this.selected = i;
        }

        /** Moves the selection by {@code delta}, scrolling so it stays in view. */
        public void cycle(int delta) {
            select(this.selected + delta);
            int max = Math.max(this.suggestions.size() - this.rows, 0);
            if (this.selected < this.offset) {
                this.offset = MathHelper.clamp(this.selected, 0, max);
            } else if (this.selected > this.offset + this.rows - 1) {
                this.offset = MathHelper.clamp(this.selected + 1 - this.rows, 0, max);
            }
        }

        /** The mouse wheel: up (positive) shows earlier rows. */
        public void scroll(double amount) {
            this.offset = MathHelper.clamp((int) (this.offset - amount), 0, Math.max(this.suggestions.size() - this.rows, 0));
        }

        /** @return the text with the selected suggestion accepted */
        public String applied() {
            return this.suggestions.apply(this.selected, this.original);
        }

        public String selectedText() {
            return this.suggestions.texts.get(this.selected);
        }
    }
}
