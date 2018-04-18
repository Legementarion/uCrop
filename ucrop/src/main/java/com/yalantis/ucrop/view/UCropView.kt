package com.yalantis.ucrop.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.IntDef
import android.support.annotation.IntRange
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.yalantis.ucrop.R
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCrop.Companion.MIN_SIZE
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.callback.CropBoundsChangeListener
import com.yalantis.ucrop.callback.OverlayViewChangeListener
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.model.FreestyleMode
import kotlinx.android.synthetic.main.ucrop_view.view.*
import android.graphics.drawable.Drawable



class UCropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                          defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val DEFAULT_COMPRESS_QUALITY = 90
        const val NONE = 0
        const val SCALE = 1
        const val ROTATE = 2
        const val ALL = 3
        const val TABS_COUNT = 3
        val DEFAULT_COMPRESS_FORMAT: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
        const val SCALE_WIDGET_SENSITIVITY_COEFFICIENT = 15000
        const val ROTATE_WIDGET_SENSITIVITY_COEFFICIENT = 42
    }

    private var blockingView: View? = null

    @IntDef(NONE, SCALE, ROTATE, ALL)
    @Retention(AnnotationRetention.SOURCE)
    annotation class GestureTypes

    private var allowedGestures = intArrayOf(SCALE, ROTATE, ALL)
    private var compressFormat = DEFAULT_COMPRESS_FORMAT
    private var compressQuality = DEFAULT_COMPRESS_QUALITY

    private var aspectRatioX: Float = 0f
    private var aspectRatioY: Float = 0f
    private var maxSizeX = 0
    private var maxSizeY = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.ucrop_view, this, true)
        val a = context.obtainStyledAttributes(attrs, R.styleable.UCropView)
        overlayView.processStyledAttributes(a)
        gestureCropImageView.processStyledAttributes(a)
        a.recycle()

        setListenersToViews()
        addBlockingView()
    }

    private fun setListenersToViews() {
        gestureCropImageView.cropBoundsChangeListener = object : CropBoundsChangeListener {
            override fun onCropAspectRatioChanged(cropRatio: Float) {
                overlayView.setTargetAspectRatio(cropRatio)
            }
        }

        overlayView.overlayViewChangeListener = object : OverlayViewChangeListener {
            override fun onCropRectUpdated(cropRect: RectF) {
                gestureCropImageView.setCropRect(cropRect)
            }
        }
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    /**
     * Adds view that covers everything below the Toolbar.
     * When it's clickable - user won't be able to click/touch anything below the Toolbar.
     * Need to block user input while loading and cropping an image.
     */
    fun addBlockingView(): View? {
        if (blockingView == null) {
            blockingView = View(this.context)
            val lp = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            blockingView?.layoutParams = lp
            blockingView?.isClickable = true
        }

        addView(blockingView)
        return blockingView
    }

    /**
     * Method for reset state for UCropImageView such as rotation, scale, translation.
     * Be careful: this method recreate UCropImageView instance and reattach it to layout.
     */
    fun resetCropImageView() {
        removeView(gestureCropImageView)
        gestureCropImageView.invalidate()
        gestureCropImageView.refreshDrawableState()
        setListenersToViews()
        overlayView?.cropViewRect?.let { gestureCropImageView?.setCropRect(it) }
        addView(gestureCropImageView, 0)
    }

    /**
     * This method extracts [#optionsBundle][com.yalantis.ucrop.UCrop.Options] from incoming intent
     * and setups Activity, [OverlayView] and [CropImageView] properly.
     */
    fun processOptions(options: Bundle) {

        // Gestures options
        val allowedGestures = options.getIntArray(UCrop.Options.EXTRA_ALLOWED_GESTURES)
        if (allowedGestures != null && allowedGestures.size == TABS_COUNT) {
            this.allowedGestures = allowedGestures
        }

        // Bitmap compression options
        val compressionFormatName = options.getString(UCrop.Options.EXTRA_COMPRESSION_FORMAT_NAME)
        var compressFormat: Bitmap.CompressFormat? = null
        if (!TextUtils.isEmpty(compressionFormatName)) {
            compressFormat = Bitmap.CompressFormat.valueOf(compressionFormatName)
        }
        this.compressFormat = if (compressFormat == null) DEFAULT_COMPRESS_FORMAT else compressFormat

        compressQuality = options.getInt(UCrop.Options.EXTRA_COMPRESSION_QUALITY, DEFAULT_COMPRESS_QUALITY)


        // Crop image view options
        gestureCropImageView?.maxBitmapSize = options.getInt(UCrop.Options.EXTRA_MAX_BITMAP_SIZE, CropImageView.DEFAULT_MAX_BITMAP_SIZE)
        gestureCropImageView?.setMaxScaleMultiplier(options.getFloat(UCrop.Options.EXTRA_MAX_SCALE_MULTIPLIER, CropImageView.DEFAULT_MAX_SCALE_MULTIPLIER))
        gestureCropImageView?.setImageToWrapCropBoundsAnimDuration(options.getInt(UCrop.Options.EXTRA_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION, CropImageView.DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION).toLong())

        // Overlay view options
        overlayView?.freestyleCropMode = FreestyleMode.values()[options.getInt(UCrop.Options.EXTRA_FREE_STYLE_CROP, FreestyleMode.CROP_MODE_DISABLE.ordinal)]

        overlayView?.setDimmedColor(options.getInt(UCrop.Options.EXTRA_DIMMED_LAYER_COLOR, ContextCompat.getColor(context, R.color.ucrop_color_default_dimmed)))
        overlayView?.setCircleDimmedLayer(options.getBoolean(UCrop.Options.EXTRA_CIRCLE_DIMMED_LAYER, OverlayView.DEFAULT_CIRCLE_DIMMED_LAYER))

        overlayView?.setShowCropFrame(options.getBoolean(UCrop.Options.EXTRA_SHOW_CROP_FRAME, OverlayView.DEFAULT_SHOW_CROP_FRAME))
        overlayView?.setCropFrameColor(options.getInt(UCrop.Options.EXTRA_CROP_FRAME_COLOR, ContextCompat.getColor(context, R.color.ucrop_color_default_crop_frame)))
        overlayView?.setCropFrameStrokeWidth(options.getInt(UCrop.Options.EXTRA_CROP_FRAME_STROKE_WIDTH, resources.getDimensionPixelSize(R.dimen.ucrop_default_crop_frame_stoke_width)))

        overlayView?.setShowCropGrid(options.getBoolean(UCrop.Options.EXTRA_SHOW_CROP_GRID, OverlayView.DEFAULT_SHOW_CROP_GRID))
        overlayView?.setCropGridRowCount(options.getInt(UCrop.Options.EXTRA_CROP_GRID_ROW_COUNT, OverlayView.DEFAULT_CROP_GRID_ROW_COUNT))
        overlayView?.setCropGridColumnCount(options.getInt(UCrop.Options.EXTRA_CROP_GRID_COLUMN_COUNT, OverlayView.DEFAULT_CROP_GRID_COLUMN_COUNT))
        overlayView?.setCropGridColor(options.getInt(UCrop.Options.EXTRA_CROP_GRID_COLOR, ContextCompat.getColor(context, R.color.ucrop_color_default_crop_grid)))
        overlayView?.setCropGridStrokeWidth(options.getInt(UCrop.Options.EXTRA_CROP_GRID_STROKE_WIDTH, resources.getDimensionPixelSize(R.dimen.ucrop_default_crop_grid_stoke_width)))

        // Aspect ratio options
        aspectRatioX = options.getFloat(UCrop.EXTRA_ASPECT_RATIO_X, 0f)
        aspectRatioY = options.getFloat(UCrop.EXTRA_ASPECT_RATIO_Y, 0f)

        val aspectRationSelectedByDefault = options.getInt(UCrop.Options.EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, 0)
        val aspectRatioList = options.getParcelableArrayList<AspectRatio>(UCrop.Options.EXTRA_ASPECT_RATIO_OPTIONS)

        if (aspectRatioX > 0 && aspectRatioY > 0) {
            gestureCropImageView?.setAspectRatio(aspectRatioX / aspectRatioY)
        } else if (aspectRatioList != null && aspectRationSelectedByDefault < aspectRatioList.size) {
            gestureCropImageView?.setAspectRatio(aspectRatioList[aspectRationSelectedByDefault].aspectRatioX / aspectRatioList[aspectRationSelectedByDefault].aspectRatioY)
        } else {
            gestureCropImageView?.setAspectRatio(CropImageView.SOURCE_IMAGE_ASPECT_RATIO)
        }

        // Result bitmap max size options
        val maxSizeX = options.getInt(UCrop.EXTRA_MAX_SIZE_X, 0)
        val maxSizeY = options.getInt(UCrop.EXTRA_MAX_SIZE_Y, 0)

        if (maxSizeX > 0 && maxSizeY > 0) {
            gestureCropImageView?.setMaxResultImageSizeX(maxSizeX)
            gestureCropImageView?.setMaxResultImageSizeY(maxSizeY)
        }
    }

    fun cropAndSaveImage(callback: BitmapCropCallback) {
        gestureCropImageView?.cropAndSaveImage(compressFormat, compressQuality, callback)
    }

    fun setImage(inputUri: Uri, outputUri: Uri) {
        val aspectRationSelectedByDefault = 0
        val aspectRatioList = mutableListOf<AspectRatio>()

        if (aspectRatioX > 0 && aspectRatioY > 0) {
            gestureCropImageView?.setAspectRatio(aspectRatioX / aspectRatioY)
        } else if (!aspectRatioList.isEmpty() && aspectRationSelectedByDefault < aspectRatioList.size) {
            gestureCropImageView?.setAspectRatio(aspectRatioList[aspectRationSelectedByDefault].aspectRatioX / aspectRatioList[aspectRationSelectedByDefault].aspectRatioY)
        } else {
            gestureCropImageView?.setAspectRatio(CropImageView.SOURCE_IMAGE_ASPECT_RATIO)
        }

        if (maxSizeX > 0 && maxSizeY > 0) {
            gestureCropImageView?.setMaxResultImageSizeX(maxSizeX)
            gestureCropImageView?.setMaxResultImageSizeY(maxSizeY)
        }


        gestureCropImageView?.setImageUri(inputUri, outputUri)
        blockingView?.isClickable = false
    }

    // Crop image view options

    /**
     * Setter for max size for both width and height of bitmap that will be decoded from an input Uri and used in the view.
     *
     * @param maxBitmapSize - size in pixels
     */
    fun setMaxBitmapSize(@IntRange(from = MIN_SIZE.toLong()) maxBitmapSize: Int) {
        gestureCropImageView?.maxBitmapSize = maxBitmapSize
    }

    /**
     * This method sets multiplier that is used to calculate max image scale from min image scale.
     *
     * @param multiplier - (minScale * maxScaleMultiplier) = maxScale
     */
    fun setMaxScaleMultiplier(multiplier: Float) {
        gestureCropImageView?.setMaxScaleMultiplier(multiplier)
    }

    /**
     * This method sets animation duration for image to wrap the crop bounds
     *
     * @param durationMillis - duration in milliseconds
     */
    fun setImageToWrapCropBoundsAnimDuration(durationMillis: Long) {
        gestureCropImageView?.setImageToWrapCropBoundsAnimDuration(durationMillis)
    }

    // Overlay view options

    /**
     * @param mode - let user resize crop bounds (disabled by default)
     */
    fun setFreeStyleCropEnabled(mode: FreestyleMode) {
        overlayView.freestyleCropMode = mode
    }

    /**
     * @param color - desired color of dimmed area around the crop bounds
     */
    fun setDimmedColor(@ColorInt color: Int) {
        overlayView.setDimmedColor(color)
    }

    /**
     * @param isCircle - set it to true if you want dimmed layer to have an circle inside
     */
    fun setCircleDimmedLayer(value: Boolean) {
        overlayView.setCircleDimmedLayer(value)
    }

    /**
     * @param show - set to true if you want to see a crop frame rectangle on top of an image
     */
    fun setShowCropFrame(show: Boolean) {
        overlayView?.setShowCropFrame(show)
    }

    /**
     * @param color - desired color of crop frame
     */
    fun setCropFrameColor(@ColorInt color: Int) {
        overlayView?.setCropFrameColor(color)
    }

    /**
     * @param width - desired width of crop frame line in pixels
     */
    fun setCropFrameStrokeWidth(width: Int) {
        overlayView?.setCropFrameStrokeWidth(width)
    }

    /**
     * @param show - set to true if you want to see a crop grid/guidelines on top of an image
     */
    fun setShowCropGrid(show: Boolean) {
        overlayView?.setShowCropGrid(show)
    }

    /**
     * @param count - crop grid rows count.
     */
    fun setCropGridRowCount(count: Int) {
        overlayView?.setCropGridRowCount(count)
    }

    /**
     * @param count - crop grid columns count.
     */
    fun setCropGridColumnCount(count: Int) {
        overlayView?.setCropGridColumnCount(count)
    }

    /**
     * @param color - desired color of crop grid/guidelines
     */
    fun setCropGridColor(@ColorInt color: Int) {
        overlayView?.setCropGridColor(color)
    }

    /**
     * @param width - desired width of crop grid lines in pixels
     */
    fun setCropGridStrokeWidth(width: Int) {
        overlayView?.setCropGridStrokeWidth(width)
    }

    // Gestures options

    fun setAllowedGestures(tab: Int) {
        gestureCropImageView?.isScaleEnabled = allowedGestures[tab] == ALL || allowedGestures[tab] == SCALE
        gestureCropImageView?.isRotateEnabled = allowedGestures[tab] == ALL || allowedGestures[tab] == ROTATE
    }

    fun setRotateEnabled(status: Boolean) {
        gestureCropImageView.isRotateEnabled = status
    }

    fun setScaleEnabled(status: Boolean) {
        gestureCropImageView.isScaleEnabled = status
    }

    fun setDoubleTapScaleSteps(step: Int) {
        gestureCropImageView.doubleTapScaleSteps = step
    }

    fun resetRotation() {
        gestureCropImageView?.let {
            it.postRotate(-it.currentAngle)
            it.setImageToWrapCropBounds()
        }
    }

    fun rotateByAngle(angle: Int) {
        gestureCropImageView?.postRotate(angle.toFloat())
        gestureCropImageView?.setImageToWrapCropBounds()
    }

    fun getCropImageView(): GestureCropImageView? {
        return gestureCropImageView
    }

    /**
     * Set an aspect ratio for crop bounds that is evaluated from source image width and height.
     * User won't see the menu with other ratios options.
     */
    fun useSourceImageAspectRatio() {
        aspectRatioX = 0f
        aspectRatioY = 0f
    }

    /**
     * Set an aspect ratio for crop bounds.
     * User won't see the menu with other ratios options.
     *
     * @param x aspect ratio X
     * @param y aspect ratio Y
     */
    fun withAspectRatio(x: Float, y: Float) {
        aspectRatioX = x
        aspectRatioY = y
    }

    /**
     * Set maximum size for result cropped image.
     *
     * @param width  max cropped image width
     * @param height max cropped image height
     */
    fun withMaxResultSize(width: Int, height: Int) {
        maxSizeX = width
        maxSizeY = height
    }

    /**
     * Set one of {@link android.graphics.Bitmap.CompressFormat} that will be used to save resulting Bitmap.
     */
    fun setCompressionFormat(format: Bitmap.CompressFormat) {
        compressFormat = format
    }

    /**
     * Set compression quality [0-100] that will be used to save resulting Bitmap.
     */
    fun setCompressionQuality(compressQuality: Int) {
        this.compressQuality = compressQuality
    }

    /**
     * @param color - desired background color that should be applied to the root view
     */
    fun setRootViewBackgroundColor(@ColorInt color: Int) {
        setBackgroundColor(color)
    }

    fun setRotate(delta: Float) {
        gestureCropImageView.postRotate(delta / ROTATE_WIDGET_SENSITIVITY_COEFFICIENT)
    }

    fun setScale(delta: Float) {
        if (delta > 0) {
            gestureCropImageView.zoomInImage(gestureCropImageView.currentScale + delta * ((gestureCropImageView.maxScale - gestureCropImageView.minScale) / SCALE_WIDGET_SENSITIVITY_COEFFICIENT))
        } else {
            gestureCropImageView.zoomOutImage(gestureCropImageView.currentScale + delta * ((gestureCropImageView.maxScale - gestureCropImageView.minScale) / SCALE_WIDGET_SENSITIVITY_COEFFICIENT))
        }
    }

    fun cancelAllAnimations() {
        gestureCropImageView.cancelAllAnimations()
    }

    fun setImageToWrapCropBounds() {
        gestureCropImageView.setImageToWrapCropBounds()
    }

}