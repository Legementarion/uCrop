package com.yalantis.ucrop.view

import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.os.Build
import android.support.annotation.ColorInt
import android.support.annotation.IntRange
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.yalantis.ucrop.R
import com.yalantis.ucrop.callback.OverlayViewChangeListener
import com.yalantis.ucrop.model.FreestyleMode.CROP_MODE_DISABLE
import com.yalantis.ucrop.model.FreestyleMode.CROP_MODE_ENABLE
import com.yalantis.ucrop.util.getCenterFromRect
import com.yalantis.ucrop.util.getCornersFromRect

/**
 * This view is used for drawing the overlay on top of the image. It may have frame, crop guidelines and dimmed area.
 * This must have LAYER_TYPE_SOFTWARE to draw itself properly.
 */
class OverlayView @JvmOverloads constructor(context: Context,
                                            attrs: AttributeSet? = null,
                                            defStyle: Int = 0) : View(context, attrs, defStyle) {

    companion object {
        const val DEFAULT_SHOW_CROP_FRAME = true
        const val DEFAULT_SHOW_CROP_GRID = true
        const val DEFAULT_CIRCLE_DIMMED_LAYER = false
        const val DEFAULT_CROP_GRID_ROW_COUNT = 2
        const val DEFAULT_CROP_GRID_COLUMN_COUNT = 2
        val DEFAULT_FREESTYLE_CROP_MODE = CROP_MODE_DISABLE
    }

    val cropViewRect = RectF()
    private val tempRect = RectF()

    private var currentWidth: Int = 0
    private var currentHeight: Int = 0
    private var cropGridCorners: FloatArray = floatArrayOf()
    private var cropGridCenter: FloatArray = floatArrayOf()

    private var cropGridRowCount: Int = 0
    private var cropGridColumnCount: Int = 0
    private var targetAspectRatio: Float = 0f
    private var gridPoints: FloatArray? = null
    private var isShowCropFrame: Boolean = true
    private var isShowCropGrid: Boolean = true
    private var circleDimmedLayer: Boolean = false
    private var dimmedColor: Int = 0
    private val circularPath = Path()
    private val dimmedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cropGridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cropFramePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cropFrameCornersPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var previousTouchX = -1f
    private var previousTouchY = -1f
    private var currentTouchCornerIndex = -1
    private var touchPointThreshold: Int = 0
    private var cropRectMinSize: Int = 0
    private var cropRectCornerTouchAreaLineLength: Int = 0

    var overlayViewChangeListener: OverlayViewChangeListener? = null

    private var mShouldSetupCropBounds: Boolean = false

    var freestyleCropMode = DEFAULT_FREESTYLE_CROP_MODE
        set(value) {
            field = value
            postInvalidate()
        }

    init {
        touchPointThreshold = resources.getDimensionPixelSize(R.dimen.ucrop_default_crop_rect_corner_touch_threshold)
        cropRectMinSize = resources.getDimensionPixelSize(R.dimen.ucrop_default_crop_rect_min_size)
        cropRectCornerTouchAreaLineLength = resources.getDimensionPixelSize(R.dimen.ucrop_default_crop_rect_corner_touch_area_line_length)
    }

    init {
        init()
    }

    /**
     * Please use the new method {@link #getFreestyleCropMode() getFreestyleCropMode} method as we have more than 1 freestyle crop mode.
     */
    @Deprecated("", ReplaceWith("freestyleCropMode == CROP_MODE_ENABLE", "com.yalantis.ucrop.model.FreestyleMode.CROP_MODE_ENABLE"))
    fun isFreestyleCropEnabled(): Boolean {
        return freestyleCropMode == CROP_MODE_ENABLE
    }

    /**
     * Please use the new method [setFreestyleCropMode][.setFreestyleCropMode] method as we have more than 1 freestyle crop mode.
     */
    @Deprecated("", ReplaceWith("freestyleCropMode = if (freestyleCropEnabled) CROP_MODE_ENABLE else CROP_MODE_DISABLE", "com.yalantis.ucrop.model.FreestyleMode.CROP_MODE_ENABLE", "com.yalantis.ucrop.model.FreestyleMode.CROP_MODE_DISABLE"))
    fun setFreestyleCropEnabled(freestyleCropEnabled: Boolean) {
        freestyleCropMode = if (freestyleCropEnabled) CROP_MODE_ENABLE else CROP_MODE_DISABLE
    }

    /**
     * Setter for [.circleDimmedLayer] variable.
     *
     * @param circleDimmedLayer - set it to true if you want dimmed layer to be an circle
     */
    fun setCircleDimmedLayer(circleDimmedLayer: Boolean) {
        this.circleDimmedLayer = circleDimmedLayer
    }

    /**
     * Setter for crop grid rows count.
     * Resets [.gridPoints] variable because it is not valid anymore.
     */
    fun setCropGridRowCount(@IntRange(from = 0) cropGridRowCount: Int) {
        this.cropGridRowCount = cropGridRowCount
        gridPoints = null
    }

    /**
     * Setter for crop grid columns count.
     * Resets [.gridPoints] variable because it is not valid anymore.
     */
    fun setCropGridColumnCount(@IntRange(from = 0) cropGridColumnCount: Int) {
        this.cropGridColumnCount = cropGridColumnCount
        gridPoints = null
    }

    /**
     * Setter for [.isShowCropFrame] variable.
     *
     * @param showCropFrame - set to true if you want to see a crop frame rectangle on top of an image
     */
    fun setShowCropFrame(showCropFrame: Boolean) {
        isShowCropFrame = showCropFrame
    }

    /**
     * Setter for [.isShowCropGrid] variable.
     *
     * @param showCropGrid - set to true if you want to see a crop grid on top of an image
     */
    fun setShowCropGrid(showCropGrid: Boolean) {
        isShowCropGrid = showCropGrid
    }

    /**
     * Setter for [.dimmedColor] variable.
     *
     * @param dimmedColor - desired color of dimmed area around the crop bounds
     */
    fun setDimmedColor(@ColorInt dimmedColor: Int) {
        this.dimmedColor = dimmedColor
    }

    /**
     * Setter for crop frame stroke width
     */
    fun setCropFrameStrokeWidth(@IntRange(from = 0) width: Int) {
        cropFramePaint.strokeWidth = width.toFloat()
    }

    /**
     * Setter for crop grid stroke width
     */
    fun setCropGridStrokeWidth(@IntRange(from = 0) width: Int) {
        cropGridPaint.strokeWidth = width.toFloat()
    }

    /**
     * Setter for crop frame color
     */
    fun setCropFrameColor(@ColorInt color: Int) {
        cropFramePaint.color = color
    }

    /**
     * Setter for crop grid color
     */
    fun setCropGridColor(@ColorInt color: Int) {
        cropGridPaint.color = color
    }

    /**
     * This method sets aspect ratio for crop bounds.
     *
     * @param targetAspectRatio - aspect ratio for image crop (e.g. 1.77(7) for 16:9)
     */
    fun setTargetAspectRatio(targetAspectRatio: Float) {
        this.targetAspectRatio = targetAspectRatio
        if (currentWidth > 0) {
            setupCropBounds()
            postInvalidate()
        } else {
            mShouldSetupCropBounds = true
        }
    }

    /**
     * This method setups crop bounds rectangles for given aspect ratio and view size.
     * [.mCropViewRect] is used to draw crop bounds - uses padding.
     */
    private fun setupCropBounds() {
        val height = (currentWidth / targetAspectRatio).toInt()
        if (height > currentHeight) {
            val width = (currentHeight * targetAspectRatio).toInt()
            val halfDiff = (currentWidth - width) / 2
            cropViewRect.set((paddingLeft + halfDiff).toFloat(), paddingTop.toFloat(),
                    (paddingLeft + width + halfDiff).toFloat(), (paddingTop + currentHeight).toFloat())
        } else {
            val halfDiff = (currentHeight - height) / 2
            cropViewRect.set(paddingLeft.toFloat(), (paddingTop + halfDiff).toFloat(),
                    (paddingLeft + currentWidth).toFloat(), (paddingTop + height + halfDiff).toFloat())
        }

        overlayViewChangeListener?.onCropRectUpdated(cropViewRect)

        updateGridPoints()
    }

    private fun updateGridPoints() {
        cropGridCorners = cropViewRect.getCornersFromRect()
        cropGridCenter = cropViewRect.getCenterFromRect()

        gridPoints = null
        circularPath.reset()
        circularPath.addCircle(cropViewRect.centerX(), cropViewRect.centerY(),
                Math.min(cropViewRect.width(), cropViewRect.height()) / 2f, Path.Direction.CW)
    }

    private fun init() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var newLeft = left
        var newTop = top
        var newRight = right
        var newBottom = bottom
        super.onLayout(changed, newLeft, newTop, newRight, newBottom)
        if (changed) {
            newLeft = paddingLeft
            newTop = paddingTop
            newRight = width - paddingRight
            newBottom = height - paddingBottom
            currentWidth = newRight - newLeft
            currentHeight = newBottom - newTop

            if (mShouldSetupCropBounds) {
                mShouldSetupCropBounds = false
                setTargetAspectRatio(targetAspectRatio)
            }
        }
    }

    /**
     * Along with image there are dimmed layer, crop bounds and crop guidelines that must be drawn.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawDimmedLayer(canvas)
        drawCropGrid(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (cropViewRect.isEmpty || freestyleCropMode == CROP_MODE_DISABLE) {
            return false
        }

        var x = event.x
        var y = event.y

        if (event.action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_DOWN) {
            currentTouchCornerIndex = getCurrentTouchIndex(x, y)
            val shouldHandle = currentTouchCornerIndex != -1
            if (!shouldHandle) {
                previousTouchX = -1f
                previousTouchY = -1f
            } else if (previousTouchX < 0) {
                previousTouchX = x
                previousTouchY = y
            }
            return shouldHandle
        }

        if (event.action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_MOVE) {
            if (event.pointerCount == 1 && currentTouchCornerIndex != -1) {

                x = Math.min(Math.max(x, paddingLeft.toFloat()), (width - paddingRight).toFloat())
                y = Math.min(Math.max(y, paddingTop.toFloat()), (height - paddingBottom).toFloat())

                updateCropViewRect(x, y)

                previousTouchX = x
                previousTouchY = y

                return true
            }
        }

        if (event.action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_UP) {
            previousTouchX = -1f
            previousTouchY = -1f
            currentTouchCornerIndex = -1

            overlayViewChangeListener?.onCropRectUpdated(cropViewRect)
        }

        return false
    }

    /**
     * * The order of the corners is:
     * 0------->1
     * ^        |
     * |   4    |
     * |        v
     * 3<-------2
     */
    private fun updateCropViewRect(touchX: Float, touchY: Float) {
        tempRect.set(cropViewRect)

        when (currentTouchCornerIndex) {
        // resize rectangle
            0 -> tempRect.set(touchX, touchY, cropViewRect.right, cropViewRect.bottom)
            1 -> tempRect.set(cropViewRect.left, touchY, touchX, cropViewRect.bottom)
            2 -> tempRect.set(cropViewRect.left, cropViewRect.top, touchX, touchY)
            3 -> tempRect.set(touchX, cropViewRect.top, cropViewRect.right, touchY)
        // move rectangle
            4 -> {
                tempRect.offset(touchX - previousTouchX, touchY - previousTouchY)
                if (tempRect.left > left && tempRect.top > top
                        && tempRect.right < right && tempRect.bottom < bottom) {
                    cropViewRect.set(tempRect)
                    updateGridPoints()
                    postInvalidate()
                }
                return
            }
        }

        val changeHeight = tempRect.height() >= cropRectMinSize
        val changeWidth = tempRect.width() >= cropRectMinSize
        cropViewRect.set(
                if (changeWidth) tempRect.left else cropViewRect.left,
                if (changeHeight) tempRect.top else cropViewRect.top,
                if (changeWidth) tempRect.right else cropViewRect.right,
                if (changeHeight) tempRect.bottom else cropViewRect.bottom)

        if (changeHeight || changeWidth) {
            updateGridPoints()
            postInvalidate()
        }
    }

    /**
     * * The order of the corners in the float array is:
     * 0------->1
     * ^        |
     * |   4    |
     * |        v
     * 3<-------2
     *
     * @return - index of corner that is being dragged
     */
    private fun getCurrentTouchIndex(touchX: Float, touchY: Float): Int {
        var closestPointIndex = -1
        var closestPointDistance = touchPointThreshold.toDouble()
        var i = 0
        while (i < 8) {
            val distanceToCorner = Math.sqrt(Math.pow((touchX - cropGridCorners[i]).toDouble(), 2.0) + Math.pow((touchY - cropGridCorners[i + 1]).toDouble(), 2.0))
            if (distanceToCorner < closestPointDistance) {
                closestPointDistance = distanceToCorner
                closestPointIndex = i / 2
            }
            i += 2
        }

        return if (freestyleCropMode == CROP_MODE_ENABLE && closestPointIndex < 0 && cropViewRect.contains(touchX, touchY)) {
            4
        } else closestPointIndex

    }

    /**
     * This method draws dimmed area around the crop bounds.
     *
     * @param canvas - valid canvas object
     */
    private fun drawDimmedLayer(canvas: Canvas) {
        canvas.save()
        if (circleDimmedLayer) {
            canvas.clipPath(circularPath, Region.Op.DIFFERENCE)
        } else {
            canvas.clipRect(cropViewRect, Region.Op.DIFFERENCE)
        }
        canvas.drawColor(dimmedColor)
        canvas.restore()

        if (circleDimmedLayer) { // Draw 1px stroke to fix antialias
            canvas.drawCircle(cropViewRect.centerX(), cropViewRect.centerY(),
                    Math.min(cropViewRect.width(), cropViewRect.height()) / 2f, dimmedStrokePaint)
        }
    }

    /**
     * This method draws crop bounds (empty rectangle)
     * and crop guidelines (vertical and horizontal lines inside the crop bounds) if needed.
     *
     * @param canvas - valid canvas object
     */
    private fun drawCropGrid(canvas: Canvas) {
        if (isShowCropGrid) {
            gridPoints?.let {
                if (!cropViewRect.isEmpty) {

                    gridPoints = FloatArray(cropGridRowCount * 4 + cropGridColumnCount * 4)

                    var index = 0
                    for (i in 0 until cropGridRowCount) {
                        it[index++] = cropViewRect.left
                        it[index++] = cropViewRect.height() * ((i.toFloat() + 1.0f) / (cropGridRowCount + 1).toFloat()) + cropViewRect.top
                        it[index++] = cropViewRect.right
                        it[index++] = cropViewRect.height() * ((i.toFloat() + 1.0f) / (cropGridRowCount + 1).toFloat()) + cropViewRect.top
                    }

                    for (i in 0 until cropGridColumnCount) {
                        it[index++] = cropViewRect.width() * ((i.toFloat() + 1.0f) / (cropGridColumnCount + 1).toFloat()) + cropViewRect.left
                        it[index++] = cropViewRect.top
                        it[index++] = cropViewRect.width() * ((i.toFloat() + 1.0f) / (cropGridColumnCount + 1).toFloat()) + cropViewRect.left
                        it[index++] = cropViewRect.bottom
                    }
                }

                canvas.drawLines(it, cropGridPaint)
            }
        }

        if (isShowCropFrame) {
            canvas.drawRect(cropViewRect, cropFramePaint)
        }

        if (freestyleCropMode != CROP_MODE_DISABLE) {
            canvas.save()

            tempRect.set(cropViewRect)
            tempRect.inset(cropRectCornerTouchAreaLineLength.toFloat(), (-cropRectCornerTouchAreaLineLength).toFloat())
            canvas.clipRect(tempRect, Region.Op.DIFFERENCE)

            tempRect.set(cropViewRect)
            tempRect.inset((-cropRectCornerTouchAreaLineLength).toFloat(), cropRectCornerTouchAreaLineLength.toFloat())
            canvas.clipRect(tempRect, Region.Op.DIFFERENCE)

            canvas.drawRect(cropViewRect, cropFrameCornersPaint)

            canvas.restore()
        }
    }

    /**
     * This method extracts all needed values from the styled attributes.
     * Those are used to configure the view.
     */
    fun processStyledAttributes(a: TypedArray) {
        circleDimmedLayer = a.getBoolean(R.styleable.UCropView_ucrop_circle_dimmed_layer, DEFAULT_CIRCLE_DIMMED_LAYER)
        dimmedColor = a.getColor(R.styleable.UCropView_ucrop_dimmed_color,
                ContextCompat.getColor(context, R.color.ucrop_color_default_dimmed))
        dimmedStrokePaint.color = dimmedColor
        dimmedStrokePaint.style = Paint.Style.STROKE
        dimmedStrokePaint.strokeWidth = 1f

        initCropFrameStyle(a)
        isShowCropFrame = a.getBoolean(R.styleable.UCropView_ucrop_show_frame, DEFAULT_SHOW_CROP_FRAME)

        initCropGridStyle(a)
        isShowCropGrid = a.getBoolean(R.styleable.UCropView_ucrop_show_grid, DEFAULT_SHOW_CROP_GRID)
    }

    /**
     * This method setups Paint object for the crop bounds.
     */
    private fun initCropFrameStyle(a: TypedArray) {
        val cropFrameStrokeSize = a.getDimensionPixelSize(R.styleable.UCropView_ucrop_frame_stroke_size,
                resources.getDimensionPixelSize(R.dimen.ucrop_default_crop_frame_stoke_width))
        val cropFrameColor = a.getColor(R.styleable.UCropView_ucrop_frame_color,
                ContextCompat.getColor(context, R.color.ucrop_color_default_crop_frame))
        cropFramePaint.strokeWidth = cropFrameStrokeSize.toFloat()
        cropFramePaint.color = cropFrameColor
        cropFramePaint.style = Paint.Style.STROKE

        cropFrameCornersPaint.strokeWidth = (cropFrameStrokeSize * 3).toFloat()
        cropFrameCornersPaint.color = cropFrameColor
        cropFrameCornersPaint.style = Paint.Style.STROKE
    }

    /**
     * This method setups Paint object for the crop guidelines.
     */
    private fun initCropGridStyle(a: TypedArray) {
        val cropGridStrokeSize = a.getDimensionPixelSize(R.styleable.UCropView_ucrop_grid_stroke_size,
                resources.getDimensionPixelSize(R.dimen.ucrop_default_crop_grid_stoke_width))
        val cropGridColor = a.getColor(R.styleable.UCropView_ucrop_grid_color,
                ContextCompat.getColor(context, R.color.ucrop_color_default_crop_grid))
        cropGridPaint.strokeWidth = cropGridStrokeSize.toFloat()
        cropGridPaint.color = cropGridColor

        cropGridRowCount = a.getInt(R.styleable.UCropView_ucrop_grid_row_count, DEFAULT_CROP_GRID_ROW_COUNT)
        cropGridColumnCount = a.getInt(R.styleable.UCropView_ucrop_grid_column_count, DEFAULT_CROP_GRID_COLUMN_COUNT)
    }

}
