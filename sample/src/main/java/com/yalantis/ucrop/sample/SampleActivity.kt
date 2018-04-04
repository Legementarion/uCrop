package com.yalantis.ucrop.sample

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Toast
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.view.UCropView.Companion.DEFAULT_COMPRESS_QUALITY
import kotlinx.android.synthetic.main.activity_sample.*
import kotlinx.android.synthetic.main.include_settings.*
import java.io.File

class SampleActivity : BaseActivity() {

    companion object {
        private const val TAG = "SampleActivity"
        private const val REQUEST_SELECT_PICTURE = 0x01
        private const val SAMPLE_CROPPED_IMAGE_NAME = "SampleCropImage"
    }

    private val radioArray = mutableListOf<RadioButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)
        setupUI()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_SELECT_PICTURE) {
                val selectedUri = data.data
                if (selectedUri != null) {
                    startCrop(selectedUri)
                } else {
                    Toast.makeText(this@SampleActivity, R.string.toast_cannot_retrieve_selected_image, Toast.LENGTH_SHORT).show()
                }
            } else if (requestCode == UCrop.REQUEST_CROP) {
                handleCropResult(data)
            }
        }
        if (resultCode == UCrop.RESULT_ERROR) {
            handleCropError(data)
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            BaseActivity.REQUEST_STORAGE_READ_ACCESS_PERMISSION -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickFromGallery()
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun setupUI() {
        btnCrop.setOnClickListener {
            pickFromGallery()
        }

        radioGroupCompressionSettings.setOnCheckedChangeListener { _, checkedId ->
            seekbarQuality.isEnabled = checkedId == R.id.radio_jpeg
        }
        seekbarQuality.progress = DEFAULT_COMPRESS_QUALITY
        textViewQuality.text = String.format(getString(R.string.format_quality_d), seekbarQuality.progress)
        seekbarQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                textViewQuality.text = String.format(getString(R.string.format_quality_d), progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {} //do nothing

            override fun onStopTrackingTouch(seekBar: SeekBar) {} //do nothing
        })

        checkboxMaxSize.setOnCheckedChangeListener { _, checked ->
            editTextMaxWidth.isEnabled = checked
            editTextMaxHeight.isEnabled = checked
        }
        editTextMaxWidth.isEnabled = false
        editTextMaxHeight.isEnabled = false

        radioDynamic.setOnClickListener(radioBtnClick)
        radioArray.add(radioDynamic)
        radioOrigin.setOnClickListener(radioBtnClick)
        radioArray.add(radioOrigin)
        radioSquare.setOnClickListener(radioBtnClick)
        radioArray.add(radioSquare)
        radio16x9.setOnClickListener(radioBtnClick)
        radioArray.add(radio16x9)
    }

    private val radioBtnClick = View.OnClickListener {
        radioArray.forEach { it.isChecked = false }
        (it as RadioButton).isChecked = true
    }

    private fun pickFromGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE,
                    getString(R.string.permission_read_storage_rationale),
                    BaseActivity.REQUEST_STORAGE_READ_ACCESS_PERMISSION)
        } else {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
                    .setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                val mimeTypes = arrayOf("image/jpeg", "image/png")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            }

            startActivityForResult(Intent.createChooser(intent, getString(R.string.label_select_picture)), REQUEST_SELECT_PICTURE)
        }
    }


    private fun startCrop(uri: Uri) {
        val destinationFileName = SAMPLE_CROPPED_IMAGE_NAME
        basisConfig()
        uCropView.visibility = View.VISIBLE
        settings.visibility = View.GONE
        uCropView.setImage(uri, Uri.fromFile(File(cacheDir, destinationFileName)))
        uCropView.animate().alpha(1f).setDuration(300).interpolator = AccelerateInterpolator()
    }

    /**
     * In most cases you need only to set crop aspect ration and max size for resulting image.
     *
     * @param uCrop - ucrop builder instance
     * @return - ucrop builder instance
     */
    private fun basisConfig() {

        radioArray.forEach { if (it.isChecked) {
            when (it.id) {
                R.id.radioOrigin -> uCropView.useSourceImageAspectRatio()
                R.id.radioSquare -> uCropView.withAspectRatio(1f, 1f)
                R.id.radioDynamic -> {
                }
                R.id.radio16x9 -> {
                    uCropView.withAspectRatio(16f, 9f)
                }
                else -> {
                }
            }
        } }

        if (checkboxMaxSize.isChecked) {
            try {
                val maxWidth = Integer.valueOf(editTextMaxWidth.text.toString().trim({ it <= ' ' }))!!
                val maxHeight = Integer.valueOf(editTextMaxHeight.text.toString().trim({ it <= ' ' }))!!
                if (maxWidth > UCrop.MIN_SIZE && maxHeight > UCrop.MIN_SIZE) {
                    uCropView.withMaxResultSize(maxWidth, maxHeight)
                }
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Number please", e)
            }

        }

        when (radioGroupCompressionSettings.checkedRadioButtonId) {
            R.id.radio_png -> uCropView.setCompressionFormat(Bitmap.CompressFormat.PNG)
            R.id.radio_jpeg -> {
                uCropView.setCompressionFormat(Bitmap.CompressFormat.JPEG)
                uCropView.setCompressionQuality(seekbarQuality.progress)
            }
            R.id.radio_webp -> uCropView.setCompressionFormat(Bitmap.CompressFormat.WEBP)
        }

    }

    private fun handleCropResult(result: Intent) {
        val resultUri = UCrop.getOutput(result)
        if (resultUri != null) {
            ResultActivity.startWithUri(this@SampleActivity, resultUri)
        } else {
            Toast.makeText(this@SampleActivity, R.string.toast_cannot_retrieve_cropped_image, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCropError(result: Intent) {
        val cropError = UCrop.getError(result)
        if (cropError != null) {
            Log.e(TAG, "handleCropError: ", cropError)
            Toast.makeText(this@SampleActivity, cropError.message, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this@SampleActivity, R.string.toast_unexpected_error, Toast.LENGTH_SHORT).show()
        }
    }

}