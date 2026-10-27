package com.example.joyvolumeremote;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import java.util.Locale;

/** Appearance and language preferences are independent of keyboard configurations. */
final class AppPreferences {
    static final String[] SKINS = {"Blue", "Pink", "Purple", "Green", "Black"};
    static final String[] LANGUAGES = {"en", "zh-CN", "zh-TW", "ko", "ja"};
    static final String[] LANGUAGE_NAMES = {"English", "简体中文", "繁體中文", "한국어", "日本語"};
    // Large surfaces stay airy; separate foreground tones keep small text legible.
    private static final String[] COLORS = {"#A9DDF7", "#F6C1D5", "#D5C7F2", "#B9E9DA", "#DEE3EA"};
    private static final String[] FOREGROUNDS = {"#205F82", "#84445D", "#604985", "#2A6859", "#3E4653"};
    private final Context context;
    private final SharedPreferences preferences;

    AppPreferences(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE);
    }

    int skin() { return Math.max(0, Math.min(4, preferences.getInt("skin", 0))); }
    int accent() { return Color.parseColor(FOREGROUNDS[skin()]); }
    int primary() { return color(skin()); }
    static int color(int index) { return Color.parseColor(COLORS[index]); }
    int tint() {
        int color = primary();
        return Color.rgb(204 + Color.red(color) / 5,
                204 + Color.green(color) / 5, 204 + Color.blue(color) / 5);
    }

    String language() {
        String saved = preferences.getString("language", "");
        if (!saved.isEmpty()) return saved;
        Configuration configuration = context.getResources().getConfiguration();
        Locale locale = Build.VERSION.SDK_INT >= 24 ? configuration.getLocales().get(0) : configuration.locale;
        if (locale.getLanguage().equals("zh")) {
            return locale.getCountry().equals("TW") || locale.getCountry().equals("HK")
                    || locale.getScript().equals("Hant") ? "zh-TW" : "zh-CN";
        }
        return locale.getLanguage().equals("ko") ? "ko" : locale.getLanguage().equals("ja") ? "ja" : "en";
    }

    void setLanguage(String language) { preferences.edit().putString("language", language).apply(); }

    static Context localized(Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(new AppPreferences(context).language()));
        return context.createConfigurationContext(config);
    }

    void setSkin(int index) {
        PackageManager manager = context.getPackageManager();
        // Enable the new entry first so there is always a launchable activity on older Android versions.
        manager.setComponentEnabledSetting(component(index), PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        for (int i = 0; i < SKINS.length; i++) {
            if (i != index) manager.setComponentEnabledSetting(component(i),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
        preferences.edit().putInt("skin", index).apply();
    }

    private ComponentName component(int index) {
        return new ComponentName(context, context.getPackageName() + ".Launcher" + SKINS[index]);
    }
}
