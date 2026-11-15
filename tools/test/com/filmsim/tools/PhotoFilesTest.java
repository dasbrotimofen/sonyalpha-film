package com.filmsim.tools;

import com.filmsim.app.PhotoFiles;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PhotoFilesTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("filmsim-files-").toFile();
        try {
            File dcim = new File(root, "DCIM"), folder = new File(dcim, "100MSDCF");
            check(folder.mkdirs(), "test directory");
            File old = new File(folder, "DSC00001.JPG");
            Files.write(old.toPath(), new byte[] {1});
            Map<String, String> before = PhotoFiles.snapshot(dcim);
            check(PhotoFiles.captured(before, before) == null, "no new file must not select old image");
            File raw = new File(folder, "DSC00002.ARW"), jpg = new File(folder, "DSC00002.JPG");
            Files.write(raw.toPath(), new byte[] {1, 2});
            check(PhotoFiles.captured(before, PhotoFiles.snapshot(dcim)).equals(raw), "RAW-only capture");
            Files.write(jpg.toPath(), new byte[] {1, 2});
            check(PhotoFiles.captured(before, PhotoFiles.snapshot(dcim)).equals(jpg), "RAW+JPEG chooses JPEG");
            Map<String, String> two = new HashMap<String, String>(PhotoFiles.snapshot(dcim));
            two.put(new File(folder, "DSC00003.JPG").getAbsolutePath(), "2:1");
            try {
                PhotoFiles.captured(before, two);
                throw new AssertionError("ambiguous shots must not be guessed");
            } catch (IOException expected) {}
            File a = PhotoFiles.copyDirectory(root, jpg);
            File otherSource = new File(dcim, "102MSDCF/DSC00002.JPG");
            check(otherSource.getParentFile().mkdirs(), "second source folder");
            File b = PhotoFiles.copyDirectory(root, otherSource);
            check(a.getName().equals("101FILMS") && b.getName().equals("103FILMS"), "allocate distinct unused numbered folders");
            check(a.equals(PhotoFiles.copyDirectory(root, raw)), "RAW+JPEG share output folder");
            File copy = PhotoFiles.allocateCopy(root, jpg);
            check(copy.getName().equals("DSC00002") && copy.getParentFile().equals(a), "retain source number in FILMS folder");
            File version = PhotoFiles.allocateCopy(root, raw);
            check(version.getName().equals("DSC00003"), "queued reservation cannot collide");
            Map<String, String> captured = PhotoFiles.snapshot(dcim);
            File developed = new File(copy + ".JPG");
            Files.write(developed.toPath(), new byte[] {1,2,3});
            check(PhotoFiles.snapshot(dcim).equals(captured), "developed DSC output excluded from capture detection");
            check(PhotoFiles.newestMappedCopy(jpg).equals(developed), "persistent source lookup");
            check(PhotoFiles.newestMappedCopy(raw).equals(developed), "RAW and JPEG resolve same copy");
            check(PhotoFiles.newestMappedCopy(otherSource) == null, "same-number different folder not confused");
            Files.write(new File(a, "DSC00004.ARW").toPath(), new byte[] {1});
            check(PhotoFiles.allocateCopy(root, jpg).getName().equals("DSC00005"), "RAW originals in output folder are never reused");
            check(PhotoFiles.snapshot(dcim).size() == captured.size() + 1, "unmapped camera files remain detectable even in FILMS");
            try {
                PhotoFiles.copyDirectory(root, new File(root, "outside.JPG"));
                throw new AssertionError("outside DCIM");
            } catch (IOException expected) {}
            check(new File(dcim, "999OTHER").mkdirs(), "occupied last number");
            try {
                PhotoFiles.copyDirectory(root, new File(dcim, "104MSDCF/DSC00001.JPG"));
                throw new AssertionError("folder exhaustion must fail");
            } catch (IOException expected) {}
            Set<String> reserved = new HashSet<String>();
            String[] slots = {"DV000002", "D2000002", "D3000002"};
            Files.write(new File(a, slots[0] + ".JPG").toPath(), new byte[] {42});
            reserved.add(new File(a, slots[1]).getPath());
            check(PhotoFiles.availableCopy(a, slots, reserved).getName().equals(slots[2]), "skip disk and queued copies");
            check(PhotoFiles.availableCopy(b, slots, reserved).getName().equals(slots[0]),
                    "same-number originals in different folders have separate copy slots");
            reserved.add(new File(a, slots[2]).getPath());
            try {
                PhotoFiles.availableCopy(a, slots, reserved);
                throw new AssertionError("full slots must fail");
            } catch (IOException expected) {}
            File tmp = new File(a, "test.TMP"), done = new File(a, slots[0] + ".JPG");
            Files.write(tmp.toPath(), new byte[] {99});
            try {
                PhotoFiles.publish(tmp, done);
                throw new AssertionError("must not overwrite");
            } catch (IOException expected) {}
            check(Files.readAllBytes(done.toPath())[0] == 42 && tmp.exists(), "existing copy preserved");
            File fresh = new File(a, "fresh.JPG");
            PhotoFiles.publish(tmp, fresh);
            check(fresh.exists() && !tmp.exists(), "successful publication");
            for (int[] size : new int[][] {{1500,1000}, {6000,4000}, {844,1500}, {64,64}}) {
                int sample = PhotoFiles.previewSample(size[0], size[1], 640, 480);
                check((size[0] + sample - 1) / sample <= 640 && (size[1] + sample - 1) / sample <= 480,
                        "preview fits both display dimensions");
            }
            System.out.println("PASS capture identity, RAW pairing, ambiguity rejection, DCIM output/reservation/exhaustion/capture exclusion, safe publication, preview bounds");
        } finally { remove(root); }
    }

    private static void remove(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        Files.delete(file.toPath());
    }
}
