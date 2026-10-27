package com.example.joyvolumeremote;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GestureDemoTest extends InstrumentationTestCase {
    private Activity activity;
    private GestureDemoCatalog catalog;
    private final List<Dialog> opened = new ArrayList<>();

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        catalog = new GestureDemoCatalog(activity);
        getInstrumentation().waitForIdleSync();
    }
    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> { for (Dialog dialog : opened) dialog.dismiss(); activity.finish(); });
        super.tearDown();
    }

    public void testAllSignalsAndConfigurableActionsHaveOfflineAssets() throws Exception {
        Set<String> ids = new HashSet<>();
        for (HidAction.ActionDefinition action : HidAction.all()) if (action.outputType == HidAction.OutputType.TOUCH) {
            GestureDemoCatalog.Demo demo = requireDemo(action.id); ids.add(demo.id);
            assertFalse(demo.action); assertEquals(1, demo.fingers);
            List<SignalReports.Frame> frames = SignalReports.compile(SignalProgram.single(action.id));
            byte[] first = frames.get(0).data, last = first;
            for (SignalReports.Frame frame : frames) if (TouchState.mask(frame.data) != 0) last = frame.data;
            float[] path = demo.paths.get(0);
            assertEquals(coordinate(first, 2), path[0], .00001f); assertEquals(coordinate(first, 4), path[1], .00001f);
            assertEquals(coordinate(last, 2), path[path.length-2], .00001f); assertEquals(coordinate(last, 4), path[path.length-1], .00001f);
        }
        assertEquals(18, ids.size());
        for (int fingers = 1; fingers <= 3; fingers++) for (int taps = 1; taps <= 3; taps++)
            for (String area : KeyboardDefinitions.TAP_AREAS) {
                GestureDemoCatalog.Demo demo = requireDemo(GestureBinding.tapKey(fingers, taps, area));
                assertEquals(demo.id, demo.binding().key()); ids.add(demo.id);
            }
        for (int fingers = 1; fingers <= 3; fingers++) for (String motion : KeyboardDefinitions.MOTION_GESTURES) {
            if (fingers > 1 && motion.startsWith("circle_")) continue;
            GestureDemoCatalog.Demo demo = requireDemo(GestureBinding.motionKey(fingers, motion));
            assertEquals(demo.id, demo.binding().key()); ids.add(demo.id);
        }
        assertEquals(77, ids.size()); assertEquals(77, activity.getAssets().list("gesture_demos/svg").length);
        assertNull(catalog.find("not-a-demo"));
    }

    public void testSvgTextAndThemeFollowTheActiveLocale() throws Exception {
        GestureDemoCatalog.Demo demo = requireDemo("tap:2:2:left");
        for (String language : AppPreferences.LANGUAGES) {
            Configuration config = new Configuration(activity.getResources().getConfiguration()); config.setLocale(Locale.forLanguageTag(language));
            Context context = activity.createConfigurationContext(config);
            AppPreferences appearance = new AppPreferences(context);
            String svg = GestureDemoText.localize(context, appearance, demo, "Preview & title", catalog.svg(demo));
            assertFalse(svg.contains("var(")); assertTrue(svg.contains("Preview &amp; title"));
            String renderedText = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(svg))).getDocumentElement().getTextContent();
            assertTrue(language, renderedText.contains(context.getString(R.string.demo_action_role)));
            assertTrue(language, renderedText.contains(context.getString(R.string.demo_tap_step, 2)));
            if (!language.startsWith("zh")) { assertFalse(svg.contains(">准备<")); assertFalse(svg.contains("本机 · 触发动作")); }
        }
    }

    public void testOfflineSvgPlaybackPauseReplayAndDisposal() throws Exception {
        GesturePreviewDialog dialog = openDemo("tap:2:2:left");
        WebView web = (WebView) field(dialog, "webView");
        boolean[] settings = new boolean[3];
        getInstrumentation().runOnMainSync(() -> {
            settings[0] = web.getSettings().getBlockNetworkLoads(); settings[1] = web.getSettings().getAllowFileAccess();
            settings[2] = web.getSettings().getAllowContentAccess();
        });
        assertTrue(settings[0]); assertFalse(settings[1]); assertFalse(settings[2]);
        assertEquals("2", js(web, "document.querySelectorAll('.contact').length"));
        click(dialog, R.string.demo_pause);
        js(web, "Demo.seek(.52)");
        assertEquals("[1,1]", new org.json.JSONTokener(js(web,
                "JSON.stringify(Array.from(document.querySelectorAll('.pressed')).map(n=>Number(getComputedStyle(n).opacity)))")).nextValue());
        float before = Float.parseFloat(js(web, "Demo.time()")); Thread.sleep(220);
        assertEquals(before, Float.parseFloat(js(web, "Demo.time()")), .02f);
        js(web, "Demo.seek(.72)");
        assertEquals("[0,0]", new org.json.JSONTokener(js(web,
                "JSON.stringify(Array.from(document.querySelectorAll('.pressed')).map(n=>Number(getComputedStyle(n).opacity)))")).nextValue());
        click(dialog, R.string.demo_replay); Thread.sleep(180);
        assertTrue(Float.parseFloat(js(web, "Demo.time()")) > .04f);
        getInstrumentation().runOnMainSync(dialog::dismiss);
        assertTrue((Boolean) field(dialog, "disposed")); assertNull(field(dialog, "webView"));
        GesturePreviewDialog reopened = openDemo("touch_right_edge_down");
        assertEquals("1", js((WebView) field(reopened, "webView"), "document.querySelectorAll('.contact').length"));
    }

    public void testLongPressOnlyPreviewsAndClickStillSelects() throws Exception {
        int[] commits = {0}; String[] selected = {null};
        SignalPickerDialog[] holder = {null};
        getInstrumentation().runOnMainSync(() -> {
            holder[0] = new SignalPickerDialog(activity, new AppPreferences(activity), catalog, "kb_b", "Demo QA", value -> { commits[0]++; selected[0] = value; });
            holder[0].show(); opened.add(holder[0]);
        });
        SignalPickerDialog picker = holder[0];
        getInstrumentation().runOnMainSync(() -> {
            try { ((PickerTabs) field(picker, "categoryTabs")).getChildAt(3).performClick(); }
            catch (Exception e) { throw new AssertionError(e); }
        });
        Map<?,?> keys = (Map<?,?>) field(picker, "keyButtons");
        TextView key = (TextView) keys.get("touch_top_right_down");
        assertNotNull(key.getCompoundDrawables()[0]);
        getInstrumentation().runOnMainSync(key::performLongClick);
        GesturePreviewDialog preview = (GesturePreviewDialog) field(picker, "gesturePreview");
        assertNotNull(preview); awaitReady(preview);
        assertEquals(0, commits[0]); assertEquals(Arrays.asList("kb_b"), ((List<?>) field(picker, "steps")).get(0));
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
        assertFalse(preview.isShowing()); assertNull(field(picker, "gesturePreview"));
        assertTrue(picker.isShowing());
        getInstrumentation().runOnMainSync(key::performClick);
        assertEquals(Arrays.asList("touch_top_right_down"), ((List<?>) field(picker, "steps")).get(0));
        getInstrumentation().runOnMainSync(() -> { try { ((TextView) field(picker, "done")).performClick(); } catch (Exception e) { throw new AssertionError(e); } });
        assertEquals(1, commits[0]); assertEquals("touch_top_right_down", selected[0]);
    }

    public void testMissingAssetShowsRetryAndCanBeClosed() throws Exception {
        JSONObject data = new JSONObject("{\"id\":\"missing\",\"category\":\"tap\",\"kind\":\"action\",\"area\":\"full\",\"file\":\"svg/missing.svg\",\"fingers\":1,\"tapCount\":1,\"cycle\":3.2,\"contacts\":[{\"path\":[[0,0.5,0.5]]}]}");
        GesturePreviewDialog[] holder = {null};
        GestureDemoCatalog.Demo demo = new GestureDemoCatalog.Demo(data);
        getInstrumentation().runOnMainSync(() -> { holder[0] = new GesturePreviewDialog(activity, new AppPreferences(activity), catalog, demo, "Missing"); holder[0].show(); opened.add(holder[0]); });
        assertEquals(View.VISIBLE, ((View) field(holder[0], "failure")).getVisibility());
        assertFalse((Boolean) field(holder[0], "ready"));
        click(holder[0], R.string.demo_retry);
        assertEquals(View.VISIBLE, ((View) field(holder[0], "failure")).getVisibility());
        getInstrumentation().runOnMainSync(holder[0]::dismiss);
        assertFalse(holder[0].isShowing());
    }

    private GestureDemoCatalog.Demo requireDemo(String id) throws Exception {
        GestureDemoCatalog.Demo demo = catalog.find(id); assertNotNull(id, demo);
        String svg = catalog.svg(demo); assertTrue(svg.contains("<svg")); assertTrue(svg.contains("<animate")); assertFalse(svg.contains("<script"));
        assertEquals(demo.fingers, demo.paths.size()); return demo;
    }
    private GesturePreviewDialog openDemo(String id) throws Exception {
        GestureDemoCatalog.Demo demo = requireDemo(id); GesturePreviewDialog[] holder = {null};
        getInstrumentation().runOnMainSync(() -> { holder[0] = new GesturePreviewDialog(activity, new AppPreferences(activity), catalog, demo, "Demo QA"); holder[0].show(); opened.add(holder[0]); });
        awaitReady(holder[0]); return holder[0];
    }
    private void awaitReady(GesturePreviewDialog dialog) throws Exception {
        for (int i = 0; i < 100; i++) { if ((Boolean) field(dialog, "ready")) return; Thread.sleep(100); getInstrumentation().waitForIdleSync(); }
        fail("Packaged SVG did not become ready");
    }
    private Object field(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private float coordinate(byte[] report, int index) { return ((report[index]&255)|((report[index+1]&255)<<8))/(float)TouchState.MAX_COORDINATE; }
    private String js(WebView web, String script) throws Exception {
        String[] value = {null}; CountDownLatch done = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(script, result -> { value[0] = result; done.countDown(); }));
        assertTrue(done.await(5, TimeUnit.SECONDS)); return value[0];
    }
    private void click(Dialog dialog, int resource) {
        getInstrumentation().runOnMainSync(() -> { TextView view = find(dialog.getWindow().getDecorView(), activity.getString(resource)); assertNotNull(view); view.performClick(); });
    }
    private TextView find(View root, String label) {
        if (root instanceof TextView && label.contentEquals(((TextView)root).getText())) return (TextView)root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { TextView found = find(((ViewGroup)root).getChildAt(i), label); if (found != null) return found; }
        return null;
    }
}
