package com.yalantis.ucrop.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.support.annotation.IntRange
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView

import com.yalantis.ucrop.callback.BitmapLoadCallback
import com.yalantis.ucrop.model.ExifInfo
import com.yalantis.ucrop.util.BitmapLoadUtils
import com.yalantis.ucrop.util.FastBitmapDrawable
import com.yalantis.ucrop.util.getCenterFromRect
import com.yalantis.ucrop.util.getCornersFromRect

/**
 * This class provides base logic to setup the image, transform it with matrix (move, scale, rotate),
 * and methods to get current matrix state.
 */
open class TransformImageView @JvmOverloads constructor(context: Context,
                                                        attrs: AttributeSet? = null,
                                                        defStyle: Int = 0) : AppCompatImageView(context, attrs, defStyle) {

    companion object {
        private const val TAG = "TransformImageView"

        private const val RECT_CORNER_POINTS_COORDS = 8
        private const val RECT_CENTER_POINT_COORDS = 2
        private const val MATRIX_VALUES_COUNT = 9
    }

    protected val currentImageCorners = FloatArray(RECT_CORNER_POINTS_COORDS)
    protected val currentImageCenter = FloatArray(RECT_CENTER_POINT_COORDS)

    private val matrixValues = FloatArray(MATRIX_VALUES_COUNT)

    protected var currentImageMatrix = Matrix()
    protected var currentWidth: Int = 0
    protected var currentHeight: Int = 0

    var transformImageListener: TransformImageListener? = null

    private var initialImageCorners: FloatArray? = null
    private var initialImageCenter: FloatArray? = null

    protected var isBitmapDecoded = false
    protected var isBitmapLaidOut = false

    /**
     * Be sure to call it before [.setImageURI] or other image setters.
     *
     * @param maxBitmapSize - max size for both width and height of bitmap that will be used in the view.
     */
    var maxBitmapSize = 0
        get() {
            if (field <= 0) {
                maxBitmapSize = BitmapLoadUtils.calculateMaxBitmapSize(context)
            }
            return field
        }

    var inputPath: String? = null
        private set
    var outputPath: String? = null
        private set
    var currentExifInfo: ExifInfo? = null
        private set

    /**
     * @return - current image scale value.
     * [1.0f - for original image, 2.0f - for 200% scaled image, etc.]
     */
    val currentScale: Float
        get() = getMatrixScale(currentImageMatrix)

    /**
     * @return - current image rotation angle.
     */
    val currentAngle: Float
        get() = getMatrixAngle(currentImageMatrix)

    val viewBitmap: Bitmap?
        get() = if (drawable == null || drawable !is FastBitmapDrawable) {
            null
        } else {
            (drawable as FastBitmapDrawable).bitmap
        }

    /**
     * Interface for rotation and scale change notifying.
     */
    interface TransformImageListener {

        fun onLoadComplete()

        fun onLoadFailure(e: Exception)

        fun onRotate(currentAngle: Float)

        fun onScale(currentScale: Float)

    }

    init {
        init()
    }

    override fun setScaleType(scaleType: ImageView.ScaleType) {
        if (scaleType == ImageView.ScaleType.MATRIX) {
            super.setScaleType(scaleType)
        } else {
            Log.w(TAG, "Invalid ScaleType. Only ScaleType.MATRIX can be used")
        }
    }

    override fun setImageBitmap(bitmap: Bitmap) {
        setImageDrawable(FastBitmapDrawable(bitmap))
    }

    /**
     * This method takes an Uri as a parameter, then calls method to decode it into Bitmap with specified size.
     *
     * @param imageUri - image Uri
     * @throws Exception - can throw exception if having problems with decoding Uri or OOM.
     */
    @Throws(Exception::class)
    fun setImageUri(imageUri: Uri, outputUri: Uri?) {
        val maxBitmapSize = maxBitmapSize

        BitmapLoadUtils.decodeBitmapInBackground(context, imageUri, outputUri, maxBitmapSize, maxBitmapSize,
                object : BitmapLoadCallback {

                    override fun onBitmapLoaded(bitmap: Bitmap, exifInfo: ExifInfo, imageInputPath: String, imageOutputPath: String?) {
                        inputPath = imageInputPath
                        outputPath = imageOutputPath
                        currentExifInfo = exifInfo

                        isBitmapDecoded = true
                        setImageBitmap(bitmap)
                    }

                    override fun onFailure(bitmapWorkerException: Exception) {
                        Log.e(TAG, "onFailure: setImageUri", bitmapWorkerException)
                        transformImageListener?.onLoadFailure(bitmapWorkerException)
                    }
                })
    }

    /**
     * This method calculates scale value for given Matrix object.
     */
    fun getMatrixScale(matrix: Matrix): Float {
        return Math.sqrt(Math.pow(getMatrixValue(matrix, Matrix.MSCALE_X).toDouble(), 2.0) + Math.pow(getMatrixValue(matrix, Matrix.MSKEW_Y).toDouble(), 2.0)).toFloat()
    }

    /**
     * This method calculates rotation angle for given Matrix object.
     */
    fun getMatrixAngle(matrix: Matrix): Float {
        return (-(Math.atan2(getMatrixValue(matrix, Matrix.MSKEW_X).toDouble(),
                getMatrixValue(matrix, Matrix.MSCALE_X).toDouble()) * (180 / Math.PI))).toFloat()
    }

    override fun setImageMatrix(matrix: Matrix) {
        super.setImageMatrix(matrix)
        currentImageMatrix.set(matrix)
        updateCurrentImagePoints()
    }

    /**
     * This method translates current image.
     *
     * @param deltaX - horizontal shift
     * @param deltaY - vertical shift
     */
    fun postTranslate(deltaX: Float, deltaY: Float) {
        if (deltaX != 0f || deltaY != 0f) {
            currentImageMatrix.postTranslate(deltaX, deltaY)
            imageMatrix = currentImageMatrix
        }
    }

    /**
     * This method scales current image.
     *
     * @param deltaScale - scale value
     * @param px         - scale center X
     * @param py         - scale center Y
     */
    open fun postScale(deltaScale: Float, px: Float, py: Float) {
        if (deltaScale != 0f) {
            currentImageMatrix.postScale(deltaScale, deltaScale, px, py)
            imageMatrix = currentImageMatrix
            transformImageListener?.onScale(getMatrixScale(currentImageMatrix))
        }
    }

    /**
     * This method rotates current image.
     *
     * @param deltaAngle - rotation angle
     * @param px         - rotation center X
     * @param py         - rotation center Y
     */
    fun postRotate(deltaAngle: Float, px: Float, py: Float) {
        if (deltaAngle != 0f) {
            currentImageMatrix.postRotate(deltaAngle, px, py)
            imageMatrix = currentImageMatrix
            transformImageListener?.onRotate(getMatrixAngle(currentImageMatrix))
        }
    }

    protected open fun init() {
        scaleType = ScaleType.MATRIX
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var newLeft = left
        var newTop = top
        var newRight = right
        var newBottom = bottom
        super.onLayout(changed, newLeft, newTop, newRight, newBottom)
        if (changed || isBitmapDecoded && !isBitmapLaidOut) {
            newLeft = paddingLeft
            newTop = paddingTop
            newRight = width - paddingRight
            newBottom = height - paddingBottom
            currentWidth = newRight - newLeft
            currentHeight = newBottom - newTop

            onImageLaidOut()
        }
    }

    /**
     * When image is laid out [.initialImageCenter] and [.initialImageCenter]
     * must be set.
     */
    protected open fun onImageLaidOut() {
        val drawable = drawable ?: return

        val w = drawable.intrinsicWidth.toFloat()
        val h = drawable.intrinsicHeight.toFloat()

        Log.d(TAG, String.format("Image size: [%d:%d]", w.toInt(), h.toInt()))

        val initialImageRect = RectF(0f, 0f, w, h)
        initialImageCorners = initialImageRect.getCornersFromRect()
        initialImageCenter = initialImageRect.getCenterFromRect()

        isBitmapLaidOut = true

        transformImageListener?.onLoadComplete()
    }

    /**
     * This method returns Matrix value for given index.
     *
     * @param matrix     - valid Matrix object
     * @param valueIndex - index of needed value. See [Matrix.MSCALE_X] and others.
     * @return - matrix value for index
     */
    private fun getMatrixValue(matrix: Matrix, @IntRange(from = 0, to = MATRIX_VALUES_COUNT.toLong()) valueIndex: Int): Float {
        matrix.getValues(matrixValues)
        return matrixValues[valueIndex]
    }

    /**
     * This method logs given matrix X, Y, scale, and angle values.
     * Can be used for debug.
     */
    protected fun printMatrix(logPrefix: String, matrix: Matrix) {
        val x = getMatrixValue(matrix, Matrix.MTRANS_X)
        val y = getMatrixValue(matrix, Matrix.MTRANS_Y)
        val rScale = getMatrixScale(matrix)
        val rAngle = getMatrixAngle(matrix)
        Log.d(TAG, "$logPrefix: matrix: { x: $x, y: $y, scale: $rScale, angle: $rAngle }")
    }

    /**
     * This method updates current image corners and center points that are stored in
     * [.currentImageCorners] and [.currentImageCenter] arrays.
     * Those are used for several calculations.
     */
    private fun updateCurrentImagePoints() {
        currentImageMatrix.mapPoints(currentImageCorners, initialImageCorners)
        currentImageMatrix.mapPoints(currentImageCenter, initialImageCenter)
    }

}
