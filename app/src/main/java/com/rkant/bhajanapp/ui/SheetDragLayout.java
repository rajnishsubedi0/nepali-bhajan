package com.rkant.bhajanapp.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

public class SheetDragLayout extends FrameLayout {

    public interface DragCallback {
        void onSheetDrag(float fraction);

        void onSheetRelease(float fraction, float velocityY);
    }

    private DragCallback callback;
    private VelocityTracker velocityTracker;

    private float downX, downY;
    private boolean dragging = false;
    private int touchSlop;

    public SheetDragLayout(Context context) {
        super(context);
        init();
    }

    public SheetDragLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SheetDragLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    public void setDragCallback(DragCallback cb) {
        this.callback = cb;
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getRawX();
                downY = ev.getRawY();
                dragging = false;
                return false;

            case MotionEvent.ACTION_MOVE: {
                float dy = ev.getRawY() - downY;
                float dx = Math.abs(ev.getRawX() - downX);
                if (!dragging && dy > touchSlop && dy > dx * 1.2f) {
                    dragging = true;
                    return true; // children receive CANCEL, we take over
                }
                return false;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return false;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getRawX();
                downY = ev.getRawY();
                dragging = true;
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(ev);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    if (velocityTracker != null) velocityTracker.addMovement(ev);
                    float dy = ev.getRawY() - downY;
                    float fraction = clamp01(dy / Math.max(1, getHeight()));
                    if (callback != null) callback.onSheetDrag(fraction);
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    float vy = 0f;
                    if (velocityTracker != null) {
                        velocityTracker.addMovement(ev);
                        velocityTracker.computeCurrentVelocity(1000);
                        vy = velocityTracker.getYVelocity();
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                    float dy = ev.getRawY() - downY;
                    float fraction = clamp01(dy / Math.max(1, getHeight()));
                    dragging = false;
                    if (callback != null) callback.onSheetRelease(fraction, vy);
                    return true;
                }
                return false;
        }
        return super.onTouchEvent(ev);
    }
}