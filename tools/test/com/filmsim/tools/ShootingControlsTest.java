package com.filmsim.tools;

import com.filmsim.app.ShootingControls;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ShootingControlsTest {
    static final class Fake implements ShootingControls.Backend {
        String[] values = {"multi", "wide", "af-s", null, null, "auto"};
        String lock = "unlocked";
        boolean held, reject, supported = true, manual, peaking, zoom;
        int toggles, zoomStarts;
        public List<String> supported(int setting) {
            if (setting == 0) return Arrays.asList("multi", "center-weighted", "center-spot");
            if (setting == 1) return Arrays.asList("wide", "center", "flex-spot");
            if (setting == 2) return Arrays.asList("af-s", "af-c");
            if (setting == 3 && peaking) return Arrays.asList("off", "low", "mid", "high");
            if (setting == ShootingControls.FOCUS) return Arrays.asList("auto", "manual");
            return null;
        }
        public String get(int setting) { return values[setting]; }
        public void set(int setting, String value) throws Exception {
            if (reject) throw new Exception("rejected");
            values[setting] = value;
        }
        public boolean canToggleFocus() { return supported; }
        public void toggleFocus(boolean held) { this.held = held; toggles++; }
        public boolean manualFocus() { return manual || "manual".equals(values[ShootingControls.FOCUS]); }
        public void magnify() { zoom = true; zoomStarts++; }
        public void stopMagnification() { zoom = false; }
        public List<String> exposureLocks() { return Arrays.asList("unlocked", "locked"); }
        public String exposureLock() { return lock; }
        public void exposureLock(String value) { lock = value; }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        final List<String> errors = new ArrayList<String>();
        ShootingControls controls = new ShootingControls(new ShootingControls.Reporter() {
            public void error(String message) { errors.add(message); }
        });
        Fake camera = new Fake();
        controls.open(camera);
        controls.step(ShootingControls.METER, -1);
        check(camera.values[0].equals("center-spot"), "wrap only through supported metering modes");
        controls.step(ShootingControls.AREA, 1);
        check(camera.values[1].equals("center"), "focus area setter");
        controls.step(ShootingControls.AF, 1);
        check(camera.values[2].equals("af-c"), "AF mode setter");
        check(controls.value(ShootingControls.PEAK).equals("Unavailable"), "do not pretend native peaking exists");
        controls.step(ShootingControls.PEAK, 1);
        check(camera.values[3] == null, "unsupported control never written");
        controls.holdFocus(); controls.holdFocus();
        check(camera.held && camera.toggles == 1, "key repeat must not retoggle focus");
        controls.step(ShootingControls.AF, 1);
        check(camera.values[2].equals("af-c"), "block setting changes during hold");
        controls.release();
        check(!camera.held, "focus release restores mode");
        controls.holdExposure(); controls.holdExposure();
        check(camera.lock.equals("locked") && controls.indicator().contains("AEL"), "exposure hold indicator");
        controls.release();
        check(camera.lock.equals("unlocked"), "restore previous exposure lock");
        camera.lock = "locked";
        controls.holdExposure(); controls.release();
        check(camera.lock.equals("locked"), "preserve pre-existing lock");
        camera.reject = true;
        controls.step(ShootingControls.METER, 1);
        check(camera.values[0].equals("center-spot"), "failed setter does not advance selection");
        camera.reject = false;
        controls.holdFocus();
        controls.close();
        check(!camera.held && camera.values[0].equals("multi") && camera.values[1].equals("wide")
                && camera.values[2].equals("af-s"), "close releases hold and restores original settings");
        Fake reopened = new Fake();
        controls.open(reopened);
        check(reopened.values[0].equals("center-spot") && reopened.values[2].equals("af-c"), "reopen restores session choices");
        reopened.supported = false;
        controls.holdFocus();
        check(!reopened.held, "unsupported focus toggle is not called");
        controls.close();
        check(errors.size() >= 4, "unsupported/failed operations reported");
        Fake assist = new Fake();
        assist.peaking = true;
        assist.values[3] = "high";
        controls.open(assist);
        check(assist.values[3].equals("off"), "peaking disabled outside hold");
        controls.step(ShootingControls.PEAK, 1);
        check(assist.values[3].equals("off"), "Fn chooses hold level without enabling peaking");
        controls.focusMoved(controls.focusGeneration());
        check(!assist.zoom, "ring outside hold must not zoom");
        controls.holdFocus();
        check(assist.held && !assist.values[3].equals("off") && !assist.zoom, "hold enables MF and peaking, not zoom");
        int generation = controls.focusGeneration();
        controls.focusMoved(generation); controls.focusMoved(generation);
        check(assist.zoom && assist.zoomStarts == 1, "ring zooms once while held");
        controls.holdExposure();
        controls.cancelFocusZoom();
        check(!assist.zoom && assist.held && !assist.values[3].equals("off"), "half shutter exits zoom but preserves held MF and peaking");
        check(controls.manualFocusActive(), "half shutter must not request autofocus during held MF");
        check(assist.lock.equals("locked"), "half shutter preserves AEL");
        controls.focusMoved(generation);
        controls.focusMoved(controls.focusGeneration());
        controls.holdFocus();
        check(!assist.zoom && controls.focusHeld(), "ring events and button repeats cannot restart cancelled zoom");
        controls.cancelFocusZoom();
        check(assist.held && controls.manualFocusActive(), "capture also preserves held MF");
        controls.release();
        check(!assist.held && !controls.manualFocusActive() && assist.values[3].equals("off"), "physical release restores autofocus and stops peaking");
        assist.manual = true;
        int togglesBefore = assist.toggles;
        controls.holdFocus();
        check(controls.focusHeld() && assist.toggles == togglesBefore, "already MF must not toggle into AF");
        controls.focusMoved(generation);
        check(!assist.zoom, "old-session movement ignored after new press");
        controls.focusMoved(controls.focusGeneration());
        check(assist.zoom, "new movement while held zooms");
        controls.release();
        check(!assist.zoom && assist.values[3].equals("off"), "physical release ends zoom and peaking");
        controls.close();
        check(assist.values[3].equals("high"), "camera's original peaking setting restored on close");
        Fake focusMode = new Fake();
        controls.open(focusMode);
        controls.step(ShootingControls.FOCUS, 1);
        check(controls.manualFocusSelected() && focusMode.values[5].equals("manual"), "Fn selects manual focus");
        controls.holdFocus();
        check(focusMode.toggles == 0, "hold in selected MF does not switch to AF");
        controls.cancelFocusZoom();
        check(controls.manualFocusSelected() && focusMode.values[5].equals("manual"), "half shutter preserves selected MF");
        controls.release();
        controls.close();
        check(focusMode.values[5].equals("auto"), "close restores original focus mode");
        controls.open(focusMode);
        check(focusMode.values[5].equals("manual"), "MF choice survives playback reopen");
        controls.step(ShootingControls.FOCUS, 1);
        check(!controls.manualFocusSelected() && focusMode.values[5].equals("auto"), "Fn returns to autofocus");
        controls.close();
        System.out.println("PASS shooting controls: capabilities, cycling, holds, repeat suppression, errors, restoration and reopen");
        System.out.println("PASS held focus assist: peaking, ring gating, half shutter, release, stale callbacks, manual-focus preservation");
    }
}
