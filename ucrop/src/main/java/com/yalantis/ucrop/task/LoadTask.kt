package com.yalantis.ucrop.task

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.util.Log
import com.yalantis.ucrop.callback.BitmapLoadCallback
import com.yalantis.ucrop.model.ExifInfo
import com.yalantis.ucrop.util.BitmapLoadUtils
import com.yalantis.ucrop.util.getPath
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import okio.Okio
import okio.Sink
import java.io.*

/**
 * Creates and returns a Bitmap for a given Uri(String url).
 * inSampleSize is calculated based on requiredWidth property. However can be adjusted if OOM occurs.
 * If any EXIF config is found - bitmap is transformed properly.
 */
class LoadTask(private val context: Context,
               private var inputUri: Uri,
               private val outputUri: Uri?,
               private val requiredWidth: Int,
               private val requiredHeight: Int,
               private val loadCallback: BitmapLoadCallback) {

    companion object {
        private const val TAG = "BitmapWorkerTask"
    }

    data class BitmapResult(val bitmapResult: Bitmap? = null,
                            val exifInfo: ExifInfo? = null,
                            val bitmapException: Exception? = null)

    fun loadImage() = launch {
        val result = async { load() }.await()
        if (result.bitmapException == null) {
            result.bitmapResult?.let {
                result.exifInfo?.let { info -> loadCallback.onBitmapLoaded(it, info, inputUri.path, outputUri?.path) }
            }
        } else {
            loadCallback.onFailure(result.bitmapException)
        }
    }


    private fun load(): BitmapResult {
        try {
            processInputUri()
        } catch (e: NullPointerException) {
            return BitmapResult(bitmapException = e)
        } catch (e: IOException) {
            return BitmapResult(bitmapException = e)
        }

        val parcelFileDescriptor: ParcelFileDescriptor?
        try {
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(inputUri, "r")
        } catch (e: FileNotFoundException) {
            return BitmapResult(bitmapException = e)
        }

        val fileDescriptor: FileDescriptor
        if (parcelFileDescriptor != null) {
            fileDescriptor = parcelFileDescriptor.fileDescriptor
        } else {
            return BitmapResult(bitmapException = NullPointerException("ParcelFileDescriptor was null for given Uri: [$inputUri]"))
        }

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options)
        if (options.outWidth == -1 || options.outHeight == -1) {
            return BitmapResult(bitmapException = IllegalArgumentException("Bounds for bitmap could not be retrieved from the Uri: [$inputUri]"))
        }

        options.inSampleSize = BitmapLoadUtils.calculateInSampleSize(options, requiredWidth, requiredHeight)
        options.inJustDecodeBounds = false

        var decodeSampledBitmap: Bitmap? = null

        var decodeAttemptSuccess = false
        while (!decodeAttemptSuccess) {
            try {
                decodeSampledBitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options)
                decodeAttemptSuccess = true
            } catch (error: OutOfMemoryError) {
                Log.e(TAG, "doInBackground: BitmapFactory.decodeFileDescriptor: ", error)
                options.inSampleSize *= 2
            }

        }

        if (decodeSampledBitmap == null) {
            return BitmapResult(bitmapException = IllegalArgumentException("Bitmap could not be decoded from the Uri: [$inputUri]"))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            BitmapLoadUtils.close(parcelFileDescriptor)
        }

        val exifOrientation = BitmapLoadUtils.getExifOrientation(context, inputUri)
        val exifDegrees = BitmapLoadUtils.exifToDegrees(exifOrientation)
        val exifTranslation = BitmapLoadUtils.exifToTranslation(exifOrientation)

        val exifInfo = ExifInfo(exifOrientation, exifDegrees, exifTranslation)

        val matrix = Matrix()
        if (exifDegrees != 0) {
            matrix.preRotate(exifDegrees.toFloat())
        }
        if (exifTranslation != 1) {
            matrix.postScale(exifTranslation.toFloat(), 1f)
        }
        return if (!matrix.isIdentity) {
            BitmapResult(BitmapLoadUtils.transformBitmap(decodeSampledBitmap, matrix), exifInfo)
        } else BitmapResult(decodeSampledBitmap, exifInfo)
    }

    @Throws(NullPointerException::class, IOException::class)
    private fun processInputUri() {
        val inputUriScheme = inputUri.scheme
        Log.d(TAG, "Uri scheme: $inputUriScheme")
        if ("http" == inputUriScheme || "https" == inputUriScheme) {
            try {
                downloadFile(inputUri, outputUri)
            } catch (e: NullPointerException) {
                Log.e(TAG, "Downloading failed", e)
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Downloading failed", e)
                throw e
            }

        } else if ("content" == inputUriScheme) {
            getFilePath()?.let { path ->
                if (!TextUtils.isEmpty(path) && File(path).exists()) {
                    inputUri = Uri.fromFile(File(path))
                } else {
                    try {
                        copyFile(inputUri, outputUri)
                    } catch (e: NullPointerException) {
                        Log.e(TAG, "Copying failed", e)
                        throw e
                    } catch (e: IOException) {
                        Log.e(TAG, "Copying failed", e)
                        throw e
                    }
                }

            }
        } else if ("file" != inputUriScheme) {
            Log.e(TAG, "Invalid Uri scheme $inputUriScheme")
            throw IllegalArgumentException("Invalid Uri scheme$inputUriScheme")
        }
    }

    private fun getFilePath(): String? {
        return if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            getPath(context, inputUri)
        } else {
            null
        }
    }

    @Throws(NullPointerException::class, IOException::class)
    private fun copyFile(inputUri: Uri, outputUri: Uri?) {
        Log.d(TAG, "copyFile")

        if (outputUri == null) {
            throw NullPointerException("Output Uri is null - cannot copy image")
        }

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(inputUri)
            outputStream = FileOutputStream(File(outputUri.path))
            if (inputStream == null) {
                throw NullPointerException("InputStream for given input Uri is null")
            }

            val buffer = ByteArray(1024)
            while (inputStream.read(buffer) > 0) {
                outputStream.write(buffer, 0, inputStream.read(buffer))
            }
        } finally {
            BitmapLoadUtils.close(outputStream)
            BitmapLoadUtils.close(inputStream)

            // swap uris, because input image was copied to the output destination
            // (cropped image will override it later)
            this.outputUri?.let { this.inputUri = it }
        }
    }

    @Throws(NullPointerException::class, IOException::class)
    private fun downloadFile(inputUri: Uri, outputUri: Uri?) {
        Log.d(TAG, "downloadFile")

        if (outputUri == null) {
            throw NullPointerException("Output Uri is null - cannot download image")
        }

        val client = OkHttpClient()

        var source: BufferedSource? = null
        var sink: Sink? = null
        var response: Response? = null
        try {
            val request = Request.Builder()
                    .url(inputUri.toString())
                    .build()
            response = client.newCall(request).execute()
            source = response?.body()?.source()

            val outputStream = context.contentResolver.openOutputStream(outputUri)
            if (outputStream != null) {
                sink = Okio.sink(outputStream)
                sink?.let {
                    source?.readAll(it)
                }
            } else {
                throw NullPointerException("OutputStream for given output Uri is null")
            }
        } finally {
            BitmapLoadUtils.close(source)
            BitmapLoadUtils.close(sink)
            if (response != null) {
                BitmapLoadUtils.close(response.body())
            }
            client.dispatcher().cancelAll()

            // swap uris, because input image was downloaded to the output destination
            // (cropped image will override it later)
            this.outputUri?.let { this.inputUri = it }
        }
    }

}