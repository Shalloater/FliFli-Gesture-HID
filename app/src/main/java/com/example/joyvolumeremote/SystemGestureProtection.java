package com.example.joyvolumeremote;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.Window;
import java.util.Collections;

/** Uses public APIs only; mandatory Home/notification gestures remain under system control. */
final class SystemGestureProtection {
    static void excludeBackGestures(View view, int width, int height) {
        if (Build.VERSION.SDK_INT >= 29) {
            // The OS may restrict the granted area. Never claim this disables mandatory gestures.
            view.setSystemGestureExclusionRects(width > 0 && height > 0
                    ? Collections.singletonList(new Rect(0, 0, width, height)) : Collections.emptyList());
        }
    }

    static Rect reservedEdges(View view) {
        return Build.VERSION.SDK_INT < 23 ? new Rect() : reservedEdges(view.getRootWindowInsets());
    }

    static Rect reservedEdges(android.view.WindowInsets insets) {
        if (insets == null) return new Rect();
        Rect edges = new Rect(0, insets.getStableInsetTop(), 0, insets.getStableInsetBottom());
        if (Build.VERSION.SDK_INT >= 29) InsetsApi29.includeGestures(insets, edges);
        return edges;
    }

    @android.annotation.TargetApi(29)
    private static final class InsetsApi29 {
        static void includeGestures(android.view.WindowInsets insets, Rect edges) {
            android.graphics.Insets gestures = insets.getSystemGestureInsets();
            edges.top = Math.max(edges.top, gestures.top);
            edges.bottom = Math.max(edges.bottom, gestures.bottom);
        }
    }

    static void hideBars(Window window) { if (Build.VERSION.SDK_INT >= 30) BarsApi30.hide(window); }
    static void restoreBars(Window window) { if (Build.VERSION.SDK_INT >= 30) BarsApi30.restore(window); }

    static Object registerBackCallback(Activity activity, Runnable callback) {
        return Build.VERSION.SDK_INT >= 33 ? BackApi33.register(activity, callback) : null;
    }

    static void unregisterBackCallback(Activity activity, Object callback) {
        if (Build.VERSION.SDK_INT >= 33 && callback != null) BackApi33.unregister(activity, callback);
    }

    @android.annotation.TargetApi(30)
    private static final class BarsApi30 {
        static void hide(Window window) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(android.view.WindowInsets.Type.systemBars());
            }
        }
        static void restore(Window window) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.show(android.view.WindowInsets.Type.systemBars());
        }
    }

    @android.annotation.TargetApi(33)
    private static final class BackApi33 {
        static Object register(Activity activity, Runnable action) {
            android.window.OnBackInvokedCallback callback = action::run;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return callback;
        }
        static void unregister(Activity activity, Object callback) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) callback);
        }
    }

    private SystemGestureProtection() {}
}
