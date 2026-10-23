package com.generalsx.zerohour;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Compact speed shortcut. The corner button toggles the controls open/closed;
 * when closed, the rest of the game surface is completely click-through.
 * Native code is authoritative and forces 1x in network/replay modes.
 */
final class WaruGameSpeedOverlay extends View {
    private static final String PREFS = "waru_game_keyboard";
    private static final String SPEED_KEY = "single_player_speed";
    private static final float[] SPEEDS = {0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f};

    private final SharedPreferences prefs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF toggle = new RectF();
    private final RectF minus = new RectF();
    private final RectF value = new RectF();
    private final RectF plus = new RectF();
    private boolean expanded = false;
    private float speed;

    WaruGameSpeedOverlay(Context context) {
        super(context);
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        speed = normalize(nativeSetGameSpeed(prefs.getFloat(SPEED_KEY, 1f)));
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setClickable(true);
    }

    private static float normalize(float candidate) {
        for (float item : SPEEDS) {
            if (Math.abs(item - candidate) < 0.001f) return item;
        }
        return 1f;
    }

    private int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void drawButton(Canvas canvas, RectF rect, String label, boolean active) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? 0xe625b8ff : 0xd91a2636);
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0xe625b8ff);
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xfff3f7ff);
        paint.setTextSize(dp(13));
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(label, rect.centerX(),
                rect.centerY() - (fm.ascent + fm.descent) / 2, paint);
        paint.setFakeBoldText(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float right = getWidth() - dp(12);
        float bottom = getHeight() - dp(12);

        // Small, unobtrusive corner shortcut while the speed controls are closed.
        float toggleSize = dp(42);
        toggle.set(right - toggleSize, bottom - toggleSize, right, bottom);
        drawButton(canvas, toggle, expanded ? "×" : "»", expanded);

        if (!expanded) return;

        float h = dp(38);
        float gap = dp(5);
        float plusW = dp(42);
        float valueW = dp(70);
        float minusW = dp(42);
        float panelRight = right;
        float panelBottom = toggle.top - dp(8);
        float panelTop = panelBottom - h;
        plus.set(panelRight - plusW, panelTop, panelRight, panelBottom);
        value.set(plus.left - gap - valueW, panelTop, plus.left - gap, panelBottom);
        minus.set(value.left - gap - minusW, panelTop, value.left - gap, panelBottom);

        drawButton(canvas, minus, "−", false);
        drawButton(canvas, value, label(speed), speed == 1f);
        drawButton(canvas, plus, "+", false);
    }

    private static String label(float value) {
        if (value == (int) value) return ((int) value) + "×";
        return value + "×";
    }

    private void change(int direction) {
        int index = 3;
        for (int i = 0; i < SPEEDS.length; i++) {
            if (Math.abs(SPEEDS[i] - speed) < 0.001f) {
                index = i;
                break;
            }
        }
        index = Math.max(0, Math.min(SPEEDS.length - 1, index + direction));
        speed = normalize(nativeSetGameSpeed(SPEEDS[index]));
        prefs.edit().putFloat(SPEED_KEY, speed).apply();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Only the corner toggle and, while open, the three settings buttons
            // consume input. Every other touch passes through to the game/keyboard.
            return toggle.contains(x, y)
                    || (expanded && (minus.contains(x, y)
                    || value.contains(x, y) || plus.contains(x, y)));
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (toggle.contains(x, y)) {
                expanded = !expanded;
            } else if (expanded && minus.contains(x, y)) {
                change(-1);
            } else if (expanded && plus.contains(x, y)) {
                change(1);
            } else if (expanded && value.contains(x, y)) {
                speed = normalize(nativeSetGameSpeed(1f));
                prefs.edit().putFloat(SPEED_KEY, speed).apply();
            } else {
                return false;
            }
            invalidate();
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        nativeSetGameSpeed(1f);
        super.onDetachedFromWindow();
    }

    private static native float nativeSetGameSpeed(float multiplier);
}
