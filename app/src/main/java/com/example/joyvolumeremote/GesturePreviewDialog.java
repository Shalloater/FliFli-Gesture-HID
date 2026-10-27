package com.example.joyvolumeremote;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.IOException;
import java.util.Locale;

/** Plays a trusted, packaged SVG locally. Viewing a demo has no selection or transport callbacks. */
final class GesturePreviewDialog extends Dialog {
    private final AppPreferences appearance;
    private final GestureDemoCatalog catalog;
    private final GestureDemoCatalog.Demo demo;
    private final String title;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private LinearLayout root;
    private FrameLayout stage;
    private ProgressBar loading;
    private LinearLayout failure;
    private TextView play, replay, speed;
    private SeekBar progress;
    private boolean ready, disposed, playing = true, focused = true;
    private int speedIndex = 1;
    private static final float[] SPEEDS = {.5f, 1f, 1.5f};
    private final Runnable loadTimeout = () -> { if (!ready && !disposed) showFailure(); };
    private final Runnable pollTime = new Runnable() {
        @Override public void run() {
            if (!ready || disposed || !playing || !focused || webView == null) return;
            webView.evaluateJavascript("Demo.time()", value -> {
                if (!ready || disposed || !playing) return;
                try { progress.setProgress(Math.round(Float.parseFloat(value) * 1000)); }
                catch (NumberFormatException ignored) { /* The document may have been dismissed between callbacks. */ }
            });
            handler.postDelayed(this, 120);
        }
    };

