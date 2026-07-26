package com.termux.terminal;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * One-time code generator for WcWidth two-level nibble-packed table.
 * Run with: java -cp ... com.termux.terminal.WcWidthGenerator > WcWidth.java
 */
public class WcWidthGenerator {
    public static void main(String[] args) throws Exception {
        Map<Integer, Integer> widthMap = new HashMap<>();
        // Default all to 1
        for (int i = 0; i <= 0x10FFFF; i++) widthMap.put(i, 1);
        // Zero-width combining marks
        List<int[]> zero = parseRanges("https://www.unicode.org/Public/UNIDATA/DerivedCoreProperties.txt",
            "Default_Ignorable_Code_Point");
        // Also fetch zero-width from PropList
        List<int[]> zero2 = parseRanges("https://www.unicode.org/Public/UNIDATA/PropList.txt",
            "Grapheme_Extend");
        zero.addAll(zero2);
        for (int[] r : zero) for (int i = r[0]; i <= r[1]; i++) widthMap.put(i, 0);
        // Variation selectors and VS supplement
        for (int i = 0xFE00; i <= 0xFE0F; i++) widthMap.put(i, 0);
        for (int i = 0xE0100; i <= 0xE01EF; i++) widthMap.put(i, 0);
        // Soft hyphen, ZWJ, ZWNJ, LRM, RLM, etc.
        int[] specialZero = {0x00AD, 0x034F, 0x061C, 0x200B,0x200C,0x200D,0x200E,0x200F,
            0x2028,0x2029,0x202A,0x202B,0x202C,0x202D,0x202E,
            0x2060,0x2061,0x2062,0x2063,0x2064,0x2066,0x2067,0x2068,0x2069,
            0xFEFF};
        for (int cp : specialZero) widthMap.put(cp, 0);
        // Regional indicators
        for (int i = 0x1F1E6; i <= 0x1F1FF; i++) widthMap.put(i, 2);
        // Wide East Asian
        List<int[]> wide = parseRanges("https://www.unicode.org/Public/UNIDATA/EastAsianWidth.txt",
            "W", "F");
        for (int[] r : wide) for (int i = r[0]; i <= r[1]; i++) widthMap.put(i, 2);
        // Emoji: most emoji have width 2 in terminals
        // Misc Symbols, Dingbats, Emoticons, Transport, etc.
        int[] emojiWide = {
            0x231A,0x231B,0x23E9,0x23EA,0x23EB,0x23EC,0x23F0,0x23F3,
            0x25FD,0x25FE,0x2614,0x2615,0x2648,0x2649,0x264A,0x264B,
            0x264C,0x264D,0x264E,0x264F,0x2650,0x2651,0x2652,0x2653,
            0x267F,0x2693,0x26A1,0x26AA,0x26AB,0x26BD,0x26BE,0x26C4,
            0x26C5,0x26CE,0x26D4,0x26EA,0x26F2,0x26F3,0x26F5,0x26FA,
            0x26FD,0x2702,0x2705,0x2708,0x2709,0x270A,0x270B,0x270C,
            0x270D,0x270F,0x2712,0x2714,0x2716,0x271D,0x2721,0x2728,
            0x2733,0x2734,0x2744,0x2747,0x274C,0x274E,0x2753,0x2754,
            0x2755,0x2757,0x2763,0x2764,0x2795,0x2796,0x2797,0x27A1,
            0x27B0,0x27BF,0x2934,0x2935,0x2B05,0x2B06,0x2B07,0x2B1B,
            0x2B1C,0x2B50,0x2B55,0x3030,0x303D,0x3297,0x3299,
            0x1F000,0x1F0CF,0x1F18E,0x1F191,0x1F19A,
            0x1F200,0x1F202,0x1F210,0x1F23B,0x1F240,0x1F248,
            0x1F250,0x1F251,0x1F260,0x1F265,0x1F300,0x1F6FF,
            0x1F780,0x1F7FF,0x1F800,0x1F8FF,0x1F900,0x1FAFF,
            0x1FB00,0x1FBFF
        };
        for (int i = 0; i < emojiWide.length; i += 2) {
            for (int cp = emojiWide[i]; cp <= emojiWide[i+1]; cp++) {
                widthMap.put(cp, 2);
            }
        }

        // Build two-level table
        int numMajor = 0x110;
        int[][] minor = new int[numMajor][];
        short[] major = new short[numMajor];
        List<int[]> minorList = new ArrayList<>();
        int[] firstMinor = new int[256];
        Arrays.fill(firstMinor, 1);
        minorList.add(firstMinor);

        for (int page = 0; page < numMajor; page++) {
            int[] row = new int[256];
            boolean allOne = true;
            for (int i = 0; i < 256; i++) {
                int cp = (page << 8) | i;
                row[i] = widthMap.getOrDefault(cp, 1);
                if (row[i] != 1) allOne = false;
            }
            if (allOne) {
                major[page] = 0;
                minor[page] = firstMinor;
            } else {
                int idx = findOrAdd(minorList, row);
                major[page] = (short) idx;
                minor[page] = row;
            }
        }

        // Generate Java
        StringBuilder sb = new StringBuilder();
        sb.append("package com.termux.terminal;\n\n");
        sb.append("/** Auto-generated two-level width table by WcWidthGenerator. */\n");
        sb.append("public final class WcWidth {\n\n");
        sb.append("    private static final short[] WIDTH_MAJOR = {\n");
        for (int i = 0; i < major.length; i++) {
            if (i % 16 == 0) sb.append("        ");
            sb.append(String.format("%4d", (int) major[i]));
            if (i < major.length - 1) sb.append(", ");
            if ((i + 1) % 16 == 0) sb.append("\n");
        }
        sb.append("\n    };\n\n");
        sb.append("    private static final byte[][] WIDTH_MINOR = {\n");
        for (int t = 0; t < minorList.size(); t++) {
            int[] row = minorList.get(t);
            sb.append("        {");
            for (int i = 0; i < 256; i += 2) {
                int b = (row[i+1] << 4) | row[i];
                sb.append(String.format("(byte)0x%02X", b));
                if (i < 254) sb.append(", ");
            }
            sb.append("}");
            if (t < minorList.size() - 1) sb.append(",");
            sb.append(" // table ").append(t).append("\n");
        }
        sb.append("    };\n\n");
        sb.append("    public static int width(int codePoint) {\n");
        sb.append("        if (codePoint < 0 || codePoint > 0x10FFFF) return 1;\n");
        sb.append("        int major = codePoint >>> 8;\n");
        sb.append("        int minorIndex = WIDTH_MAJOR[major];\n");
        sb.append("        byte[] table = WIDTH_MINOR[minorIndex];\n");
        sb.append("        int minor = codePoint & 0xFF;\n");
        sb.append("        int b = table[minor >> 1] & 0xFF;\n");
        sb.append("        return (minor & 1) == 0 ? (b & 0xF) : (b >>> 4);\n");
        sb.append("    }\n\n");

        // graphemeWidth
        sb.append("    public static int graphemeWidth(String grapheme) {\n");
        sb.append("        int cp = Character.codePointAt(grapheme, 0);\n");
        sb.append("        if (cp >= 0x1F1E6 && cp <= 0x1F1FF && grapheme.codePointCount(0, grapheme.length()) >= 2) return 2;\n");
        sb.append("        for (int i = 0; i < grapheme.length(); ) {\n");
        sb.append("            if (Character.codePointAt(grapheme, i) == 0xFE0F) return 2;\n");
        sb.append("            i += Character.charCount(Character.codePointAt(grapheme, i));\n");
        sb.append("        }\n");
        sb.append("        int w = width(cp);\n");
        sb.append("        return w > 0 ? w : 1;\n");
        sb.append("    }\n\n");

        // zeroWidthCharsCount and width(char[], int)
        sb.append("    public static int width(char[] chars, int index) {\n");
        sb.append("        char c = chars[index];\n");
        sb.append("        return Character.isHighSurrogate(c) ? width(Character.toCodePoint(c, chars[index + 1])) : width(c);\n");
        sb.append("    }\n\n");
        sb.append("    public static int zeroWidthCharsCount(char[] chars, int start, int end) {\n");
        sb.append("        int count = 0;\n");
        sb.append("        for (int i = start; i < end && i < chars.length; ) {\n");
        sb.append("            int cp = Character.isHighSurrogate(chars[i]) ? Character.toCodePoint(chars[i], chars[i+1]) : chars[i];\n");
        sb.append("            if (width(cp) <= 0) count++;\n");
        sb.append("            i += (cp > 0xFFFF) ? 2 : 1;\n");
        sb.append("        }\n");
        sb.append("        return count;\n");
        sb.append("    }\n\n");
        sb.append("    private WcWidth() {}\n");
        sb.append("}\n");

        System.out.println(sb.toString());
    }

    private static List<int[]> parseRanges(String url, String... props) throws Exception {
        List<int[]> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new URL(url).openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                for (String prop : props) {
                    if (line.contains(prop)) {
                        String[] parts = line.split("[;.#]")[0].trim().split("\\.\\.");
                        if (parts.length == 2) {
                            result.add(new int[]{Integer.parseInt(parts[0].trim(), 16), Integer.parseInt(parts[1].trim(), 16)});
                        } else {
                            int cp = Integer.parseInt(parts[0].trim(), 16);
                            result.add(new int[]{cp, cp});
                        }
                        break;
                    }
                }
            }
        }
        return result;
    }

    private static int findOrAdd(List<int[]> list, int[] row) {
        for (int i = 0; i < list.size(); i++) {
            if (Arrays.equals(list.get(i), row)) return i;
        }
        list.add(row);
        return list.size() - 1;
    }
}
