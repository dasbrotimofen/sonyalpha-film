package com.filmsim.app;

import java.util.ArrayList;
import java.util.List;

/** Session controls with capability checks, held-button state, and restoration. */
public final class ShootingControls {
    public static final int METER = 0, AREA = 1, AF = 2, PEAK = 3, COLOR = 4, FOCUS = 5;
    private static final String[] NAMES = {"Metering", "Focus area", "AF mode", "Focus peaking", "Peaking colour", "Focus mode"};
    public interface Backend {
        List<String> supported(int setting) throws Exception;
        String get(int setting) throws Exception;
        void set(int setting, String value) throws Exception;
        boolean canToggleFocus() throws Exception;
        void toggleFocus(boolean held) throws Exception;
        boolean manualFocus() throws Exception;
        void magnify() throws Exception;
        void stopMagnification() throws Exception;
        List<String> exposureLocks() throws Exception;
        String exposureLock() throws Exception;
        void exposureLock(String value) throws Exception;
    }
    public interface Reporter { void error(String message); }
    private final Reporter reporter;
    private Backend backend;
    private final String[] original = new String[NAMES.length], selected = new String[NAMES.length];
    private final List<List<String>> choices = new ArrayList<List<String>>();
    private final boolean[] changed = new boolean[NAMES.length];
    private boolean focusHeld, aeHeld;
    private boolean focusToggled, magnified, peakActive, focusButtonPressed;
    private volatile int focusGeneration;
    private String priorLock;

    public ShootingControls(Reporter reporter) {
        this.reporter = reporter;
        for (int i = 0; i < NAMES.length; i++) choices.add(new ArrayList<String>());
    }

    public void open(Backend backend) {
        this.backend = backend;
        focusHeld = aeHeld = false;
        focusToggled = magnified = peakActive = focusButtonPressed = false;
        focusGeneration++;
        for (int i = 0; i < NAMES.length; i++) {
            choices.get(i).clear(); original[i] = null; changed[i] = false;
            try {
                original[i] = backend.get(i);
                List<String> values = backend.supported(i);
                if (original[i] != null && values != null) choices.get(i).addAll(values);
                if (i == PEAK && choices.get(i).contains("off")) {
                    if (!choices.get(i).contains(selected[i]) || "off".equals(selected[i])) {
                        selected[i] = "off";
                        for (String level : new String[] {"mid", "medium", "low", "on", "high"})
                            if (choices.get(i).contains(level)) { selected[i] = level; break; }
                    }
                    backend.set(i, "off");
                    changed[i] = !"off".equals(original[i]);
                } else if (i == PEAK) {
                    choices.get(i).clear(); // Hold-only peaking requires a known off state.
                } else if (choices.get(i).contains(selected[i])) {
                    backend.set(i, selected[i]);
                    changed[i] = !selected[i].equals(original[i]);
                } else selected[i] = original[i];
            } catch (Exception | LinkageError e) { choices.get(i).clear(); reporter.error(NAMES[i] + " unavailable: " + e); }
        }
    }

    public String value(int setting) {
        return choices.get(setting).isEmpty() ? "Unavailable" : label(selected[setting]);
    }

    public boolean manualFocusSelected() {
        return "manual".equals(selected[FOCUS]) || "dmf".equals(selected[FOCUS]);
    }

    public void step(int setting, int direction) {
        List<String> values = choices.get(setting);
        if (backend == null || values.isEmpty()) { reporter.error("This control is unavailable in the current camera mode"); return; }
        if (focusHeld || aeHeld) { reporter.error("Release the AF/MF or AEL button first"); return; }
        int index = values.indexOf(selected[setting]);
        String next = values.get((Math.max(0, index) + direction + values.size()) % values.size());
        if (setting == PEAK) { selected[setting] = next; return; }
        try {
            backend.set(setting, next);
            changed[setting] = true;
            selected[setting] = backend.get(setting);
        } catch (Exception | LinkageError e) { reporter.error("Could not change " + NAMES[setting] + ": " + e); }
    }

    public void holdFocus() {
        if (backend == null || focusButtonPressed) return;
        focusButtonPressed = true;
        try {
            focusToggled = false;
            if (!backend.manualFocus()) {
                if (!backend.canToggleFocus()) { reporter.error("AF/MF switching is unavailable"); return; }
                backend.toggleFocus(true);
                focusToggled = true;
            }
            focusHeld = true;
            focusGeneration++;
            if (!choices.get(PEAK).isEmpty()) {
                peakActive = true;
                backend.set(PEAK, selected[PEAK]);
                changed[PEAK] = true;
            }
        } catch (Exception | LinkageError e) { reporter.error("AF/MF: " + e); }
    }

    public int focusGeneration() { return focusGeneration; }
    public boolean focusHeld() { return focusHeld; }

    public void focusMoved(int generation) {
        if (backend == null || !focusHeld || magnified || generation != focusGeneration) return;
        try { backend.magnify(); magnified = true; }
        catch (Exception | LinkageError e) { reporter.error("Focus magnification unavailable: " + e); }
    }

    /** Half shutter ends focus assist without releasing an independently held AEL. */
    public void releaseFocus() {
        focusGeneration++;
        if (backend == null || (!focusHeld && !magnified && !focusToggled && !peakActive)) return;
        focusHeld = false; // Invalidate queued movement callbacks before restoring camera state.
        try { backend.stopMagnification(); magnified = false; }
        catch (Exception | LinkageError e) { reporter.error("Stop focus magnification: " + e); }
        if (peakActive) try { backend.set(PEAK, "off"); peakActive = false; }
        catch (Exception | LinkageError e) { reporter.error("Stop focus peaking: " + e); }
        if (focusToggled) try { backend.toggleFocus(false); focusToggled = false; }
        catch (Exception | LinkageError e) { reporter.error("Release AF/MF: " + e); }
    }

    public void holdExposure() {
        if (backend == null || aeHeld) return;
        try {
            List<String> locks = backend.exposureLocks();
            priorLock = backend.exposureLock();
            if (priorLock == null || locks == null || !locks.contains("locked") || !locks.contains(priorLock)) {
                reporter.error("Exposure lock is unavailable"); return;
            }
            backend.exposureLock("locked"); aeHeld = true;
        } catch (Exception | LinkageError e) { reporter.error("AEL: " + e); }
    }

    public void release() {
        focusButtonPressed = false;
        if (backend == null) return;
        releaseFocus();
        if (aeHeld) try { backend.exposureLock(priorLock); aeHeld = false; }
        catch (Exception | LinkageError e) { reporter.error("Release AEL: " + e); }
    }

    public String indicator() { return (aeHeld ? "  AEL" : "") + (focusHeld ? "  MF ASSIST" : "")
            + (magnified ? "  FOCUS ZOOM" : ""); }

    public void close() {
        release();
        if (backend != null) for (int i = 0; i < NAMES.length; i++) {
            if (changed[i] && original[i] != null) try { backend.set(i, original[i]); }
            catch (Exception | LinkageError e) { reporter.error("Could not restore " + NAMES[i] + ": " + e); }
        }
        backend = null;
    }

    private static String label(String value) {
        if (value == null) return "Unavailable";
        if (value.equals("manual")) return "Manual (MF)";
        if (value.equals("auto")) return "Auto (AF)";
        if (value.startsWith("af-")) return value.toUpperCase(java.util.Locale.US);
        if (value.equals("flex-spot")) return "Flexible spot";
        if (value.equals("multi")) return "Multi";
        return value.replace('-', ' ');
    }
}
