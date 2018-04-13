package com.yalantis.ucrop

import android.annotation.TargetApi
import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.annotation.FloatRange
import android.support.annotation.IntRange
import com.yalantis.ucrop.callback.UCropFragmentCallback
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.view.UCropView
import java.util.*

/**
 * Builder class to ease Intent setup.
 */
class UCrop private constructor(source: Uri, destination: Uri) {

    private val mCropIntent: Intent = Intent()
    private var mCropOptionsBundle: Bundle = Bundle()

    init {
        mCropOptionsBundle.putParcelable(EXTRA_INPUT_URI, source)
        mCropOptionsBundle.putParcelable(EXTRA_OUTPUT_URI, destination)
    }

    /**
     * Set an aspect ratio for crop bounds.
     * User won't see the menu with other ratios options.
     *
     * @param x aspect ratio X
     * @param y aspect ratio Y
     */
    fun withAspectRatio(x: Float, y: Float): UCrop {
        mCropOptionsBundle.putFloat(EXTRA_ASPECT_RATIO_X, x)
        mCropOptionsBundle.putFloat(EXTRA_ASPECT_RATIO_Y, y)
        return this
    }

    /**
     * Set an aspect ratio for crop bounds that is evaluated from source image width and height.
     * User won't see the menu with other ratios options.
     */
    fun useSourceImageAspectRatio(): UCrop {
        mCropOptionsBundle.putFloat(EXTRA_ASPECT_RATIO_X, 0f)
        mCropOptionsBundle.putFloat(EXTRA_ASPECT_RATIO_Y, 0f)
        return this
    }

    /**
     * Set maximum size for result cropped image. Maximum size cannot be less then {@value MIN_SIZE}
     *
     * @param width  max cropped image width
     * @param height max cropped image height
     */
    fun withMaxResultSize(@IntRange(from = MIN_SIZE.toLong()) width: Int,
                          @IntRange(from = MIN_SIZE.toLong()) height: Int): UCrop {
        var newWidth = width
        var newHeight = height
        if (newWidth < MIN_SIZE) {
            newWidth = MIN_SIZE
        }

        if (newHeight < MIN_SIZE) {
            newHeight = MIN_SIZE
        }

        mCropOptionsBundle.putInt(EXTRA_MAX_SIZE_X, newWidth)
        mCropOptionsBundle.putInt(EXTRA_MAX_SIZE_Y, newHeight)
        return this
    }

    fun withOptions(options: Options): UCrop {
        mCropOptionsBundle.putAll(options.optionBundle)
        return this
    }

    /**
     * Send the crop Intent from an Activity with a custom request code
     *
     * @param activity    Activity to receive result
     * @param requestCode requestCode for result
     */
    @JvmOverloads
    fun start(activity: Activity, requestCode: Int = REQUEST_CROP) {
        activity.startActivityForResult(getIntent(activity), requestCode)
    }

    /**
     * Send the crop Intent from a Fragment
     *
     * @param fragment Fragment to receive result
     */
    fun start(context: Context, fragment: Fragment) {
        start(context, fragment, REQUEST_CROP)
    }

    /**
     * Send the crop Intent with a custom request code
     *
     * @param fragment    Fragment to receive result
     * @param requestCode requestCode for result
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    fun start(context: Context, fragment: Fragment, requestCode: Int) {
        fragment.startActivityForResult(getIntent(context), requestCode)
    }

    /**
     * Send the crop Intent with a custom request code
     *
     * @param fragment    Fragment to receive result
     * @param requestCode requestCode for result
     */
    @JvmOverloads
    fun start(context: Context, fragment: android.support.v4.app.Fragment, requestCode: Int = REQUEST_CROP) {
        fragment.startActivityForResult(getIntent(context), requestCode)
    }

    /**
     * Get Intent to start [UCropActivity]
     *
     * @return Intent for [UCropActivity]
     */
    fun getIntent(context: Context): Intent {
        mCropIntent.setClass(context, UCropActivity::class.java)
        mCropIntent.putExtras(mCropOptionsBundle)
        return mCropIntent
    }

    /**
     * Get Fragment [UCropFragment]
     *
     * @return Fragment of [UCropFragment]
     */
    fun getFragment(callback: UCropFragmentCallback): UCropFragment {
        return UCropFragment.newInstance(mCropOptionsBundle, callback)
    }

