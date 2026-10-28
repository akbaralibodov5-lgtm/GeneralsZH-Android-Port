package com.generalsx.zerohour;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Visible Waru cheat-menu shell.
 *
 * This class deliberately does not pretend that UI toggles modify the game:
 * the deterministic native simulation commands and multiplayer negotiation
 * must be implemented before the controls can safely be enabled.
 */
final class WaruCheatMenuOverlay extends FrameLayout {
    private final int dp;
    private final Button launcher;
    private final LinearLayout panel;
    private boolean expanded;

    WaruCheatMenuOverlay(Context context) {
        super(context);
        dp = Math.round(getResources().getDisplayMetrics().density);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);

        launcher = new Button(context);
        launcher.setText("⚡ WARU CHEATS");
        launcher.setTextSize(12);
        launcher.setAllCaps(false);
        launcher.setTextColor(Color.WHITE);
        launcher.setBackground(background(0xEE102B46, 0xFF25B8FF));
        launcher.setMinHeight(dp(42));
        launcher.setPadding(dp(12), 0, dp(12), 0);
        launcher.setOnClickListener(v -> setExpanded(!expanded));
        FrameLayout.LayoutParams launcherParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, dp(44), Gravity.TOP | Gravity.END);
        launcherParams.setMargins(0, dp(72), dp(12), 0);
        addView(launcher, launcherParams);

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(background(0xF018202D, 0xFF25B8FF));
        panel.setElevation(dp(10));
        panel.setVisibility(GONE);

        TextView title = text(context, "WARU EDITION  •  CHEAT MENU", 16, Color.WHITE, true);
        panel.addView(title, matchWrap());

        TextView mode = text(context, "LOCAL / OFFLINE TEST MENU", 11, 0xFF75D5FF, true);
        LinearLayout.LayoutParams modeParams = matchWrap();
        modeParams.topMargin = dp(4);
        panel.addView(mode, modeParams);

        TextView status = text(context,
                "UI is connected. Game-engine cheat actions are not wired yet; controls stay disabled to prevent fake or unsafe changes.",
                12, 0xFFFFD27D, false);
        status.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(8);
        statusParams.bottomMargin = dp(8);
        panel.addView(status, statusParams);

        addDisabledSwitch(context, "O‘lmas birliklar", "Friendly units take no damage");
        addDisabledSwitch(context, "Cheksiz elektr", "Keep power available");
        Button cash = new Button(context);
        cash.setText("+100 000 PUL");
        cash.setAllCaps(false);
        cash.setTextColor(0xFFB7C0CB);
        cash.setEnabled(false);
        cash.setContentDescription("Add 100,000 cash; disabled until native game integration is complete");
        LinearLayout.LayoutParams cashParams = matchWrap();
        cashParams.topMargin = dp(5);
        panel.addView(cash, cashParams);

        Button close = new Button(context);
        close.setText("Yashirish");
        close.setAllCaps(false);
        close.setOnClickListener(v -> setExpanded(false));
        LinearLayout.LayoutParams closeParams = matchWrap();
        closeParams.topMargin = dp(5);
        panel.addView(close, closeParams);

        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(false);
        scroller.addView(panel);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                dp(286), LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        panelParams.setMargins(0, dp(122), dp(12), dp(12));
        addView(scroller, panelParams);
    }

    private void addDisabledSwitch(Context context, String label, String description) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        Switch toggle = new Switch(context);
        toggle.setText(label);
        toggle.setTextColor(0xFFB7C0CB);
        toggle.setEnabled(false);
        row.addView(toggle, matchWrap());

        TextView hint = text(context, description, 11, 0xFF98A8BA, false);
        row.addView(hint, matchWrap());
        panel.addView(row, matchWrap());
    }

    private TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable background(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setExpanded(boolean value) {
        expanded = value;
        panel.setVisibility(value ? VISIBLE : GONE);
        launcher.setText(value ? "⚡ CHEATS ▲" : "⚡ WARU CHEATS");
        if (value) panel.bringToFront();
        launcher.bringToFront();
    }
}
