package com.generalsx.zerohour;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Translucent PC-style keyboard overlay for the game.
 * Settings are saved in SharedPreferences and individual keys can be hidden.
 */
final class VirtualKeyboardOverlay {
    interface KeySink {
        void onKey(int scanCode, boolean down);
    }

    private static final class Key {
        final String id;
        final String label;
        final int scanCode;
        Key(String label, int scanCode) {
            this.id = label + "_" + scanCode;
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
            new Key("0", 39), new Key("-", 45), new Key("=", 46),
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

    private static final String PREFS = "generalszh_keyboard";
    private static final String PREF_SCALE = "scale_percent";
    private static final String PREF_KEY_PREFIX = "show_key_";
    private static final int MIN_SCALE = 60;
    private static final int MAX_SCALE = 125;
    private static final int DEFAULT_SCALE = 100;

    private final Activity activity;
    private final ViewGroup parent;
    private final KeySink keySink;
    private final SharedPreferences prefs;
    private Button toggle;
    private Button settingsButton;
    private LinearLayout panel;
    private LinearLayout keysContainer;
    private LinearLayout settingsPanel;
    private TextView sizeLabel;
    private int scalePercent;
    private boolean visible;
    private boolean settingsVisible;

    VirtualKeyboardOverlay(Activity activity, ViewGroup parent, KeySink keySink) {
        this.activity = activity;
        this.parent = parent;
        this.keySink = keySink;
        this.prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        this.scalePercent = clamp(prefs.getInt(PREF_SCALE, DEFAULT_SCALE), MIN_SCALE, MAX_SCALE);
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

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText("WARU  •  PC KEYBOARD");
        title.setTextColor(0xFFDDEEFF);
        title.setTextSize(11);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(4), 0, dp(4), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(28), 1f));

