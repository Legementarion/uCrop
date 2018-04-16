package com.yalantis.ucrop.view.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.support.annotation.ColorInt
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

import com.yalantis.ucrop.R

class HorizontalProgressWheelView @JvmOverloads constructor(context: Context,
                                                            attrs: AttributeSet? = null,
                                                            defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    private val canvasClipBounds = Rect()

    private var scrollingListener: ScrollingListener? = null
    private var lastTouchedPosition: Float = 0f

    private var progressLinePaint: Paint? = null
    private var progressLineWidth: Int = 0
    private var progressLineHeight: Int = 0
    private var progressLineMargin: Int = 0
    private var isScrollStarted: Boolean = false
    private var totalScrollDistance: Float = 0f
    private var middleLineColor: Int = 0

    init {
        init()
    }

    fun setScrollingListener(scrollingListener: ScrollingListener) {
        this.scrollingListener = scrollingListener
    }

    fun setMiddleLineColor(@ColorInt middleLineColor: Int) {
        this.middleLineColor = middleLineColor
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> lastTouchedPosition = event.x
            MotionEvent.ACTION_UP -> if (scrollingListener != null) {
                isScrollStarted = false
                scrollingListener?.onScrollEnd()
            }
            MotionEvent.ACTION_MOVE -> {
                val distance = event.x - lastTouchedPosition
                if (distance != 0f) {
                    if (!isScrollStarted) {
                        isScrollStarted = true
                        scrollingListener?.onScrollStart()
                    }
                    onScrollEvent(event, distance)
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.getClipBounds(canvasClipBounds)

        val linesCount = canvasClipBounds.width() / (progressLineWidth + progressLineMargin)
        val deltaX = totalScrollDistance % (progressLineMargin + progressLineWidth).toFloat()

        progressLinePaint?.let {
            it.color = ContextCompat.getColor(context, R.color.ucrop_color_progress_wheel_line)
            for (i in 0 until linesCount) {
                when {
                    i < linesCount / 4 -> it.alpha = (255 * (i / (linesCount / 4).toFloat())).toInt()
                    i > linesCount * 3 / 4 -> it.alpha = (255 * ((linesCount - i) / (linesCount / 4).toFloat())).toInt()
                    else -> it.alpha = 255
                }
                canvas.drawLine(
                        -deltaX + canvasClipBounds.left.toFloat() + (i * (progressLineWidth + progressLineMargin)).toFloat(),
                        canvasClipBounds.centerY() - progressLineHeight / 4.0f,
                        -deltaX + canvasClipBounds.left.toFloat() + (i * (progressLineWidth + progressLineMargin)).toFloat(),
                        canvasClipBounds.centerY() + progressLineHeight / 4.0f, it)
            }
            it.color = middleLineColor
            canvas.drawLine(canvasClipBounds.centerX().toFloat(),
                    canvasClipBounds.centerY() - progressLineHeight / 2.0f, canvasClipBounds.centerX().toFloat(),
                    canvasClipBounds.centerY() + progressLineHeight / 2.0f, it)
        }

    }

    private fun onScrollEvent(event: MotionEvent, distance: Float) {
        totalScrollDistance -= distance
        postInvalidate()
        lastTouchedPosition = event.x
        scrollingListener?.onScroll(-distance, totalScrollDistance)
    }

    private fun init() {
        middleLineColor = ContextCompat.getColor(context, R.color.ucrop_color_progress_wheel_line)

        progressLineWidth = context.resources.getDimensionPixelSize(R.dimen.ucrop_width_horizontal_wheel_progress_line)
        progressLineHeight = context.resources.getDimensionPixelSize(R.dimen.ucrop_height_horizontal_wheel_progress_line)
        progressLineMargin = context.resources.getDimensionPixelSize(R.dimen.ucrop_margin_horizontal_wheel_progress_line)

        progressLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        progressLinePaint?.style = Paint.Style.STROKE
        progressLinePaint?.strokeWidth = progressLineWidth.toFloat()
    }

    interface ScrollingListener {

        fun onScrollStart()

        fun onScroll(delta: Float, totalDistance: Float)

        fun onScrollEnd()
    }

}