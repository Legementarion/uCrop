package com.yalantis.ucrop.model

import android.graphics.RectF

data class ImageState(val cropRect: RectF,
                      val currentImageRect: RectF,
                      val currentScale: Float,
                      val currentAngle: Float)
