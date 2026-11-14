package com.filmsim.tools;

import com.filmsim.app.Develop;
import java.io.IOException;

/** Regression: native Bitmap.setPixels needs opaque ARGB, not zero-alpha RGB. */
public class DevelopAlphaTest {
    private static final int W = 128, H = 130; // Includes a partial final strip.
    private static final int[] INPUT = new int[W * H];

    public static void main(String[] args) throws IOException {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            INPUT[y * W + x] = x >= 40 && x < 88 && y >= 40 && y < 88
                    ? 0xffffffff : 0xff604830;
        }
        int failures = 0;
        for (String name : new String[] {"unchanged", "grain", "mono-grain", "halation", "leak", "combined"}) {
            Develop.Options opt = new Develop.Options();
            opt.seed = 1234;
            opt.grain = name.contains("grain") || name.equals("combined") ? 3 : 0;
            opt.monochrome = name.equals("mono-grain");
            opt.halation = name.equals("halation") || name.equals("combined");
            opt.lightLeak = name.equals("leak") || name.equals("combined");
            final int[] result = new int[INPUT.length];
            new Develop(opt).process(new Develop.Source() {
                public int width() { return W; }
                public int height() { return H; }
                public void read(int y, int count, int[] rgb) {
                    System.arraycopy(INPUT, y * W, rgb, 0, count * W);
                }
                public Develop.Image preview(int factor) {
                    int w = W / factor, h = H / factor;
                    int[] px = new int[w * h];
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                        px[y * w + x] = INPUT[(y * factor) * W + x * factor];
                    return new Develop.Image(w, h, px);
                }
            }, new Develop.Sink() {
                private int offset;
                public void writeRows(int[] rgb, int count) {
                    System.arraycopy(rgb, 0, result, offset, count * W);
                    offset += count * W;
                }
                public void finish() {
                    if (offset != result.length) throw new AssertionError("Missing output rows");
                }
            }, null);
            int transparent = 0, changed = 0;
            for (int i = 0; i < result.length; i++) {
                if ((result[i] >>> 24) != 255) transparent++;
                if ((result[i] & 0xffffff) != (INPUT[i] & 0xffffff)) changed++;
            }
            boolean valid = transparent == 0 && (name.equals("unchanged") ? changed == 0 : changed > 0);
            System.out.println((valid ? "PASS " : "FAIL ") + name
                    + ": non-opaque pixels=" + transparent + ", changed RGB pixels=" + changed);
            if (!valid) failures++;
        }
        if (failures != 0) throw new AssertionError(failures + " alpha regression cases failed");
    }
}
