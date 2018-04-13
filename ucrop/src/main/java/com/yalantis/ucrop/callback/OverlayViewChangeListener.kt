package com.yalantis.ucrop.callback

import android.graphics.RectF

interface OverlayViewChangeListener {

    fun onCropRectUpdated(cropRect: RectF)

}