package com.exiledradio.backportcore.presentation;

/**
 * How the chat box's command suggestion list is drawn: plain data, so a later version can hand Core its own look
 * ({@link Contribution#commandSuggestionLook}). {@link #V1_13} is 1.13.2's {@code GuiChat.SuggestionsList}.
 */
public final class SuggestionLook {

    /** 1.13.2: 10 rows of 12 pixels on black at 80% (0xD0000000), the selected row yellow, the others light grey. */
    public static final SuggestionLook V1_13 = new SuggestionLook(10, 12, 0xD0000000, 0xFFFFFF00, 0xFFAAAAAA, 0xFF808080,
            0xFFFFFFFF, 0xFF000000, 0xFFAAAAAA);

    /** Rows shown at once; the list scrolls past this. */
    public final int rows;
    /** Pixels per row. */
    public final int rowHeight;
    /** The rows' background, ARGB. */
    public final int background;
    public final int selected;
    public final int unselected;
    /** The selected suggestion's rest, drawn after the typed text. */
    public final int preview;
    /** The dotted line above or below the list when it scrolls further that way. */
    public final int scrollDots;
    /** The usage line's background. */
    public final int usageBackground;
    public final int usageText;

    public SuggestionLook(int rows, int rowHeight, int background, int selected, int unselected, int preview, int scrollDots,
            int usageBackground, int usageText) {
        this.rows = rows;
        this.rowHeight = rowHeight;
        this.background = background;
        this.selected = selected;
        this.unselected = unselected;
        this.preview = preview;
        this.scrollDots = scrollDots;
        this.usageBackground = usageBackground;
        this.usageText = usageText;
    }
}
