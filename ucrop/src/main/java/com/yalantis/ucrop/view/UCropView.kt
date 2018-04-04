package com.yalantis.ucrop.view

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.annotation.IntDef
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
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.callback.CropBoundsChangeListener
import com.yalantis.ucrop.callback.OverlayViewChangeListener
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.view.OverlayView.FREESTYLE_CROP_MODE_DISABLE
import kotlinx.android.synthetic.main.ucrop_view.view.*

class UCropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                          defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val DEFAULT_COMPRESS_QUALITY = 90
        private const val NONE = 0
        private const val SCALE = 1
        private const val ROTATE = 2
        private const val ALL = 3
        private const val TABS_COUNT = 3
        private val DEFAULT_COMPRESS_FORMAT: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    }

    private var blockingView: View? = null


    @IntDef(NONE, SCALE, ROTATE, ALL)
    @Retention(AnnotationRetention.SOURCE)
    annotation class GestureTypes

    private var mAllowedGestures = intArrayOf(SCALE, ROTATE, ALL)
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
        gestureCropImageView?.cropBoundsChangeListener = CropBoundsChangeListener { cropRatio -> overlayView.setTargetAspectRatio(cropRatio) }
        overlayView?.overlayViewChangeListener = OverlayViewChangeListener { cropRect -> gestureCropImageView?.setCropRect(cropRect) }
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    /**
     * Adds view that covers everything below the Toolbar.
     * When it's clickable - user won't be able to click/touch anything below the Toolbar.
     * Need to block user input while loading and cropping an image.
     */
    private fun addBlockingView() {
        if (blockingView == null) {
            blockingView = View(this.context)
            val lp = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            blockingView?.layoutParams = lp
            blockingView?.isClickable = true
        }

        addView(blockingView)
    }

    /**
     * Method for reset state for UCropImageView such as rotation, scale, translation.
     * Be careful: this method recreate UCropImageView instance and reattach it to layout.
     */
    fun resetCropImageView() {
        removeView(gestureCropImageView)
//        gestureCropImageView = GestureCropImageView(context)
        setListenersToViews()
        gestureCropImageView?.setCropRect(overlayView?.cropViewRect)
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
            mAllowedGestures = allowedGestures
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
        overlayView?.freestyleCropMode = options.getInt(UCrop.Options.EXTRA_FREE_STYLE_CROP, FREESTYLE_CROP_MODE_DISABLE)

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
            //            if (mWrapperStateAspectRatio != null) {
            //                mWrapperStateAspectRatio.setVisibility(View.GONE);
            //            }
            gestureCropImageView?.targetAspectRatio = aspectRatioX / aspectRatioY
        } else if (aspectRatioList != null && aspectRationSelectedByDefault < aspectRatioList.size) {
            gestureCropImageView?.targetAspectRatio = aspectRatioList[aspectRationSelectedByDefault].aspectRatioX / aspectRatioList[aspectRationSelectedByDefault].aspectRatioY
        } else {
            gestureCropImageView?.targetAspectRatio = CropImageView.SOURCE_IMAGE_ASPECT_RATIO
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

    fun setAllowedGestures(tab: Int) {
        gestureCropImageView?.isScaleEnabled = mAllowedGestures[tab] == ALL || mAllowedGestures[tab] == SCALE
        gestureCropImageView?.isRotateEnabled = mAllowedGestures[tab] == ALL || mAllowedGestures[tab] == ROTATE
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

    fun useSourceImageAspectRatio() {
        aspectRatioX = 0f
        aspectRatioY = 0f
    }

    fun withAspectRatio(ratioX: Float, ratioY: Float) {
        aspectRatioX = ratioX
        aspectRatioY = ratioY
    }

    fun withMaxResultSize(width: Int, height: Int) {
        maxSizeX = width
        maxSizeY = height
    }

    fun setCompressionFormat(format: Bitmap.CompressFormat) {
        compressFormat = format
    }

    fun setCompressionQuality(compressQuality: Int) {
        this.compressQuality = compressQuality
    }

    fun setImage(inputUri: Uri, outputUri: Uri) {
        val aspectRationSelectedByDefault = 0
        val aspectRatioList = mutableListOf<AspectRatio>()

        if (aspectRatioX > 0 && aspectRatioY > 0) {
            gestureCropImageView?.targetAspectRatio = aspectRatioX / aspectRatioY
        } else if (!aspectRatioList.isEmpty() && aspectRationSelectedByDefault < aspectRatioList.size) {
            gestureCropImageView?.targetAspectRatio = aspectRatioList[aspectRationSelectedByDefault].aspectRatioX / aspectRatioList[aspectRationSelectedByDefault].aspectRatioY
        } else {
            gestureCropImageView?.targetAspectRatio = CropImageView.SOURCE_IMAGE_ASPECT_RATIO
        }

        if (maxSizeX > 0 && maxSizeY > 0) {
            gestureCropImageView?.setMaxResultImageSizeX(maxSizeX)
            gestureCropImageView?.setMaxResultImageSizeY(maxSizeY)
        }


        gestureCropImageView?.setImageUri(inputUri, outputUri)
        blockingView?.isClickable = false
    }

}