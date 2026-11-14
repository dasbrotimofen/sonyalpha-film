package com.filmsim.app;

import android.hardware.Camera;
import com.sony.scalar.hardware.CameraEx;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Typed Sony controls; peaking uses only keys actually advertised by this camera. */
public final class SonyControls implements ShootingControls.Backend {
    private final CameraEx camera;
    private final Camera.Parameters supported;
    private final String[] peakKeys = new String[2];
    public SonyControls(CameraEx camera) {
        this.camera = camera;
        supported = camera.getSupportedParameters();
        Camera.Parameters current = camera.getNormalCamera().getParameters();
        for (String key : new String[] {"peaking-level", "peaking-mode"})
            if (supported.get(key + "-values") != null && current.get(key) != null) { peakKeys[0] = key; break; }
        if (supported.get("peaking-color-values") != null && current.get("peaking-color") != null)
            peakKeys[1] = "peaking-color";
    }
    private CameraEx.ParametersModifier current() { return camera.createParametersModifier(camera.getNormalCamera().getParameters()); }
    @SuppressWarnings("unchecked")
    public List<String> supported(int setting) {
        CameraEx.ParametersModifier m = camera.createParametersModifier(supported);
        switch (setting) {
            case ShootingControls.METER: return m.getSupportedMeteringModes();
            case ShootingControls.AREA: return m.getSupportedFocusAreaModes();
            case ShootingControls.AF: return m.getSupportedAutoFocusModes();
            case ShootingControls.FOCUS:
                List<String> modes = supported.getSupportedFocusModes();
                List<String> selectable = new ArrayList<String>();
                if (modes != null) for (String mode : new String[] {"auto", "manual"})
                    if (modes.contains(mode)) selectable.add(mode);
                return selectable;
            default:
                String key = peakKeys[setting - ShootingControls.PEAK];
                return key == null ? new ArrayList<String>() : Arrays.asList(supported.get(key + "-values").split(","));
        }
    }
    public String get(int setting) {
        switch (setting) {
            case ShootingControls.METER: return current().getMeteringMode();
            case ShootingControls.AREA: return current().getFocusAreaMode();
            case ShootingControls.AF: return current().getAutoFocusMode();
            case ShootingControls.FOCUS: return camera.getNormalCamera().getParameters().getFocusMode();
            default:
                String key = peakKeys[setting - ShootingControls.PEAK];
                return key == null ? null : camera.getNormalCamera().getParameters().get(key);
        }
    }
    public void set(int setting, String value) {
        Camera.Parameters p = camera.createEmptyParameters();
        CameraEx.ParametersModifier m = camera.createParametersModifier(p);
        switch (setting) {
            case ShootingControls.METER: m.setMeteringMode(value); break;
            case ShootingControls.AREA: m.setFocusAreaMode(value); break;
            case ShootingControls.AF: m.setAutoFocusMode(value); break;
            case ShootingControls.FOCUS: p.setFocusMode(value); break;
            default:
                String key = peakKeys[setting - ShootingControls.PEAK];
                if (key == null) throw new IllegalStateException("Native peaking is not exposed");
                p.set(key, value);
        }
        camera.getNormalCamera().setParameters(p);
    }
    public boolean canToggleFocus() { return camera.createParametersModifier(supported).isSupportedToggleFocusMode(); }
    public void toggleFocus(boolean held) { camera.setToggleFocusMode(held); }
    public boolean manualFocus() {
        String mode = camera.getNormalCamera().getParameters().getFocusMode();
        return "manual".equals(mode) || "dmf".equals(mode);
    }
    public void magnify() {
        List<?> levels = camera.createParametersModifier(supported).getSupportedPreviewMagnification();
        int first = Integer.MAX_VALUE;
        if (levels != null) for (Object level : levels) {
            int value = Integer.parseInt(level.toString());
            if (value > 0 && value < first) first = value;
        }
        if (first == Integer.MAX_VALUE) throw new IllegalStateException("No magnification steps available");
        camera.setPreviewMagnification(first, new android.util.Pair<Integer, Integer>(0, 0));
    }
    public void stopMagnification() { camera.stopPreviewMagnification(); }
    @SuppressWarnings("unchecked")
    public List<String> exposureLocks() { return camera.createParametersModifier(supported).getSupportedAutoExposureLocks(); }
    public String exposureLock() { return current().getAutoExposureLock(); }
    public void exposureLock(String value) {
        Camera.Parameters p = camera.createEmptyParameters();
        camera.createParametersModifier(p).setAutoExposureLock(value);
        camera.getNormalCamera().setParameters(p);
    }
}
