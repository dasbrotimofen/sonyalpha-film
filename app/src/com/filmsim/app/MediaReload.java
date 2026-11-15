package com.filmsim.app;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Handler;
import android.os.SystemClock;
import com.sony.scalar.provider.AvindexStore;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/** Experimental Sony database update; never unmounts storage or edits database files directly. */
final class MediaReload {
    interface Listener { void report(String text); }
    // A timed-out native call may still be running. Never stack another call behind it.
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();
    private final Handler handler;
    private final ContentResolver resolver;
    private final Listener listener;
    private volatile boolean cancelled;
    private volatile boolean finished;
    private Thread worker;
    private final Runnable timeout = new Runnable() {
        public void run() {
            if (!cancelled && !finished) {
                listener.report("media reload: observation timed out after 12 s; saved JPEG kept; native call may still be running");
                cancel();
            }
        }
    };

    MediaReload(Handler handler, ContentResolver resolver, Listener listener) {
        this.handler = handler;
        this.resolver = resolver;
        this.listener = listener;
    }

    void start(final File copy) {
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            listener.report("media reload: skipped; previous native request still running");
            return;
        }
        listener.report("media reload: starting test13 waitAndUpdateDatabase for " + copy.getPath());
        handler.postDelayed(timeout, 12000);
        worker = new Thread(new Runnable() {
            public void run() {
                try {
                    String[] ids = AvindexStore.getExternalMediaIds();
                    // This camera has one card. Do not guess a target on an ambiguous device.
                    if (ids == null || ids.length != 1) {
                        report("media reload: expected one external media ID, got " + (ids == null ? "null" : ids.length));
                        return;
                    }
                    if (cancelled) return;
                    try { report("media reload: indexed before=" + indexed(copy) + ", media=" + ids[0]); }
                    catch (Throwable e) { report("media reload: initial index query unavailable: " + e); }
                    if (cancelled) return;
                    // Sony Digital Filter's AvindexStoreFiles adapter passes mediaId here.
                    // Reflection avoids linking a V8 method on firmware that lacks it.
                    java.lang.reflect.Method update;
                    try {
                        update = Class.forName("com.sony.scalar.provider.AvindexStore$Files")
                                .getMethod("waitAndUpdateDatabase", ContentResolver.class, String.class);
                    } catch (ClassNotFoundException e) {
                        report("media reload: Files API unavailable; saved JPEG kept");
                        return;
                    } catch (NoSuchMethodException e) {
                        report("media reload: waitAndUpdateDatabase unavailable; saved JPEG kept");
                        return;
                    }
                    if (cancelled) return;
                    report("media reload: calling Files.waitAndUpdateDatabase, media=" + ids[0]);
                    Object outcome;
                    try { outcome = update.invoke(null, resolver, ids[0]); }
                    catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause() == null ? e : e.getCause();
                    }
                    boolean accepted = Boolean.TRUE.equals(outcome);
                    report("media reload: waitAndUpdateDatabase returned " + outcome);
                    // The watchdog bounds observation, not the duration of a native call.
                    long deadline = SystemClock.elapsedRealtime() + 10000;
                    while (!cancelled) {
                        if (indexed(copy)) {
                            report("media reload: copy present in Sony index after request (gallery display still needs checking)");
                            return;
                        }
                        if (!accepted || SystemClock.elapsedRealtime() >= deadline) break;
                        Thread.sleep(1000);
                    }
                    report("media reload: copy not found in Sony index; saved JPEG kept");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable e) {
                    report("media reload failed (saved JPEG kept): " + android.util.Log.getStackTraceString(e));
                } finally {
                    finished = true;
                    handler.removeCallbacks(timeout);
                    IN_FLIGHT.set(false);
                }
            }
        }, "filmsim-media-load");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean indexed(File copy) {
        // _data may be a Sony virtual URI. Match both DCF numbers, not the basename alone.
        int folder = Integer.parseInt(copy.getParentFile().getName().substring(0, 3));
        int number = Integer.parseInt(copy.getName().substring(4, 8));
        Cursor c = resolver.query(AvindexStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {AvindexStore.Images.Media.DCF_FOLDER_NUMBER,
                        AvindexStore.Images.Media.DCF_FILE_NUMBER, AvindexStore.Images.Media.EXIST_JPEG},
                AvindexStore.Images.Media.DCF_FOLDER_NUMBER + "=? AND "
                        + AvindexStore.Images.Media.DCF_FILE_NUMBER + "=?",
                new String[] {String.valueOf(folder), String.valueOf(number)}, null);
        try {
            while (c != null && c.moveToNext())
                if (c.getInt(0) == folder && c.getInt(1) == number && c.getInt(2) != 0) return true;
            return false;
        } finally { if (c != null) c.close(); }
    }

    private void report(final String text) {
        handler.post(new Runnable() {
            public void run() { if (!cancelled) listener.report(text); }
        });
    }

    void cancel() {
        cancelled = true;
        handler.removeCallbacks(timeout);
        if (worker != null) worker.interrupt();
    }
}
