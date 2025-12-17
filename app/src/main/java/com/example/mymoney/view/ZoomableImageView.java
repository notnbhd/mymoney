package com.example.mymoney.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * Custom ImageView that supports pinch-to-zoom and pan gestures
 */
public class ZoomableImageView extends AppCompatImageView {

    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();

    // Modes
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;

    // Zoom limits
    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 5f;

    // Touch points
    private PointF start = new PointF();
    private PointF mid = new PointF();
    private float oldDist = 1f;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float[] matrixValues = new float[9];
    private float currentScale = 1f;

    // Original dimensions for reset
    private float originalWidth;
    private float originalHeight;
    private boolean isInitialized = false;

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
        
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed || !isInitialized) {
            fitImageToView();
        }
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        isInitialized = false;
        if (getWidth() > 0 && getHeight() > 0) {
            fitImageToView();
        }
    }

    private void fitImageToView() {
        Drawable drawable = getDrawable();
        if (drawable == null) return;

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();

        if (viewWidth == 0 || viewHeight == 0 || drawableWidth == 0 || drawableHeight == 0) {
            return;
        }

        // Calculate scale to fit image in view while maintaining aspect ratio
        float scaleX = (float) viewWidth / drawableWidth;
        float scaleY = (float) viewHeight / drawableHeight;
        float scale = Math.min(scaleX, scaleY);

        // Calculate translation to center the image
        float scaledWidth = drawableWidth * scale;
        float scaledHeight = drawableHeight * scale;
        float translateX = (viewWidth - scaledWidth) / 2f;
        float translateY = (viewHeight - scaledHeight) / 2f;

        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(translateX, translateY);

        setImageMatrix(matrix);
        savedMatrix.set(matrix);
        currentScale = scale;

        originalWidth = scaledWidth;
        originalHeight = scaledHeight;
        isInitialized = true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                start.set(event.getX(), event.getY());
                mode = DRAG;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                oldDist = spacing(event);
                if (oldDist > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    mode = ZOOM;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    matrix.set(savedMatrix);
                    float dx = event.getX() - start.x;
                    float dy = event.getY() - start.y;
                    matrix.postTranslate(dx, dy);
                    constrainTranslation();
                } else if (mode == ZOOM && event.getPointerCount() >= 2) {
                    float newDist = spacing(event);
                    if (newDist > 10f) {
                        matrix.set(savedMatrix);
                        float scale = newDist / oldDist;
                        
                        // Constrain scale
                        float currentScaleFromMatrix = getCurrentScale();
                        float targetScale = currentScaleFromMatrix * scale;
                        
                        if (targetScale < MIN_SCALE) {
                            scale = MIN_SCALE / currentScaleFromMatrix;
                        } else if (targetScale > MAX_SCALE) {
                            scale = MAX_SCALE / currentScaleFromMatrix;
                        }
                        
                        matrix.postScale(scale, scale, mid.x, mid.y);
                        constrainTranslation();
                    }
                }
                setImageMatrix(matrix);
                break;
        }

        return true;
    }

    private float getCurrentScale() {
        matrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    private void constrainTranslation() {
        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];
        float scaleX = matrixValues[Matrix.MSCALE_X];

        Drawable drawable = getDrawable();
        if (drawable == null) return;

        float scaledWidth = drawable.getIntrinsicWidth() * scaleX;
        float scaledHeight = drawable.getIntrinsicHeight() * scaleX;

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        // Calculate boundaries
        float minTransX, maxTransX, minTransY, maxTransY;

        if (scaledWidth <= viewWidth) {
            // Image width is smaller than view, center it
            minTransX = (viewWidth - scaledWidth) / 2f;
            maxTransX = minTransX;
        } else {
            // Image width is larger, allow panning
            minTransX = viewWidth - scaledWidth;
            maxTransX = 0;
        }

        if (scaledHeight <= viewHeight) {
            // Image height is smaller than view, center it
            minTransY = (viewHeight - scaledHeight) / 2f;
            maxTransY = minTransY;
        } else {
            // Image height is larger, allow panning
            minTransY = viewHeight - scaledHeight;
            maxTransY = 0;
        }

        // Constrain translation
        float newTransX = Math.max(minTransX, Math.min(maxTransX, transX));
        float newTransY = Math.max(minTransY, Math.min(maxTransY, transY));

        matrix.postTranslate(newTransX - transX, newTransY - transY);
    }

    private float spacing(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        if (event.getPointerCount() < 2) return;
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    /**
     * Reset zoom to fit image in view
     */
    public void resetZoom() {
        fitImageToView();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return true; // Handled in onTouchEvent
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // Double tap to toggle between fit and 2x zoom
            float currentScaleValue = getCurrentScale();
            Drawable drawable = getDrawable();
            if (drawable == null) return true;

            int viewWidth = getWidth();
            int viewHeight = getHeight();
            float fitScale = Math.min(
                    (float) viewWidth / drawable.getIntrinsicWidth(),
                    (float) viewHeight / drawable.getIntrinsicHeight()
            );

            if (currentScaleValue > fitScale * 1.5f) {
                // Zoom out to fit
                fitImageToView();
            } else {
                // Zoom in 2x at tap location
                matrix.postScale(2f, 2f, e.getX(), e.getY());
                constrainTranslation();
                setImageMatrix(matrix);
            }
            return true;
        }
    }
}