        settingsButton = new Button(activity);
        settingsButton.setText("⚙");
        settingsButton.setTextColor(0xFFFFFFFF);
        settingsButton.setTextSize(15);
        settingsButton.setAllCaps(false);
        settingsButton.setMinWidth(0);
        settingsButton.setMinimumWidth(0);
        settingsButton.setMinHeight(0);
        settingsButton.setMinimumHeight(0);
        settingsButton.setPadding(dp(8), 0, dp(8), 0);
        settingsButton.setBackground(background(0xD9263A50, 0x8875BFFF));
        settingsButton.setContentDescription("Keyboard settings");
        settingsButton.setOnClickListener(v -> {
            settingsVisible = !settingsVisible;
            settingsPanel.setVisibility(settingsVisible ? View.VISIBLE : View.GONE);
            settingsButton.setText(settingsVisible ? "✓" : "⚙");
        });
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(44), dp(30)));
        panel.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        settingsPanel = new LinearLayout(activity);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(5), dp(3), dp(5), dp(5));
        settingsPanel.setBackground(background(0xD91B2735, 0x886BA8D8));
        buildSettingsPanel();
        settingsPanel.setVisibility(View.GONE);
        panel.addView(settingsPanel, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        keysContainer = new LinearLayout(activity);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(keysContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildKeys();

        RelativeLayout.LayoutParams panelParams = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        panelParams.setMargins(dp(6), 0, dp(6), dp(8));
        panel.setVisibility(View.GONE);
        parent.addView(panel, panelParams);
        toggle.bringToFront();
    }

    private void buildSettingsPanel() {
        TextView sizeTitle = new TextView(activity);
        sizeTitle.setText("Keyboard size");
        sizeTitle.setTextColor(0xFFF1F6FC);
        sizeTitle.setTextSize(12);
        settingsPanel.addView(sizeTitle);

        LinearLayout sizeLine = new LinearLayout(activity);
        sizeLine.setGravity(Gravity.CENTER_VERTICAL);
        Button smaller = settingsButton("-", "Smaller keyboard");
        smaller.setOnClickListener(v -> setScalePercent(scalePercent - 10));
        sizeLine.addView(smaller, new LinearLayout.LayoutParams(dp(44), dp(34)));
        sizeLabel = new TextView(activity);
        sizeLabel.setTextColor(0xFFF1F6FC);
        sizeLabel.setTextSize(12);
        sizeLabel.setGravity(Gravity.CENTER);
        sizeLine.addView(sizeLabel, new LinearLayout.LayoutParams(0, dp(34), 1f));
        Button bigger = settingsButton("+", "Larger keyboard");
        bigger.setOnClickListener(v -> setScalePercent(scalePercent + 10));
        sizeLine.addView(bigger, new LinearLayout.LayoutParams(dp(44), dp(34)));
        settingsPanel.addView(sizeLine);
        updateSizeLabel();

        TextView keysTitle = new TextView(activity);
        keysTitle.setText("Show / hide individual keys (unchecked keys use no space)");
        keysTitle.setTextColor(0xFFDDEEFF);
        keysTitle.setTextSize(11);
        keysTitle.setPadding(0, dp(5), 0, dp(3));
        settingsPanel.addView(keysTitle);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout keyOptions = new LinearLayout(activity);
        keyOptions.setOrientation(LinearLayout.VERTICAL);
        for (int rowIndex = 0; rowIndex < ROWS.length; rowIndex++) {
            LinearLayout optionRow = new LinearLayout(activity);
            optionRow.setOrientation(LinearLayout.HORIZONTAL);
            optionRow.setGravity(Gravity.CENTER_VERTICAL);
            for (Key key : ROWS[rowIndex]) {
                android.widget.CheckBox check = new android.widget.CheckBox(activity);
                check.setText(key.label);
                check.setTextColor(0xFFF1F6FC);
                check.setTextSize(10);
                check.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF75BFFF));
                check.setPadding(0, 0, dp(2), 0);
                check.setChecked(prefs.getBoolean(PREF_KEY_PREFIX + key.id, true));
                check.setOnCheckedChangeListener((button, checked) -> {
                    prefs.edit().putBoolean(PREF_KEY_PREFIX + key.id, checked).apply();
                    rebuildKeys();
                });
                optionRow.addView(check, new LinearLayout.LayoutParams(
                    0, dp(32), 1f));
            }
            keyOptions.addView(optionRow);
        }
        scroll.addView(keyOptions);
        settingsPanel.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(130)));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END);
        Button all = settingsButton("All keys", "Enable all keys");
        all.setTextSize(10);
        all.setOnClickListener(v -> setAllKeys(true));
        actions.addView(all);
        Button none = settingsButton("Clear", "Disable all keys");
        none.setTextSize(10);
        none.setOnClickListener(v -> setAllKeys(false));
        actions.addView(none);
        Button reset = settingsButton("Reset", "Restore default keyboard settings");
        reset.setTextSize(10);
        reset.setOnClickListener(v -> resetSettings());
        actions.addView(reset);
        settingsPanel.addView(actions);
    }

    private Button settingsButton(String text, String description) {
        Button button = new Button(activity);
        button.setText(text);
        button.setContentDescription(description);
        button.setTextColor(0xFFFFFFFF);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(3), 0, dp(3), 0);
        button.setBackground(background(0xC52C3949, 0x775B7692));
        return button;
    }

    private void setScalePercent(int value) {
        scalePercent = clamp(value, MIN_SCALE, MAX_SCALE);
        prefs.edit().putInt(PREF_SCALE, scalePercent).apply();
        updateSizeLabel();
        rebuildKeys();
    }

    private void updateSizeLabel() {
        if (sizeLabel != null) sizeLabel.setText(scalePercent + "%");
    }

    private void setAllKeys(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit();
        for (Key[] row : ROWS) {
            for (Key key : row) editor.putBoolean(PREF_KEY_PREFIX + key.id, enabled);
        }
        editor.apply();
        // Replace the settings content so checkboxes reflect the saved values.
        settingsPanel.removeAllViews();
        buildSettingsPanel();
        settingsPanel.setVisibility(View.VISIBLE);
        settingsVisible = true;
        settingsButton.setText("✓");
        rebuildKeys();
    }

    private void resetSettings() {
        SharedPreferences.Editor editor = prefs.edit().remove(PREF_SCALE);
        for (Key[] row : ROWS) {
            for (Key key : row) editor.remove(PREF_KEY_PREFIX + key.id);
        }
        editor.apply();
        scalePercent = DEFAULT_SCALE;
        updateSizeLabel();
        settingsPanel.removeAllViews();
        buildSettingsPanel();
        settingsPanel.setVisibility(View.VISIBLE);
        settingsVisible = true;
        settingsButton.setText("✓");
        rebuildKeys();
    }

    private void rebuildKeys() {
        if (keysContainer == null) return;
        keysContainer.removeAllViews();
        int keyHeight = Math.max(dp(20), Math.round(dp(34) * scalePercent / 100f));
        int rowHeight = Math.max(dp(22), Math.round(dp(36) * scalePercent / 100f));
        int textSize = Math.max(7, Math.round(11 * scalePercent / 100f));

        for (Key[] row : ROWS) {
            int visibleCount = 0;
            for (Key key : row) {
                if (isKeyVisible(key)) visibleCount++;
            }
            if (visibleCount == 0) continue;
            LinearLayout keyRow = new LinearLayout(activity);
            keyRow.setOrientation(LinearLayout.HORIZONTAL);
            keyRow.setGravity(Gravity.CENTER);
            float totalWeight = 0f;
            for (Key key : row) {
                if (isKeyVisible(key)) {
                    totalWeight += "SPACE".equals(key.label) ? 2.8f
                        : (key.label.length() > 3 ? 1.35f : 1.0f);
                }
            }
            for (Key key : row) {
                if (!isKeyVisible(key)) continue;
                Button button = new Button(activity);
                button.setText(key.label);
                button.setTextColor(0xFFF1F6FC);
                button.setTextSize(Math.max(7, Math.round(
                    (key.label.length() > 3 ? 9 : 11) * scalePercent / 100f)));
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
                    0, keyHeight, weight);
                keyParams.setMargins(dp(1), dp(1), dp(1), dp(1));
                keyRow.addView(button, keyParams);
            }
            keysContainer.addView(keyRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));
        }
    }

    private boolean isKeyVisible(Key key) {
        return prefs.getBoolean(PREF_KEY_PREFIX + key.id, true);
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