    fun getFragment(bundle: Bundle, callback: UCropFragmentCallback): UCropFragment {
        mCropOptionsBundle = bundle
        return getFragment(callback)
    }

    /**
     * Class that helps to setup advanced configs that are not commonly used.
     * Use it with method [.withOptions]
     */
    class Options {

        val optionBundle: Bundle = Bundle()

        /**
         * Set one of [android.graphics.Bitmap.CompressFormat] that will be used to save resulting Bitmap.
         */
        fun setCompressionFormat(format: Bitmap.CompressFormat) {
            optionBundle.putString(EXTRA_COMPRESSION_FORMAT_NAME, format.name)
        }

        /**
         * Set compression quality [0-100] that will be used to save resulting Bitmap.
         */
        fun setCompressionQuality(@IntRange(from = 0) compressQuality: Int) {
            optionBundle.putInt(EXTRA_COMPRESSION_QUALITY, compressQuality)
        }

        /**
         * Choose what set of gestures will be enabled on each tab - if any.
         */
        fun setAllowedGestures(@UCropView.GestureTypes tabScale: Int,
                               @UCropView.GestureTypes tabRotate: Int,
                               @UCropView.GestureTypes tabAspectRatio: Int) {
            optionBundle.putIntArray(EXTRA_ALLOWED_GESTURES, intArrayOf(tabScale, tabRotate, tabAspectRatio))
        }

        /**
         * This method sets multiplier that is used to calculate max image scale from min image scale.
         *
         * @param maxScaleMultiplier - (minScale * maxScaleMultiplier) = maxScale
         */
        fun setMaxScaleMultiplier(@FloatRange(from = 1.0, fromInclusive = false) maxScaleMultiplier: Float) {
            optionBundle.putFloat(EXTRA_MAX_SCALE_MULTIPLIER, maxScaleMultiplier)
        }

        /**
         * This method sets animation duration for image to wrap the crop bounds
         *
         * @param durationMillis - duration in milliseconds
         */
        fun setImageToCropBoundsAnimDuration(@IntRange(from = MIN_SIZE.toLong()) durationMillis: Int) {
            optionBundle.putInt(EXTRA_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION, durationMillis)
        }

        /**
         * Setter for max size for both width and height of bitmap that will be decoded from an input Uri and used in the view.
         *
         * @param maxBitmapSize - size in pixels
         */
        fun setMaxBitmapSize(@IntRange(from = MIN_SIZE.toLong()) maxBitmapSize: Int) {
            optionBundle.putInt(EXTRA_MAX_BITMAP_SIZE, maxBitmapSize)
        }

        /**
         * @param color - desired color of dimmed area around the crop bounds
         */
        fun setDimmedLayerColor(@ColorInt color: Int) {
            optionBundle.putInt(EXTRA_DIMMED_LAYER_COLOR, color)
        }

        /**
         * @param isCircle - set it to true if you want dimmed layer to have an circle inside
         */
        fun setCircleDimmedLayer(isCircle: Boolean) {
            optionBundle.putBoolean(EXTRA_CIRCLE_DIMMED_LAYER, isCircle)
        }

        /**
         * @param show - set to true if you want to see a crop frame rectangle on top of an image
         */
        fun setShowCropFrame(show: Boolean) {
            optionBundle.putBoolean(EXTRA_SHOW_CROP_FRAME, show)
        }

        /**
         * @param color - desired color of crop frame
         */
        fun setCropFrameColor(@ColorInt color: Int) {
            optionBundle.putInt(EXTRA_CROP_FRAME_COLOR, color)
        }

        /**
         * @param width - desired width of crop frame line in pixels
         */
        fun setCropFrameStrokeWidth(@IntRange(from = 0) width: Int) {
            optionBundle.putInt(EXTRA_CROP_FRAME_STROKE_WIDTH, width)
        }

        /**
         * @param show - set to true if you want to see a crop grid/guidelines on top of an image
         */
        fun setShowCropGrid(show: Boolean) {
            optionBundle.putBoolean(EXTRA_SHOW_CROP_GRID, show)
        }

        /**
         * @param count - crop grid rows count.
         */
        fun setCropGridRowCount(@IntRange(from = 0) count: Int) {
            optionBundle.putInt(EXTRA_CROP_GRID_ROW_COUNT, count)
        }

