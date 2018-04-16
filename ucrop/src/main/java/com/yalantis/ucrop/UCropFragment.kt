package com.yalantis.ucrop

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.IdRes
import android.support.annotation.IntDef
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.callback.UCropFragmentCallback
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.model.UCropResult
import com.yalantis.ucrop.util.SelectedStateListDrawable
import com.yalantis.ucrop.view.CropImageView
import com.yalantis.ucrop.view.GestureCropImageView
import com.yalantis.ucrop.view.TransformImageView
import com.yalantis.ucrop.view.UCropView.Companion.ALL
import com.yalantis.ucrop.view.UCropView.Companion.DEFAULT_COMPRESS_FORMAT
import com.yalantis.ucrop.view.UCropView.Companion.DEFAULT_COMPRESS_QUALITY
import com.yalantis.ucrop.view.UCropView.Companion.NONE
import com.yalantis.ucrop.view.UCropView.Companion.ROTATE
import com.yalantis.ucrop.view.UCropView.Companion.ROTATE_WIDGET_SENSITIVITY_COEFFICIENT
import com.yalantis.ucrop.view.UCropView.Companion.SCALE
import com.yalantis.ucrop.view.UCropView.Companion.SCALE_WIDGET_SENSITIVITY_COEFFICIENT
import com.yalantis.ucrop.view.widget.AspectRatioTextView
import com.yalantis.ucrop.view.widget.HorizontalProgressWheelView
import kotlinx.android.synthetic.main.ucrop_controls.*
import kotlinx.android.synthetic.main.ucrop_fragment_photobox.*
import kotlinx.android.synthetic.main.ucrop_layout_rotate_wheel.*
import kotlinx.android.synthetic.main.ucrop_layout_scale_wheel.*
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

class UCropFragment : Fragment() {

    companion object {

        const val TAG = "UCropFragment"

        fun newInstance(uCrop: Bundle, callback: UCropFragmentCallback): UCropFragment {
            val fragment = UCropFragment()
            fragment.arguments = uCrop
            fragment.setCallback(callback)
            return fragment
        }
    }

    private var callback: UCropFragmentCallback? = null

    private var mActiveWidgetColor: Int = 0
    @ColorInt
    private var mRootViewBackgroundColor: Int = 0
    private var mLogoColor: Int = 0

    private var isShowBottomControls: Boolean = false

    private var mGestureCropImageView: GestureCropImageView? = null
    private var mLayoutAspectRatio: ViewGroup? = null
    private var mLayoutRotate: ViewGroup? = null
    private var mLayoutScale: ViewGroup? = null
    private val mCropAspectRatioViews = ArrayList<ViewGroup>()
    private var blockingView: View? = null

