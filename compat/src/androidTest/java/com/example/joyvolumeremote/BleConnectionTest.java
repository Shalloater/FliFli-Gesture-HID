package com.example.joyvolumeremote;

import android.app.NotificationManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/** Callback regressions on Android objects; intentionally does not establish a radio link. */
public class BleConnectionTest extends InstrumentationTestCase {
    private HidRemoteService service;
    private BluetoothDevice device;
    private BleGattProfile profile;
    private BluetoothGattServerCallback callback;

    private static final class TestService extends HidRemoteService {
        TestService(Context context) { attachBaseContext(context); }
    }

    @Override protected void setUp() throws Exception {
        super.setUp();
        getInstrumentation().runOnMainSync(() -> service = new TestService(getInstrumentation().getTargetContext()));
        device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("02:00:00:00:00:42");
        profile = new BleGattProfile();
        setField("profile", profile);
        setField("selectedHost", device);
        Method method = HidRemoteService.class.getDeclaredMethod("callbacks", int.class);
        method.setAccessible(true);
        callback = (BluetoothGattServerCallback) method.invoke(service, 0);
    }

    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(service::onDestroy);
        NotificationManager notifications = (NotificationManager) getInstrumentation().getTargetContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notifications.cancel(21);
        super.tearDown();
    }

    public void testDuplicateConnectedCallbackPreservesInputSubscription() throws Exception {
        setField("peer", device);
        subscriptions().add(profile.keyboard);
        callback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
        getInstrumentation().waitForIdleSync();
        assertSame(device, field("peer"));
        assertTrue(subscriptions().contains(profile.keyboard));
        assertNull("A subscription without a real bond cannot enable input", service.getConnectedHost());
    }

    public void testConnectingCallbackDoesNotDisconnectExistingPeer() throws Exception {
        setField("peer", device);
        subscriptions().add(profile.keyboard);
        callback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTING);
        getInstrumentation().waitForIdleSync();
        assertSame(device, field("peer"));
        assertTrue(subscriptions().contains(profile.keyboard));
    }

    public void testPendingConnectionFailureShowsGattStatusWithoutEnablingInput() throws Exception {
        setField("pendingHost", device);
        callback.onConnectionStateChange(device, 133, BluetoothProfile.STATE_DISCONNECTED);
        getInstrumentation().waitForIdleSync();
        assertNull(field("pendingHost"));
        assertTrue(service.getStatus().contains("GATT 状态 133"));
        assertTrue(service.getStatus().contains("尚未建立本 App 的 BLE 键盘连接"));
        assertNull(service.getConnectedHost());
    }

    public void testTimeoutClearsAttemptAndDoesNotClaimKeyboardConnection() throws Exception {
        setField("pendingHost", device);
        getInstrumentation().runOnMainSync((Runnable) field("connectionTimeout"));
        assertNull(field("pendingHost"));
        assertTrue(service.getStatus().contains("连接尝试超时"));
        assertNull(service.getConnectedHost());
    }

    public void testTimeoutDiagnosticIsFullyRenderedOnConnectionPage() throws Exception {
        setField("pendingHost", device);
        getInstrumentation().runOnMainSync((Runnable) field("connectionTimeout"));
        String message = service.getStatus();
        Activity activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            getInstrumentation().waitForIdleSync();
            Method show = MainActivity.class.getDeclaredMethod("showSetupPage");
            Method update = MainActivity.class.getDeclaredMethod("updateStatus", String.class);
            show.setAccessible(true);
            update.setAccessible(true);
            getInstrumentation().runOnMainSync(() -> {
                try { show.invoke(activity); update.invoke(activity, message); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            getInstrumentation().waitForIdleSync();
            Field subtitleField = MainActivity.class.getDeclaredField("connectionSubtitleView");
            subtitleField.setAccessible(true);
            TextView subtitle = (TextView) subtitleField.get(activity);
            assertEquals(message, subtitle.getText().toString());
            getInstrumentation().runOnMainSync(() -> {
                assertTrue(subtitle.isShown());
                assertNotNull(subtitle.getLayout());
                for (int line = 0; line < subtitle.getLayout().getLineCount(); line++) {
                    assertEquals("Diagnostic must wrap rather than be ellipsized", 0, subtitle.getLayout().getEllipsisCount(line));
                }
            });
            getInstrumentation().getUiAutomation().waitForIdle(500, 3000);
            Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            File file = new File(getInstrumentation().getTargetContext().getFilesDir(), "ble-timeout-diagnostic.png");
            try (FileOutputStream output = new FileOutputStream(file)) {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            screenshot.recycle();
        } finally {
            getInstrumentation().runOnMainSync(activity::finish);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<BluetoothGattCharacteristic> subscriptions() throws Exception {
        return (Set<BluetoothGattCharacteristic>) field("subscribed");
    }
    private Object field(String name) throws Exception {
        Field field = HidRemoteService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(service);
    }
    private void setField(String name, Object value) throws Exception {
        Field field = HidRemoteService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