        /**
         * @param count - crop grid columns count.
         */
        fun setCropGridColumnCount(@IntRange(from = 0) count: Int) {
            optionBundle.putInt(EXTRA_CROP_GRID_COLUMN_COUNT, count)
        }

        /**
         * @param color - desired color of crop grid/guidelines
         */
        fun setCropGridColor(@ColorInt color: Int) {
            optionBundle.putInt(EXTRA_CROP_GRID_COLOR, color)
        }

        /**
         * @param width - desired width of crop grid lines in pixels
         */
        fun setCropGridStrokeWidth(@IntRange(from = 0) width: Int) {
            optionBundle.putInt(EXTRA_CROP_GRID_STROKE_WIDTH, width)
        }

        /**
         * @param color - desired resolved color of the toolbar
         */
        fun setToolbarColor(@ColorInt color: Int) {
            optionBundle.putInt(EXTRA_TOOL_BAR_COLOR, color)
        }

        /**
         * @param color - desired resolved color of the statusbar
         */
        fun setStatusBarColor(@ColorInt color: Int) {
            optionBundle.putInt(EXTRA_STATUS_BAR_COLOR, color)
        }

        /**
         * @param color - desired resolved color of the active and selected widget (default is orange) and progress wheel middle line
         */
        fun setActiveWidgetColor(@ColorInt color: Int) {
            optionBundle.putInt(EXTRA_UCROP_COLOR_WIDGET_ACTIVE, color)
        }

        /**
         * @param color - desired resolved color of Toolbar text and buttons (default is darker orange)
         */
        fun setToolbarWidgetColor(@ColorInt color: Int) {
            optionBundle.putInt(EXTRA_UCROP_WIDGET_COLOR_TOOLBAR, color)
        }

        /**
         * @param text - desired text for Toolbar title
         */
        fun setToolbarTitle(text: String?) {
            optionBundle.putString(EXTRA_UCROP_TITLE_TEXT_TOOLBAR, text)
        }

        /**
         * @param drawable - desired drawable for the Toolbar left cancel icon
         */
        fun setToolbarCancelDrawable(@DrawableRes drawable: Int) {
            optionBundle.putInt(EXTRA_UCROP_WIDGET_CANCEL_DRAWABLE, drawable)
        }

        /**
         * @param drawable - desired drawable for the Toolbar right crop icon
         */
        fun setToolbarCropDrawable(@DrawableRes drawable: Int) {
            optionBundle.putInt(EXTRA_UCROP_WIDGET_CROP_DRAWABLE, drawable)
        }

        /**
         * @param color - desired resolved color of logo fill (default is darker grey)
         */
        fun setLogoColor(@ColorInt color: Int) {
            optionBundle.putInt(EXTRA_UCROP_LOGO_COLOR, color)
        }

        /**
         * @param hide - set to true to hide the bottom controls (shown by default)
         */
        fun setHideBottomControls(hide: Boolean) {
            optionBundle.putBoolean(EXTRA_HIDE_BOTTOM_CONTROLS, hide)
        }

        /**
         * @param mode - let user resize crop bounds (disabled by default)
         */
        fun setFreeStyleCropEnabled(mode: Int) {
            optionBundle.putInt(EXTRA_FREE_STYLE_CROP, mode)
        }

        /**
         * Pass an ordered list of desired aspect ratios that should be available for a user.
         *
         * @param selectedByDefault - index of aspect ratio option that is selected by default (starts with 0).
         * @param aspectRatio       - list of aspect ratio options that are available to user
         */
        fun setAspectRatioOptions(selectedByDefault: Int, vararg aspectRatio: AspectRatio) {
            if (selectedByDefault > aspectRatio.size) {
                throw IllegalArgumentException(String.format(Locale.US,
                        "Index [selectedByDefault = %d] cannot be higher than aspect ratio options count [count = %d].",
                        selectedByDefault, aspectRatio.size))
            }
            optionBundle.putInt(EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, selectedByDefault)
            optionBundle.putParcelableArrayList(EXTRA_ASPECT_RATIO_OPTIONS, ArrayList<Parcelable>(Arrays.asList(*aspectRatio)))
        }

