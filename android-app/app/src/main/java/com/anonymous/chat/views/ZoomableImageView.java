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
    private final PointF lastTouch = new PointF();
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
                lastTouch.set(event.getX(), event.getY());
                isDragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && currentScale > MIN_SCALE) {
                    float dx = event.getX() - lastTouch.x;
                    float dy = event.getY() - lastTouch.y;

                    if (!isDragging && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        drawMatrix.postTranslate(dx, dy);
                        checkMatrixBounds();
                        setImageMatrix(drawMatrix);
                        lastTouch.set(event.getX(), event.getY());
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
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
        final float endScale = targetScale;
        final float fx = focalX;
        final float fy = focalY;

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f);
        zoomAnimator.setDuration(220);
        zoomAnimator.setInterpolator(new DecelerateInterpolator());
        zoomAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            float newScale = startScale + (endScale - startScale) * progress;
            float factor = (currentScale > 0) ? (newScale / currentScale) : 1f;
            currentScale = newScale;

            drawMatrix.postScale(factor, factor, fx, fy);
            checkMatrixBounds();
            setImageMatrix(drawMatrix);
        });
        zoomAnimator.start();
    }

    private void checkMatrixBounds() {
        RectF rect = getTransformedRect();
        if (rect == null) return;

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

        drawMatrix.postTranslate(deltaX, deltaY);
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
