package com.generalsx.zerohour;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Small in-game simulation-speed control. Native code is the authority: it
 * returns 1x whenever a network session is active, so the UI cannot accelerate
 * a multiplayer match even if a touch is received.
 */
final class WaruGameSpeedOverlay extends View {
    private static final String PREFS = "waru_game_keyboard";
    private static final String SPEED_KEY = "single_player_speed";
    private static final float[] SPEEDS = {0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f};
    private final SharedPreferences prefs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float speed;
    private final RectF minus = new RectF();
    private final RectF value = new RectF();
    private final RectF plus = new RectF();

    WaruGameSpeedOverlay(Context context) {
        super(context);
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        float saved = prefs.getFloat(SPEED_KEY, 1f);
        speed = normalize(nativeSetGameSpeed(saved));
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setClickable(true);
    }

    private static float normalize(float candidate) {
        for (float item : SPEEDS) if (Math.abs(item - candidate) < 0.001f) return item;
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
        canvas.drawText(label, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2, paint);
        paint.setFakeBoldText(false);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = dp(42), h = dp(36), gap = dp(5);
        float right = getWidth() - dp(12);
        float top = dp(76);
        plus.set(right - w, top, right, top + h);
        value.set(plus.left - dp(70) - gap, top, plus.left - gap, top + h);
        minus.set(value.left - w - gap, top, value.left - gap, top + h);
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
            if (Math.abs(SPEEDS[i] - speed) < 0.001f) { index = i; break; }
        }
        index = Math.max(0, Math.min(SPEEDS.length - 1, index + direction));
        float accepted = normalize(nativeSetGameSpeed(SPEEDS[index]));
        speed = accepted;
        // Only persist the engine-accepted value. A network game always returns 1x.
        prefs.edit().putFloat(SPEED_KEY, speed).apply();
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Only claim touches inside our three controls; let battlefield and
            // keyboard touches fall through to the existing overlay underneath.
            return minus.contains(event.getX(), event.getY())
                    || value.contains(event.getX(), event.getY())
                    || plus.contains(event.getX(), event.getY());
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX(), y = event.getY();
            if (minus.contains(x, y)) change(-1);
            else if (plus.contains(x, y)) change(1);
            else if (value.contains(x, y)) {
                speed = normalize(nativeSetGameSpeed(1f));
                prefs.edit().putFloat(SPEED_KEY, speed).apply();
                invalidate();
            } else {
                return false;
            }
            performClick();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        nativeSetGameSpeed(1f);
        super.onDetachedFromWindow();
    }

    private static native float nativeSetGameSpeed(float multiplier);
}