        /**
         * @param color - desired background color that should be applied to the root view
         */
        fun setRootViewBackgroundColor(@ColorInt color: Int) {
            optionBundle.putInt(EXTRA_UCROP_ROOT_VIEW_BACKGROUND_COLOR, color)
        }

        /**
         * Set an aspect ratio for crop bounds.
         * User won't see the menu with other ratios options.
         *
         * @param x aspect ratio X
         * @param y aspect ratio Y
         */
        fun withAspectRatio(x: Float, y: Float) {
            optionBundle.putFloat(EXTRA_ASPECT_RATIO_X, x)
            optionBundle.putFloat(EXTRA_ASPECT_RATIO_Y, y)
        }

        /**
         * Set an aspect ratio for crop bounds that is evaluated from source image width and height.
         * User won't see the menu with other ratios options.
         */
        fun useSourceImageAspectRatio() {
            optionBundle.putFloat(EXTRA_ASPECT_RATIO_X, 0f)
            optionBundle.putFloat(EXTRA_ASPECT_RATIO_Y, 0f)
        }

        /**
         * Set maximum size for result cropped image.
         *
         * @param width  max cropped image width
         * @param height max cropped image height
         */
        fun withMaxResultSize(@IntRange(from = MIN_SIZE.toLong()) width: Int, @IntRange(from = MIN_SIZE.toLong()) height: Int) {
            optionBundle.putInt(EXTRA_MAX_SIZE_X, width)
            optionBundle.putInt(EXTRA_MAX_SIZE_Y, height)
        }

        companion object {

            const val EXTRA_COMPRESSION_FORMAT_NAME = "$EXTRA_PREFIX.CompressionFormatName"
            const val EXTRA_COMPRESSION_QUALITY = "$EXTRA_PREFIX.CompressionQuality"

            const val EXTRA_ALLOWED_GESTURES = "$EXTRA_PREFIX.AllowedGestures"

            const val EXTRA_MAX_BITMAP_SIZE = "$EXTRA_PREFIX.MaxBitmapSize"
            const val EXTRA_MAX_SCALE_MULTIPLIER = "$EXTRA_PREFIX.MaxScaleMultiplier"
            const val EXTRA_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION = "$EXTRA_PREFIX.ImageToCropBoundsAnimDuration"

            const val EXTRA_DIMMED_LAYER_COLOR = "$EXTRA_PREFIX.DimmedLayerColor"
            const val EXTRA_CIRCLE_DIMMED_LAYER = "$EXTRA_PREFIX.CircleDimmedLayer"

            const val EXTRA_SHOW_CROP_FRAME = "$EXTRA_PREFIX.ShowCropFrame"
            const val EXTRA_CROP_FRAME_COLOR = "$EXTRA_PREFIX.CropFrameColor"
            const val EXTRA_CROP_FRAME_STROKE_WIDTH = "$EXTRA_PREFIX.CropFrameStrokeWidth"

            const val EXTRA_SHOW_CROP_GRID = "$EXTRA_PREFIX.ShowCropGrid"
            const val EXTRA_CROP_GRID_ROW_COUNT = "$EXTRA_PREFIX.CropGridRowCount"
            const val EXTRA_CROP_GRID_COLUMN_COUNT = "$EXTRA_PREFIX.CropGridColumnCount"
            const val EXTRA_CROP_GRID_COLOR = "$EXTRA_PREFIX.CropGridColor"
            const val EXTRA_CROP_GRID_STROKE_WIDTH = "$EXTRA_PREFIX.CropGridStrokeWidth"

            const val EXTRA_TOOL_BAR_COLOR = "$EXTRA_PREFIX.ToolbarColor"
            const val EXTRA_STATUS_BAR_COLOR = "$EXTRA_PREFIX.StatusBarColor"
            const val EXTRA_UCROP_COLOR_WIDGET_ACTIVE = "$EXTRA_PREFIX.UcropColorWidgetActive"

            const val EXTRA_UCROP_WIDGET_COLOR_TOOLBAR = "$EXTRA_PREFIX.UcropToolbarWidgetColor"
            const val EXTRA_UCROP_TITLE_TEXT_TOOLBAR = "$EXTRA_PREFIX.UcropToolbarTitleText"
            const val EXTRA_UCROP_WIDGET_CANCEL_DRAWABLE = "$EXTRA_PREFIX.UcropToolbarCancelDrawable"
            const val EXTRA_UCROP_WIDGET_CROP_DRAWABLE = "$EXTRA_PREFIX.UcropToolbarCropDrawable"

            const val EXTRA_UCROP_LOGO_COLOR = "$EXTRA_PREFIX.UcropLogoColor"

            const val EXTRA_HIDE_BOTTOM_CONTROLS = "$EXTRA_PREFIX.HideBottomControls"
            const val EXTRA_FREE_STYLE_CROP = "$EXTRA_PREFIX.FreeStyleCrop"

            const val EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT = "$EXTRA_PREFIX.AspectRatioSelectedByDefault"
            const val EXTRA_ASPECT_RATIO_OPTIONS = "$EXTRA_PREFIX.AspectRatioOptions"

            const val EXTRA_UCROP_ROOT_VIEW_BACKGROUND_COLOR = "$EXTRA_PREFIX.UcropRootViewBackgroundColor"
        }

    }

