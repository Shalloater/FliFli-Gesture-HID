package com.example.joyvolumeremote;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Requests ordinary screen pinning, with system confirmation and ownership-aware cleanup. */
class ScreenPinningProtection {
    private static final String TAG = "FliFliScreenPinning";
    enum Result { PENDING, ACTIVE, INACTIVE, STOP_FAILED }
    interface Listener { void onResult(Result result); }
    private final Activity activity;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean requestedHere, confirmed;
    private int remainingChecks;
    private final Runnable pollState = this::pollState;

    ScreenPinningProtection(Activity activity, Listener listener) {
        this.activity = activity; this.listener = listener;
    }

    void request() {
        Log.i(TAG, "Request pinning; alreadyRequested=" + requestedHere + " active=" + isActive());
        if (requestedHere) return;
        if (isActive()) { listener.onResult(Result.ACTIVE); return; }
        requestedHere = true;
        confirmed = false;
        try {
            activity.startLockTask();
            Log.i(TAG, "System pinning request sent; active=" + isActive());
            if (isActive()) checkResult();
            else {
                listener.onResult(Result.PENDING);
                remainingChecks = 40;
                handler.postDelayed(pollState, 250);
            }
        } catch (SecurityException | IllegalStateException e) {
            requestedHere = false;
            listener.onResult(Result.INACTIVE);
        }
    }

    void onWindowFocusChanged(boolean focused) {
        if (requestedHere && focused) checkResult();
    }

    void stop() {
        Log.i(TAG, "Stop app-owned pinning; requested=" + requestedHere + " active=" + isActive());
        handler.removeCallbacks(pollState);
        if (requestedHere) {
            try { activity.stopLockTask(); }
            catch (SecurityException | IllegalStateException e) {
                if (isActive()) {
                    listener.onResult(Result.STOP_FAILED);
                    return;
                }
            }
        }
        requestedHere = false;
        confirmed = false;
    }

    boolean isActive() {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        return Build.VERSION.SDK_INT >= 23 ? manager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE
                : manager.isInLockTaskMode();
    }

    private void pollState() {
        if (!requestedHere) return;
        checkResult();
        if (confirmed) return;
        if (--remainingChecks > 0) handler.postDelayed(pollState, 250);
        else listener.onResult(Result.INACTIVE);
        // Keep ownership until stop(), even if a user accepts the system prompt much later.
    }

    private void checkResult() {
        if (!requestedHere) return;
        boolean active = isActive();
        if (active && !confirmed) {
            Log.i(TAG, "System confirmed screen pinning");
            confirmed = true;
            listener.onResult(Result.ACTIVE);
        } else if (!active && confirmed) {
            confirmed = false;
            listener.onResult(Result.INACTIVE);
        }
    }
}
