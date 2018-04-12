package com.yalantis.ucrop.sample

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.FileProvider
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.yalantis.ucrop.sample.utils.NotificationUtils
import kotlinx.android.synthetic.main.activity_sample.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

class ResultActivity : BaseActivity() {

    companion object {
        private const val TAG = "ResultActivity"

        fun startWithUri(context: Context, uri: Uri) {
            val intent = Intent(context, ResultActivity::class.java)
            intent.data = uri
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        val uri = intent.data
        uri?.let {
            try {
                uCropView.getCropImageView()?.setImageUri(it, null)
                uCropView.setShowCropFrame(false)
                uCropView.setShowCropGrid(false)
                uCropView.setDimmedColor(Color.TRANSPARENT)
            } catch (e: Exception) {
                Log.e(TAG, "setImageUri", e)
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(File(uri?.path).absolutePath, options)

        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.title = getString(R.string.format_crop_result_d_d, options.outWidth, options.outHeight)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_result, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_download) {
            saveCroppedImage()
        } else if (item.itemId == android.R.id.home) {
            onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            BaseActivity.REQUEST_STORAGE_WRITE_ACCESS_PERMISSION -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveCroppedImage()
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun saveCroppedImage() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    getString(R.string.permission_write_storage_rationale),
                    BaseActivity.REQUEST_STORAGE_WRITE_ACCESS_PERMISSION)
        } else {
            val imageUri = intent.data
            if (imageUri != null && imageUri.scheme == "file") {
                try {
                    copyFileToDownloads(imageUri)
                } catch (e: Exception) {
                    Toast.makeText(this@ResultActivity, e.message, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, imageUri.toString(), e)
                }

            } else {
                Toast.makeText(this@ResultActivity, getString(R.string.toast_unexpected_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Throws(Exception::class)
    private fun copyFileToDownloads(croppedFileUri: Uri) {
        val downloadsDirectoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val filename = String.format("%d_%s", Calendar.getInstance().timeInMillis, croppedFileUri.lastPathSegment)

        val saveFile = File(downloadsDirectoryPath, filename)

        val inStream = FileInputStream(File(croppedFileUri.path))
        val outStream = FileOutputStream(saveFile)
        val inChannel = inStream.channel
        val outChannel = outStream.channel
        inChannel.transferTo(0, inChannel.size(), outChannel)
        inStream.close()
        outStream.close()

        showNotification(saveFile)
        Toast.makeText(this, R.string.notification_image_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showNotification(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fileUri = FileProvider.getUriForFile(
                this,
                getString(R.string.file_provider_authorities),
                file)

        intent.setDataAndType(fileUri, "image/*")

        val resInfoList = packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY)
        for (info in resInfoList) {
            grantUriPermission(
                    info.activityInfo.packageName,
                    fileUri, FLAG_GRANT_WRITE_URI_PERMISSION or FLAG_GRANT_READ_URI_PERMISSION)
        }

        val notificationUtils = NotificationUtils(this)
        notificationUtils.sendNotification(getString(R.string.app_name), getString(R.string.notification_image_saved_click_to_preview), intent)
    }

}