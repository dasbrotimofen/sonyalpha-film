package com.filmsim.app;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** File identity helpers shared by capture and development. */
public final class PhotoFiles {
    private PhotoFiles() {}

    public static Map<String, String> snapshot(File dcim) {
        Map<String, String> files = new HashMap<String, String>();
        File[] dirs = dcim.listFiles();
        if (dirs != null) for (File dir : dirs) {
            File[] children = dir.listFiles();
            if (children != null) for (File f : children) {
                String name = f.getName().toUpperCase(java.util.Locale.US);
                if (f.isFile() && (name.endsWith(".JPG") || name.endsWith(".ARW")))
                    files.put(f.getAbsolutePath(), f.length() + ":" + f.lastModified());
            }
        }
        return files;
    }

    /** Only accept one changed photo identity; RAW+JPEG is a single photo, preferring JPEG. */
    public static File captured(Map<String, String> before, Map<String, String> after) throws IOException {
        String identity = null;
        File result = null;
        for (Map.Entry<String, String> entry : after.entrySet()) {
            if (entry.getValue().equals(before.get(entry.getKey()))) continue;
            String path = entry.getKey();
            String stem = path.substring(0, path.lastIndexOf('.'));
            if (identity != null && !identity.equals(stem))
                throw new IOException("More than one new photo found; refusing to guess which shot to develop");
            identity = stem;
            if (result == null || path.toUpperCase(java.util.Locale.US).endsWith(".JPG")) result = new File(path);
        }
        return result;
    }

    public static File copyDirectory(File root, File source) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        return new File(new File(root, source.getParentFile().getName()), dot < 0 ? name : name.substring(0, dot));
    }

    public static File availableCopy(File dir, String[] names, java.util.Set<String> reserved) throws IOException {
        for (String name : names) {
            File candidate = new File(dir, name);
            if (!new File(candidate.getPath() + ".JPG").exists() && !reserved.contains(candidate.getPath()))
                return candidate;
        }
        throw new IOException("No free copy slots for this photo");
    }

    public static void publish(File tmp, File done) throws IOException {
        // One development worker owns these paths. Never remove an existing good copy.
        if (done.exists()) throw new IOException("Destination already exists; existing copy kept");
        if (!tmp.renameTo(done)) throw new IOException("Could not save " + done.getName());
    }

    /** Power-of-two decode size bounded in both dimensions, including portrait images. */
    public static int previewSample(int width, int height, int maxWidth, int maxHeight) {
        int sample = 1;
        while ((width + (long) sample - 1) / sample > maxWidth
                || (height + (long) sample - 1) / sample > maxHeight) sample *= 2;
        return sample;
    }
}
