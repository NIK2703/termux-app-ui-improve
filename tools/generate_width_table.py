#!/usr/bin/env python3
"""
Generate WcWidth.java two-level nibble-packed width table.
Sources:
  - VTE unicode-width.hh (GLib 2.78.0) for base Unicode widths
  - Termux overrides for terminal behavior (emoji=2, controls=0, regionals=2)
"""
import re, os, sys

VTE_HH = '/data/local/projects/vte/src/unicode-width.hh'
OUT = 'terminal-emulator/src/main/java/com/termux/terminal/WcWidth.java'

# ── Parse VTE table ──────────────────────────────────────────────
with open(VTE_HH) as f:
    vte = f.read()

# Major table: 17*256 = 4352 entries
major = []  # list of ('min', idx) or ('width', w) or ('ambig', w)
for m in re.finditer(r'MIN_TABLE\((\d+)\)|WIDTH\((\d+)\)', vte):
    if m.group(1):
        major.append(('min', int(m.group(1))))
    else:
        w = int(m.group(2))
        major.append(('ambig' if w == 3 else 'width', w if w != 3 else 1))

# Minor tables
min_tables_raw = {}
for m in re.finditer(r'/\* \[(\d+)\].*?\{([^}]+)\}', vte, re.DOTALL):
    idx = int(m.group(1))
    body = m.group(2)
    vals = re.findall(r'WIDTHS\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)', body)
    if vals:
        min_tables_raw[idx] = vals

# Build base width map
W = {}
for maj_idx, entry in enumerate(major):
    base = maj_idx * 256
    if entry[0] == 'width' or entry[0] == 'ambig':
        w = entry[1]
        for i in range(256):
            W[base + i] = w
    else:
        ti = entry[1]
        if ti in min_tables_raw:
            vals = min_tables_raw[ti]
            for bi, (a, b, c, d) in enumerate(vals):
                for bit_i, v in enumerate([a, b, c, d]):
                    W[base + bi * 4 + bit_i] = int(v) if int(v) != 3 else 1
        else:
            for i in range(256):
                W[base + i] = 1

# Surrogates → 1
for i in range(0xD800, 0xE000):
    W[i] = 1

# ── Termux overrides ──────────────────────────────────────────
# C0/C1 controls
for i in range(0, 32):
    W[i] = 0
for i in range(0x7F, 0xA0):
    W[i] = 0

# Soft hyphen, BOM, various specials
for cp in [0x00AD, 0x034F, 0x061C, 0xFEFF]:
    W[cp] = 0



# ── Build two-level table ──────────────────────────────────────
num_pages = len(major)
minor_list = []
major_idx = []

all_ones = [1] * 256
minor_list.append(all_ones)  # index 0 = all ones

def find_or_add(tbl):
    for i, t in enumerate(minor_list):
        if t == tbl:
            return i
    minor_list.append(tbl)
    return len(minor_list) - 1

for page in range(num_pages):
    row = [W.get((page << 8) | i, 1) for i in range(256)]
    if row == all_ones:
        major_idx.append(0)
    else:
        major_idx.append(find_or_add(row))

print(f'Minor tables: {len(minor_list)}, Major entries: {len(major_idx)}', file=sys.stderr)

# ── Helper: build inline byte array literal ─────────────────────
def gen_byte_array(values, indent=8):
    """values: list of 0-255 ints. Returns lines of Java literal."""
    lines = []
    prefix = ' ' * indent
    for i in range(0, len(values), 24):
        chunk = values[i:i+24]
        lines.append(prefix + ', '.join(f'(byte)0x{x:02X}' for x in chunk) + ',')
    return lines

# Split minor tables into chunks (each builds a flat byte[] in its own method)
# 105 tables × 128 bytes each = 13440 bytes
MINOR_CHUNK_SIZE = 50  # 50 tables per chunk (6400 entries, ~38KB bytecode)
minor_flat = []
for ti in sorted(range(len(minor_list)), key=lambda x: x):
    tbl = minor_list[ti]
    for i in range(0, 256, 2):
        minor_flat.append((tbl[i+1] << 4) | tbl[i])

minor_chunks = []
for start in range(0, len(minor_flat), MINOR_CHUNK_SIZE * 128):
    minor_chunks.append(minor_flat[start:start + MINOR_CHUNK_SIZE * 128])

# ── Generate Java ──────────────────────────────────────────────
out = []
out.append('package com.termux.terminal;')
out.append('')
out.append('/**')
out.append(' * Two-level nibble-packed width table.')
out.append(' * Base data from VTE unicode-width.hh (GLib).')
out.append(' * Regenerate: python3 tools/generate_width_table.py')
out.append(' */')
out.append('public final class WcWidth {')
out.append('')

# Build major table in separate method to avoid clinit code limit
out.append('    private static byte[] buildMajor() {')
out.append('        return new byte[] {')
out.extend(gen_byte_array(major_idx))
out.append('        };')
out.append('    }')
out.append('')

for ci, chunk in enumerate(minor_chunks):
    out.append(f'    private static byte[] buildMinor{ci}() {{')
    out.append('        return new byte[] {')
    out.extend(gen_byte_array(chunk))
    out.append('        };')
    out.append('    }')
    out.append('')

out.append('    private static final byte[] WIDTH_MAJOR = buildMajor();')
out.append('    private static final byte[] WIDTH_MINOR;')
out.append('    static {')
if len(minor_chunks) == 1:
    out.append('        WIDTH_MINOR = buildMinor0();')
