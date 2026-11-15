package com.filmsim.app;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

public final class PhotoFilesPublicationTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("filmsim-publication-").toFile();
        try {
            byte[] data = new byte[200003];
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31);
            File tmp = new File(root, "DSC04881.TMP") {
                @Override public boolean renameTo(File dest) {
                    throw new AssertionError("Final publication must not rename TMP");
                }
            };
            File done = new File(root, "DSC04881.JPG");
            Files.write(tmp.toPath(), data);
            PhotoFiles.publish(tmp, done);
            check(Arrays.equals(data, Files.readAllBytes(done.toPath())), "all bytes copied across buffer boundaries");
            check(!tmp.exists(), "temporary removed after success");
            try {
                PhotoFiles.publishStream(new ByteArrayInputStream(new byte[]{1}), done);
                throw new AssertionError("existing destination accepted");
            } catch (IOException expected) { }
            check(Arrays.equals(data, Files.readAllBytes(done.toPath())), "existing destination untouched");

            final File partial = new File(root, "DSC04882.JPG");
            InputStream failing = new InputStream() {
                int calls;
                @Override public int read() throws IOException { throw new IOException("injected failure"); }
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    if (calls++ == 0) {
                        Arrays.fill(b, off, off + len, (byte) 42);
                        return len;
                    }
                    check(partial.length() > 0, "failure occurs after actual partial write");
                    throw new IOException("injected failure");
                }
            };
            try {
                PhotoFiles.publishStream(failing, partial);
                throw new AssertionError("read failure ignored");
            } catch (IOException expected) {
                check("injected failure".equals(expected.getMessage()), "original failure retained");
            }
            check(!partial.exists(), "partial final JPEG removed after failure");
            try {
                PhotoFiles.publish(new File(root, "missing.TMP"), partial);
                throw new AssertionError("missing source accepted");
            } catch (IOException expected) { }
            check(!partial.exists(), "missing source leaves no final JPEG");
            PhotoFiles.publishStream(new ByteArrayInputStream(data), partial);
            check(Arrays.equals(data, Files.readAllBytes(partial.toPath())), "retry after failure succeeds");
            System.out.println("PASS direct JPEG publication: exact bytes, no rename, no overwrite, partial cleanup and retry");
        } finally {
            File[] files = root.listFiles();
            if (files != null) for (File file : files) Files.delete(file.toPath());
            Files.delete(root.toPath());
        }
    }
}