    companion object {

        const val REQUEST_CROP = 69
        const val RESULT_ERROR = 96
        const val MIN_SIZE = 10

        private const val EXTRA_PREFIX = BuildConfig.APPLICATION_ID

        const val EXTRA_INPUT_URI = "$EXTRA_PREFIX.InputUri"
        const val EXTRA_OUTPUT_URI = "$EXTRA_PREFIX.OutputUri"
        const val EXTRA_OUTPUT_CROP_ASPECT_RATIO = "$EXTRA_PREFIX.CropAspectRatio"
        const val EXTRA_OUTPUT_IMAGE_WIDTH = "$EXTRA_PREFIX.ImageWidth"
        const val EXTRA_OUTPUT_IMAGE_HEIGHT = "$EXTRA_PREFIX.ImageHeight"
        const val EXTRA_OUTPUT_OFFSET_X = "$EXTRA_PREFIX.OffsetX"
        const val EXTRA_OUTPUT_OFFSET_Y = "$EXTRA_PREFIX.OffsetY"
        const val EXTRA_ERROR = "$EXTRA_PREFIX.Error"

        const val EXTRA_ASPECT_RATIO_X = "$EXTRA_PREFIX.AspectRatioX"
        const val EXTRA_ASPECT_RATIO_Y = "$EXTRA_PREFIX.AspectRatioY"

        const val EXTRA_MAX_SIZE_X = "$EXTRA_PREFIX.MaxSizeX"
        const val EXTRA_MAX_SIZE_Y = "$EXTRA_PREFIX.MaxSizeY"

        /**
         * This method creates new Intent builder and sets both source and destination image URIs.
         *
         * @param source      Uri for image to crop
         * @param destination Uri for saving the cropped image
         */
        fun of(source: Uri, destination: Uri): UCrop {
            return UCrop(source, destination)
        }

        /**
         * Retrieve cropped image Uri from the result Intent
         *
         * @param intent crop result intent
         */
        fun getOutput(intent: Intent): Uri? {
            return intent.getParcelableExtra(EXTRA_OUTPUT_URI)
        }

        /**
         * Retrieve the width of the cropped image
         *
         * @param intent crop result intent
         */
        fun getOutputImageWidth(intent: Intent): Int {
            return intent.getIntExtra(EXTRA_OUTPUT_IMAGE_WIDTH, -1)
        }

        /**
         * Retrieve the height of the cropped image
         *
         * @param intent crop result intent
         */
        fun getOutputImageHeight(intent: Intent): Int {
            return intent.getIntExtra(EXTRA_OUTPUT_IMAGE_HEIGHT, -1)
        }

        /**
         * Retrieve cropped image aspect ratio from the result Intent
         *
         * @param intent crop result intent
         * @return aspect ratio as a floating point value (x:y) - so it will be 1 for 1:1 or 4/3 for 4:3
         */
        fun getOutputCropAspectRatio(intent: Intent): Float {
            return intent.getParcelableExtra<Parcelable>(EXTRA_OUTPUT_CROP_ASPECT_RATIO).toString().toFloat()
        }

        /**
         * Method retrieves error from the result intent.
         *
         * @param result crop result Intent
         * @return Throwable that could happen while image processing
         */
        fun getError(result: Intent): Throwable? {
            return result.getSerializableExtra(EXTRA_ERROR) as Throwable
        }
    }
}