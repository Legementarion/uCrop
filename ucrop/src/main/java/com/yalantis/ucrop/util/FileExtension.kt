package com.yalantis.ucrop.util

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.*

private const val TAG = "FileUtils"

/**
 * The Uri to check.
 * @return Whether the Uri authority is ExternalStorageProvider.
 */
fun Uri.isExternalStorageDocument() = "com.android.externalstorage.documents" == authority

/**
 * The Uri to check.
 * @return Whether the Uri authority is DownloadsProvider.
 */
fun Uri.isDownloadsDocument() = "com.android.providers.downloads.documents" == authority

/**
 * The Uri to check.
 * @return Whether the Uri authority is MediaProvider.
 */
fun Uri.isMediaDocument() = "com.android.providers.media.documents" == authority

/**
 * The Uri to check.
 * @return Whether the Uri authority is Google Photos.
 */
fun Uri.isGooglePhotosUri() = "com.google.android.apps.photos.content" == authority


/**
 * Get the value of the data column for this Uri. This is useful for
 * MediaStore Uris, and other file-based ContentProviders.
 *
 * @param context       The context.
 * @param selection     (Optional) Filter used in the query.
 * @param selectionArgs (Optional) Selection arguments used in the query.
 * @return The value of the _data column, which is typically a file path.
 */
fun Uri.getDataColumn(context: Context, selection: String?,
                      selectionArgs: Array<String>?): String? {
    var cursor: Cursor? = null
    val column = "_data"
    val projection = arrayOf(column)

    try {
        cursor = context.contentResolver.query(this, projection, selection, selectionArgs, null)
        cursor?.let {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(column)
                return it.getString(columnIndex)
            }
        }
    } catch (ex: IllegalArgumentException) {
        Log.i(TAG, String.format(Locale.getDefault(), "getDataColumn: _data - [%s]", ex.message))
    } finally {
        cursor?.close()
    }
    return null
}

/**
 * Get a file path from a Uri. This will get the the path for Storage Access
 * Framework Documents, as well as the _data field for the MediaStore and
 * other file-based ContentProviders.<br></br>
 * <br></br>
 * Callers should check whether the path is local before assuming it
 * represents a local file.
 *
 * @param context The context.
 * @param uri     The Uri to query.
 */
@SuppressLint("NewApi")
fun getPath(context: Context, uri: Uri): String? {
    val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

    // DocumentProvider
    if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
        if (uri.isExternalStorageDocument()) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val type = split[0]

            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
            }

            // TODO handle non-primary volumes
        } else if (uri.isDownloadsDocument()) {

            val id = DocumentsContract.getDocumentId(uri)
            if (!TextUtils.isEmpty(id)) {
                return try {
                    val contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), id.toLong())
                    contentUri.getDataColumn(context, null, null)
                } catch (e: NumberFormatException) {
                    Log.i(TAG, e.message)
                    null
                }

            }

        } else if (uri.isMediaDocument()) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val type = split[0]

            var contentUri: Uri? = null
            when (type) {
                "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val selection = "_id=?"
            val selectionArgs = arrayOf(split[1])

            return contentUri?.getDataColumn(context, selection, selectionArgs)
        }// MediaProvider
        // DownloadsProvider
    } else if ("content".equals(uri.scheme, ignoreCase = true)) {

        // Return the remote address
        return if (uri.isGooglePhotosUri()) {
            uri.lastPathSegment
        } else uri.getDataColumn(context, null, null)

    } else if ("file".equals(uri.scheme, ignoreCase = true)) {
        return uri.path
    }// File
    // MediaStore (and general)

    return null
}

/**
 * Copies one file into the other with the given paths.
 * In the event that the paths are the same, trying to copy one file to the other
 * will cause both files to become null.
 * Simply skipping this step if the paths are identical.
 */
@Throws(IOException::class)
fun copyFile(pathFrom: String, pathTo: String) {
    if (pathFrom.equals(pathTo, ignoreCase = true)) {
        return
    }

    var outputChannel: FileChannel? = null
    var inputChannel: FileChannel? = null
    try {
        inputChannel = FileInputStream(File(pathFrom)).channel
        outputChannel = FileOutputStream(File(pathTo)).channel
        inputChannel?.transferTo(0, inputChannel.size(), outputChannel)
        inputChannel.close()
    } finally {
        inputChannel?.close()
        outputChannel?.close()
    }
}