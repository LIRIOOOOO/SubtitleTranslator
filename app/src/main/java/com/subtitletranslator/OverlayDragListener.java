package com.subtitletranslator;

import android.view.*;

public class OverlayDragListener implements View.OnTouchListener {

    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final View view;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private long touchDownTime;

    public OverlayDragListener(WindowManager wm, WindowManager.LayoutParams p, View v) {
        this.windowManager = wm;
        this.params = p;
        this.view = v;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = params.x;
                initialY = params.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                touchDownTime = System.currentTimeMillis();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - initialTouchX;
                float dy = event.getRawY() - initialTouchY;
                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                    params.x = initialX + (int) dx;
                    params.y = initialY - (int) dy;
                    try {
                        windowManager.updateViewLayout(view, params);
                    } catch (Exception e) { }
                }
                return true;

            case MotionEvent.ACTION_UP:
                long duration = System.currentTimeMillis() - touchDownTime;
                if (duration < 200 && Math.abs(event.getRawX() - initialTouchX) < 10) {
                    return false;
                }
                return true;
        }
        return false;
    }
}