    private val mCompressFormat = DEFAULT_COMPRESS_FORMAT
    private val mCompressQuality = DEFAULT_COMPRESS_QUALITY
    private val mAllowedGestures = intArrayOf(SCALE, ROTATE, ALL)

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
            callback?.loadingProgress(false)
        }

        override fun onLoadFailure(e: Exception) {
            callback?.onCropFinish(getError(e))
        }

    }

    private val stateClickListener = View.OnClickListener { v ->
        if (!v.isSelected) {
            setWidgetState(v.id)
        }
    }

    @IntDef(NONE, SCALE, ROTATE, ALL)
    @Retention(RetentionPolicy.SOURCE)
    annotation class GestureTypes

    fun setCallback(callback: UCropFragmentCallback) {
        this.callback = callback
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.ucrop_fragment_photobox, container, false)

        arguments?.let {
            setupViews(rootView, it)
            setImageData(it)
        }
        setInitialState()
        blockingView = ucropView.addBlockingView()

        return rootView
    }

    private fun setupViews(view: View, args: Bundle) {
        context?.let {
            mActiveWidgetColor = args.getInt(UCrop.Options.EXTRA_UCROP_COLOR_WIDGET_ACTIVE, ContextCompat.getColor(it, R.color.ucrop_color_widget_active))
            mLogoColor = args.getInt(UCrop.Options.EXTRA_UCROP_LOGO_COLOR, ContextCompat.getColor(it, R.color.ucrop_color_default_logo))
            mRootViewBackgroundColor = args.getInt(UCrop.Options.EXTRA_UCROP_ROOT_VIEW_BACKGROUND_COLOR, ContextCompat.getColor(it, R.color.ucrop_color_crop_background))
        }
        isShowBottomControls = !args.getBoolean(UCrop.Options.EXTRA_HIDE_BOTTOM_CONTROLS, false)

        initiateRootViews(view)
        callback?.loadingProgress(true)

        if (isShowBottomControls) {
            val photoBox = view.findViewById<ViewGroup>(R.id.ucropPhotobox)
            View.inflate(context, R.layout.ucrop_controls, photoBox)

            stateAspectRatio.setOnClickListener(stateClickListener)
            stateRotate.setOnClickListener(stateClickListener)
            stateScale.setOnClickListener(stateClickListener)

            setupAspectRatioWidget(args, view)
            setupRotateWidget()
            setupScaleWidget()
            setupStatesWrapper(view)
        }
    }

    private fun setImageData(bundle: Bundle?) {
        val inputUri = bundle?.getParcelable<Uri>(UCrop.EXTRA_INPUT_URI)
        val outputUri = bundle?.getParcelable<Uri>(UCrop.EXTRA_OUTPUT_URI)

        bundle?.let {
            ucropView.processOptions(it)
        }

        if (inputUri != null && outputUri != null) {
            try {
                mGestureCropImageView?.setImageUri(inputUri, outputUri)
            } catch (e: Exception) {
                callback?.onCropFinish(getError(e))
            }

        } else {
            callback?.onCropFinish(getError(NullPointerException(getString(R.string.ucrop_error_input_data_is_absent))))
        }
    }

    private fun initiateRootViews(view: View) {
        mGestureCropImageView = ucropView.getCropImageView()
        mGestureCropImageView?.transformImageListener = mImageListener

        (view.findViewById<View>(R.id.image_view_logo) as ImageView).setColorFilter(mLogoColor, PorterDuff.Mode.SRC_ATOP)

        view.findViewById<View>(R.id.ucrop_frame).setBackgroundColor(mRootViewBackgroundColor)
    }

    /**
     * Use [.mActiveWidgetColor] for color filter
     */
    private fun setupStatesWrapper(view: View) {
        val stateScaleImageView = view.findViewById<ImageView>(R.id.image_view_state_scale)
        val stateRotateImageView = view.findViewById<ImageView>(R.id.image_view_state_rotate)
        val stateAspectRatioImageView = view.findViewById<ImageView>(R.id.image_view_state_aspect_ratio)

        stateScaleImageView.setImageDrawable(SelectedStateListDrawable(stateScaleImageView.drawable, mActiveWidgetColor))
        stateRotateImageView.setImageDrawable(SelectedStateListDrawable(stateRotateImageView.drawable, mActiveWidgetColor))
        stateAspectRatioImageView.setImageDrawable(SelectedStateListDrawable(stateAspectRatioImageView.drawable, mActiveWidgetColor))
    }

    private fun setupAspectRatioWidget(bundle: Bundle, view: View) {
        var aspectRationSelectedByDefault = bundle.getInt(UCrop.Options.EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, 0)
        var aspectRatioList = bundle.getParcelableArrayList<AspectRatio>(UCrop.Options.EXTRA_ASPECT_RATIO_OPTIONS)

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

        val wrapperAspectRatioList = view.findViewById<LinearLayout>(R.id.layoutAspectRatio)

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

            wrapperAspectRatioList.addView(wrapperAspectRatio)
            mCropAspectRatioViews.add(wrapperAspectRatio)
        }

        mCropAspectRatioViews[aspectRationSelectedByDefault].isSelected = true

        for (cropAspectRatioView in mCropAspectRatioViews) {
            cropAspectRatioView.setOnClickListener { v ->
                mGestureCropImageView?.targetAspectRatio = ((v as ViewGroup).getChildAt(0) as AspectRatioTextView).getAspectRatio(v.isSelected())
                mGestureCropImageView?.setImageToWrapCropBounds()
                if (!v.isSelected()) {
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

        wrapperResetRotate.setOnClickListener { resetRotation() }
        wrapperRotateByAngle.setOnClickListener { rotateByAngle(90) }
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
        textViewRotateAngle.text = String.format(Locale.getDefault(), "%.1f°", angle)
    }

    private fun setScaleText(scale: Float) {
        textViewScalePercent.text = String.format(Locale.getDefault(), "%d%%", (scale * 100).toInt())
    }

    private fun resetRotation() {
        mGestureCropImageView?.let {
            it.postRotate(-it.currentAngle)
            it.setImageToWrapCropBounds()
        }
    }

    private fun rotateByAngle(angle: Int) {
        mGestureCropImageView?.postRotate(angle.toFloat())
        mGestureCropImageView?.setImageToWrapCropBounds()
    }

    private fun setInitialState() {
        if (isShowBottomControls) {
            if (stateAspectRatio.visibility == View.VISIBLE) {
                setWidgetState(R.id.stateAspectRatio)
            } else {
                setWidgetState(R.id.stateScale)
            }
        } else {
            setAllowedGestures(0)
        }
    }

    private fun setWidgetState(@IdRes stateViewId: Int) {
        if (!isShowBottomControls) return

        stateAspectRatio.isSelected = stateViewId == R.id.stateAspectRatio
        stateRotate.isSelected = stateViewId == R.id.stateRotate
        stateScale.isSelected = stateViewId == R.id.stateScale

        mLayoutAspectRatio?.visibility = if (stateViewId == R.id.stateAspectRatio) View.VISIBLE else View.GONE
        mLayoutRotate?.visibility = if (stateViewId == R.id.stateRotate) View.VISIBLE else View.GONE
        mLayoutScale?.visibility = if (stateViewId == R.id.stateScale) View.VISIBLE else View.GONE

        when (stateViewId) {
            R.id.stateScale -> setAllowedGestures(0)
            R.id.stateRotate -> setAllowedGestures(1)
            else -> setAllowedGestures(2)
        }
    }

    private fun setAllowedGestures(tab: Int) {
        mGestureCropImageView?.isScaleEnabled = mAllowedGestures[tab] == ALL || mAllowedGestures[tab] == SCALE
        mGestureCropImageView?.isRotateEnabled = mAllowedGestures[tab] == ALL || mAllowedGestures[tab] == ROTATE
    }

    fun cropAndSaveImage() {
        blockingView?.isClickable = true
        callback?.loadingProgress(true)

        mGestureCropImageView?.cropAndSaveImage(mCompressFormat, mCompressQuality, object : BitmapCropCallback {

            override fun onBitmapCropped(resultUri: Uri, offsetX: Int, offsetY: Int, imageWidth: Int, imageHeight: Int) {
                mGestureCropImageView?.let {
                    callback?.onCropFinish(getResult(resultUri, it.targetAspectRatio, offsetX, offsetY, imageWidth, imageHeight))
                    callback?.loadingProgress(false)
                }
            }

            override fun onCropFailure(t: Throwable) {
                callback?.onCropFinish(getError(t))
            }
        })
    }

    private fun getResult(uri: Uri, resultAspectRatio: Float, offsetX: Int, offsetY: Int, imageWidth: Int, imageHeight: Int): UCropResult {
        return UCropResult(RESULT_OK, Intent()
                .putExtra(UCrop.EXTRA_OUTPUT_URI, uri)
                .putExtra(UCrop.EXTRA_OUTPUT_CROP_ASPECT_RATIO, resultAspectRatio)
                .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_WIDTH, imageWidth)
                .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_HEIGHT, imageHeight)
                .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_X, offsetX)
                .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_Y, offsetY)
        )
    }

    private fun getError(throwable: Throwable): UCropResult {
        return UCropResult(UCrop.RESULT_ERROR, Intent().putExtra(UCrop.EXTRA_ERROR, throwable))
    }

}