else:
    sizes = [len(c) for c in minor_chunks]
    out.append(f'        byte[] a = buildMinor0();')
    out.append(f'        byte[] b = buildMinor1();')
    total = sum(sizes)
    if len(minor_chunks) >= 3:
        for ci in range(2, len(minor_chunks)):
            out.append(f'        byte[] c{ci} = buildMinor{ci}();')
    out.append(f'        WIDTH_MINOR = new byte[{total}];')
    off = 0
    for ci in range(len(minor_chunks)):
        src = f'c{ci}' if ci >= 2 else ('a' if ci == 0 else 'b')
        out.append(f'        System.arraycopy({src}, 0, WIDTH_MINOR, {off}, {sizes[ci]});')
        off += sizes[ci]
out.append('    }')
out.append('')

out.append('    public static int width(int codePoint) {')
out.append('        if (codePoint < 0 || codePoint > 0x10FFFF) return 1;')
out.append('        int major = codePoint >>> 8;')
out.append('        int minorIndex = WIDTH_MAJOR[major] & 0xFF;')
out.append('        int minor = codePoint & 0xFF;')
out.append('        int b = WIDTH_MINOR[(minorIndex << 7) + (minor >> 1)] & 0xFF;')
out.append('        return (minor & 1) == 0 ? (b & 0xF) : (b >>> 4);')
out.append('    }')
out.append('')
out.append('    private static boolean isRegionalIndicator(int cp) {')
out.append('        return cp >= 0x1F1E6 && cp <= 0x1F1FF;')
out.append('    }')
out.append('')
out.append('    private static boolean containsVS16(String grapheme) {')
out.append('        for (int i = 0; i < grapheme.length(); ) {')
out.append('            int cp = Character.codePointAt(grapheme, i);')
out.append('            if (cp == 0xFE0F) return true;')
out.append('            i += Character.charCount(cp);')
out.append('        }')
out.append('        return false;')
out.append('    }')
out.append('')
out.append('    public static int graphemeWidth(String grapheme) {')
out.append('        int cp = Character.codePointAt(grapheme, 0);')
out.append('        if (isRegionalIndicator(cp) && grapheme.codePointCount(0, grapheme.length()) >= 2) return 2;')
out.append('        if (containsVS16(grapheme)) return 2;')
out.append('        int w = width(cp);')
out.append('        return w > 0 ? w : 1;')
out.append('    }')
out.append('')
out.append('    public static int width(char[] chars, int index) {')
out.append('        char c = chars[index];')
out.append('        return Character.isHighSurrogate(c) ? width(Character.toCodePoint(c, chars[index + 1])) : width(c);')
out.append('    }')
out.append('')
out.append('    public static int zeroWidthCharsCount(char[] chars, int start, int end) {')
out.append('        if (start < 0 || start >= chars.length) return 0;')
out.append('        int count = 0;')
out.append('        for (int i = start; i < end && i < chars.length; ) {')
out.append('            if (Character.isHighSurrogate(chars[i])) {')
out.append('                if (width(Character.toCodePoint(chars[i], chars[i + 1])) <= 0) count++;')
out.append('                i += 2;')
out.append('            } else {')
out.append('                if (width(chars[i]) <= 0) count++;')
out.append('                i++;')
out.append('            }')
out.append('        }')
out.append('        return count;')
out.append('    }')
out.append('')
out.append('    private WcWidth() {}')
out.append('}')

# Write to temp file first, then copy (avoid truncation issue)
tmp = '/data/data/com.termux/files/home/.cache/opencode/tmp/WcWidth.java.new'
with open(tmp, 'w') as f:
    f.write('\n'.join(out) + '\n')

# Verify
tests = [
    (0x0041, 1, 'A'),
    (0x000A, 0, 'newline'),
    (0x007F, 0, 'DEL'),
    (0x00AD, 0, 'soft hyphen'),
    (0x0300, 0, 'combining grave'),
    (0xFE0F, 0, 'VS16'),
    (0x200B, 0, 'zero width space'),
    (0x200D, 0, 'ZWJ'),
    (0xFEFF, 0, 'BOM'),
    (0x1100, 2, 'hangul choseong'),
    (0x3000, 2, 'ideographic space'),
    (0x4E00, 2, 'CJK'),
    (0xAC00, 2, 'hangul syllable'),
    (0x1F600, 2, 'emoji grin'),  # W from EAW
    (0x2600, 1, 'sun (EAW=N, in graphemeWidth with VS16→2)'),
    (0x2702, 1, 'scissors (EAW=N)'),
    (0x231A, 2, 'watch (W from EAW)'),
    (0x1F1E6, 1, 'regional indicator (individual=1, pair→2 in graphemeWidth)'),
    (0x25A0, 1, 'black square'),
    (0x2500, 1, 'box drawing'),
]
print('Verification:', file=sys.stderr)
ok = True
for cp, exp, name in tests:
    w = W.get(cp, 1)
    if w != exp:
        print(f'  FAIL U+{cp:04X} {name}: expected {exp} got {w}', file=sys.stderr)
        ok = False
if ok:
    print('  All OK!', file=sys.stderr)
    os.rename(tmp, OUT)
    print(f'Written to {OUT}', file=sys.stderr)
else:
    print(f'TEMP left at {tmp} for inspection', file=sys.stderr)
    sys.exit(1)
