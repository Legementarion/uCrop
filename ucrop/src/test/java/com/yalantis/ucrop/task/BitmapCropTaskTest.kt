package com.yalantis.ucrop.task

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.model.CropParameters
import com.yalantis.ucrop.model.ImageState
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class BitmapCropTaskTest {

    @Mock
    private lateinit var context: Context
    @Mock
    private lateinit var callback: BitmapCropCallback
    @Mock
    private lateinit var bitmap: Bitmap
    @Mock
    private lateinit var cropParam: CropParameters

    private lateinit var image: ImageState
    private lateinit var task: BitmapCropTask
    @Before
    @Throws(Exception::class)
    fun setUp() {

        image = ImageState(RectF(), RectF(), 1.88f, 0f)
        task = BitmapCropTask(context, bitmap, image, cropParam, callback)
    }

    @Test
    @Throws(Exception::class)
    fun shouldCrop() {
        assertTrue(task.shouldCrop(1, 1, image.cropRect, image.currentImageRect))
    }

}