    GesturePreviewDialog(Context context, AppPreferences appearance, GestureDemoCatalog catalog,
            GestureDemoCatalog.Demo demo, String title) {
        super(context); this.appearance = appearance; this.catalog = catalog; this.demo = demo; this.title = title;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = column(); root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackground(shape(Color.WHITE, 22));
        LinearLayout heading = row();
        LinearLayout titles = column();
        TextView role = text(getContext().getString(demo.action ? R.string.demo_action_role : R.string.demo_signal_role), 11, false);
        role.setTextColor(Color.rgb(105, 117, 139)); titles.addView(role);
        TextView name = text(title, 17, true); name.setPadding(0, dp(5), 0, dp(8)); titles.addView(name);
        heading.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = button("×", v -> dismiss()); close.setTextSize(24);
        close.setContentDescription(getContext().getString(R.string.demo_close));
        heading.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44))); root.addView(heading);

        ScrollView scroll = new ScrollView(getContext()); scroll.setFillViewport(false);
        LinearLayout body = column();
        TextView instruction = text(GestureDemoText.instruction(getContext(), demo), 12, false);
        instruction.setTextColor(Color.rgb(91, 102, 121)); instruction.setPadding(0, dp(6), 0, dp(10));
        body.addView(instruction);
        stage = new FrameLayout(getContext()); stage.setBackground(shape(Color.rgb(248, 249, 252), 14));
        int height = Math.min(dp(390), Math.max(dp(220), (int) (getContext().getResources().getDisplayMetrics().heightPixels * .48f)));
        body.addView(stage, new LinearLayout.LayoutParams(-1, height));
        loading = new ProgressBar(getContext()); loading.setContentDescription(getContext().getString(R.string.demo_loading));
        stage.addView(loading, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        failure = column(); failure.setGravity(Gravity.CENTER); failure.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView error = text(getContext().getString(R.string.demo_failed), 13, false); error.setGravity(Gravity.CENTER); failure.addView(error);
        TextView retry = button(getContext().getString(R.string.demo_retry), v -> loadDemo());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-2, -2); retryParams.topMargin = dp(12);
        failure.addView(retry, retryParams); failure.setVisibility(View.GONE);
        stage.addView(failure, new FrameLayout.LayoutParams(-1, -1));
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = row();
        play = button("", v -> { playing = !playing; updateControls(); applyPlayback(); });
        replay = button(getContext().getString(R.string.demo_replay), v -> {
            playing = true; progress.setProgress(0); command("Demo.seek(0)"); updateControls(); applyPlayback();
        });
        speed = button("", v -> { speedIndex = (speedIndex + 1) % SPEEDS.length; updateControls(); applyPlayback(); });
        for (TextView control : new TextView[]{play, replay, speed}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(3), 0, dp(3), 0); controls.addView(control, params);
        }
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, -2);
        controlsParams.topMargin = dp(10);
        root.addView(controls, controlsParams);
        progress = new SeekBar(getContext()); progress.setMax(Math.round(demo.cycle * 1000));
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(appearance.accent()));
        progress.setThumbTintList(android.content.res.ColorStateList.valueOf(appearance.accent()));
        progress.setContentDescription(getContext().getString(R.string.demo_progress));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && ready) {
                    playing = false; updateControls(); applyPlayback();
                    command(String.format(Locale.US, "Demo.seek(%.3f)", value / 1000f));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(40)));
        TextView slow = text(getContext().getString(R.string.demo_slow), 10, false);
        slow.setTextColor(Color.rgb(105, 117, 139)); slow.setGravity(Gravity.CENTER); root.addView(slow);
        setContentView(root); updateControls(); loadDemo();
    }

    @Override protected void onStart() {
        super.onStart(); Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.setDimAmount(.32f);
            android.util.DisplayMetrics screen = getContext().getResources().getDisplayMetrics();
            int width = Math.min(screen.widthPixels - dp(24), dp(480));
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int maximumHeight = (int) (screen.heightPixels * .87f);
            if (root.getMeasuredHeight() > maximumHeight) {
                ViewGroup.LayoutParams stageParams = stage.getLayoutParams();
                stageParams.height = Math.max(dp(160), stageParams.height - (root.getMeasuredHeight() - maximumHeight));
                stage.setLayoutParams(stageParams);
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            }
            window.setLayout(width, Math.min(root.getMeasuredHeight(), maximumHeight));
        }
    }

    private void loadDemo() {
        if (disposed) return;
        handler.removeCallbacks(loadTimeout); handler.postDelayed(loadTimeout, 10000);
        ready = false; loading.setVisibility(View.VISIBLE); failure.setVisibility(View.GONE); updateControls();
        try {
            String svg = GestureDemoText.localize(getContext(), appearance, demo, title, catalog.svg(demo));
            if (webView == null) {
                webView = new WebView(getContext()); webView.setBackgroundColor(Color.TRANSPARENT);
                // Only our packaged SVG and static playback code are loaded. There is no JS bridge or remote navigation.
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setBlockNetworkLoads(true);
                webView.getSettings().setAllowFileAccess(false); webView.getSettings().setAllowContentAccess(false);
                webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
                webView.setVerticalScrollBarEnabled(false); webView.setHorizontalScrollBarEnabled(false);
                webView.setOnLongClickListener(v -> true);
                webView.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
                    @Override public void onPageFinished(WebView view, String url) {
                        if (disposed || !url.startsWith("https://appassets.androidplatform.net/gesture-demo/")) return;
                        view.evaluateJavascript("Boolean(window.Demo)", value -> {
                            if (disposed) return;
                            if (!"true".equals(value)) { showFailure(); return; }
                            ready = true; handler.removeCallbacks(loadTimeout); view.setVisibility(View.VISIBLE); loading.setVisibility(View.GONE); failure.setVisibility(View.GONE);
                            updateControls(); applyPlayback();
                        });
                    }
                    @Override public void onReceivedError(WebView view, int code, String description, String url) { showFailure(); }
                });
                stage.addView(webView, 0, new FrameLayout.LayoutParams(-1, -1));
            }
            webView.setVisibility(View.VISIBLE);
            String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'\">"
                    + "<link rel='icon' href='data:image/svg+xml,%3Csvg%20xmlns=%22http://www.w3.org/2000/svg%22/%3E'>"
                    + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent}body{display:flex;align-items:center;justify-content:center}svg{width:100%;height:100%}</style></head><body>"
                    + svg + "<script>(function(){var s=document.querySelector('svg'),t=0,p=false,r=1,last=performance.now();s.pauseAnimations();"
                    + "function draw(){s.setCurrentTime(t)}window.Demo={time:function(){return t},seek:function(v){t=Math.max(0,Math.min(" + demo.cycle + ",v));draw()},"
                    + "playing:function(v){p=v;last=performance.now()},speed:function(v){r=v}};"
                    + "function frame(n){if(p){t=(t+(n-last)/1000*r)%" + demo.cycle + ";draw()}last=n;requestAnimationFrame(frame)}draw();requestAnimationFrame(frame)})();</script></body></html>";
            webView.loadDataWithBaseURL("https://appassets.androidplatform.net/gesture-demo/", html, "text/html", "UTF-8", null);
        } catch (IOException | RuntimeException e) {
            android.util.Log.e("FliFliGestureDemo", "Cannot play packaged SVG", e); showFailure();
        }
    }

    private void showFailure() {
        if (disposed) return;
        ready = false; handler.removeCallbacks(pollTime); handler.removeCallbacks(loadTimeout); loading.setVisibility(View.GONE); failure.setVisibility(View.VISIBLE);
        if (webView != null) webView.setVisibility(View.GONE);
        updateControls();
    }
    private void updateControls() {
        if (play == null) return;
        play.setText(playing ? R.string.demo_pause : R.string.demo_play);
        String rate = speedIndex == 0 ? "0.5×" : speedIndex == 1 ? "1×" : "1.5×";
        speed.setText(getContext().getString(R.string.demo_speed, rate));
        for (TextView control : new TextView[]{play, replay, speed}) { control.setEnabled(ready); control.setAlpha(ready ? 1 : .45f); }
        if (progress != null) progress.setEnabled(ready);
    }
    private void applyPlayback() {
        handler.removeCallbacks(pollTime);
        if (!ready || disposed) return;
        command("Demo.speed(" + SPEEDS[speedIndex] + ");Demo.playing(" + (playing && focused) + ")");
        if (playing && focused) handler.post(pollTime);
    }
    private void command(String code) { if (ready && !disposed && webView != null) webView.evaluateJavascript(code, null); }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus); focused = hasFocus; applyPlayback();
        if (webView != null) { if (hasFocus) webView.onResume(); else webView.onPause(); }
    }
    @Override protected void onStop() {
        disposed = true; ready = false; handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading(); webView.onPause();
            if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy(); webView = null;
        }
        super.onStop();
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(getContext()); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView text(String label, int size, boolean bold) {
        TextView view = new TextView(getContext()); view.setText(label); view.setTextSize(size); view.setTextColor(Color.rgb(24, 31, 43));
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return view;
    }
    private TextView button(String label, View.OnClickListener listener) {
        TextView button = text(label, 12, true); button.setTextColor(appearance.accent()); button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(44)); button.setPadding(dp(8), dp(7), dp(8), dp(7));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(appearance.primary()), shape(appearance.tint(), 12), null));
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
            }
        });
        button.setFocusable(true); button.setOnClickListener(listener); return button;
    }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(float value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }
}
