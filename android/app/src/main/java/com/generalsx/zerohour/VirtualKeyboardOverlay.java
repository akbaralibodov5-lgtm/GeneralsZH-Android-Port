package com.generalsx.zerohour;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Optional translucent PC-style keyboard overlay above the SDL game surface.
 * All key presses are routed to SDL3 by the Activity-provided callback.
 */
final class VirtualKeyboardOverlay {
    interface KeySink {
        void onKey(int scanCode, boolean down);
    }

    private static final class Key {
        final String label;
        final int scanCode;
        Key(String label, int scanCode) {
            this.label = label;
            this.scanCode = scanCode;
        }
    }

    private static final Key[][] ROWS = new Key[][] {
        {
            new Key("ESC", 41), new Key("F1", 58), new Key("F2", 59),
            new Key("F3", 60), new Key("F4", 61), new Key("F5", 62),
            new Key("F6", 63), new Key("F7", 64), new Key("F8", 65),
            new Key("F9", 66), new Key("F10", 67), new Key("F11", 68),
            new Key("F12", 69)
        },
        {
            new Key("1", 30), new Key("2", 31), new Key("3", 32),
            new Key("4", 33), new Key("5", 34), new Key("6", 35),
            new Key("7", 36), new Key("8", 37), new Key("9", 38),
            new Key("0", 39), new Key("-", 45), new Key("+", 46),
            new Key("⌫", 42)
        },
        {
            new Key("Q", 20), new Key("W", 26), new Key("E", 8),
            new Key("R", 21), new Key("T", 23), new Key("Y", 28),
            new Key("U", 24), new Key("I", 12), new Key("O", 18),
            new Key("P", 19), new Key("[", 47), new Key("]", 48)
        },
        {
            new Key("TAB", 43), new Key("A", 4), new Key("S", 22),
            new Key("D", 7), new Key("F", 9), new Key("G", 10),
            new Key("H", 11), new Key("J", 13), new Key("K", 14),
            new Key("L", 15), new Key("ENTER", 40)
        },
        {
            new Key("SHIFT", 225), new Key("Z", 29), new Key("X", 27),
            new Key("C", 6), new Key("V", 25), new Key("B", 5),
            new Key("N", 17), new Key("M", 16), new Key(",", 54),
            new Key(".", 55), new Key("/", 56), new Key("SHIFT", 229)
        },
        {
            new Key("CTRL", 224), new Key("ALT", 226), new Key("←", 80),
            new Key("↓", 81), new Key("↑", 82), new Key("→", 79),
            new Key("SPACE", 44), new Key("ALT", 230), new Key("CTRL", 228),
            new Key("DEL", 76)
        }
    };

    private final Activity activity;
    private final ViewGroup parent;
    private final KeySink keySink;
    private Button toggle;
    private LinearLayout panel;
    private boolean visible;

    VirtualKeyboardOverlay(Activity activity, ViewGroup parent, KeySink keySink) {
        this.activity = activity;
        this.parent = parent;
        this.keySink = keySink;
        install();
    }

    private void install() {
        toggle = new Button(activity);
        toggle.setText("⌨");
        toggle.setTextSize(20);
        toggle.setTextColor(0xFFFFFFFF);
        toggle.setAllCaps(false);
        toggle.setMinWidth(dp(48));
        toggle.setMinHeight(dp(44));
        toggle.setPadding(dp(8), dp(2), dp(8), dp(2));
        toggle.setBackground(background(0xD91B2735, 0xFF75BFFF));
        toggle.setContentDescription("Show or hide game keyboard");
        toggle.setOnClickListener(v -> setVisible(!visible));

        RelativeLayout.LayoutParams toggleParams = new RelativeLayout.LayoutParams(dp(52), dp(48));
        toggleParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        toggleParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        toggleParams.setMargins(0, dp(10), dp(10), 0);
        parent.addView(toggle, toggleParams);

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(6), dp(5), dp(6), dp(5));
        panel.setBackground(background(0xA8151D26, 0x885C9DCD));
        panel.setClickable(true);

        TextView title = new TextView(activity);
        title.setText("WARU  •  PC KEYBOARD");
        title.setTextColor(0xFFDDEEFF);
        title.setTextSize(11);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(4), 0, dp(4), dp(3));
        panel.addView(title, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        for (Key[] row : ROWS) {
            LinearLayout keyRow = new LinearLayout(activity);
            keyRow.setOrientation(LinearLayout.HORIZONTAL);
            keyRow.setGravity(Gravity.CENTER);
            for (Key key : row) {
                Button button = new Button(activity);
                button.setText(key.label);
                button.setTextColor(0xFFF1F6FC);
                button.setTextSize(key.label.length() > 3 ? 9 : 11);
                button.setAllCaps(false);
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setMinHeight(0);
                button.setMinimumHeight(0);
                button.setPadding(dp(1), 0, dp(1), 0);
                button.setBackground(background(0xC52C3949, 0x775B7692));
                button.setFocusable(false);
                button.setLongClickable(false);
                button.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        keySink.onKey(key.scanCode, true);
                        v.setAlpha(0.70f);
                        return true;
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP ||
                            event.getAction() == MotionEvent.ACTION_CANCEL) {
                        keySink.onKey(key.scanCode, false);
                        v.setAlpha(1.0f);
                        return true;
                    }
                    return true;
                });

                float weight = "SPACE".equals(key.label) ? 2.8f
                    : (key.label.length() > 3 ? 1.35f : 1.0f);
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                    0, dp(34), weight);
                keyParams.setMargins(dp(1), dp(1), dp(1), dp(1));
                keyRow.addView(button, keyParams);
            }
            panel.addView(keyRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        }

        RelativeLayout.LayoutParams panelParams = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        panelParams.setMargins(dp(6), 0, dp(6), dp(8));
        panel.setVisibility(View.GONE);
        parent.addView(panel, panelParams);
        toggle.bringToFront();
    }

    private void setVisible(boolean show) {
        visible = show;
        panel.setVisibility(show ? View.VISIBLE : View.GONE);
        toggle.setText(show ? "✕" : "⌨");
        if (show) panel.bringToFront();
        toggle.bringToFront();
    }

    private GradientDrawable background(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(6));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
