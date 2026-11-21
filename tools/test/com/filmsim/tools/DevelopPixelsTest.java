package com.filmsim.tools;

import com.filmsim.app.Develop;
import java.security.MessageDigest;

/** Golden pixel checks captured before the test14 grain allocation optimization. */
public final class DevelopPixelsTest {
    private static final String[] EXPECTED = {
        "9038fd764b3b11800ef1881628939e7138d30088a2176195fc71d4ac848324fb",
        "99a2bcfe4ef2f6e0ec28cf58ec589b60b6c87016668d57295e2ae99a500698a5",
        "aba0fc210a7471948e3693e609f650b9cd3d0e99fc639393144953cceca49490",
        "5fb56875b3260e88daae77551ede5c570db5b4644100fabbd8d3c876f1e25442",
        "f060c19663df04b8563c0f819ef05e9eaf344da4aa1f47df50f0ab925f28440b",
        "95ed9859cddf7b81f2023b5e7df1d86faa78f943e6ea0d40176f75e0bc7f66f3",
        "905c8149e28634e875a405474de29bbe4b07ce059e3a7984c3a747f94163c8bc",
        "3ccb460bb4a6343f3501308bccb6c818c6db1a6b99080dac2ca8fa2b58ffc7b5",
        "013785c887654568fcd7fc770bbe01b6390c76fe4813b2785616466dd3b10228",
        "5bf4dc500bb992bf6cdb0475e788b53d0bfe4d1e601bbf9d3c7ddf70a36a6c65",
        "e5e56510d89cbe928125200f9f963151e1614f9cb519dcc2dde00b660d3b05c3",
        "2d2b5e45d7ff6e20fedbddda68350a2de549b138ad462b8a33ec7c8ef15b03a2",
        "17c5bac4c9596af4a4008d931b6a8cc0dc98db201c36af1799687477d198da38",
        "b0d4e44b95b5c7da4f9a4b9c84fd5cb0a2e489384fc102ccaf0d749cf73f4885",
        "fd52494aab0f36e2750454e2747d41dad4e1fa67bb7f9b28f8952971b7874464",
        "ca4cc4090d6f6e849ba02e2d8f5a6c3c657c649a43fa0f006780d14079f6f5cd",
        "8a0d0f269517af98aa37cc9f815c95f0f379f37fadb1707735480d8da77d29bb",
        "943eacd0551c84cf6dd4f8548866fd4001fd867a1ce4a21ea4c597f632ea5cd4",
        "cbc3cedc25e8e279d7a7d7ae83ecb366a3931520c4e5fdd40e4fa2f43823d7ce",
        "b86eef0cd3528392733af123dc2324b998f382a2f8deb533bca77c6db5973377",
        "b7ebc0a48663a88bc2b06112ef3d5ef2e2db64a1b77255e1758c41725eefa5a1",
        "e551539c2074a075a71745dc8e067d49d17b968596b461adcca5ef1b9afcdc1a",
        "87619a25546d8afe9dbc377fe4b5234d77d2508c997f81e65e7ead06aab0d68e",
        "68c03273085bcd6e0d823a9e21cd925f59138efc42e66cb197c307d968274ca6",
        "7549672733d8e8ac4d5368dc19eac25e744e435264f32160d5fabb6ee2e3a939",
        "88bf49c554cfe53d223031db457c9d2c574f85d98e641c143853b17bc028324b",
        "4075906e64a8198b96d9e958c123337934c684dab397eb8de39422871cf00391",
        "900986ce154583295d03f907c0b60aa40ce35307577f860f74b1f79fda4401bb",
        "100aa8829f36d6515b6b77b0addb04a9de634c45294dda2cd8fec8b2a17a2eb2",
        "d3b454a94b163ce7e3cdfb128008bd7e724d17af99ced218dc8171626f4c8d48",
        "cd56ee4f6d5d8586b1263e0d017e5190f55ab4884b58d431d06a0785db4fc86d",
        "74b98abcc0f29d4668ece3434f863736849aa5876a0ec4c27638fef8c1db7609"
    };
    public static void main(String[] args) throws Exception {
        int index = 0;
        for (int grain = 1; grain <= 4; grain++) for (int mode = 0; mode < 8; mode++) {
            final int w = 513, h = 129; // Tile wrap and partial final strip.
            final int[] input = new int[w * h];
            for (int i = 0; i < input.length; i++) input[i] = 0xff000000 | ((i * 7919) & 0xffffff);
            Develop.Options opt = new Develop.Options();
            opt.seed = 0x12345678abcdefL + grain;
            opt.grain = grain;
            opt.monochrome = (mode & 1) != 0;
            opt.halation = (mode & 2) != 0;
            opt.lightLeak = (mode & 4) != 0;
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            new Develop(opt).process(new Develop.Source() {
                public int width() { return w; }
                public int height() { return h; }
                public void read(int y, int count, int[] rgb) {
                    System.arraycopy(input, y * w, rgb, 0, count * w);
                }
                public Develop.Image preview(int factor) {
                    int pw = w / factor, ph = h / factor;
                    int[] px = new int[pw * ph];
                    for (int y = 0; y < ph; y++) for (int x = 0; x < pw; x++)
                        px[y * pw + x] = input[y * factor * w + x * factor];
                    return new Develop.Image(pw, ph, px);
                }
            }, new Develop.Sink() {
                public void writeRows(int[] rgb, int count) {
                    for (int i = 0; i < count * w; i++) {
                        int px = rgb[i];
                        digest.update((byte)(px >>> 24)); digest.update((byte)(px >>> 16));
                        digest.update((byte)(px >>> 8)); digest.update((byte)px);
                    }
                }
                public void finish() { }
            }, null);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
            if (args.length > 0 && "--record".equals(args[0])) System.out.println(hex);
            else if (!hex.toString().equals(EXPECTED[index]))
                throw new AssertionError("Pixels changed: grain=" + grain + ", mode=" + mode);
            index++;
        }
        if (args.length == 0) System.out.println("PASS 32 pre-optimization pixel hashes: grain levels, mono/colour, halation/leaks, tile wrap, final strip");
    }
}
