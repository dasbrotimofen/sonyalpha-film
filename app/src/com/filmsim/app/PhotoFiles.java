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
                if (f.isFile() && !isDevelopedCopy(name) && !isMappedCopy(f) && (name.endsWith(".JPG") || name.endsWith(".ARW")))
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

    public static File copyDirectory(File root, File source) throws IOException {
        File parent = source.getAbsoluteFile().getParentFile();
        if (parent == null || parent.getParentFile() == null
                || !"DCIM".equalsIgnoreCase(parent.getParentFile().getName())
                || !parent.getName().matches("[1-9][0-9]{2}[A-Za-z0-9_]{5}"))
            throw new IOException("Source must be in a camera DCIM folder");
        File dcim = parent.getParentFile();
        File[] dirs = dcim.listFiles();
        if (dirs == null) throw new IOException("Cannot read DCIM");
        int highest = 99;
        for (File dir : dirs) {
            if (dir.getName().matches("[1-9][0-9]{2}.*"))
                highest = Math.max(highest, Integer.parseInt(dir.getName().substring(0, 3)));
            if (dir.getName().matches("[1-9][0-9]{2}FILMS")
                    && parent.getName().equals(readOwner(dir))) return dir;
        }
        if (highest >= 999) throw new IOException("No free DCIM folder numbers");
        File dir = new File(dcim, (highest + 1) + "FILMS");
        if (!dir.mkdir()) throw new IOException("Cannot create FilmSim DCIM folder");
        writeMarker(new File(indexDirectory(dir), "OWNER.TXT"), parent.getName());
        return dir;
    }

    private static String readMarker(File file) throws IOException {
        if (!file.isFile()) return null;
        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(file), "UTF-8"));
        try { return in.readLine(); } finally { in.close(); }
    }

    /** Keep camera DCIM folders image-only; Sony storage may reject dotfiles/long names. */
    private static File indexDirectory(File outputDirectory) {
        File card = outputDirectory.getAbsoluteFile().getParentFile().getParentFile();
        return new File(new File(new File(card, "FILMSIM"), "INDEX"), outputDirectory.getName());
    }

    private static File sourceMarker(File photo) {
        String name = photo.getName();
        int dot = name.lastIndexOf('.');
        return new File(indexDirectory(photo.getParentFile()), (dot < 0 ? name : name.substring(0, dot)) + ".SRC");
    }

    private static String readOwner(File dir) throws IOException {
        String owner = readMarker(new File(indexDirectory(dir), "OWNER.TXT"));
        return owner != null ? owner : readMarker(new File(dir, ".filmsim"));
    }

    private static String readSource(File photo) throws IOException {
        String source = readMarker(sourceMarker(photo));
        return source != null ? source : readMarker(new File(photo.getParentFile(), "." + photo.getName() + ".source"));
    }

    private static void writeMarker(File file, String text) throws IOException {
        try {
            File parent = file.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create index directory");
            if (!file.createNewFile()) throw new IOException("Copy reservation already exists");
            java.io.Writer out = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), "UTF-8");
            try { out.write(text); out.write("\n"); } finally { out.close(); }
        } catch (IOException error) {
            throw new IOException("Writing FilmSim index " + file.getPath() + ": " + error.getMessage(), error);
        }
    }

    private static String sourceKey(File photo) {
        String name = photo.getName();
        return photo.getParentFile().getName() + "/" + name.substring(0, name.lastIndexOf('.'));
    }

    public static boolean isMappedCopy(File photo) {
        return sourceMarker(photo).isFile()
                || new File(photo.getParentFile(), "." + photo.getName() + ".source").isFile();
    }

    /** Persistent reservations also link versions to their source across app restarts. */
    public static synchronized File allocateCopy(File root, File source) throws IOException {
        File dir = copyDirectory(root, source);
        String stem = source.getName().replaceFirst("\\.[^.]+$", "");
        String digits = stem.replaceAll("[^0-9]", "");
        int preferred = 1;
        if (digits.length() > 0) preferred = Integer.parseInt(digits.substring(Math.max(0, digits.length() - 4)));
        if (preferred == 0) preferred = 1;
        // Successful recovery tests used fresh numbers, not the original's number in another folder.
        java.util.Set<Integer> used = new java.util.HashSet<Integer>();
        File[] folders = dir.getParentFile().listFiles();
        if (folders == null) throw new IOException("Cannot read DCIM numbers");
        java.util.List<File> unlisted = new java.util.ArrayList<File>();
        for (File folder : folders) if (!collectNumbers(folder, used)) unlisted.add(folder);
        File index = indexDirectory(dir).getParentFile();
        File[] indexes = index.listFiles();
        if (indexes != null) for (File folder : indexes) collectNumbers(folder, used);
        used.add(preferred);
        for (int i = 0; i < 9999; i++) {
            int number = (preferred - 1 + i) % 9999 + 1;
            if (used.contains(number)) continue;
            String name = String.format(java.util.Locale.US, "DSC0%04d", number);
            boolean occupied = false;
            for (File folder : unlisted) if (candidateExists(folder, name)) { occupied = true; break; }
            if (occupied) continue;
            File jpg = new File(dir, name + ".JPG");
            File marker = sourceMarker(jpg);
            if (jpg.exists() || new File(dir, name + ".ARW").exists() || isMappedCopy(jpg)
                    || new File(dir, name + ".TMP").exists()) continue;
            writeMarker(marker, sourceKey(source));
            return new File(dir, name);
        }
        throw new IOException("FilmSim folder is full; no free copy numbers");
    }

    /** False means a known FilmSim folder needs direct candidate probes instead of enumeration. */
    static boolean collectNumbers(File directory, java.util.Set<Integer> numbers) throws IOException {
        if (!directory.isDirectory()) return true;
        File[] files = directory.listFiles();
        if (files == null) {
            // Sony can report null for newly created empty image folders. Ownership must be proven;
            // never silently skip an unreadable original-photo directory.
            if (directory.getName().matches("[1-9][0-9]{2}FILMS") && readOwner(directory) != null) return false;
            throw new IOException("Cannot read image numbers in " + directory.getPath());
        }
        for (File file : files) {
            String name = file.getName().toUpperCase(java.util.Locale.US);
            if (name.matches("[A-Z0-9_]{4}[0-9]{4}\\.(JPG|ARW|SRC|TMP|MET)"))
                numbers.add(Integer.parseInt(name.substring(4, 8)));
        }
        return true;
    }

    static boolean candidateExists(File directory, String name) {
        for (String extension : new String[]{".JPG", ".ARW", ".TMP", ".MET"})
            if (new File(directory, name + extension).exists()) return true;
        return isMappedCopy(new File(directory, name + ".JPG"));
    }

    public static File newestMappedCopy(File source) {
        File dcim = source.getParentFile().getParentFile();
        File[] dirs = dcim.listFiles();
        File best = null;
        if (dirs != null) for (File dir : dirs) {
            if (!dir.getName().matches("[1-9][0-9]{2}FILMS")) continue;
            File[] files = dir.listFiles();
            if (files != null) for (File copy : files) {
                if (!copy.getName().matches("DSC0[0-9]{4}\\.JPG")) continue;
                try {
                    if (sourceKey(source).equals(readSource(copy))
                            && (best == null || copy.lastModified() >= best.lastModified())) best = copy;
                } catch (IOException ignored) { }
            }
        }
        return best;
    }

    /** Reserved FilmSim copy names, excluded from detection of new camera captures. */
    public static boolean isDevelopedCopy(String name) {
        return name.toUpperCase(java.util.Locale.US).matches("D[V23456789ABCDEFGHJKLMNPQRSTUWXYZ][0-9]{6}\\.JPG");
    }

    /** Eight-character basename; old seven-character names remain readable in DEV. */
    public static String dcimCopyName(String photo, char slot) {
        StringBuilder digits = new StringBuilder();
        for (char c : photo.toCharArray()) {
            if (c == '.') break;
            if (c >= '0' && c <= '9') digits.append(c);
        }
        String number = "000000" + digits;
        return "D" + slot + number.substring(number.length() - 6);
    }

    /** Read compatibility for copies produced by fix2; never used for new output. */
    public static File legacyCopyDirectory(File root, File source) {
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
        java.io.InputStream in = new java.io.FileInputStream(tmp);
        try {
            publishStream(in, done);
        } finally {
            in.close();
        }
        // Publication succeeded. A leftover temporary file is preferable to losing the JPEG.
        tmp.delete();
    }

    /** Create under the final extension, rather than renaming a TMP on Sony's filesystem. */
    static void publishStream(java.io.InputStream in, File done) throws IOException {
        // Atomically claim a new path; never truncate an existing original or developed copy.
        if (!done.createNewFile()) throw new IOException("Destination already exists; existing copy kept");
        boolean complete = false;
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(done);
            try {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (count > 0) out.write(buffer, 0, count);
                }
                out.flush();
                out.getFD().sync();
            } finally {
                out.close();
            }
            complete = true;
        } finally {
            if (!complete && !done.delete() && done.exists())
                throw new IOException("Save failed and partial JPEG could not be removed: " + done.getPath());
        }
    }

    /** Power-of-two decode size bounded in both dimensions, including portrait images. */
    public static int previewSample(int width, int height, int maxWidth, int maxHeight) {
        int sample = 1;
        while ((width + (long) sample - 1) / sample > maxWidth
                || (height + (long) sample - 1) / sample > maxHeight) sample *= 2;
        return sample;
    }
}
