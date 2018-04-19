package com.yalantis.ucrop.task

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.support.annotation.NonNull
import android.support.annotation.Nullable
import android.support.media.ExifInterface
import android.util.Log
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.model.CropParameters
import com.yalantis.ucrop.model.ImageState
import com.yalantis.ucrop.util.BitmapLoadUtils
import com.yalantis.ucrop.util.ImageHeaderParser
import com.yalantis.ucrop.util.copyFile
import kotlinx.coroutines.experimental.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * Crops part of image that fills the crop bounds.
 * <p/>
 * First image is downscaled if max size was set and if resulting image is larger that max size.
 * Then image is rotated accordingly.
 * Finally new Bitmap object is created and saved to file.
 */
class CropTask(@NonNull val context: Context, private var viewBitmap: Bitmap,
               @NonNull imageState: ImageState, @NonNull cropParameters: CropParameters,
               @Nullable private val cropCallback: BitmapCropCallback) {

    companion object {
        private const val TAG = "BitmapCropTask"
    }

    private val cropRect: RectF = imageState.cropRect
    private val currentImageRect: RectF = imageState.currentImageRect

    private var currentScale: Float = imageState.currentScale
    private val currentAngle: Float = imageState.currentAngle
    private val maxResultImageSizeX: Int = cropParameters.maxResultImageSizeX
    private val maxResultImageSizeY: Int = cropParameters.maxResultImageSizeY

    private val compressFormat: Bitmap.CompressFormat = cropParameters.compressFormat
    private val compressQuality: Int = cropParameters.compressQuality
    private val imageInputPath: String = cropParameters.imageInputPath
    private val imageOutputPath: String = cropParameters.imageOutputPath

    private var croppedImageWidth: Int = 0
    private var croppedImageHeight: Int = 0
    private var cropOffsetX: Int = 0
    private var cropOffsetY: Int = 0

    fun cropThemAll() = launch {
        when {
            viewBitmap.isRecycled -> throw IllegalArgumentException("ViewBitmap is recycled")
            currentImageRect.isEmpty -> throw NullPointerException("CurrentImageRect is empty")
            else -> try {
                crop()
                val uri = Uri.fromFile(File(imageOutputPath))
                cropCallback.onBitmapCropped(uri, cropOffsetX, cropOffsetY, croppedImageWidth, croppedImageHeight)
            } catch (throwable: Throwable) {
                cropCallback.onCropFailure(throwable)
            }
        }
    }

    @Throws(IOException::class)
    private fun crop() {
        // Downsize if needed
        if (maxResultImageSizeX > 0 && maxResultImageSizeY > 0) {
            val cropWidth = cropRect.width() / currentScale
            val cropHeight = cropRect.height() / currentScale

            if (cropWidth > maxResultImageSizeX || cropHeight > maxResultImageSizeY) {

                val scaleX = maxResultImageSizeX / cropWidth
                val scaleY = maxResultImageSizeY / cropHeight
                val resizeScale = Math.min(scaleX, scaleY)

                val resizedBitmap = Bitmap.createScaledBitmap(viewBitmap,
                        Math.round(viewBitmap.width * resizeScale),
                        Math.round(viewBitmap.height * resizeScale), false)
                if (viewBitmap != resizedBitmap) {
                    viewBitmap.recycle()
                }
                viewBitmap = resizedBitmap

                currentScale /= resizeScale
            }
        }

        // Rotate if needed
        if (currentAngle != 0f) {
            val tempMatrix = Matrix()
            tempMatrix.setRotate(currentAngle, (viewBitmap.width / 2).toFloat(), (viewBitmap.height / 2).toFloat())

            val rotatedBitmap = Bitmap.createBitmap(viewBitmap, 0, 0, viewBitmap.width, viewBitmap.height,
                    tempMatrix, true)
            if (viewBitmap != rotatedBitmap) {
                viewBitmap.recycle()
            }
            viewBitmap = rotatedBitmap
        }

        cropOffsetX = Math.round((cropRect.left - currentImageRect.left) / currentScale)
        cropOffsetY = Math.round((cropRect.top - currentImageRect.top) / currentScale)
        croppedImageWidth = Math.round(cropRect.width() / currentScale)
        croppedImageHeight = Math.round(cropRect.height() / currentScale)

        val shouldCrop = shouldCrop(croppedImageWidth, croppedImageHeight, cropRect, currentImageRect)
        Log.i(TAG, "Should crop: $shouldCrop")

        if (shouldCrop) {
            val originalExif = ExifInterface(imageInputPath)
            saveImage(Bitmap.createBitmap(viewBitmap, cropOffsetX, cropOffsetY, croppedImageWidth, croppedImageHeight))
            if (compressFormat == Bitmap.CompressFormat.JPEG) {
                ImageHeaderParser.copyExif(originalExif, croppedImageWidth, croppedImageHeight, imageOutputPath)
            }
        } else {
            copyFile(imageInputPath, imageOutputPath)
        }
    }


    @Throws(FileNotFoundException::class)
    private fun saveImage(croppedBitmap: Bitmap) {
        var outputStream: OutputStream? = null
        try {
            outputStream = context.contentResolver.openOutputStream(Uri.fromFile(File(imageOutputPath)))
            croppedBitmap.compress(compressFormat, compressQuality, outputStream)
            croppedBitmap.recycle()
        } finally {
            BitmapLoadUtils.close(outputStream)
        }
    }


    /**
     * Check whether an image should be cropped at all or just file can be copied to the destination path.
     * For each 1000 pixels there is one pixel of error due to matrix calculations etc.
     *
     * @param width  - crop area width
     * @param height - crop area height
     * @return - true if image must be cropped, false - if original image fits requirements
     */
    private fun shouldCrop(width: Int, height: Int, cropRect: RectF, currentImageRect: RectF): Boolean {
        var pixelError = 1
        pixelError += Math.round(Math.max(width, height) / 1000f)
        return (maxResultImageSizeX > 0 && maxResultImageSizeY > 0
                || Math.abs(cropRect.left - currentImageRect.left) > pixelError
                || Math.abs(cropRect.top - currentImageRect.top) > pixelError
                || Math.abs(cropRect.bottom - currentImageRect.bottom) > pixelError
                || Math.abs(cropRect.right - currentImageRect.right) > pixelError)
    }

}