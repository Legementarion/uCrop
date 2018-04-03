package com.yalantis.ucrop.view

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.support.annotation.IntDef
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.yalantis.ucrop.R
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.callback.CropBoundsChangeListener
import com.yalantis.ucrop.callback.OverlayViewChangeListener
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.view.OverlayView.FREESTYLE_CROP_MODE_DISABLE

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


    @IntDef(NONE, SCALE, ROTATE, ALL)
    @Retention(AnnotationRetention.SOURCE)
    annotation class GestureTypes

    private var mAllowedGestures = intArrayOf(SCALE, ROTATE, ALL)
    private var mCompressFormat = DEFAULT_COMPRESS_FORMAT
    private var mCompressQuality = DEFAULT_COMPRESS_QUALITY
    private var mGestureCropImageView: GestureCropImageView? = null
    private var mOverlayView: OverlayView? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.ucrop_view, this, true)
        mGestureCropImageView = findViewById(R.id.image_view_crop)
        mOverlayView = findViewById(R.id.view_overlay)

        val a = context.obtainStyledAttributes(attrs, R.styleable.UCropView)
        mOverlayView?.processStyledAttributes(a)
        mGestureCropImageView?.processStyledAttributes(a)
        a.recycle()

        setListenersToViews()
    }

    private fun setListenersToViews() {
        mGestureCropImageView?.cropBoundsChangeListener = CropBoundsChangeListener { cropRatio -> mOverlayView?.setTargetAspectRatio(cropRatio) }
        mOverlayView?.overlayViewChangeListener = OverlayViewChangeListener { cropRect -> mGestureCropImageView?.setCropRect(cropRect) }
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    /**
     * Method for reset state for UCropImageView such as rotation, scale, translation.
     * Be careful: this method recreate UCropImageView instance and reattach it to layout.
     */
    fun resetCropImageView() {
        removeView(mGestureCropImageView)
        mGestureCropImageView = GestureCropImageView(context)
        setListenersToViews()
        mGestureCropImageView?.setCropRect(mOverlayView?.cropViewRect)
        addView(mGestureCropImageView, 0)
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
        mCompressFormat = if (compressFormat == null) DEFAULT_COMPRESS_FORMAT else compressFormat

        mCompressQuality = options.getInt(UCrop.Options.EXTRA_COMPRESSION_QUALITY, DEFAULT_COMPRESS_QUALITY)


        // Crop image view options
        mGestureCropImageView?.maxBitmapSize = options.getInt(UCrop.Options.EXTRA_MAX_BITMAP_SIZE, CropImageView.DEFAULT_MAX_BITMAP_SIZE)
        mGestureCropImageView?.setMaxScaleMultiplier(options.getFloat(UCrop.Options.EXTRA_MAX_SCALE_MULTIPLIER, CropImageView.DEFAULT_MAX_SCALE_MULTIPLIER))
        mGestureCropImageView?.setImageToWrapCropBoundsAnimDuration(options.getInt(UCrop.Options.EXTRA_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION, CropImageView.DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION).toLong())

        // Overlay view options
        mOverlayView?.freestyleCropMode = options.getInt(UCrop.Options.EXTRA_FREE_STYLE_CROP, FREESTYLE_CROP_MODE_DISABLE)

        mOverlayView?.setDimmedColor(options.getInt(UCrop.Options.EXTRA_DIMMED_LAYER_COLOR, ContextCompat.getColor(context, R.color.ucrop_color_default_dimmed)))
        mOverlayView?.setCircleDimmedLayer(options.getBoolean(UCrop.Options.EXTRA_CIRCLE_DIMMED_LAYER, OverlayView.DEFAULT_CIRCLE_DIMMED_LAYER))

        mOverlayView?.setShowCropFrame(options.getBoolean(UCrop.Options.EXTRA_SHOW_CROP_FRAME, OverlayView.DEFAULT_SHOW_CROP_FRAME))
        mOverlayView?.setCropFrameColor(options.getInt(UCrop.Options.EXTRA_CROP_FRAME_COLOR, ContextCompat.getColor(context, R.color.ucrop_color_default_crop_frame)))
        mOverlayView?.setCropFrameStrokeWidth(options.getInt(UCrop.Options.EXTRA_CROP_FRAME_STROKE_WIDTH, resources.getDimensionPixelSize(R.dimen.ucrop_default_crop_frame_stoke_width)))

        mOverlayView?.setShowCropGrid(options.getBoolean(UCrop.Options.EXTRA_SHOW_CROP_GRID, OverlayView.DEFAULT_SHOW_CROP_GRID))
        mOverlayView?.setCropGridRowCount(options.getInt(UCrop.Options.EXTRA_CROP_GRID_ROW_COUNT, OverlayView.DEFAULT_CROP_GRID_ROW_COUNT))
        mOverlayView?.setCropGridColumnCount(options.getInt(UCrop.Options.EXTRA_CROP_GRID_COLUMN_COUNT, OverlayView.DEFAULT_CROP_GRID_COLUMN_COUNT))
        mOverlayView?.setCropGridColor(options.getInt(UCrop.Options.EXTRA_CROP_GRID_COLOR, ContextCompat.getColor(context, R.color.ucrop_color_default_crop_grid)))
        mOverlayView?.setCropGridStrokeWidth(options.getInt(UCrop.Options.EXTRA_CROP_GRID_STROKE_WIDTH, resources.getDimensionPixelSize(R.dimen.ucrop_default_crop_grid_stoke_width)))

        // Aspect ratio options
        val aspectRatioX = options.getFloat(UCrop.EXTRA_ASPECT_RATIO_X, 0f)
        val aspectRatioY = options.getFloat(UCrop.EXTRA_ASPECT_RATIO_Y, 0f)

        val aspectRationSelectedByDefault = options.getInt(UCrop.Options.EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, 0)
        val aspectRatioList = options.getParcelableArrayList<AspectRatio>(UCrop.Options.EXTRA_ASPECT_RATIO_OPTIONS)

        if (aspectRatioX > 0 && aspectRatioY > 0) {
            //            if (mWrapperStateAspectRatio != null) {
            //                mWrapperStateAspectRatio.setVisibility(View.GONE);
            //            }
            mGestureCropImageView?.targetAspectRatio = aspectRatioX / aspectRatioY
        } else if (aspectRatioList != null && aspectRationSelectedByDefault < aspectRatioList.size) {
            mGestureCropImageView?.targetAspectRatio = aspectRatioList[aspectRationSelectedByDefault].aspectRatioX / aspectRatioList[aspectRationSelectedByDefault].aspectRatioY
        } else {
            mGestureCropImageView?.targetAspectRatio = CropImageView.SOURCE_IMAGE_ASPECT_RATIO
        }

        // Result bitmap max size options
        val maxSizeX = options.getInt(UCrop.EXTRA_MAX_SIZE_X, 0)
        val maxSizeY = options.getInt(UCrop.EXTRA_MAX_SIZE_Y, 0)

        if (maxSizeX > 0 && maxSizeY > 0) {
            mGestureCropImageView?.setMaxResultImageSizeX(maxSizeX)
            mGestureCropImageView?.setMaxResultImageSizeY(maxSizeY)
        }
    }

    fun cropAndSaveImage(callback: BitmapCropCallback) {
        mGestureCropImageView?.cropAndSaveImage(mCompressFormat, mCompressQuality, callback)
    }

    fun setAllowedGestures(tab: Int) {
        mGestureCropImageView?.isScaleEnabled = mAllowedGestures[tab] == ALL || mAllowedGestures[tab] == SCALE
        mGestureCropImageView?.isRotateEnabled = mAllowedGestures[tab] == ALL || mAllowedGestures[tab] == ROTATE
    }

    fun resetRotation() {
        mGestureCropImageView?.let {
            it.postRotate(-it.currentAngle)
            it.setImageToWrapCropBounds()
        }
    }

    fun rotateByAngle(angle: Int) {
        mGestureCropImageView?.postRotate(angle.toFloat())
        mGestureCropImageView?.setImageToWrapCropBounds()
    }

    fun getCropImageView(): GestureCropImageView? {
        return mGestureCropImageView
    }


}