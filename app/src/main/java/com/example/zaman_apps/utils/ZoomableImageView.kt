package com.example.zaman_apps.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

/**
 * ZoomableImageView memfasilitasi pinch-to-zoom, pan/drag gambar saat di-zoom,
 * serta double tap untuk zoom in / zoom out dengan animasi yang halus.
 * Memastikan posisi gambar tetap simetris di tengah saat skala normal (1.0x).
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr),
    View.OnTouchListener,
    ScaleGestureDetector.OnScaleGestureListener,
    GestureDetector.OnDoubleTapListener {

    private enum class State {
        NONE, DRAG, ZOOM
    }

    private var state = State.NONE
    private val matrix = Matrix()

    private val minScale = 1.0f
    private val maxScale = 5.0f
    private var currentScale = 1.0f

    private var viewWidth = 0
    private var viewHeight = 0
    private var origWidth = 0f
    private var origHeight = 0f

    private val lastTouch = PointF()
    private var zoomAnimator: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(context, this)
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTapListener?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            return this@ZoomableImageView.onDoubleTap(e)
        }
    })

    /**
     * Callback saat status zoom berubah:
     * true -> sedang di-zoom (skala > 1.05x), Kunci ViewPager2
     * false -> skala normal (1.0x), Buka ViewPager2
     */
    var onZoomStateChanged: ((Boolean) -> Unit)? = null
    var onSingleTapListener: (() -> Unit)? = null

    val isZoomed: Boolean
        get() = currentScale > 1.05f

    init {
        super.setClickable(true)
        scaleType = ScaleType.MATRIX
        imageMatrix = matrix
        setOnTouchListener(this)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable != null) {
            origWidth = drawable.intrinsicWidth.toFloat()
            origHeight = drawable.intrinsicHeight.toFloat()
        } else {
            origWidth = 0f
            origHeight = 0f
        }
        resetZoom()
    }

    fun resetZoom() {
        zoomAnimator?.cancel()
        currentScale = 1.0f
        state = State.NONE
        fitImageToView()
        notifyZoomState()
    }

    private fun notifyZoomState() {
        onZoomStateChanged?.invoke(isZoomed)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = right - left
        val h = bottom - top
        if (w != viewWidth || h != viewHeight) {
            viewWidth = w
            viewHeight = h
            if (currentScale == 1.0f) {
                fitImageToView()
            } else {
                checkMatrixBounds()
                imageMatrix = matrix
            }
        }
    }

    private fun fitImageToView() {
        val drawable = drawable ?: return
        if (drawable.intrinsicWidth == 0 || drawable.intrinsicHeight == 0) return
        if (viewWidth == 0 || viewHeight == 0) return

        origWidth = drawable.intrinsicWidth.toFloat()
        origHeight = drawable.intrinsicHeight.toFloat()

        val scaleX = viewWidth.toFloat() / origWidth
        val scaleY = viewHeight.toFloat() / origHeight
        val scale = scaleX.coerceAtMost(scaleY)

        val redundantXSpace = viewWidth.toFloat() - (scale * origWidth)
        val redundantYSpace = viewHeight.toFloat() - (scale * origHeight)

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(redundantXSpace / 2f, redundantYSpace / 2f)

        currentScale = 1.0f
        imageMatrix = matrix
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        if (zoomAnimator?.isRunning == true) {
            return true
        }

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val currentTouch = PointF(event.x, event.y)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch.set(currentTouch)
                if (isZoomed) {
                    state = State.DRAG
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == State.DRAG) {
                    val deltaX = currentTouch.x - lastTouch.x
                    val deltaY = currentTouch.y - lastTouch.y

                    matrix.postTranslate(deltaX, deltaY)
                    checkMatrixBounds()
                    lastTouch.set(currentTouch.x, currentTouch.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                state = State.NONE
                if (!isZoomed) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }

        imageMatrix = matrix
        invalidate()
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        var scaleFactor = detector.scaleFactor
        val prevScale = currentScale
        currentScale *= scaleFactor

        if (currentScale > maxScale) {
            currentScale = maxScale
            scaleFactor = maxScale / prevScale
        } else if (currentScale < minScale) {
            currentScale = minScale
            scaleFactor = minScale / prevScale
        }

        matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
        checkMatrixBounds()
        notifyZoomState()
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        state = State.ZOOM
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        state = State.NONE
        if (currentScale < minScale) {
            resetZoom()
        }
    }

    private fun checkMatrixBounds() {
        if (origWidth == 0f || origHeight == 0f || viewWidth == 0 || viewHeight == 0) return

        val rect = RectF(0f, 0f, origWidth, origHeight)
        matrix.mapRect(rect)

        var deltaX = 0f
        var deltaY = 0f

        // Horizontal Bounds
        if (rect.width() <= viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) {
                deltaX = -rect.left
            } else if (rect.right < viewWidth) {
                deltaX = viewWidth - rect.right
            }
        }

        // Vertical Bounds
        if (rect.height() <= viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) {
                deltaY = -rect.top
            } else if (rect.bottom < viewHeight) {
                deltaY = viewHeight - rect.bottom
            }
        }

        if (deltaX != 0f || deltaY != 0f) {
            matrix.postTranslate(deltaX, deltaY)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean = false

    override fun onDoubleTap(e: MotionEvent): Boolean {
        val targetScale = if (currentScale > 1.2f) 1.0f else 2.5f
        animateZoomTo(targetScale, e.x, e.y)
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false

    private fun animateZoomTo(targetScale: Float, focusX: Float, focusY: Float) {
        zoomAnimator?.cancel()

        val startScale = currentScale
        val startMatrix = Matrix(matrix)

        val targetMatrix = Matrix(matrix)
        val scaleFactor = targetScale / startScale
        targetMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY)

        val rect = RectF(0f, 0f, origWidth, origHeight)
        targetMatrix.mapRect(rect)

        var deltaX = 0f
        var deltaY = 0f

        if (rect.width() <= viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) deltaX = -rect.left
            else if (rect.right < viewWidth) deltaX = viewWidth - rect.right
        }

        if (rect.height() <= viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) deltaY = -rect.top
            else if (rect.bottom < viewHeight) deltaY = viewHeight - rect.bottom
        }

        if (deltaX != 0f || deltaY != 0f) {
            targetMatrix.postTranslate(deltaX, deltaY)
        }

        val startValues = FloatArray(9)
        val targetValues = FloatArray(9)
        startMatrix.getValues(startValues)
        targetMatrix.getValues(targetValues)

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val currentValues = FloatArray(9)
                for (i in 0..8) {
                    currentValues[i] = startValues[i] + (targetValues[i] - startValues[i]) * fraction
                }
                matrix.setValues(currentValues)
                currentScale = startScale + (targetScale - startScale) * fraction
                imageMatrix = matrix
                invalidate()
                notifyZoomState()
            }
            start()
        }
    }
}
