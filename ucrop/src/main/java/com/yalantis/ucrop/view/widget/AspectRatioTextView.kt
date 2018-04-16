package com.yalantis.ucrop.view.widget

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.support.annotation.ColorInt
import android.support.v4.content.ContextCompat
import android.support.v7.widget.AppCompatTextView
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import com.yalantis.ucrop.R
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.view.CropImageView
import java.util.*

class AspectRatioTextView @JvmOverloads constructor(context: Context,
                                                    attrs: AttributeSet? = null,
                                                    defStyleAttr: Int = 0) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val mCanvasClipBounds = Rect()
    private var mDotPaint: Paint? = null
    private var mDotSize: Int = 0
    private var mAspectRatio: Float = 0.toFloat()

    private var mAspectRatioTitle: String? = null
    private var mAspectRatioX: Float = 0.toFloat()
    private var mAspectRatioY: Float = 0.toFloat()

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.AspectRatioTextView)
        init(a)
    }

    /**
     * @param activeColor the resolved color for active elements
     */

    fun setActiveColor(@ColorInt activeColor: Int) {
        applyActiveColor(activeColor)
        invalidate()
    }

    fun setAspectRatio(aspectRatio: AspectRatio) {
        mAspectRatioTitle = aspectRatio.aspectRatioTitle
        mAspectRatioX = aspectRatio.aspectRatioX
        mAspectRatioY = aspectRatio.aspectRatioY

        mAspectRatio = if (mAspectRatioX == CropImageView.SOURCE_IMAGE_ASPECT_RATIO || mAspectRatioY == CropImageView.SOURCE_IMAGE_ASPECT_RATIO) {
            CropImageView.SOURCE_IMAGE_ASPECT_RATIO
        } else {
            mAspectRatioX / mAspectRatioY
        }

        setTitle()
    }

    fun getAspectRatio(toggleRatio: Boolean): Float {
        if (toggleRatio) {
            toggleAspectRatio()
            setTitle()
        }
        return mAspectRatio
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        mDotPaint?.let {
            if (isSelected) {
                canvas.getClipBounds(mCanvasClipBounds)
                canvas.drawCircle((mCanvasClipBounds.right - mCanvasClipBounds.left) / 2.0f, (mCanvasClipBounds.bottom - mDotSize).toFloat(),
                        (mDotSize / 2).toFloat(), it)
            }
        }
    }

    private fun init(a: TypedArray) {
        gravity = Gravity.CENTER_HORIZONTAL

        mAspectRatioTitle = a.getString(R.styleable.AspectRatioTextView_ucrop_artv_ratio_title)
        mAspectRatioX = a.getFloat(R.styleable.AspectRatioTextView_ucrop_artv_ratio_x, CropImageView.SOURCE_IMAGE_ASPECT_RATIO)
        mAspectRatioY = a.getFloat(R.styleable.AspectRatioTextView_ucrop_artv_ratio_y, CropImageView.SOURCE_IMAGE_ASPECT_RATIO)

        mAspectRatio = if (mAspectRatioX == CropImageView.SOURCE_IMAGE_ASPECT_RATIO || mAspectRatioY == CropImageView.SOURCE_IMAGE_ASPECT_RATIO) {
            CropImageView.SOURCE_IMAGE_ASPECT_RATIO
        } else {
            mAspectRatioX / mAspectRatioY
        }

        mDotSize = context.resources.getDimensionPixelSize(R.dimen.ucrop_size_dot_scale_text_view)
        mDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mDotPaint?.style = Paint.Style.FILL

        setTitle()

        val activeColor = ContextCompat.getColor(context, R.color.ucrop_color_widget_active)
        applyActiveColor(activeColor)

        a.recycle()
    }

    private fun applyActiveColor(@ColorInt activeColor: Int) {
        mDotPaint?.color = activeColor

        val textViewColorStateList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf(0)),
                intArrayOf(activeColor, ContextCompat.getColor(context, R.color.ucrop_color_widget))
        )

        setTextColor(textViewColorStateList)
    }

    private fun toggleAspectRatio() {
        if (mAspectRatio != CropImageView.SOURCE_IMAGE_ASPECT_RATIO) {
            val tempRatioW = mAspectRatioX
            mAspectRatioX = mAspectRatioY
            mAspectRatioY = tempRatioW

            mAspectRatio = mAspectRatioX / mAspectRatioY
        }
    }

    private fun setTitle() {
        text = if (!TextUtils.isEmpty(mAspectRatioTitle)) {
            mAspectRatioTitle
        } else {
            String.format(Locale.US, "%d:%d", mAspectRatioX.toInt(), mAspectRatioY.toInt())
        }
    }

}