package com.termux.installer;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public final class TarGzExtractor {

    public static final long DEFAULT_MAX_TOTAL_SIZE = 512L * 1024L * 1024L;
    public static final long DEFAULT_MAX_ENTRY_SIZE = 256L * 1024L * 1024L;
    public static final int DEFAULT_MAX_ENTRIES = 50000;

    private static final int BLOCK_SIZE = 512;
    private static final long MAX_METADATA_ENTRY_SIZE = 1L * 1024L * 1024L;
    private static final int MAX_PATH_LENGTH = 4096;
    private static final int MAX_SEGMENT_LENGTH = 255;

    private static final byte TYPE_REGULAR = '0';
    private static final byte TYPE_HARDLINK = '1';
    private static final byte TYPE_SYMLINK = '2';
    private static final byte TYPE_CHAR = '3';
    private static final byte TYPE_BLOCK = '4';
    private static final byte TYPE_DIRECTORY = '5';
    private static final byte TYPE_FIFO = '6';
    private static final byte TYPE_CONTIGUOUS = '7';

    private static final byte TYPE_GNU_LONGLINK = 'K';
    private static final byte TYPE_GNU_LONGNAME = 'L';
    private static final byte TYPE_PAX_EXTENDED = 'x';
    private static final byte TYPE_PAX_GLOBAL = 'g';

    public static final class TarException extends IOException {
        public TarException(String message) { super(message); }
        public TarException(String message, Throwable cause) { super(message, cause); }
    }

    public interface Listener {
        void onEntry(String name, long entryBytes, long totalBytes, int entryCount);
    }

    private TarGzExtractor() {}

    public static void extract(File tarGzFile, File targetDir, File finalPrefixDir, Listener listener) throws IOException {
        try (InputStream in = new FileInputStream(tarGzFile)) {
            extract(in, targetDir, finalPrefixDir, listener);
        }
    }

    public static void extract(InputStream rawInputStream, File targetDir, File finalPrefixDir,
                                Listener listener) throws IOException {
        extract(rawInputStream, targetDir, finalPrefixDir, listener,
            DEFAULT_MAX_TOTAL_SIZE, DEFAULT_MAX_ENTRY_SIZE, DEFAULT_MAX_ENTRIES);
    }

    public static void extract(InputStream rawInputStream, File targetDir, File finalPrefixDir,
                                Listener listener, long maxTotalSize, long maxEntrySize,
                                int maxEntries) throws IOException {
        try (InputStream in = new BufferedInputStream(new GZIPInputStream(rawInputStream, 65536), 65536)) {
            extractTar(in, targetDir, finalPrefixDir, listener, maxTotalSize, maxEntrySize, maxEntries);
        }
    }

    private static void extractTar(InputStream in, File targetDir, File finalPrefixDir,
                                    Listener listener, long maxTotalSize, long maxEntrySize,
                                    int maxEntries) throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs())
            throw new TarException("Cannot create target: " + targetDir);
        if (!targetDir.isDirectory())
            throw new TarException("Target not a directory: " + targetDir);

        String rootCanonical = targetDir.getCanonicalPath();
        String finalPrefixCanonical = finalPrefixDir != null ? finalPrefixDir.getCanonicalPath() : null;

        byte[] header = new byte[BLOCK_SIZE];
        long totalBytes = 0;
        int entryCount = 0;
        int metadataCount = 0;

        while (true) {
            int read = readBlock(in, header);
            if (read == -1) break;
            if (read != BLOCK_SIZE) throw new TarException("Truncated header");

            if (isZeroBlock(header)) {
                readBlock(in, header);
                break;
            }

            TarHeader h = parseHeader(header);
            boolean metadata = isMetadataType(h.type);

            if (metadata) {
                metadataCount++;
                if (metadataCount > maxEntries)
                    throw new TarException("Too many metadata entries");
            } else {
                entryCount++;
                if (entryCount > maxEntries)
                    throw new TarException("Too many entries");
            }

            if (h.size < 0) throw new TarException("Negative entry size");
            if (h.size > maxEntrySize)
                throw new TarException("Entry exceeds max size: " + h.name);
            if (metadata && h.size > MAX_METADATA_ENTRY_SIZE)
                throw new TarException("Metadata entry too large");
            if (h.size > maxTotalSize - totalBytes)
                throw new TarException("Archive exceeds max total size");

            if (metadata) {
                handleMetadata(in, h);
                totalBytes += h.size;
                skipPadding(in, h.size);
                continue;
            }

            if (!isSupportedType(h.type))
                throw new TarException("Unsupported entry type 0x" + Integer.toHexString(h.type & 0xff) + ": " + h.name);

            if ((h.type == TYPE_REGULAR || h.type == TYPE_CONTIGUOUS) && h.name.endsWith("/"))
                h.type = TYPE_DIRECTORY;

            String relative = mapToRelativePath(h.name, finalPrefixCanonical);

            switch (h.type) {
                case TYPE_REGULAR:
                case TYPE_CONTIGUOUS: {
                    if (relative.isEmpty()) throw new TarException("Regular file with empty path");
                    File outFile = safeResolve(targetDir, rootCanonical, relative);
                    long copied = writeRegularFile(in, outFile, h.size, h.mode, targetDir, rootCanonical);
                    if (copied != h.size)
                        throw new TarException("Size mismatch: " + h.name);
                    totalBytes += copied;
                    skipPadding(in, h.size);
                    break;
                }
                case TYPE_DIRECTORY: {
                    File dir = safeResolve(targetDir, rootCanonical, relative);
                    writeDirectory(dir, h.mode, targetDir, rootCanonical);
                    if (h.size > 0) skipFully(in, h.size);
                    totalBytes += h.size;
                    skipPadding(in, h.size);
                    break;
                }
                case TYPE_SYMLINK: {
                    if (relative.isEmpty()) throw new TarException("Symlink with empty path");
                    if (h.linkname == null || h.linkname.isEmpty())
                        throw new TarException("Symlink with empty target: " + h.name);
                    File linkFile = safeResolve(targetDir, rootCanonical, relative);
                    String linkTarget = validateSymlinkTarget(h.linkname, relative, targetDir,
                        rootCanonical, finalPrefixCanonical);
                    writeSymlink(linkFile, linkTarget, targetDir, rootCanonical);
                    if (h.size > 0) skipFully(in, h.size);
                    totalBytes += h.size;
                    skipPadding(in, h.size);
                    break;
                }
                default:
                    throw new TarException("Unsupported type for: " + h.name);
            }

            if (listener != null) {
                listener.onEntry(relative, h.size, totalBytes, entryCount);
            }
        }
    }

    private static void handleMetadata(InputStream in, TarHeader h) throws IOException {
        if (h.size <= 0) return;
        byte[] data = readData(in, h.size);
        switch (h.type) {
            case TYPE_GNU_LONGNAME:
            case 'N':
                h.name = parseCString(data);
                break;
            case TYPE_GNU_LONGLINK:
                h.linkname = parseCString(data);
                break;
            case TYPE_PAX_EXTENDED:
            case 'X':
                applyPaxHeaders(data, h);
                break;
        }
    }

    private static void applyPaxHeaders(byte[] data, TarHeader h) {
        int pos = 0;
        while (pos < data.length && data[pos] != 0) {
            int lenStart = pos;
            while (pos < data.length && data[pos] != ' ') pos++;
            if (pos >= data.length) break;
            int length;
            try { length = Integer.parseInt(new String(data, lenStart, pos - lenStart, StandardCharsets.US_ASCII)); }
            catch (NumberFormatException e) { break; }
            int recEnd = lenStart + length;
            if (recEnd > data.length) break;
            int eq = indexOf(data, (byte) '=', pos + 1, recEnd);
            if (eq < 0) { pos = recEnd; continue; }
            String key = new String(data, pos + 1, eq - (pos + 1), StandardCharsets.UTF_8);
            int vs = eq + 1, ve = recEnd;
            if (ve > vs && data[ve - 1] == '\n') ve--;
            String value = new String(data, vs, ve - vs, StandardCharsets.UTF_8);
            if ("path".equals(key)) h.name = value;
            else if ("linkpath".equals(key)) h.linkname = value;
            pos = recEnd;
        }
    }

    private static int indexOf(byte[] data, byte b, int from, int to) {
        for (int i = from; i < to; i++) if (data[i] == b) return i;
        return -1;
    }

    private static String mapToRelativePath(String raw, String finalPrefixCanonical) throws TarException {
        if (raw == null) return "";
        String p = raw.replace('\\', '/');
        int nul = p.indexOf('\0');
        if (nul >= 0) p = p.substring(0, nul);

        p = stripLeading(p);

        if (p.startsWith("/"))
            p = stripAbsolute(p, finalPrefixCanonical);

        p = stripKnownPrefixes(p);
        p = stripLeading(p);

        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.isEmpty() || ".".equals(p)) return "";
        if (p.length() > MAX_PATH_LENGTH) throw new TarException("Path too long: " + raw);

        String[] segs = p.split("/");
        StringBuilder sb = new StringBuilder();
        for (String s : segs) {
            if (s.isEmpty() || ".".equals(s)) continue;
            if ("..".equals(s)) throw new TarException("Path traversal: " + raw);
            if (s.length() > MAX_SEGMENT_LENGTH) throw new TarException("Segment too long: " + raw);
            if (sb.length() > 0) sb.append('/');
            sb.append(s);
        }
        if (sb.length() > MAX_PATH_LENGTH) throw new TarException("Path too long: " + raw);
        return sb.toString();
    }

    private static String stripLeading(String p) {
        while (p.startsWith("./")) p = p.substring(2);
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    private static String stripKnownPrefixes(String p) {
        boolean changed;
        do {
            changed = false;
            if (p.startsWith("files/usr/") || p.equals("files/usr")) {
                p = p.startsWith("files/usr/") ? p.substring(10) : "";
                changed = true;
            }
            if (p.startsWith("usr/") || p.equals("usr")) {
                p = p.startsWith("usr/") ? p.substring(4) : "";
                changed = true;
            }
            int idx = p.indexOf("/files/usr/");
            if (idx >= 0) { p = p.substring(idx + 10); changed = true; }
            p = stripLeading(p);
        } while (changed && !p.isEmpty());
        return p;
    }

    private static String stripAbsolute(String p, String finalPrefixCanonical) throws TarException {
        String stripped = tryStripAbsolute(p, finalPrefixCanonical);
        if (stripped != null) return stripped;
        String canonical = safeCanonical(p);
        if (canonical != null) {
            stripped = tryStripAbsolute(canonical, finalPrefixCanonical);
            if (stripped != null) return stripped;
        }
        throw new TarException("Absolute path outside prefix: " + p);
    }

    private static String tryStripAbsolute(String p, String prefix) {
        if (prefix != null) {
            if (p.equals(prefix)) return "";
            if (p.startsWith(prefix + "/")) return p.substring(prefix.length() + 1);
        }
        int idx = p.indexOf("/files/usr/");
        if (idx >= 0) return p.substring(idx + 10);
        if (p.endsWith("/files/usr")) return "";
        if (p.startsWith("/usr/")) return p.substring(5);
        if (p.equals("/usr")) return "";
        if (p.startsWith("/data/")) {
            int u = p.indexOf("/files/usr/");
            if (u >= 0) return p.substring(u + 10);
        }
        return null;
    }

    private static String validateSymlinkTarget(String linkname, String relativePath,
                                                 File targetDir, String rootCanonical,
                                                 String finalPrefixCanonical) throws IOException {
        String target = linkname.replace('\\', '/');
        int nul = target.indexOf('\0');
        if (nul >= 0) target = target.substring(0, nul);
        if (target.isEmpty()) throw new TarException("Empty symlink target: " + relativePath);
        if (target.length() > MAX_PATH_LENGTH) throw new TarException("Symlink target too long");

        if (target.startsWith("/")) {
            String rewritten = rewriteAbsolute(target, finalPrefixCanonical);
            if (rewritten != null) {
                String parent = parentPath(relativePath);
                String rel = computeRelative(parent, rewritten);
                return validateRelative(resolvedTarget(targetDir, rootCanonical, parent, rel), rootCanonical);
            }
            String canonical = safeCanonical(target);
            if (canonical != null && isAllowedAbsolute(canonical))
                return canonical;
            throw new TarException("Absolute symlink not allowed: " + linkname);
        }

        return validateRelative(resolvedTarget(targetDir, rootCanonical, parentPath(relativePath), target), rootCanonical);
    }

    private static String rewriteAbsolute(String target, String prefix) {
        try {
            String canonical = safeCanonical(target);
            String p = canonical != null ? canonical : target;
            if (prefix != null) {
                if (p.equals(prefix)) return "";
                if (p.startsWith(prefix + "/")) return normalizeNoDotDot(p.substring(prefix.length() + 1));
            }
            int idx = p.indexOf("/files/usr/");
            if (idx >= 0) return normalizeNoDotDot(p.substring(idx + 10));
            if (p.endsWith("/files/usr")) return "";
            if (p.startsWith("/usr/")) return normalizeNoDotDot(p.substring(5));
            if (p.equals("/usr")) return "";
            if (p.startsWith("/data/")) {
                int u = p.indexOf("/files/usr/");
                if (u >= 0) return normalizeNoDotDot(p.substring(u + 10));
            }
        } catch (TarException e) {
            return null;
        }
        return null;
    }

    private static String normalizeNoDotDot(String p) throws TarException {
        p = p.replace('\\', '/');
        while (p.startsWith("./")) p = p.substring(2);
        while (p.startsWith("/")) p = p.substring(1);
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.isEmpty()) return "";
        String[] segs = p.split("/");
        StringBuilder sb = new StringBuilder();
        for (String s : segs) {
            if (s.isEmpty() || ".".equals(s)) continue;
            if ("..".equals(s)) return null;
            if (sb.length() > 0) sb.append('/');
            sb.append(s);
        }
        return sb.toString();
    }

    private static String validateRelative(File resolved, String rootCanonical) throws IOException {
        String c = resolved.getCanonicalPath();
        if (!c.equals(rootCanonical) && !c.startsWith(rootCanonical + "/"))
            throw new TarException("Symlink escapes prefix");
        return new File(rootCanonical).toURI().relativize(resolved.toURI()).getPath();
    }

    private static File resolvedTarget(File targetDir, String rootCanonical, String parent, String target) throws IOException {
        File linkParent = parent.isEmpty() ? targetDir : safeResolve(targetDir, rootCanonical, parent);
        return new File(linkParent, target);
    }

    private static String computeRelative(String from, String to) throws TarException {
        String[] f = from.isEmpty() ? new String[0] : from.split("/");
        String[] t = to.isEmpty() ? new String[0] : to.split("/");
        int common = 0;
        while (common < f.length && common < t.length && f[common].equals(t[common])) common++;
        StringBuilder sb = new StringBuilder();
        for (int i = common; i < f.length; i++) { if (sb.length() > 0) sb.append('/'); sb.append(".."); }
        for (int i = common; i < t.length; i++) { if (sb.length() > 0) sb.append('/'); sb.append(t[i]); }
        return sb.length() == 0 ? "." : sb.toString();
    }

    private static String parentPath(String relative) {
        int idx = relative.lastIndexOf('/');
        return idx < 0 ? "" : relative.substring(0, idx);
    }

    private static boolean isAllowedAbsolute(String canonical) {
        String[] allowedFiles = {"/dev/null", "/dev/zero", "/dev/random", "/dev/urandom"};
        String[] allowedPrefixes = {"/system", "/vendor", "/apex", "/product", "/odm", "/bin", "/etc"};
        for (String f : allowedFiles) if (canonical.equals(f)) return true;
        for (String p : allowedPrefixes) if (canonical.equals(p) || canonical.startsWith(p + "/")) return true;
        return false;
    }

    private static TarHeader parseHeader(byte[] header) throws TarException {
        int stored = (int) parseOctal(header, 148, 8);
        int computed = computeChecksum(header);
        if (stored != computed) throw new TarException("Invalid header checksum");

        TarHeader h = new TarHeader();
        h.name = parseString(header, 0, 100);
        h.mode = (int) parseNumeric(header, 100, 8);
        h.size = parseNumeric(header, 124, 12);
        h.type = header[156];
        if (h.type == 0) h.type = TYPE_REGULAR;
        h.linkname = parseString(header, 157, 100);

        String magic = parseString(header, 257, 6);
        String prefix = parseString(header, 345, 155);
        if (magic.startsWith("ustar") && !prefix.isEmpty()) {
            h.name = h.name.isEmpty() ? prefix : prefix + "/" + h.name;
        }
        return h;
    }

    private static int computeChecksum(byte[] h) {
        int sum = 0;
        for (int i = 0; i < BLOCK_SIZE; i++)
            sum += (i >= 148 && i < 156) ? ' ' : (h[i] & 0xff);
        return sum;
    }

    private static String parseString(byte[] buf, int off, int len) {
        int end = off;
        while (end < off + len && buf[end] != 0) end++;
        return end == off ? "" : new String(buf, off, end - off, StandardCharsets.UTF_8);
    }

    private static String parseCString(byte[] data) {
        if (data == null) return "";
        int end = 0;
        while (end < data.length && data[end] != 0) end++;
        return new String(data, 0, end, StandardCharsets.UTF_8);
    }

    private static long parseOctal(byte[] buf, int off, int len) throws TarException {
        int end = off + len, start = off;
        while (start < end && (buf[start] == 0 || buf[start] == ' ')) start++;
        if (start == end) return 0;
        long value = 0;
        while (start < end && buf[start] != 0 && buf[start] != ' ') {
            int c = buf[start];
            if (c < '0' || c > '7') throw new TarException("Invalid octal");
            value = (value << 3) + (c - '0');
            start++;
        }
        return value;
    }

    private static long parseNumeric(byte[] buf, int off, int len) throws TarException {
        if ((buf[off] & 0x80) != 0) {
            long value = buf[off] & 0x7f;
            for (int i = off + 1; i < off + len; i++)
                value = (value << 8) | (buf[i] & 0xff);
            return value;
        }
        return parseOctal(buf, off, len);
    }

    private static boolean isMetadataType(byte t) {
        return t == TYPE_GNU_LONGNAME || t == 'N' || t == TYPE_GNU_LONGLINK
            || t == TYPE_PAX_EXTENDED || t == 'X' || t == TYPE_PAX_GLOBAL;
    }

    private static boolean isSupportedType(byte t) {
        return t == TYPE_REGULAR || t == TYPE_CONTIGUOUS || t == TYPE_DIRECTORY || t == TYPE_SYMLINK;
    }

    private static File safeResolve(File base, String baseCanonical, String relative) throws IOException {
        File f = relative.isEmpty() ? base : new File(base, relative);
        String c = f.getCanonicalPath();
        if (!c.equals(baseCanonical) && !c.startsWith(baseCanonical + "/"))
            throw new TarException("Path escapes target: " + c);
        return f;
    }

    private static long writeRegularFile(InputStream in, File out, long size, int mode,
                                          File targetDir, String rootCanonical) throws IOException {
        ensureParent(out, targetDir, rootCanonical);
        deleteDest(out, false);
        long copied = copyData(in, out, size);
        chmodSafe(out, mode & 0777, false);
        return copied;
    }

    private static void writeDirectory(File dir, int mode, File targetDir, String rootCanonical) throws IOException {
        ensureParent(dir, targetDir, rootCanonical);
        deleteDest(dir, true);
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory())
            throw new TarException("Cannot create dir: " + dir);
        chmodSafe(dir, mode & 0777, true);
    }

    private static void writeSymlink(File linkFile, String target, File targetDir, String rootCanonical) throws IOException {
        ensureParent(linkFile, targetDir, rootCanonical);
        deleteDest(linkFile, false);
        try { Os.symlink(target, linkFile.getAbsolutePath()); }
        catch (ErrnoException e) { throw new IOException("symlink failed: " + linkFile, e); }
    }

    private static void ensureParent(File f, File targetDir, String rootCanonical) throws IOException {
        File p = f.getParentFile();
        if (p == null) return;
        if (p.exists()) {
            if (!p.isDirectory()) throw new TarException("Parent not a dir: " + p);
        } else if (!p.mkdirs() && !p.isDirectory()) {
            throw new TarException("Cannot create parent: " + p);
        }
    }

    private static void deleteDest(File f, boolean expectDir) throws IOException {
        if (isSymlink(f)) { f.delete(); return; }
        if (!f.exists()) return;
        if (expectDir) {
            if (!f.isDirectory()) throw new TarException("Cannot replace non-dir with dir: " + f);
            return;
        }
        if (f.isDirectory()) throw new TarException("Cannot replace dir with file: " + f);
        if (!f.delete()) throw new TarException("Cannot delete: " + f);
    }

    private static void chmodSafe(File f, int mode, boolean isDir) throws IOException {
        int perms = mode;
        if (isDir) {
            if (perms == 0) perms = 0700;
            perms |= 0700;
        } else {
            if (perms == 0) perms = 0600;
            perms |= 0600;
            if ((mode & 0111) != 0) perms |= 0100;
        }
        try { Os.chmod(f.getAbsolutePath(), perms); }
        catch (ErrnoException e) { throw new IOException("chmod failed: " + f, e); }
    }

    private static long copyData(InputStream in, File out, long size) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            long remaining = size, total = 0;
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int r = in.read(buf, 0, toRead);
                if (r < 0) throw new TarException("Truncated entry");
                fos.write(buf, 0, r);
                remaining -= r;
                total += r;
            }
            fos.flush();
            return total;
        }
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        byte[] buf = new byte[65536];
        while (n > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, n));
            if (r < 0) throw new TarException("Truncated archive");
            n -= r;
        }
    }

    private static byte[] readData(InputStream in, long size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) size);
        byte[] buf = new byte[65536];
        long remaining = size;
        while (remaining > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (r < 0) throw new TarException("Truncated metadata");
            out.write(buf, 0, r);
            remaining -= r;
        }
        return out.toByteArray();
    }

    private static int readBlock(InputStream in, byte[] block) throws IOException {
        int off = 0;
        while (off < block.length) {
            int r = in.read(block, off, block.length - off);
            if (r < 0) return off == 0 ? -1 : off;
            off += r;
        }
        return off;
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long pad = (BLOCK_SIZE - (size % BLOCK_SIZE)) % BLOCK_SIZE;
        if (pad > 0) skipFully(in, pad);
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) if (b != 0) return false;
        return true;
    }

    private static boolean isSymlink(File f) {
        try { return OsConstants.S_ISLNK(Os.lstat(f.getAbsolutePath()).st_mode); }
        catch (ErrnoException e) { return false; }
    }

    private static String safeCanonical(String path) {
        try { return new File(path).getCanonicalPath(); }
        catch (IOException e) { return null; }
    }

    private static final class TarHeader {
        String name;
        String linkname;
        int mode;
        long size;
        byte type;
    }
}
