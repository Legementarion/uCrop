package com.yalantis.ucrop

import android.annotation.TargetApi
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.annotation.IdRes
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.util.SelectedStateListDrawable
import com.yalantis.ucrop.view.CropImageView
import com.yalantis.ucrop.view.GestureCropImageView
import com.yalantis.ucrop.view.TransformImageView
import com.yalantis.ucrop.view.UCropView.Companion.ROTATE_WIDGET_SENSITIVITY_COEFFICIENT
import com.yalantis.ucrop.view.UCropView.Companion.SCALE_WIDGET_SENSITIVITY_COEFFICIENT
import com.yalantis.ucrop.view.widget.AspectRatioTextView
import com.yalantis.ucrop.view.widget.HorizontalProgressWheelView
import kotlinx.android.synthetic.main.ucrop_activity_photobox.*
import kotlinx.android.synthetic.main.ucrop_controls.*
import kotlinx.android.synthetic.main.ucrop_layout_rotate_wheel.*
import kotlinx.android.synthetic.main.ucrop_layout_scale_wheel.*
import java.util.*

class UCropActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "UCropActivity"
    }

    private var toolbarTitleText: String? = null

    // Enables dynamic coloring
    private var toolbarColor: Int = 0
    private var mStatusBarColor: Int = 0
    private var mActiveWidgetColor: Int = 0
    private var toolbarWidgetColor: Int = 0
    @ColorInt
    private var mRootViewBackgroundColor: Int = 0
    @DrawableRes
    private var mToolbarCancelDrawable: Int = 0
    @DrawableRes
    private var mToolbarCropDrawable: Int = 0
    private var mLogoColor: Int = 0

    private var isShowBottomControls: Boolean = false
    private var isShowLoader = true

    private var mGestureCropImageView: GestureCropImageView? = null
    private var mWrapperStateAspectRatio: ViewGroup? = null
    private var mWrapperStateRotate: ViewGroup? = null
    private var mWrapperStateScale: ViewGroup? = null
    private var mLayoutAspectRatio: ViewGroup? = null
    private var mLayoutRotate: ViewGroup? = null
    private var mLayoutScale: ViewGroup? = null
    private val mCropAspectRatioViews = ArrayList<ViewGroup>()
    private var mTextViewRotateAngle: TextView? = null
    private var mTextViewScalePercent: TextView? = null
    private var blockingView: View? = null

    private val mImageListener = object : TransformImageView.TransformImageListener {
        override fun onRotate(currentAngle: Float) {
            setAngleText(currentAngle)
        }

        override fun onScale(currentScale: Float) {
            setScaleText(currentScale)
        }

        override fun onLoadComplete() {
            ucropView.animate().alpha(1f).setDuration(300).interpolator = AccelerateInterpolator()
            blockingView?.isClickable = false
            isShowLoader = false
            supportInvalidateOptionsMenu()
        }

        override fun onLoadFailure(e: Exception) {
            setResultError(e)
            finish()
        }

    }

    private val stateClickListener = View.OnClickListener { v ->
        if (!v.isSelected) {
            setWidgetState(v.id)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ucrop_activity_photobox)

        val intent = intent

        setupViews(intent)
        setImageData(intent)
        setInitialState()
        blockingView = ucropView.addBlockingView()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.ucrop_menu_activity, menu)

        // Change crop & loader menu icons color to match the rest of the UI colors

        val menuItemLoader = menu.findItem(R.id.menu_loader)
        val menuItemLoaderIcon = menuItemLoader.icon
        if (menuItemLoaderIcon != null) {
            try {
                menuItemLoaderIcon.mutate()
                menuItemLoaderIcon.setColorFilter(toolbarWidgetColor, PorterDuff.Mode.SRC_ATOP)
                menuItemLoader.icon = menuItemLoaderIcon
            } catch (e: IllegalStateException) {
                Log.i(TAG, String.format("%s - %s", e.message, getString(R.string.ucrop_mutate_exception_hint)))
            }

            (menuItemLoader.icon as Animatable).start()
        }

        val menuItemCrop = menu.findItem(R.id.menu_crop)
        val menuItemCropIcon = ContextCompat.getDrawable(this, mToolbarCropDrawable)
        if (menuItemCropIcon != null) {
            menuItemCropIcon.mutate()
            menuItemCropIcon.setColorFilter(toolbarWidgetColor, PorterDuff.Mode.SRC_ATOP)
            menuItemCrop.icon = menuItemCropIcon
        }

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_crop).isVisible = !isShowLoader
        menu.findItem(R.id.menu_loader).isVisible = isShowLoader
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_crop) {
            blockingView?.isClickable = true
            isShowLoader = true
            supportInvalidateOptionsMenu()
            ucropView.cropAndSaveImage(object : BitmapCropCallback {
                override fun onBitmapCropped(resultUri: Uri, offsetX: Int, offsetY: Int, imageWidth: Int, imageHeight: Int) {
                    mGestureCropImageView?.let {
                        setResultUri(resultUri, it.targetAspectRatio, offsetX, offsetY, imageWidth, imageHeight)
                    }
                }

                override fun onCropFailure(t: Throwable) {
                    setResultError(t)
                }
            })
        } else if (item.itemId == android.R.id.home) {
            onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()
        mGestureCropImageView?.cancelAllAnimations()
    }

    /**
     * This method extracts all data from the incoming intent and setups views properly.
     */
    private fun setImageData(intent: Intent) {
        if (intent.extras != null) {
            val inputUri = intent.getParcelableExtra<Uri>(UCrop.EXTRA_INPUT_URI)
            val outputUri = intent.getParcelableExtra<Uri>(UCrop.EXTRA_OUTPUT_URI)

            ucropView.processOptions(intent.extras)

            if (inputUri != null && outputUri != null) {
                try {
                    mGestureCropImageView?.setImageUri(inputUri, outputUri)
                } catch (e: Exception) {
                    setResultError(e)
                    finish()
                }

            } else {
                setResultError(NullPointerException(getString(R.string.ucrop_error_input_data_is_absent)))
                finish()
            }
        }
    }

    private fun setupViews(intent: Intent) {
        mStatusBarColor = intent.getIntExtra(UCrop.Options.EXTRA_STATUS_BAR_COLOR, ContextCompat.getColor(this, R.color.ucrop_color_statusbar))
        toolbarColor = intent.getIntExtra(UCrop.Options.EXTRA_TOOL_BAR_COLOR, ContextCompat.getColor(this, R.color.ucrop_color_toolbar))
        mActiveWidgetColor = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_COLOR_WIDGET_ACTIVE, ContextCompat.getColor(this, R.color.ucrop_color_widget_active))
        toolbarWidgetColor = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_WIDGET_COLOR_TOOLBAR, ContextCompat.getColor(this, R.color.ucrop_color_toolbar_widget))
        mToolbarCancelDrawable = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_WIDGET_CANCEL_DRAWABLE, R.drawable.ucrop_ic_cross)
        mToolbarCropDrawable = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_WIDGET_CROP_DRAWABLE, R.drawable.ucrop_ic_done)
        toolbarTitleText = intent.getStringExtra(UCrop.Options.EXTRA_UCROP_TITLE_TEXT_TOOLBAR)
        toolbarTitleText = if (toolbarTitleText != null) toolbarTitleText else resources.getString(R.string.ucrop_label_edit_photo)
        mLogoColor = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_LOGO_COLOR, ContextCompat.getColor(this, R.color.ucrop_color_default_logo))
        isShowBottomControls = !intent.getBooleanExtra(UCrop.Options.EXTRA_HIDE_BOTTOM_CONTROLS, false)
        mRootViewBackgroundColor = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_ROOT_VIEW_BACKGROUND_COLOR, ContextCompat.getColor(this, R.color.ucrop_color_crop_background))

        setupAppBar()
        initiateRootViews()

        if (isShowBottomControls) {
            View.inflate(this, R.layout.ucrop_controls, ucropPhotobox)

            stateAspectRatio.setOnClickListener(stateClickListener)
            stateRotate.setOnClickListener(stateClickListener)
            stateScale.setOnClickListener(stateClickListener)

            setupAspectRatioWidget(intent)
            setupRotateWidget()
            setupScaleWidget()
            setupStatesWrapper()
        }
    }

    /**
     * Configures and styles both status bar and toolbar.
     */
    private fun setupAppBar() {
        setStatusBarColor(mStatusBarColor)

        // Set all of the Toolbar coloring
        toolbar.setBackgroundColor(toolbarColor)
        toolbar.setTitleTextColor(toolbarWidgetColor)

        toolbarTitle.setTextColor(toolbarWidgetColor)
        toolbarTitle.text = toolbarTitleText

        // Color buttons inside the Toolbar
        val stateButtonDrawable = ContextCompat.getDrawable(this, mToolbarCancelDrawable)?.mutate()
        stateButtonDrawable?.setColorFilter(toolbarWidgetColor, PorterDuff.Mode.SRC_ATOP)
        toolbar.navigationIcon = stateButtonDrawable

        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun initiateRootViews() {
        mGestureCropImageView = ucropView.getCropImageView()
        mGestureCropImageView?.transformImageListener = mImageListener

        (findViewById<View>(R.id.image_view_logo) as ImageView).setColorFilter(mLogoColor, PorterDuff.Mode.SRC_ATOP)

        findViewById<View>(R.id.ucrop_frame).setBackgroundColor(mRootViewBackgroundColor)
    }

    /**
     * Use [.mActiveWidgetColor] for color filter
     */
    private fun setupStatesWrapper() {
        val stateScaleImageView = findViewById<ImageView>(R.id.image_view_state_scale)
        val stateRotateImageView = findViewById<ImageView>(R.id.image_view_state_rotate)
        val stateAspectRatioImageView = findViewById<ImageView>(R.id.image_view_state_aspect_ratio)

        stateScaleImageView.setImageDrawable(SelectedStateListDrawable(stateScaleImageView.drawable, mActiveWidgetColor))
        stateRotateImageView.setImageDrawable(SelectedStateListDrawable(stateRotateImageView.drawable, mActiveWidgetColor))
        stateAspectRatioImageView.setImageDrawable(SelectedStateListDrawable(stateAspectRatioImageView.drawable, mActiveWidgetColor))
    }


    /**
     * Sets status-bar color for L devices.
     *
     * @param color - status-bar color
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setStatusBarColor(@ColorInt color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = window
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = color
            }
        }
    }

    private fun setupAspectRatioWidget(intent: Intent) {
        var aspectRationSelectedByDefault = intent.getIntExtra(UCrop.Options.EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, 0)
        var aspectRatioList: ArrayList<AspectRatio>? = intent.getParcelableArrayListExtra(UCrop.Options.EXTRA_ASPECT_RATIO_OPTIONS)

        if (aspectRatioList == null || aspectRatioList.isEmpty()) {
            aspectRationSelectedByDefault = 2

            aspectRatioList = ArrayList()
            aspectRatioList.add(AspectRatio(null, 1f, 1f))
            aspectRatioList.add(AspectRatio(null, 3f, 4f))
            aspectRatioList.add(AspectRatio(getString(R.string.ucrop_label_original).toUpperCase(),
                    CropImageView.SOURCE_IMAGE_ASPECT_RATIO, CropImageView.SOURCE_IMAGE_ASPECT_RATIO))
            aspectRatioList.add(AspectRatio(null, 3f, 2f))
            aspectRatioList.add(AspectRatio(null, 16f, 9f))
        }

        var wrapperAspectRatio: FrameLayout
        var aspectRatioTextView: AspectRatioTextView
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        lp.weight = 1f
        for (aspectRatio in aspectRatioList) {
            wrapperAspectRatio = layoutInflater.inflate(R.layout.ucrop_aspect_ratio, null) as FrameLayout
            wrapperAspectRatio.layoutParams = lp
            aspectRatioTextView = wrapperAspectRatio.getChildAt(0) as AspectRatioTextView
            aspectRatioTextView.setActiveColor(mActiveWidgetColor)
            aspectRatioTextView.setAspectRatio(aspectRatio)

            layoutAspectRatio.addView(wrapperAspectRatio)
            mCropAspectRatioViews.add(wrapperAspectRatio)
        }

        mCropAspectRatioViews[aspectRationSelectedByDefault].isSelected = true

        for (cropAspectRatioView in mCropAspectRatioViews) {
            cropAspectRatioView.setOnClickListener { v ->
                mGestureCropImageView?.setAspectRatio(((v as ViewGroup).getChildAt(0) as AspectRatioTextView).getAspectRatio(v.isSelected))
                mGestureCropImageView?.setImageToWrapCropBounds()
                if (!v.isSelected) {
                    for (aspectRatioView in mCropAspectRatioViews) {
                        aspectRatioView.isSelected = aspectRatioView === v
                    }
                }
            }
        }
    }

    private fun setupRotateWidget() {
        rotateScrollWheel.setScrollingListener(object : HorizontalProgressWheelView.ScrollingListener {
            override fun onScroll(delta: Float, totalDistance: Float) {
                mGestureCropImageView?.postRotate(delta / ROTATE_WIDGET_SENSITIVITY_COEFFICIENT)
            }

            override fun onScrollEnd() {
                mGestureCropImageView?.setImageToWrapCropBounds()
            }

            override fun onScrollStart() {
                mGestureCropImageView?.cancelAllAnimations()
            }
        })

        rotateScrollWheel.setMiddleLineColor(mActiveWidgetColor)
        wrapperResetRotate.setOnClickListener { ucropView.resetRotation() }
        wrapperRotateByAngle.setOnClickListener { ucropView.rotateByAngle(90) }
    }

    private fun setupScaleWidget() {
        scaleScrollWheel.setScrollingListener(object : HorizontalProgressWheelView.ScrollingListener {
            override fun onScroll(delta: Float, totalDistance: Float) {
                mGestureCropImageView?.let {
                    if (delta > 0) {
                        it.zoomInImage(it.currentScale + delta * ((it.maxScale - it.minScale) / SCALE_WIDGET_SENSITIVITY_COEFFICIENT))
                    } else {
                        it.zoomOutImage(it.currentScale + delta * ((it.maxScale - it.minScale) / SCALE_WIDGET_SENSITIVITY_COEFFICIENT))
                    }
                }
            }

            override fun onScrollEnd() {
                mGestureCropImageView?.setImageToWrapCropBounds()
            }

            override fun onScrollStart() {
                mGestureCropImageView?.cancelAllAnimations()
            }
        })
        scaleScrollWheel.setMiddleLineColor(mActiveWidgetColor)
    }

    private fun setAngleText(angle: Float) {
        mTextViewRotateAngle?.text = String.format(Locale.getDefault(), "%.1f°", angle)
    }

    private fun setScaleText(scale: Float) {
        mTextViewScalePercent?.text = String.format(Locale.getDefault(), "%d%%", (scale * 100).toInt())
    }

    private fun setInitialState() {
        if (isShowBottomControls) {
            if (mWrapperStateAspectRatio?.visibility == View.VISIBLE) {
                setWidgetState(R.id.stateAspectRatio)
            } else {
                setWidgetState(R.id.stateScale)
            }
        } else {
            ucropView.setAllowedGestures(0)
        }
    }

    private fun setWidgetState(@IdRes stateViewId: Int) {
        if (!isShowBottomControls) return

        mWrapperStateAspectRatio?.isSelected = stateViewId == R.id.stateAspectRatio
        mWrapperStateRotate?.isSelected = stateViewId == R.id.stateRotate
        mWrapperStateScale?.isSelected = stateViewId == R.id.stateScale

        mLayoutAspectRatio?.visibility = if (stateViewId == R.id.stateAspectRatio) View.VISIBLE else View.GONE
        mLayoutRotate?.visibility = if (stateViewId == R.id.stateRotate) View.VISIBLE else View.GONE
        mLayoutScale?.visibility = if (stateViewId == R.id.stateScale) View.VISIBLE else View.GONE

        when (stateViewId) {
            R.id.stateScale -> ucropView.setAllowedGestures(0)
            R.id.stateRotate -> ucropView.setAllowedGestures(1)
            else -> ucropView.setAllowedGestures(2)
        }
    }

    private fun setResultUri(uri: Uri, resultAspectRatio: Float, offsetX: Int, offsetY: Int, imageWidth: Int, imageHeight: Int) {
        setResult(RESULT_OK, Intent()
                .putExtra(UCrop.EXTRA_OUTPUT_URI, uri)
                .putExtra(UCrop.EXTRA_OUTPUT_CROP_ASPECT_RATIO, resultAspectRatio)
                .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_WIDTH, imageWidth)
                .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_HEIGHT, imageHeight)
                .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_X, offsetX)
                .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_Y, offsetY)
        )
    }

    private fun setResultError(throwable: Throwable) {
        setResult(UCrop.RESULT_ERROR, Intent().putExtra(UCrop.EXTRA_ERROR, throwable))
    }

}
