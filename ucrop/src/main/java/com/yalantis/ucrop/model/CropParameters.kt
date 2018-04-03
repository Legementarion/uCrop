package com.yalantis.ucrop.model

import android.graphics.Bitmap

data class CropParameters(val maxResultImageSizeX: Int,
                          val maxResultImageSizeY: Int,
                          val compressFormat: Bitmap.CompressFormat,
                          val compressQuality: Int,
                          val imageInputPath: String,
                          val imageOutputPath: String,
                          val exifInfo: ExifInfo)