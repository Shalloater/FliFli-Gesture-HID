package com.example.joyvolumeremote;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

final class BleGattProfile {
    static UUID uuid(int shortUuid) {
        return UUID.fromString(String.format(java.util.Locale.ROOT, "0000%04x-0000-1000-8000-00805f9b34fb", shortUuid));
    }
    static final UUID HID_UUID = uuid(0x1812);
    static final UUID CCC_UUID = uuid(0x2902);
    static final UUID REFERENCE_UUID = uuid(0x2908);
    final BluetoothGattCharacteristic keyboard = input(0x2A4D, 8, BleHidReports.KEYBOARD_ID);
    final BluetoothGattCharacteristic consumer = input(0x2A4D, 2, BleHidReports.CONSUMER_ID);
    final BluetoothGattCharacteristic bootKeyboard = input(0x2A22, 8, 0);
    final BluetoothGattCharacteristic keyboardOutput = output(0x2A4D, true);
    final BluetoothGattCharacteristic bootOutput = output(0x2A32, false);
    final BluetoothGattCharacteristic protocol = characteristic(0x2A4E,
            BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED | BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED, new byte[]{1});
    final BluetoothGattCharacteristic control = characteristic(0x2A4C,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED, new byte[]{1});

    List<BluetoothGattService> services(int batteryPercent) {
        BluetoothGattService hid = new BluetoothGattService(HID_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        hid.addCharacteristic(characteristic(0x2A4A, BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ, new byte[]{0x11, 0x01, 0x00, 0x02}));
        hid.addCharacteristic(characteristic(0x2A4B, BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED, BleHidReports.REPORT_MAP));
        for (BluetoothGattCharacteristic c : Arrays.asList(protocol, control, keyboard, keyboardOutput, consumer, bootKeyboard, bootOutput)) hid.addCharacteristic(c);
        BluetoothGattService battery = new BluetoothGattService(uuid(0x180F), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        battery.addCharacteristic(characteristic(0x2A19, BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ, new byte[]{(byte) batteryPercent}));
        BluetoothGattService info = new BluetoothGattService(uuid(0x180A), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        info.addCharacteristic(characteristic(0x2A29, BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ, "FliFli".getBytes(StandardCharsets.UTF_8)));
        info.addCharacteristic(characteristic(0x2A24, BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ, "BLE experimental keyboard".getBytes(StandardCharsets.UTF_8)));
        // Never advertise a fabricated battery level if the system cannot supply one.
        return batteryPercent >= 0 ? Arrays.asList(hid, battery, info) : Arrays.asList(hid, info);
    }

    private static BluetoothGattCharacteristic input(int uuid, int bytes, int reportId) {
        BluetoothGattCharacteristic c = characteristic(uuid,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED, new byte[bytes]);
        BluetoothGattDescriptor ccc = new BluetoothGattDescriptor(CCC_UUID,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED | BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED);
        ccc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        c.addDescriptor(ccc);
        if (reportId != 0) addReference(c, reportId, 1);
        return c;
    }

    private static BluetoothGattCharacteristic output(int uuid, boolean reference) {
        BluetoothGattCharacteristic c = characteristic(uuid,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED | BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED, new byte[]{0});
        if (reference) addReference(c, BleHidReports.KEYBOARD_ID, 2);
        return c;
    }

    private static void addReference(BluetoothGattCharacteristic c, int id, int type) {
        BluetoothGattDescriptor reference = new BluetoothGattDescriptor(REFERENCE_UUID, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED);
        reference.setValue(new byte[]{(byte) id, (byte) type});
        c.addDescriptor(reference);
    }

    private static BluetoothGattCharacteristic characteristic(int uuid, int properties, int permissions, byte[] value) {
        BluetoothGattCharacteristic c = new BluetoothGattCharacteristic(uuid(uuid), properties, permissions);
        c.setValue(value.clone());
        return c;
    }
}
