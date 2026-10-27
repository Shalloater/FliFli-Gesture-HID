package com.example.joyvolumeremote;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline animation assets and the exact same geometry used for native thumbnails. */
final class GestureDemoCatalog {
    static final class Demo {
        final String id, category, area, file;
        final boolean action;
        final int fingers, tapCount;
        final float cycle;
        final List<float[]> paths;

        Demo(JSONObject object) throws JSONException {
            id = object.getString("id"); category = object.getString("category");
            area = object.getString("area"); file = object.getString("file");
            action = "action".equals(object.getString("kind"));
            fingers = object.getInt("fingers"); tapCount = object.getInt("tapCount");
            cycle = (float) object.getDouble("cycle");
            if (!file.matches("svg/[a-z0-9_-]+\\.svg") || fingers < 1 || fingers > 3 || cycle <= 0)
                throw new JSONException("Invalid demo asset " + id);
            JSONArray contacts = object.getJSONArray("contacts");
            List<float[]> points = new ArrayList<>();
            for (int i = 0; i < contacts.length(); i++) {
                JSONArray path = contacts.getJSONObject(i).getJSONArray("path");
                if (path.length() == 0) throw new JSONException("Empty demo path");
                float[] xy = new float[path.length() * 2];
                for (int j = 0; j < path.length(); j++) {
                    JSONArray sample = path.getJSONArray(j);
                    xy[j * 2] = (float) sample.getDouble(1); xy[j * 2 + 1] = (float) sample.getDouble(2);
                    if (xy[j * 2] < 0 || xy[j * 2] > 1 || xy[j * 2 + 1] < 0 || xy[j * 2 + 1] > 1)
                        throw new JSONException("Demo coordinates outside screen");
                }
                points.add(xy);
            }
            if (points.size() != fingers) throw new JSONException("Demo contact count mismatch");
            paths = Collections.unmodifiableList(points);
        }

        GestureBinding binding() {
            GestureBinding binding = new GestureBinding();
            binding.kind = "tap".equals(category) ? KeyboardDefinitions.GESTURE_KIND_TAP : KeyboardDefinitions.GESTURE_KIND_MOTION;
            binding.fingers = fingers; binding.tapCount = tapCount; binding.area = area;
            binding.motion = action && !"tap".equals(category) ? id.substring(id.lastIndexOf(':') + 1) : "";
            binding.actionId = "none";
            return binding;
        }
    }

    private final AssetManager assets;
    private Map<String, Demo> demos;
    GestureDemoCatalog(Context context) { assets = context.getApplicationContext().getAssets(); }

    Demo find(String id) {
        if (demos == null) {
            Map<String, Demo> loaded = new LinkedHashMap<>();
            try {
                JSONArray entries = new JSONArray(read("catalog.json"));
                for (int i = 0; i < entries.length(); i++) {
                    Demo demo = new Demo(entries.getJSONObject(i));
                    if (loaded.put(demo.id, demo) != null) throw new JSONException("Duplicate demo ID");
                }
                demos = Collections.unmodifiableMap(loaded);
            } catch (IOException | JSONException e) {
                Log.e("FliFliGestureDemo", "Cannot load packaged demos", e);
                return null;
            }
        }
        return demos.get(id);
    }

    String svg(Demo demo) throws IOException { return read(demo.file); }

    private String read(String file) throws IOException {
        try (InputStream stream = assets.open("gesture_demos/" + file); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
