/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yalantis.ucrop.util

import android.graphics.*
import android.graphics.drawable.Drawable

class FastBitmapDrawable(b: Bitmap) : Drawable() {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    var bitmap: Bitmap? = null
        set(b) {
            field = b
            b?.let {
                width = it.width
                height = it.height
            } ?: kotlin.run {
                width = 0
                height = width
            }
        }
    private var alpha: Int = 0
    private var width: Int = 0
    private var height: Int = 0

    init {
        alpha = 255
        bitmap = b
    }

    override fun draw(canvas: Canvas) {
        bitmap?.let {
            if (!it.isRecycled) {
                canvas.drawBitmap(it, null, bounds, paint)
            }
        }
    }

    override fun setColorFilter(cf: ColorFilter?) {
        paint.colorFilter = cf
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setFilterBitmap(filterBitmap: Boolean) {
        paint.isFilterBitmap = filterBitmap
    }

    override fun getAlpha(): Int {
        return alpha
    }

    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        paint.alpha = alpha
    }

    override fun getIntrinsicWidth(): Int {
        return width
    }

    override fun getIntrinsicHeight(): Int {
        return height
    }

    override fun getMinimumWidth(): Int {
        return width
    }

    override fun getMinimumHeight(): Int {
        return height
    }

}
