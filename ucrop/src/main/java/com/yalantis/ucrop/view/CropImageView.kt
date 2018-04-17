package com.yalantis.ucrop.view

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.support.annotation.IntRange
import android.util.AttributeSet
import com.yalantis.ucrop.R
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.callback.CropBoundsChangeListener
import com.yalantis.ucrop.model.CropParameters
import com.yalantis.ucrop.model.ImageState
import com.yalantis.ucrop.task.BitmapCropTask
import com.yalantis.ucrop.util.CubicEasing
import com.yalantis.ucrop.util.getCornersFromRect
import com.yalantis.ucrop.util.getRectSidesFromCorners
import com.yalantis.ucrop.util.trapToRect
import java.lang.ref.WeakReference
import java.util.*

/**
 * This class adds crop feature, methods to draw crop guidelines, and keep image in correct state.
 * Also it extends parent class methods to add checks for scale; animating zoom in/out.
 */
open class CropImageView @JvmOverloads constructor(context: Context,
                                                   attrs: AttributeSet? = null,
                                                   defStyle: Int = 0) : TransformImageView(context, attrs, defStyle) {

    companion object {
        const val DEFAULT_MAX_BITMAP_SIZE = 0
        const val DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION = 500
        const val DEFAULT_MAX_SCALE_MULTIPLIER = 10.0f
        const val SOURCE_IMAGE_ASPECT_RATIO = 0f
        const val DEFAULT_ASPECT_RATIO = SOURCE_IMAGE_ASPECT_RATIO
    }

    private val cropRect = RectF()
    private val tempMatrix = Matrix()
    private var maxScaleMultiplier = DEFAULT_MAX_SCALE_MULTIPLIER
    var cropBoundsChangeListener: CropBoundsChangeListener? = null
    private var wrapCropBoundsRunnable: Runnable? = null
    private var zoomImageToPositionRunnable: Runnable? = null

    /**
     * @return - maximum scale value for current image and crop ratio
     */
    var maxScale: Float = 0f
        private set
    /**
     * @return - minimum scale value for current image and crop ratio
     */
    var minScale: Float = 0f
        private set
    private var maxResultImageSizeX = 0
    private var maxResultImageSizeY = 0
    private var mImageToWrapCropBoundsAnimDuration = DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION.toLong()

    /**
     * This method sets aspect ratio for crop bounds.
     * If [.SOURCE_IMAGE_ASPECT_RATIO] value is passed - aspect ratio is calculated
     * based on current image width and height.
     *
     * @param targetAspectRatio - aspect ratio for image crop (e.g. 1.77(7) for 16:9)
     */
    var targetAspectRatio: Float = 0f
        set(targetAspectRatio) {
            val drawable = drawable
            if (drawable == null) {
                field = targetAspectRatio
                return
            }

            field = if (targetAspectRatio == SOURCE_IMAGE_ASPECT_RATIO) {
                drawable.intrinsicWidth / drawable.intrinsicHeight.toFloat()
            } else {
                targetAspectRatio
            }

            cropBoundsChangeListener?.onCropAspectRatioChanged(targetAspectRatio)
        }

    /**
     * This method checks whether current image fills the crop bounds.
     */
    protected val isImageWrapCropBounds: Boolean
        get() = isImageWrapCropBounds(currentImageCorners)

    /**
     * Cancels all current animations and sets image to fill crop area (without animation).
     * Then creates and executes [BitmapCropTask] with proper parameters.
     */
    fun cropAndSaveImage(compressFormat: Bitmap.CompressFormat, compressQuality: Int,
                         cropCallback: BitmapCropCallback?) {
        cancelAllAnimations()
        setImageToWrapCropBounds(false)

        val imageState = ImageState(
                cropRect, currentImageCorners.trapToRect(),
                currentScale, currentAngle)

        val cropParameters = CropParameters(
                maxResultImageSizeX, maxResultImageSizeY,
                compressFormat, compressQuality,
                inputPath!!, outputPath!!, currentExifInfo!!)

        BitmapCropTask(context, viewBitmap, imageState, cropParameters, cropCallback).execute()
    }

    /**
     * Updates current crop rectangle with given. Also recalculates image properties and position
     * to fit new crop rectangle.
     *
     * @param cropRect - new crop rectangle
     */
    fun setCropRect(cropRect: RectF) {
//        targetAspectRatio = cropRect.width() / cropRect.height() todo set without setter
        this.cropRect.set(cropRect.left - paddingLeft, cropRect.top - paddingTop,
                cropRect.right - paddingRight, cropRect.bottom - paddingBottom)
        calculateImageScaleBounds()
        setImageToWrapCropBounds()
    }

    /**
     * This method sets maximum width for resulting cropped image
     *
     * @param maxResultImageSizeX - size in pixels
     */
    fun setMaxResultImageSizeX(@IntRange(from = 10) maxResultImageSizeX: Int) {
        this.maxResultImageSizeX = maxResultImageSizeX
    }

    /**
     * This method sets maximum width for resulting cropped image
     *
     * @param maxResultImageSizeY - size in pixels
     */
    fun setMaxResultImageSizeY(@IntRange(from = 10) maxResultImageSizeY: Int) {
        this.maxResultImageSizeY = maxResultImageSizeY
    }

    /**
     * This method sets animation duration for image to wrap the crop bounds
     *
     * @param imageToWrapCropBoundsAnimDuration - duration in milliseconds
     */
    fun setImageToWrapCropBoundsAnimDuration(@IntRange(from = 100) imageToWrapCropBoundsAnimDuration: Long) {
        if (imageToWrapCropBoundsAnimDuration > 0) {
            mImageToWrapCropBoundsAnimDuration = imageToWrapCropBoundsAnimDuration
        } else {
            throw IllegalArgumentException("Animation duration cannot be negative value.")
        }
    }

    /**
     * This method sets multiplier that is used to calculate max image scale from min image scale.
     *
     * @param maxScaleMultiplier - (minScale * maxScaleMultiplier) = maxScale
     */
    fun setMaxScaleMultiplier(maxScaleMultiplier: Float) {
        this.maxScaleMultiplier = maxScaleMultiplier
    }

    /**
     * This method scales image down for given value related to image center.
     */
    fun zoomOutImage(deltaScale: Float) {
        zoomOutImage(deltaScale, cropRect.centerX(), cropRect.centerY())
    }

    /**
     * This method scales image down for given value related given coords (x, y).
     */
    fun zoomOutImage(scale: Float, centerX: Float, centerY: Float) {
        if (scale >= minScale) {
            postScale(scale / currentScale, centerX, centerY)
        }
    }

    /**
     * This method scales image up for given value related to image center.
     */
    fun zoomInImage(deltaScale: Float) {
        zoomInImage(deltaScale, cropRect.centerX(), cropRect.centerY())
    }

    /**
     * This method scales image up for given value related to given coords (x, y).
     */
    fun zoomInImage(scale: Float, centerX: Float, centerY: Float) {
        if (scale <= maxScale) {
            postScale(scale / currentScale, centerX, centerY)
        }
    }

    /**
     * This method changes image scale for given value related to point (px, py) but only if
     * resulting scale is in min/max bounds.
     *
     * @param deltaScale - scale value
     * @param px         - scale center X
     * @param py         - scale center Y
     */
    override fun postScale(deltaScale: Float, px: Float, py: Float) {
        if (deltaScale > 1 && currentScale * deltaScale <= maxScale) {
            super.postScale(deltaScale, px, py)
        } else if (deltaScale < 1 && currentScale * deltaScale >= minScale) {
            super.postScale(deltaScale, px, py)
        }
    }

    /**
     * This method rotates image for given angle related to the image center.
     *
     * @param deltaAngle - angle to rotate
     */
    fun postRotate(deltaAngle: Float) {
        postRotate(deltaAngle, cropRect.centerX(), cropRect.centerY())
    }

    /**
     * This method cancels all current Runnable objects that represent animations.
     */
    fun cancelAllAnimations() {
        removeCallbacks(wrapCropBoundsRunnable)
        removeCallbacks(zoomImageToPositionRunnable)
    }

    /**
     * If image doesn't fill the crop bounds it must be translated and scaled properly to fill those.
     *
     *
     * Therefore this method calculates delta X, Y and scale values and passes them to the
     * [WrapCropBoundsRunnable] which animates image.
     * Scale value must be calculated only if image won't fill the crop bounds after it's translated to the
     * crop bounds rectangle center. Using temporary variables this method checks this case.
     */
    @JvmOverloads
    fun setImageToWrapCropBounds(animate: Boolean = true) {
        if (isBitmapLaidOut && !isImageWrapCropBounds) {

            val currentX = currentImageCenter[0]
            val currentY = currentImageCenter[1]
            val currentScale = currentScale

            var deltaX = cropRect.centerX() - currentX
            var deltaY = cropRect.centerY() - currentY
            var deltaScale = 0f

            tempMatrix.reset()
            tempMatrix.setTranslate(deltaX, deltaY)

            val tempCurrentImageCorners = Arrays.copyOf(currentImageCorners, currentImageCorners.size)
            tempMatrix.mapPoints(tempCurrentImageCorners)

            val willImageWrapCropBoundsAfterTranslate = isImageWrapCropBounds(tempCurrentImageCorners)

            if (willImageWrapCropBoundsAfterTranslate) {
                val imageIndents = calculateImageIndents()
                deltaX = -(imageIndents[0] + imageIndents[2])
                deltaY = -(imageIndents[1] + imageIndents[3])
            } else {
                val tempCropRect = RectF(cropRect)
                tempMatrix.reset()
                tempMatrix.setRotate(currentAngle)
                tempMatrix.mapRect(tempCropRect)

                val currentImageSides = currentImageCorners.getRectSidesFromCorners()

                deltaScale = Math.max(tempCropRect.width() / currentImageSides[0],
                        tempCropRect.height() / currentImageSides[1])
                deltaScale = deltaScale * currentScale - currentScale
            }

            if (animate) {
                post(WrapCropBoundsRunnable(
                        this@CropImageView, mImageToWrapCropBoundsAnimDuration, currentX, currentY, deltaX, deltaY,
                        currentScale, deltaScale, willImageWrapCropBoundsAfterTranslate))
            } else {
                postTranslate(deltaX, deltaY)
                if (!willImageWrapCropBoundsAfterTranslate) {
                    zoomInImage(currentScale + deltaScale, cropRect.centerX(), cropRect.centerY())
                }
            }
        }
    }

    /**
     * First, un-rotate image and crop rectangles (make image rectangle axis-aligned).
     * Second, calculate deltas between those rectangles sides.
     * Third, depending on delta (its sign) put them or zero inside an array.
     * Fourth, using Matrix, rotate back those points (indents).
     *
     * @return - the float array of image indents (4 floats) - in this order [left, top, right, bottom]
     */
    private fun calculateImageIndents(): FloatArray {
        tempMatrix.reset()
        tempMatrix.setRotate(-currentAngle)

        val unRotatedImageCorners = Arrays.copyOf(currentImageCorners, currentImageCorners.size)
        val unRotatedCropBoundsCorners = cropRect.getCornersFromRect()

        tempMatrix.mapPoints(unRotatedImageCorners)
        tempMatrix.mapPoints(unRotatedCropBoundsCorners)

        val unRotatedImageRect = unRotatedImageCorners.trapToRect()
        val unRotatedCropRect = unRotatedCropBoundsCorners.trapToRect()

        val deltaLeft = unRotatedImageRect.left - unRotatedCropRect.left
        val deltaTop = unRotatedImageRect.top - unRotatedCropRect.top
        val deltaRight = unRotatedImageRect.right - unRotatedCropRect.right
        val deltaBottom = unRotatedImageRect.bottom - unRotatedCropRect.bottom

        val indents = FloatArray(4)
        indents[0] = if (deltaLeft > 0) deltaLeft else 0f
        indents[1] = if (deltaTop > 0) deltaTop else 0f
        indents[2] = if (deltaRight < 0) deltaRight else 0f
        indents[3] = if (deltaBottom < 0) deltaBottom else 0f

        tempMatrix.reset()
        tempMatrix.setRotate(currentAngle)
        tempMatrix.mapPoints(indents)

        return indents
    }

    /**
     * When image is laid out it must be centered properly to fit current crop bounds.
     */
    override fun onImageLaidOut() {
        super.onImageLaidOut()
        val drawable = drawable ?: return

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        if (targetAspectRatio == SOURCE_IMAGE_ASPECT_RATIO) {
            targetAspectRatio = drawableWidth / drawableHeight
        }

        val height = (currentWidth / targetAspectRatio).toInt()
        if (height > currentHeight) {
            val width = (currentHeight * targetAspectRatio).toInt()
            val halfDiff = (currentWidth - width) / 2
            cropRect.set(halfDiff.toFloat(), 0f, (width + halfDiff).toFloat(), currentHeight.toFloat())
        } else {
            val halfDiff = (currentHeight - height) / 2
            cropRect.set(0f, halfDiff.toFloat(), currentWidth.toFloat(), (height + halfDiff).toFloat())
        }

        calculateImageScaleBounds(drawableWidth, drawableHeight)
        setupInitialImagePosition(drawableWidth, drawableHeight)

        cropBoundsChangeListener?.onCropAspectRatioChanged(targetAspectRatio)

        transformImageListener?.onScale(currentScale)
        transformImageListener?.onRotate(currentAngle)

    }

    /**
     * This methods checks whether a rectangle that is represented as 4 corner points (8 floats)
     * fills the crop bounds rectangle.
     *
     * @param imageCorners - corners of a rectangle
     * @return - true if it wraps crop bounds, false - otherwise
     */
    private fun isImageWrapCropBounds(imageCorners: FloatArray): Boolean {
        tempMatrix.reset()
        tempMatrix.setRotate(-currentAngle)

        val unRotatedImageCorners = Arrays.copyOf(imageCorners, imageCorners.size)
        tempMatrix.mapPoints(unRotatedImageCorners)

        val unRotatedCropBoundsCorners = cropRect.getCornersFromRect()
        tempMatrix.mapPoints(unRotatedCropBoundsCorners)

        return unRotatedImageCorners.trapToRect().contains(unRotatedCropBoundsCorners.trapToRect())
    }

    /**
     * This method changes image scale (animating zoom for given duration), related to given center (x,y).
     *
     * @param scale      - target scale
     * @param centerX    - scale center X
     * @param centerY    - scale center Y
     * @param durationMs - zoom animation duration
     */
    protected fun zoomImageToPosition(scale: Float, centerX: Float, centerY: Float, durationMs: Long) {
        var newScale = scale
        if (newScale > maxScale) {
            newScale = maxScale
        }

        val oldScale = currentScale
        val deltaScale = newScale - oldScale

        post(ZoomImageToPosition(this@CropImageView,
                durationMs, oldScale, deltaScale, centerX, centerY))
    }

    private fun calculateImageScaleBounds() {
        val drawable = drawable ?: return
        calculateImageScaleBounds(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
    }

    /**
     * This method calculates image minimum and maximum scale values for current [.cropRect].
     *
     * @param drawableWidth  - image width
     * @param drawableHeight - image height
     */
    private fun calculateImageScaleBounds(drawableWidth: Float, drawableHeight: Float) {
        val widthScale = Math.min(cropRect.width() / drawableWidth, cropRect.width() / drawableHeight)
        val heightScale = Math.min(cropRect.height() / drawableHeight, cropRect.height() / drawableWidth)

        minScale = Math.min(widthScale, heightScale)
        maxScale = minScale * maxScaleMultiplier
    }

    /**
     * This method calculates initial image position so it is positioned properly.
     * Then it sets those values to the current image matrix.
     *
     * @param drawableWidth  - image width
     * @param drawableHeight - image height
     */
    private fun setupInitialImagePosition(drawableWidth: Float, drawableHeight: Float) {
        val cropRectWidth = cropRect.width()
        val cropRectHeight = cropRect.height()

        val widthScale = cropRect.width() / drawableWidth
        val heightScale = cropRect.height() / drawableHeight

        val initialMinScale = Math.max(widthScale, heightScale)

        val tw = (cropRectWidth - drawableWidth * initialMinScale) / 2.0f + cropRect.left
        val th = (cropRectHeight - drawableHeight * initialMinScale) / 2.0f + cropRect.top

        currentImageMatrix.reset()
        currentImageMatrix.postScale(initialMinScale, initialMinScale)
        currentImageMatrix.postTranslate(tw, th)
        imageMatrix = currentImageMatrix
    }

    /**
     * This method extracts all needed values from the styled attributes.
     * Those are used to configure the view.
     */
    fun processStyledAttributes(a: TypedArray) {
        val targetAspectRatioX = Math.abs(a.getFloat(R.styleable.UCropView_ucrop_aspect_ratio_x, DEFAULT_ASPECT_RATIO))
        val targetAspectRatioY = Math.abs(a.getFloat(R.styleable.UCropView_ucrop_aspect_ratio_y, DEFAULT_ASPECT_RATIO))

        targetAspectRatio = if (targetAspectRatioX == SOURCE_IMAGE_ASPECT_RATIO || targetAspectRatioY == SOURCE_IMAGE_ASPECT_RATIO) {
            SOURCE_IMAGE_ASPECT_RATIO
        } else {
            targetAspectRatioX / targetAspectRatioY
        }
    }

    /**
     * This Runnable is used to animate an image so it fills the crop bounds entirely.
     * Given values are interpolated during the animation time.
     * Runnable can be terminated either vie [.cancelAllAnimations] method
     * or when certain conditions inside [WrapCropBoundsRunnable.run] method are triggered.
     */
    private class WrapCropBoundsRunnable(cropImageView: CropImageView,
                                         private val mDurationMs: Long,
                                         private val mOldX: Float, private val mOldY: Float,
                                         private val mCenterDiffX: Float, private val mCenterDiffY: Float,
                                         private val mOldScale: Float, private val mDeltaScale: Float,
                                         private val mWillBeImageInBoundsAfterTranslate: Boolean) : Runnable {

        private val mCropImageView: WeakReference<CropImageView> = WeakReference(cropImageView)
        private val mStartTime: Long = System.currentTimeMillis()

        override fun run() {
            val cropImageView = mCropImageView.get() ?: return

            val now = System.currentTimeMillis()
            val currentMs = Math.min(mDurationMs, now - mStartTime).toFloat()

            val newX = CubicEasing.easeOut(currentMs, 0f, mCenterDiffX, mDurationMs.toFloat())
            val newY = CubicEasing.easeOut(currentMs, 0f, mCenterDiffY, mDurationMs.toFloat())
            val newScale = CubicEasing.easeInOut(currentMs, 0f, mDeltaScale, mDurationMs.toFloat())

            if (currentMs < mDurationMs) {
                cropImageView.postTranslate(newX - (cropImageView.currentImageCenter[0] - mOldX), newY - (cropImageView.currentImageCenter[1] - mOldY))
                if (!mWillBeImageInBoundsAfterTranslate) {
                    cropImageView.zoomInImage(mOldScale + newScale, cropImageView.cropRect.centerX(), cropImageView.cropRect.centerY())
                }
                if (!cropImageView.isImageWrapCropBounds) {
                    cropImageView.post(this)
                }
            }
        }
    }

    /**
     * This Runnable is used to animate an image zoom.
     * Given values are interpolated during the animation time.
     * Runnable can be terminated either vie [.cancelAllAnimations] method
     * or when certain conditions inside [ZoomImageToPosition.run] method are triggered.
     */
    private class ZoomImageToPosition(cropImageView: CropImageView,
                                      private val mDurationMs: Long,
                                      private val mOldScale: Float, private val mDeltaScale: Float,
                                      private val mDestX: Float, private val mDestY: Float) : Runnable {

        private val mCropImageView: WeakReference<CropImageView> = WeakReference(cropImageView)
        private val mStartTime: Long = System.currentTimeMillis()

        override fun run() {
            val cropImageView = mCropImageView.get() ?: return

            val now = System.currentTimeMillis()
            val currentMs = Math.min(mDurationMs, now - mStartTime).toFloat()
            val newScale = CubicEasing.easeInOut(currentMs, 0f, mDeltaScale, mDurationMs.toFloat())

            if (currentMs < mDurationMs) {
                cropImageView.zoomInImage(mOldScale + newScale, mDestX, mDestY)
                cropImageView.post(this)
            } else {
                cropImageView.setImageToWrapCropBounds()
            }
        }
    }
}
