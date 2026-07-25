package com.termux.terminal;

/**
 * <p>
 * Encodes effects, foreground and background colors into a 64 bit long, which are stored for each cell in a terminal
 * row in {@link TerminalRow#mStyle}.
 * </p>
 * <p>
 * The bit layout is:
 * </p>
 * - 16 flags (11 currently used).
 * - 24 for foreground color (only 9 first bits if a color index).
 * - 24 for background color (only 9 first bits if a color index).
 */
public final class TextStyle {

    public final static int CHARACTER_ATTRIBUTE_BOLD = 1;
    public final static int CHARACTER_ATTRIBUTE_ITALIC = 1 << 1;
    public final static int CHARACTER_ATTRIBUTE_UNDERLINE = 1 << 2;
    public final static int CHARACTER_ATTRIBUTE_BLINK = 1 << 3;
    public final static int CHARACTER_ATTRIBUTE_INVERSE = 1 << 4;
    public final static int CHARACTER_ATTRIBUTE_INVISIBLE = 1 << 5;
    public final static int CHARACTER_ATTRIBUTE_STRIKETHROUGH = 1 << 6;
    /**
     * The selective erase control functions (DECSED and DECSEL) can only erase characters defined as erasable.
     * <p>
     * This bit is set if DECSCA (Select Character Protection Attribute) has been used to define the characters that
     * come after it as erasable from the screen.
     * </p>
     */
    public final static int CHARACTER_ATTRIBUTE_PROTECTED = 1 << 7;
    /** Dim colors. Also known as faint or half intensity. */
    public final static int CHARACTER_ATTRIBUTE_DIM = 1 << 8;
    /** If true (24-bit) color is used for the cell for foreground. */
    private final static int CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND = 1 << 9;
    /** If true (24-bit) color is used for the cell for foreground. */
    private final static int CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND= 1 << 10;

    /** Underline variant (SGR 4:0-5), stored in bits 11-13. */
    public static final int UNDERLINE_VARIANT_SHIFT = 11;
    public static final int UNDERLINE_VARIANT_MASK = 0b111 << UNDERLINE_VARIANT_SHIFT;
    public static final int UNDERLINE_NONE = 0;
    public static final int UNDERLINE_SINGLE = 1;
    public static final int UNDERLINE_DOUBLE = 2;
    public static final int UNDERLINE_CURLY = 3;
    public static final int UNDERLINE_DOTTED = 4;
    public static final int UNDERLINE_DASHED = 5;

    /** Overline (SGR 53/55). */
    public final static int CHARACTER_ATTRIBUTE_OVERLINE = 1 << 14;

    /** Boxed / framed. */
    public final static int CHARACTER_ATTRIBUTE_BOXED = 1 << 15;

    public final static int COLOR_INDEX_FOREGROUND = 256;
    public final static int COLOR_INDEX_BACKGROUND = 257;
    public final static int COLOR_INDEX_CURSOR = 258;

    /** The 256 standard color entries and the three special (foreground, background and cursor) ones. */
    public final static int NUM_INDEXED_COLORS = 259;

    /** Normal foreground and background colors and no effects. */
    final static long NORMAL = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0);

    static long encode(int foreColor, int backColor, int effect) {
        long result = effect & 0xFFFFL;
        if ((0xff000000 & foreColor) == 0xff000000) {
            // 24-bit color.
            result |= CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND | ((foreColor & 0x00ffffffL) << 40L);
        } else {
            // Indexed color.
            result |= (foreColor & 0b111111111L) << 40;
        }
        if ((0xff000000 & backColor) == 0xff000000) {
            // 24-bit color.
            result |= CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND | ((backColor & 0x00ffffffL) << 16L);
        } else {
            // Indexed color.
            result |= (backColor & 0b111111111L) << 16L;
        }

        return result;
    }

    public static int decodeForeColor(long style) {
        if ((style & CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND) == 0) {
            return (int) ((style >>> 40) & 0b111111111L);
        } else {
            return 0xff000000 | (int) ((style >>> 40) & 0x00ffffffL);
        }

    }

    public static int decodeBackColor(long style) {
        if ((style & CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND) == 0) {
            return (int) ((style >>> 16) & 0b111111111L);
        } else {
            return 0xff000000 | (int) ((style >>> 16) & 0x00ffffffL);
        }
    }

    public static int decodeEffect(long style) {
        return (int) (style & 0xFFFFL);
    }

    public static int decodeUnderlineVariant(long style) {
        return (int) ((style & UNDERLINE_VARIANT_MASK) >>> UNDERLINE_VARIANT_SHIFT);
    }

    public static long encodeUnderlineVariant(long style, int variant) {
        return (style & ~UNDERLINE_VARIANT_MASK)
            | (((long) variant) << UNDERLINE_VARIANT_SHIFT);
    }

    public static boolean decodeOverline(long style) {
        return (style & CHARACTER_ATTRIBUTE_OVERLINE) != 0;
    }

    public static boolean decodeBoxed(long style) {
        return (style & CHARACTER_ATTRIBUTE_BOXED) != 0;
    }

}
