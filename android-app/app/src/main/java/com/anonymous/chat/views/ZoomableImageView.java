package com.anonymous.chat.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class ZoomableImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 5.0f;
    private static final float DOUBLE_TAP_TARGET_SCALE = 2.5f;

    private final Matrix baseMatrix = new Matrix();
    private final Matrix drawMatrix = new Matrix();

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float currentScale = 1.0f;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private boolean isDragging = false;

    private int viewWidth = 0;
    private int viewHeight = 0;
    private int drawableWidth = 0;
    private int drawableHeight = 0;

    private ValueAnimator zoomAnimator;
    private OnClickListener singleTapListener;

    public ZoomableImageView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                float prevScale = currentScale;
                currentScale *= scaleFactor;

                if (currentScale < MIN_SCALE * 0.75f) {
                    currentScale = MIN_SCALE * 0.75f;
                    scaleFactor = currentScale / prevScale;
                } else if (currentScale > MAX_SCALE * 1.25f) {
                    currentScale = MAX_SCALE * 1.25f;
                    scaleFactor = currentScale / prevScale;
                }

                drawMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                checkMatrixBounds();
                setImageMatrix(drawMatrix);
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                if (currentScale < MIN_SCALE) {
                    animateScaleTo(MIN_SCALE, viewWidth / 2f, viewHeight / 2f);
                } else if (currentScale > MAX_SCALE) {
                    animateScaleTo(MAX_SCALE, detector.getFocusX(), detector.getFocusY());
                }
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (singleTapListener != null) {
                    singleTapListener.onClick(ZoomableImageView.this);
                    return true;
                }
                return performClick();
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (currentScale > MIN_SCALE * 1.2f) {
                    animateScaleTo(MIN_SCALE, viewWidth / 2f, viewHeight / 2f);
                } else {
                    animateScaleTo(DOUBLE_TAP_TARGET_SCALE, e.getX(), e.getY());
                }
                return true;
            }
        });
    }

    public void setOnSingleTapListener(OnClickListener listener) {
        this.singleTapListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getDrawable() == null) {
            return super.onTouchEvent(event);
        }

        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (zoomAnimator != null && zoomAnimator.isRunning()) {
                    zoomAnimator.cancel();
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDragging = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                isDragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                // Only pan/drag when single touch, not scaling, and zoomed in
                if (!scaleDetector.isInProgress() && event.getPointerCount() == 1 && currentScale > 1.01f) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;

                    if (!isDragging && (Math.abs(dx) > 4 || Math.abs(dy) > 4)) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        drawMatrix.postTranslate(dx, dy);
                        checkMatrixBounds();
                        setImageMatrix(drawMatrix);
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                // When one pointer lifts from pinch, immediately re-anchor lastTouch to the remaining pointer
                int liftedIndex = event.getActionIndex();
                int remainingIndex = (liftedIndex == 0) ? 1 : 0;
                if (event.getPointerCount() > 1 && remainingIndex < event.getPointerCount()) {
                    lastTouchX = event.getX(remainingIndex);
                    lastTouchY = event.getY(remainingIndex);
                }
                isDragging = false;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }

        return true;
    }

    private void animateScaleTo(float targetScale, float focalX, float focalY) {
        if (zoomAnimator != null && zoomAnimator.isRunning()) {
            zoomAnimator.cancel();
        }

        final float startScale = currentScale;
        final Matrix startMatrix = new Matrix(drawMatrix);
        final Matrix targetMatrix = new Matrix();

        if (targetScale <= MIN_SCALE) {
            targetMatrix.set(baseMatrix);
        } else {
            targetMatrix.set(drawMatrix);
            float factor = (currentScale > 0) ? (targetScale / currentScale) : 1f;
            targetMatrix.postScale(factor, factor, focalX, focalY);
            applyBounds(targetMatrix);
        }

        final float[] startValues = new float[9];
        final float[] targetValues = new float[9];
        startMatrix.getValues(startValues);
        targetMatrix.getValues(targetValues);

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f);
        zoomAnimator.setDuration(220);
        zoomAnimator.setInterpolator(new DecelerateInterpolator());
        zoomAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            float[] currentValues = new float[9];
            for (int i = 0; i < 9; i++) {
                currentValues[i] = startValues[i] + (targetValues[i] - startValues[i]) * fraction;
            }
            drawMatrix.setValues(currentValues);
            currentScale = startScale + (targetScale - startScale) * fraction;
            setImageMatrix(drawMatrix);
        });
        zoomAnimator.start();
    }

    private void checkMatrixBounds() {
        applyBounds(drawMatrix);
    }

    private void applyBounds(Matrix matrix) {
        if (drawableWidth <= 0 || drawableHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return;
        RectF rect = new RectF(0, 0, drawableWidth, drawableHeight);
        matrix.mapRect(rect);

        float deltaX = 0f;
        float deltaY = 0f;
        float width = rect.width();
        float height = rect.height();

        if (width <= viewWidth) {
            deltaX = (viewWidth - width) / 2f - rect.left;
        } else if (rect.left > 0) {
            deltaX = -rect.left;
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right;
        }

        if (height <= viewHeight) {
            deltaY = (viewHeight - height) / 2f - rect.top;
        } else if (rect.top > 0) {
            deltaY = -rect.top;
        } else if (rect.bottom < viewHeight) {
            deltaY = viewHeight - rect.bottom;
        }

        matrix.postTranslate(deltaX, deltaY);
    }

    private RectF getTransformedRect() {
        if (drawableWidth <= 0 || drawableHeight <= 0) return null;
        RectF rect = new RectF(0, 0, drawableWidth, drawableHeight);
        drawMatrix.mapRect(rect);
        return rect;
    }

    private void updateBaseMatrix() {
        Drawable drawable = getDrawable();
        if (drawable == null || viewWidth <= 0 || viewHeight <= 0) return;

        drawableWidth = drawable.getIntrinsicWidth();
        drawableHeight = drawable.getIntrinsicHeight();
        if (drawableWidth <= 0 || drawableHeight <= 0) return;

        baseMatrix.reset();
        float scaleX = (float) viewWidth / drawableWidth;
        float scaleY = (float) viewHeight / drawableHeight;
        float scale = Math.min(scaleX, scaleY);

        float dx = (viewWidth - drawableWidth * scale) / 2f;
        float dy = (viewHeight - drawableHeight * scale) / 2f;

        baseMatrix.postScale(scale, scale);
        baseMatrix.postTranslate(dx, dy);

        drawMatrix.set(baseMatrix);
        currentScale = MIN_SCALE;
        setImageMatrix(drawMatrix);
    }

    public void resetZoom() {
        if (zoomAnimator != null && zoomAnimator.isRunning()) {
            zoomAnimator.cancel();
        }
        updateBaseMatrix();
    }

    public float getCurrentScale() {
        return currentScale;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            viewWidth = w;
            viewHeight = h;
            updateBaseMatrix();
        }
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        updateBaseMatrix();
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        updateBaseMatrix();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (zoomAnimator != null && zoomAnimator.isRunning()) {
            zoomAnimator.cancel();
        }
    }
}
