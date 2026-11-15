package com.filmsim.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/** Rebuild portable EXIF; never retain opaque MakerNotes or stale preview offsets. */
public final class CopyExif {
    private CopyExif() {}
    private static final class Tag {
        int type, count;
        byte[] data;
        Tag(int type, int count, byte[] data) { this.type = type; this.count = count; this.data = data; }
    }
    private static int u16(byte[] b, int p) { return (b[p] & 255) | ((b[p + 1] & 255) << 8); }
    private static int u32(byte[] b, int p) { return u16(b, p) | (u16(b, p + 2) << 16); }
    private static void put16(byte[] b, int p, int v) { b[p] = (byte)v; b[p + 1] = (byte)(v >> 8); }
    private static void put32(byte[] b, int p, int v) { put16(b, p, v); put16(b, p + 2, v >> 16); }
    private static Tag number(int v) { byte[] b = new byte[4]; put32(b, 0, v); return new Tag(4, 1, b); }
    private static Tag shortTag(int v) { byte[] b = new byte[2]; put16(b, 0, v); return new Tag(3, 1, b); }
    private static Tag ascii(String v) throws IOException { byte[] b = (v + "\0").getBytes("US-ASCII"); return new Tag(2, b.length, b); }

    private static int size(int type) {
        switch (type) {
            case 1: case 2: case 7: return 1;
            case 3: return 2;
            case 4: case 9: return 4;
            case 5: case 10: return 8;
            default: return 0;
        }
    }
    private static int read(byte[] b, int p, int bytes, boolean little) throws IOException {
        if (p < 0 || p > b.length - bytes) throw new IOException("EXIF offset outside segment");
        int n = 0;
        for (int i = 0; i < bytes; i++) n |= (b[p + (little ? i : bytes - i - 1)] & 255) << (8 * i);
        return n;
    }
    private static Map<Integer, Tag> parse(byte[] b, int offset, boolean little) throws IOException {
        Map<Integer, Tag> tags = new TreeMap<Integer, Tag>();
        int count = read(b, offset, 2, little);
        if (count > 512) throw new IOException("Too many EXIF tags");
        for (int i = 0; i < count; i++) {
            int p = offset + 2 + i * 12;
            int id = read(b, p, 2, little), type = read(b, p + 2, 2, little);
            int n = read(b, p + 4, 4, little), unit = size(type);
            if (unit == 0 || n < 0 || n > 65535 / unit) continue;
            int length = unit * n;
            int start = length <= 4 ? p + 8 : read(b, p + 8, 4, little);
            if (start < 0 || start > b.length - length) throw new IOException("Invalid EXIF value");
            byte[] data = new byte[length];
            System.arraycopy(b, start, data, 0, length);
            int swap = (type == 5 || type == 10) ? 4 : unit;
            if (!little && swap > 1) for (int j = 0; j < length; j += swap)
                for (int k = 0; k < swap / 2; k++) {
                    byte t = data[j + k]; data[j + k] = data[j + swap - k - 1]; data[j + swap - k - 1] = t;
                }
            tags.put(id, new Tag(type, n, data));
        }
        return tags;
    }
    private static Map<Integer, Tag> child(byte[] b, Map<Integer, Tag> parent, int key, boolean little) throws IOException {
        Tag t = parent.get(key);
        return t != null && t.type == 4 && t.count == 1 ? parse(b, u32(t.data, 0), little) : new TreeMap<Integer, Tag>();
    }
    private static void keep(Map<Integer, Tag> tags, int... allowed) {
        java.util.Set<Integer> keys = new java.util.HashSet<Integer>();
        for (int key : allowed) keys.add(key);
        tags.keySet().retainAll(keys);
    }
    private static int tableSize(Map<Integer, Tag> tags) { return 6 + 12 * tags.size(); }
    private static int writeTable(byte[] out, int offset, Map<Integer, Tag> tags, int next, int data) {
        put16(out, offset, tags.size());
        int p = offset + 2;
        for (Map.Entry<Integer, Tag> entry : tags.entrySet()) {
            Tag t = entry.getValue();
            put16(out, p, entry.getKey()); put16(out, p + 2, t.type); put32(out, p + 4, t.count);
            if (t.data.length <= 4) System.arraycopy(t.data, 0, out, p + 8, t.data.length);
            else {
                put32(out, p + 8, data); System.arraycopy(t.data, 0, out, data, t.data.length);
                data += (t.data.length + 1) & ~1;
            }
            p += 12;
        }
        put32(out, p, next);
        return data;
    }

