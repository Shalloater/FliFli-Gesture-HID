package com.example.joyvolumeremote;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static com.example.joyvolumeremote.KeyboardDefinitions.*;

public class GestureBindingEditorTest extends InstrumentationTestCase {
    private Activity activity;
    private CustomKeyboardConfig config;
    private KeyboardConfigStore store;
    private SharedPreferences preferences;
    private Object originalStore;
    private GestureBinding original;

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        preferences = activity.getSharedPreferences("binding_editor_qa", Context.MODE_PRIVATE); preferences.edit().clear().commit();
        store = new KeyboardConfigStore(preferences); config = store.createNewKeyboardDraft(KEYBOARD_TYPE_FULLSCREEN_GESTURE);
        config.name = "Gesture editor QA";
        original = tap(1, 1, TAP_AREA_LEFT, "kb_a");
        config.bindings.add(original); config.bindings.add(tap(1,1,TAP_AREA_RIGHT,"kb_b"));
        GestureBinding motion = new GestureBinding(); motion.kind = GESTURE_KIND_MOTION; motion.fingers = 1;
        motion.motion = MOTION_SWIPE_UP; motion.area = TAP_AREA_FULL; motion.actionId = "kb_c"; config.bindings.add(motion);
        originalStore = field(activity, "keyboardStore");
        main(() -> { setField(activity, "keyboardStore", store); call("showKeyboardEditor", new Class<?>[]{CustomKeyboardConfig.class, boolean.class}, config, false); });
        settle();
    }
    @Override protected void tearDown() throws Exception {
        main(() -> { setField(activity, "keyboardStore", originalStore); activity.finish(); });
        getInstrumentation().waitForIdleSync(); preferences.edit().clear().commit(); super.tearDown();
    }

    public void testExistingRowOpensBothFieldsAndCancellationPreservesBothValues() throws Exception {
        String before = snapshot();
        clickRow(find(activity.getWindow().getDecorView(), title(original)));
        GestureBindingEditorDialog editor = editor();
        assertNotNull(find(editor.getWindow().getDecorView(), text(R.string.binding_trigger)));
        assertNotNull(find(editor.getWindow().getDecorView(), text(R.string.ui_output)));
        screenshot("edit-both-fields");
        changeTap(editor, 2, 2, TAP_AREA_LEFT);
        assertEquals("tap:2:2:left", working(editor).key()); assertEquals("kb_a", working(editor).actionId);
        changeSignal(editor, "kb_z");
        assertEquals("kb_z", working(editor).actionId); assertEquals(before, snapshot());
        assertSame(original, config.bindings.get(0));
        click(editor, R.string.ui_cancel);
        assertEquals(before, snapshot()); assertEquals("tap:1:1:left", original.key()); assertEquals("kb_a", original.actionId);
    }

    public void testChangingGestureAndSignalReplacesTheSameRowAndPersists() throws Exception {
        open(original); GestureBindingEditorDialog editor = editor();
        clickField(editor, R.string.binding_trigger); Dialog choices = choices();
        click(find(choices.getWindow().getDecorView(), text(R.string.ui_swipe)));
        TextView used = find(choices.getWindow().getDecorView(), text(R.string.ui_one_finger)+text(R.string.ui_swipe_up));
        assertFalse(used.isEnabled());
        clickRow(find(choices.getWindow().getDecorView(), text(R.string.ui_two_fingers)+text(R.string.ui_swipe_down)));
        assertEquals("motion:2:swipe_down", working(editor).key()); assertEquals("kb_a", working(editor).actionId);
        changeSignal(editor, "kb_z"); screenshot("changed-both-fields");
        click(editor, R.string.ui_done);
        assertFalse(editor.isShowing()); assertEquals(3, config.bindings.size());
        assertEquals("motion:2:swipe_down", config.bindings.get(0).key()); assertEquals("kb_z", config.bindings.get(0).actionId);
        assertEquals("tap:1:1:right", config.bindings.get(1).key()); assertEquals("kb_b", config.bindings.get(1).actionId);
        assertEquals("motion:2:swipe_down", stored().bindings.get(0).key()); assertEquals("kb_z", stored().bindings.get(0).actionId);
        open(config.bindings.get(0)); editor = editor();
        clickField(editor, R.string.binding_trigger); choices = choices();
        TextView activeTab = find(choices.getWindow().getDecorView(), text(R.string.ui_swipe)); assertTrue(activeTab.isSelected());
        TextView current = find(choices.getWindow().getDecorView(), text(R.string.ui_two_fingers)+text(R.string.ui_swipe_down));
        assertTrue(current.isEnabled()); assertTrue(clickable(current).isSelected());
        click(find(choices.getWindow().getDecorView(), text(R.string.ui_tap)));
        clickRow(find(choices.getWindow().getDecorView(), text(R.string.ui_three_fingers)+text(R.string.ui_triple_tap)));
        clickRow(find(choices().getWindow().getDecorView(), text(R.string.ui_right_half)));
        assertEquals("kb_z", working(editor).actionId);
        click(editor, R.string.ui_done);
        assertEquals(3, config.bindings.size()); assertEquals("tap:3:3:right", stored().bindings.get(0).key());
        assertEquals("kb_z", stored().bindings.get(0).actionId);
    }

    public void testCurrentGestureCanBeReselectedAndOtherOverlapsStayDisabled() throws Exception {
        open(original); GestureBindingEditorDialog editor = editor();
        clickField(editor, R.string.binding_trigger);
        clickRow(find(choices().getWindow().getDecorView(), text(R.string.ui_one_finger)+text(R.string.ui_single_tap)));
        Dialog areas = choices();
        TextView left = find(areas.getWindow().getDecorView(), text(R.string.ui_left_half));
        assertTrue(left.isEnabled()); assertTrue(clickable(left).isSelected());
        assertFalse(find(areas.getWindow().getDecorView(), text(R.string.ui_full_screen)).isEnabled());
        assertFalse(find(areas.getWindow().getDecorView(), text(R.string.ui_right_half)).isEnabled());
        screenshot("overlap-check"); clickRow(left); click(editor, R.string.ui_done);
        assertEquals(3, config.bindings.size()); assertEquals("tap:1:1:left", stored().bindings.get(0).key());
    }

    public void testChildCancelAndSystemBackDoNotSaveDraftChanges() throws Exception {
        String before = snapshot(); open(original); GestureBindingEditorDialog editor = editor();
        clickField(editor, R.string.binding_trigger); back(); assertTrue(editor.isShowing());
        assertEquals(original.key(), working(editor).key());
        clickField(editor, R.string.ui_output);
        SignalPickerDialog picker = (SignalPickerDialog) field(activity, "signalPicker");
        click((View) ((Map<?,?>) field(picker, "keyButtons")).get("kb_x"));
        back(); assertEquals("kb_a", working(editor).actionId);
        changeSignal(editor, "kb_z"); assertEquals("kb_z", working(editor).actionId);
        back(); assertEquals(before, snapshot()); assertEquals("kb_a", original.actionId);
    }

    public void testCommitRechecksConflictsWithoutOverwritingAnotherBinding() throws Exception {
        String before = snapshot(); open(original); GestureBindingEditorDialog editor = editor();
        main(() -> setField(editor, "working", config.bindings.get(1).copy()));
        click(editor, R.string.ui_done);
        assertTrue(editor.isShowing()); assertEquals(before, snapshot()); assertSame(original, config.bindings.get(0));
        assertNotNull(find(editor.getWindow().getDecorView(), text(R.string.binding_conflict)));
    }

    public void testLongSignalAndLargeTextKeepBothFieldsAndFooterReachable() throws Exception {
        java.util.ArrayList<List<String>> steps = new java.util.ArrayList<>();
        for (int i=0;i<16;i++) steps.add(Arrays.asList("kb_ctrl",i%2==0?"kb_c":"kb_v"));
        original.actionId = new SignalProgram(SignalProgram.Mode.SEQUENCE, steps, 250).encode();
        open(original); GestureBindingEditorDialog editor = editor(); screenshot("long-signal-editor");
        View done = find(editor.getWindow().getDecorView(), text(R.string.ui_done));
        Rect visible = new Rect(); assertTrue(done.getGlobalVisibleRect(visible)); assertEquals(done.getHeight(), visible.height());
        clickField(editor, R.string.ui_output);
        SignalPickerDialog picker = (SignalPickerDialog) field(activity, "signalPicker");
        assertEquals(16, ((List<?>) field(picker, "steps")).size()); back(); assertTrue(editor.isShowing());
    }

    private void changeTap(GestureBindingEditorDialog editor, int fingers, int taps, String area) throws Exception {
        clickField(editor, R.string.binding_trigger);
        int[] fingerLabels={R.string.ui_one_finger,R.string.ui_two_fingers,R.string.ui_three_fingers};
        int[] tapLabels={R.string.ui_single_tap,R.string.ui_double_tap,R.string.ui_triple_tap};
        click(find(choices().getWindow().getDecorView(),text(R.string.ui_tap)));
        clickRow(find(choices().getWindow().getDecorView(), text(fingerLabels[fingers-1])+text(tapLabels[taps-1])));
        clickRow(find(choices().getWindow().getDecorView(),text(TAP_AREA_LEFT.equals(area)?R.string.ui_left_half:R.string.ui_full_screen)));
    }
    private void changeSignal(GestureBindingEditorDialog editor,String id) throws Exception {
        clickField(editor,R.string.ui_output); SignalPickerDialog picker=(SignalPickerDialog)field(activity,"signalPicker");
        click((View)((Map<?,?>)field(picker,"keyButtons")).get(id)); click(picker,R.string.ui_done);
        assertTrue(editor.isShowing());
    }
    private void open(GestureBinding binding) throws Exception { main(() -> call("showBindingEditor",new Class<?>[]{CustomKeyboardConfig.class,GestureBinding.class,boolean.class},config,binding,false)); settle(); }
    private GestureBindingEditorDialog editor() throws Exception { GestureBindingEditorDialog d=(GestureBindingEditorDialog)field(activity,"bindingEditor");assertNotNull(d);return d; }
    private Dialog choices() throws Exception { Dialog d=(Dialog)field(activity,"choiceDialog");assertNotNull(d);return d; }
    private GestureBinding working(GestureBindingEditorDialog editor)throws Exception{return (GestureBinding)field(editor,"working");}
    private CustomKeyboardConfig stored(){for(CustomKeyboardConfig c:store.loadKeyboardConfigs())if(c.id.equals(config.id))return c;throw new AssertionError("Saved keyboard missing");}
    private String snapshot(){return preferences.getString("custom_keyboards_json","");}
    private String text(int resource){return activity.getString(resource);}
    private String title(GestureBinding b)throws Exception{return (String)call("gestureTitle",new Class<?>[]{GestureBinding.class},b);}
    private GestureBinding tap(int fingers,int count,String area,String signal){GestureBinding b=new GestureBinding();b.kind=GESTURE_KIND_TAP;b.fingers=fingers;b.tapCount=count;b.area=area;b.motion="";b.actionId=signal;return b;}
    private void clickField(Dialog d,int resource)throws Exception{clickRow(find(d.getWindow().getDecorView(),text(resource)));}
    private void click(Dialog d,int resource)throws Exception{click(find(d.getWindow().getDecorView(),text(resource)));}
    private View clickable(View v){assertNotNull(v);while(!v.hasOnClickListeners()&&v.getParent()instanceof View)v=(View)v.getParent();assertTrue(v.hasOnClickListeners());return v;}
    private void clickRow(View v)throws Exception{click(clickable(v));}
    private void click(View v)throws Exception{assertNotNull(v);main(()->{v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);v.performClick();});settle();}
    private void back()throws Exception{getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);settle();}
    private void settle()throws Exception{Thread.sleep(180);getInstrumentation().waitForIdleSync();}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private void setField(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private Object call(String n,Class<?>[] t,Object...a)throws Exception{Method m=MainActivity.class.getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(activity,a);}
    private interface UiWork{void run()throws Exception;}
    private void main(UiWork work){getInstrumentation().runOnMainSync(()->{try{work.run();}catch(Exception e){throw new AssertionError(e);}});}
    private TextView find(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return(TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView r=find(((ViewGroup)v).getChildAt(i),s);if(r!=null)return r;}return null;}
    private void screenshot(String name)throws Exception{
        Thread.sleep(500); getInstrumentation().waitForIdleSync();
        Bitmap b=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);
        String prefix=activity.getResources().getDisplayMetrics().widthPixels<900?"small-":"";
        try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),"binding-"+prefix+name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}
    }
}
