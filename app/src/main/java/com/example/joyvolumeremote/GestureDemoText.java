package com.example.joyvolumeremote;

import android.content.Context;
import android.text.Html;
import java.util.Locale;
import java.util.regex.Matcher;

/** Localize only text nodes in the approved SVG; geometry and timing remain untouched. */
final class GestureDemoText {
    static String instruction(Context context, GestureDemoCatalog.Demo demo) {
        int resource = !demo.action ? R.string.demo_signal_instruction : "tap".equals(demo.category)
                ? R.string.demo_tap_instruction : "circle".equals(demo.category) ? R.string.demo_circle_instruction
                : demo.fingers > 1 ? R.string.demo_multi_swipe_instruction : R.string.demo_swipe_instruction;
        return context.getString(resource);
    }

    static String localize(Context context, AppPreferences appearance, GestureDemoCatalog.Demo demo, String title, String svg) {
        svg = svg.replace("var(--accent,#604985)", color(appearance.accent()))
                .replace("var(--soft,#d5c7f2)", color(appearance.primary()));
        svg = svg.replaceAll("(?s)(<title[^>]*>).*?(</title>)", "$1" + Matcher.quoteReplacement(Html.escapeHtml(title)) + "$2");
        svg = svg.replaceAll("(?s)(<desc[^>]*>).*?(</desc>)", "$1" + Matcher.quoteReplacement(Html.escapeHtml(instruction(context, demo))) + "$2");
        String[] source = {"本机 · 触发动作", "受控端 · 触控信号", "实心触点为按下，淡出为抬起", "同组触点一起操作",
                "准备", "按下", "沿箭头滑动", "抬起 · 结束", "抬起", "抬起全部手指", "一起按下", "全部抬起",
                "顺时针画圈", "逆时针画圈", "抬起 · 完成", "等待下一轮"};
        int[] resources = {R.string.demo_action_role, R.string.demo_signal_role, R.string.demo_contact_legend, R.string.demo_group_legend,
                R.string.demo_ready, R.string.demo_press, R.string.demo_move, R.string.demo_finish, R.string.demo_release,
                R.string.demo_release_all, R.string.demo_press_together, R.string.demo_release_all,
                R.string.ui_clockwise_circle, R.string.ui_counterclockwise_circle, R.string.demo_finish, R.string.demo_wait};
        for (int i = 0; i < source.length; i++) svg = replaceText(svg, source[i], context.getString(resources[i]));
        for (int i = 1; i <= 3; i++) {
            svg = replaceText(svg, "第 " + i + " 次按下", context.getString(R.string.demo_press_step, i));
            svg = replaceText(svg, "第 " + i + " 次轻点", context.getString(R.string.demo_tap_step, i));
        }
        String[] areas = {"全屏", "左半屏", "右半屏", "上半屏", "下半屏"};
        int[] areaResources = {R.string.ui_full_screen, R.string.ui_left_half, R.string.ui_right_half, R.string.ui_top_half, R.string.ui_bottom_half};
        for (int i = 0; i < areas.length; i++) svg = replaceText(svg, areas[i] + " · 有效区域",
                context.getString(R.string.demo_active_area, context.getString(areaResources[i])));
        return svg;
    }

    private static String replaceText(String svg, String source, String text) {
        return svg.replace(">" + source + "<", ">" + Html.escapeHtml(text) + "<");
    }
    private static String color(int color) { return String.format(Locale.US, "#%06X", color & 0xffffff); }
    private GestureDemoText() {}
}
