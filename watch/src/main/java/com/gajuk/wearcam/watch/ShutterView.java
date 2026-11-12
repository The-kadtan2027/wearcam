package com.gajuk.wearcam.watch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public class ShutterView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean video;
    private boolean pressed;
    private Runnable action;

    public ShutterView(Context context) {
        super(context);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(4));
        ring.setColor(0xFFFFFFFF);
        inner.setStyle(Paint.Style.FILL);
        inner.setColor(0xFFFFFFFF);
    }

    public void setAction(Runnable action) {
        this.action = action;
    }

    public void setVideo(boolean video) {
        this.video = video;
        inner.setColor(video ? 0xFFFF3B30 : 0xFFFFFFFF);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - dp(4);
        canvas.drawCircle(cx, cy, radius, ring);
        canvas.drawCircle(cx, cy, radius * (video ? 0.62f : 0.78f), inner);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                setAlpha(0.6f);
                return true;
            case MotionEvent.ACTION_UP:
                setAlpha(1f);
                if (pressed && action != null) action.run();
                pressed = false;
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                setAlpha(1f);
                pressed = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