    public static byte[] build(byte[] original, int width, int height, byte[] thumbnail) throws IOException {
        if (width <= 0 || height <= 0 || thumbnail == null || thumbnail.length > 30000)
            throw new IOException("Invalid copy dimensions or thumbnail");
        Map<Integer, Tag> main = new TreeMap<Integer, Tag>();
        Map<Integer, Tag> exif = new TreeMap<Integer, Tag>();
        Map<Integer, Tag> gps = new TreeMap<Integer, Tag>();
        if (original != null) {
            if (original.length < 18 || original[4] != 'E' || original[5] != 'x') throw new IOException("Invalid EXIF segment");
            byte[] tiff = new byte[original.length - 10];
            System.arraycopy(original, 10, tiff, 0, tiff.length);
            boolean little = tiff[0] == 'I' && tiff[1] == 'I';
            if (!little && !(tiff[0] == 'M' && tiff[1] == 'M')) throw new IOException("Invalid EXIF byte order");
            if (read(tiff, 2, 2, little) != 42) throw new IOException("Invalid TIFF header");
            main = parse(tiff, read(tiff, 4, 4, little), little);
            exif = child(tiff, main, 0x8769, little); gps = child(tiff, main, 0x8825, little);
        }
        keep(main, 0x010e, 0x010f, 0x0110, 0x0112, 0x011a, 0x011b, 0x0128, 0x0132, 0x013b, 0x8298);
        // Preserve standard capture information, not camera-private image/preview structures.
        keep(exif, 0x829a, 0x829d, 0x8822, 0x8827, 0x8830, 0x8831, 0x8832, 0x8833,
                0x9003, 0x9004, 0x9010, 0x9011, 0x9012, 0x9201, 0x9202, 0x9203, 0x9204,
                0x9205, 0x9206, 0x9207, 0x9208, 0x9209, 0x920a, 0x9286, 0x9290, 0x9291,
                0x9292, 0xa217, 0xa300, 0xa301, 0xa401,
                0xa402, 0xa403, 0xa404, 0xa405, 0xa406, 0xa407, 0xa408, 0xa409,
                0xa40a, 0xa40c, 0xa430, 0xa431, 0xa432, 0xa433, 0xa434, 0xa435);
        gps.keySet().retainAll(java.util.Arrays.asList(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31));
        main.put(0x0131, ascii("FilmSim"));
        main.put(0x0213, shortTag(1));
        if (!main.containsKey(0x0112)) main.put(0x0112, shortTag(1));
        exif.put(0x9000, new Tag(7, 4, new byte[]{'0','2','3','0'}));
        exif.put(0xa000, new Tag(7, 4, new byte[]{'0','1','0','0'}));
        exif.put(0x9101, new Tag(7, 4, new byte[]{1,2,3,0}));
        exif.put(0xa001, shortTag(1));
        exif.put(0xa002, number(width)); exif.put(0xa003, number(height));
        main.put(0x8769, number(0));
        if (!gps.isEmpty()) main.put(0x8825, number(0));
        Map<Integer, Tag> thumb = new TreeMap<Integer, Tag>();
        thumb.put(0x0103, shortTag(6)); thumb.put(0x0112, main.get(0x0112));
        byte[] resolution = new byte[8]; put32(resolution, 0, 72); put32(resolution, 4, 1);
        thumb.put(0x011a, new Tag(5, 1, resolution)); thumb.put(0x011b, new Tag(5, 1, resolution));
        thumb.put(0x0128, shortTag(2));
        thumb.put(0x0201, number(0)); thumb.put(0x0202, number(thumbnail.length));
        int e = 8 + tableSize(main), g = e + tableSize(exif), t = g + (gps.isEmpty() ? 0 : tableSize(gps));
        int data = t + tableSize(thumb);
        main.put(0x8769, number(e)); if (!gps.isEmpty()) main.put(0x8825, number(g));
        byte[] out = new byte[65527]; out[0] = 'I'; out[1] = 'I'; put16(out, 2, 42); put32(out, 4, 8);
        try {
            data = writeTable(out, 8, main, t, data);
            data = writeTable(out, e, exif, 0, data);
            if (!gps.isEmpty()) data = writeTable(out, g, gps, 0, data);
            // Thumbnail IFD has two out-of-line rationals before its JPEG bytes.
            thumb.put(0x0201, number(data + 16));
            data = writeTable(out, t, thumb, 0, data);
            System.arraycopy(thumbnail, 0, out, data, thumbnail.length);
        } catch (IndexOutOfBoundsException error) { throw new IOException("Copy EXIF too large"); }
        int length = data + thumbnail.length + 8;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(255); result.write(225); result.write(length >> 8); result.write(length);
        result.write(new byte[]{'E','x','i','f',0,0}); result.write(out, 0, data + thumbnail.length);
        return result.toByteArray();
    }
}
