package com.example.joyvolumeremote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.test.InstrumentationTestCase;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Tests the production session scheduler with a recording transport, without a radio connection. */
public class TouchSessionTest extends InstrumentationTestCase {
    private static final class RecordingService extends HidRemoteService {
        final List<byte[]> sent = new ArrayList<>();
        boolean ready = true;
        RecordingService(Context context) { attachBaseContext(context); }
        @Override protected boolean hasTouchConnection() { return ready; }
        @Override protected boolean transmitTouchReport(BluetoothDevice host, byte[] report) {
            sent.add(report.clone()); return true;
        }
        @Override protected void sendAllReleasedState() { sent.add(new TouchState().report()); }
    }
    private RecordingService service;

    @Override protected void setUp() throws Exception {
        super.setUp();
        getInstrumentation().runOnMainSync(() -> service = new RecordingService(getInstrumentation().getTargetContext()));
        Field host = HidRemoteService.class.getDeclaredField("connectedHost"); host.setAccessible(true);
        host.set(service, BluetoothAdapter.getDefaultAdapter().getRemoteDevice("02:00:00:00:00:42"));
    }

    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> service.sendTouchState(null));
        java.lang.reflect.Method method = HidRemoteService.class.getDeclaredMethod("cancelTouchStream", boolean.class);
        method.setAccessible(true);
        getInstrumentation().runOnMainSync(() -> {
            try { method.invoke(service, false); } catch (Exception e) { throw new AssertionError(e); }
        });
        super.tearDown();
    }

    public void testRepeatedCloseAndReopenRetainsTheFirstContactEveryTime() throws Exception {
        TouchState state = new TouchState(); state.press(7, 50, 70, 101, 101);
        for (int i = 0; i < 8; i++) {
            getInstrumentation().runOnMainSync(() -> {
                assertTrue(service.sendTouchState(state.report()));
                assertEquals("A released reset precedes the next session", 0, TouchState.mask(last()));
            });
            settle();
            assertEquals(1, TouchState.mask(last()));
            getInstrumentation().runOnMainSync(() -> service.sendTouchState(null));
            assertEquals(0, TouchState.mask(last()));
        }
    }

    public void testOldReleaseCallbackCannotLiftTheNewSessionsContact() throws Exception {
        TouchState state = new TouchState(); state.press(0, 50, 50, 101, 101);
        getInstrumentation().runOnMainSync(() -> service.sendTouchState(state.report())); settle();
        getInstrumentation().runOnMainSync(() -> service.sendTouchState(null));
        Field pending = HidRemoteService.class.getDeclaredField("pendingTouchRelease"); pending.setAccessible(true);
        Runnable old = (Runnable) pending.get(service);
        assertNotNull(old);
        getInstrumentation().runOnMainSync(() -> service.sendTouchState(state.report())); settle();
        int before = service.sent.size();
        getInstrumentation().runOnMainSync(old);
        assertEquals(before, service.sent.size());
        assertEquals(1, TouchState.mask(last()));
    }

    public void testRepeatedNullCallbacksRetainTheFinalReleaseRetry() throws Exception {
        TouchState state = new TouchState(); state.press(0, 50, 50, 101, 101);
        getInstrumentation().runOnMainSync(() -> service.sendTouchState(state.report())); settle();
        getInstrumentation().runOnMainSync(() -> {
            service.sent.clear(); service.sendTouchState(null); service.sendTouchState(null); service.sendTouchState(null);
        });
        settle();
        assertEquals(2, service.sent.size());
        assertEquals(0, TouchState.mask(service.sent.get(0)));
        assertEquals(0, TouchState.mask(service.sent.get(1)));
        assertEquals("Release retry must terminate the contact frame", 0, service.sent.get(1)[12]);
    }

    public void testSingleFingerInputContinuesAfterRepeatedCancellation() throws Exception {
        TouchState state = new TouchState();
        for (int round = 0; round < 5; round++) {
            state.clear(); state.press(5, 20, 50, 101, 101); send(state);
            getInstrumentation().runOnMainSync(() -> service.sendTouchState(null));
            settle();
            assertEquals(0, TouchState.mask(last())); assertEquals(0, last()[12]);
            state.clear(); state.press(20, 50, 50, 101, 101); send(state);
            assertEquals(1, TouchState.mask(last())); assertEquals(1, last()[12]);
            state.lift(20); send(state);
            assertEquals(0, TouchState.mask(last())); assertEquals(0, last()[12]);
        }
    }

    private void send(TouchState state) throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            assertTrue(service.sendTouchState(state.report())); state.frameSent();
        });
        settle();
    }

    private byte[] last() { return service.sent.get(service.sent.size() - 1); }
    private void settle() throws Exception { Thread.sleep(80); getInstrumentation().waitForIdleSync(); }
}
