package com.filmsim.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;

public final class PhotoFilesListingTest {
    private static File unlisted(File real) {
        return new File(real.getPath()) {
            @Override public File[] listFiles() { return null; }
        };
    }
    private static void check(boolean value, String text) {
        if (!value) throw new AssertionError(text);
    }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("filmsim-listing-").toFile();
        try {
            File originalDir = new File(root, "DCIM/100MSDCF");
            check(originalDir.mkdirs(), "original folder");
            File source = new File(originalDir, "DSC04875.JPG");
            Files.write(source.toPath(), new byte[]{1});
            File output = PhotoFiles.copyDirectory(root, source);
            File sonyFolder = unlisted(output);
            check(!PhotoFiles.collectNumbers(sonyFolder, new HashSet<Integer>()),
                    "owned empty null-listing folder uses direct probes");
            check(!PhotoFiles.candidateExists(sonyFolder, "DSC04876"), "empty candidate is available");
            for (String suffix : new String[]{"JPG", "ARW", "TMP", "MET"}) {
                File occupied = new File(output, "DSC04876." + suffix);
                Files.write(occupied.toPath(), new byte[]{1});
                check(PhotoFiles.candidateExists(sonyFolder, "DSC04876"), "probe detects " + suffix);
                Files.delete(occupied.toPath());
            }
            File reserved = PhotoFiles.allocateCopy(root, source);
            check(PhotoFiles.candidateExists(sonyFolder, reserved.getName()), "probe detects persistent reservation");
            try {
                PhotoFiles.collectNumbers(unlisted(originalDir), new HashSet<Integer>());
                throw new AssertionError("unreadable original folder accepted");
            } catch (IOException expected) { }
            File foreign = new File(root, "DCIM/102FILMS");
            check(foreign.mkdir(), "unowned FILMS folder");
            try {
                PhotoFiles.collectNumbers(unlisted(foreign), new HashSet<Integer>());
                throw new AssertionError("unowned folder accepted just for its name");
            } catch (IOException expected) { }
            System.out.println("PASS Sony null directory listings: ownership, direct collision probes, reservations, unreadable originals rejected");
        } finally { remove(root); }
    }
    private static void remove(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        Files.delete(file.toPath());
    }
}
