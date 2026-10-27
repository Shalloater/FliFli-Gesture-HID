package com.example.joyvolumeremote;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps each finger's own origin so adding or lifting fingers never creates motion. */
final class GestureStroke {
    private static final class Contact {
        final float startX, startY;
        float x, y;
        boolean active = true;
        Contact(float x, float y) { startX = this.x = x; startY = this.y = y; }
    }
    private final Map<Integer, Contact> contacts = new LinkedHashMap<>();
    private int active, fingers;
    private float maxMovement;
    private boolean lifted, consistent = true;

    void clear() { contacts.clear(); active = fingers = 0; maxMovement = 0; lifted = false; consistent = true; }
    void press(int id, float x, float y) {
        if (lifted || contacts.containsKey(id)) consistent = false;
        contacts.put(id, new Contact(x, y));
        fingers = Math.max(fingers, ++active);
    }
    void move(int id, float x, float y) {
        Contact contact = contacts.get(id);
        if (contact == null || !contact.active) return;
        contact.x = x; contact.y = y;
        maxMovement = Math.max(maxMovement, Math.max(Math.abs(x - contact.startX), Math.abs(y - contact.startY)));
    }
    void lift(int id) {
        Contact contact = contacts.get(id);
        if (contact != null && contact.active) { contact.active = false; active--; lifted = true; }
    }
    int fingers() { return fingers; }
    boolean consistent() { return consistent && fingers > 0 && fingers <= 3; }
    float maxMovement() { return maxMovement; }
    float startX() { return average(true, false); }
    float startY() { return average(true, true); }
    float x() { return average(false, false); }
    float y() { return average(false, true); }
    private float average(boolean start, boolean y) {
        float total = 0;
        for (Contact contact : contacts.values()) total += start
                ? (y ? contact.startY : contact.startX) : (y ? contact.y : contact.x);
        return contacts.isEmpty() ? 0 : total / contacts.size();
    }
}
