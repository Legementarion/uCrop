package com.yalantis.ucrop.view

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

import com.yalantis.ucrop.util.RotationGestureDetector

class GestureCropImageView @JvmOverloads constructor(context: Context,
                                                     attrs: AttributeSet,
                                                     defStyle: Int = 0) : CropImageView(context, attrs, defStyle) {

    companion object {
        private const val DOUBLE_TAP_ZOOM_DURATION = 200
    }

    private var scaleDetector: ScaleGestureDetector? = null
    private var rotateDetector: RotationGestureDetector? = null
    private var gestureDetector: GestureDetector? = null

    private var midPntX: Float = 0f
    private var midPntY: Float = 0f

    var isRotateEnabled = true
    var isScaleEnabled = true
    var doubleTapScaleSteps = 5

    /**
     * This method calculates target scale value for double tap gesture.
     * User is able to zoom the image from min scale value
     * to the max scale value with [.mDoubleTapScaleSteps] double taps.
     */
    private val doubleTapTargetScale: Float
        get() = currentScale * Math.pow((maxScale / minScale).toDouble(), (1.0f / doubleTapScaleSteps).toDouble()).toFloat()


    /**
     * If it's ACTION_DOWN event - user touches the screen and all current animation must be canceled.
     * If it's ACTION_UP event - user removed all fingers from the screen and current image position must be corrected.
     * If there are more than 2 fingers - update focal point coordinates.
     * Pass the event to the gesture detectors if those are enabled.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_DOWN) {
            cancelAllAnimations()
        }

        if (event.pointerCount > 1) {
            midPntX = (event.getX(0) + event.getX(1)) / 2
            midPntY = (event.getY(0) + event.getY(1)) / 2
        }

        gestureDetector?.onTouchEvent(event)

        if (isScaleEnabled) {
            scaleDetector?.onTouchEvent(event)
        }

        if (isRotateEnabled) {
            rotateDetector?.onTouchEvent(event)
        }

        if (event.action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_UP) {
            setImageToWrapCropBounds()
        }
        return true
    }

    override fun init() {
        super.init()
        setupGestureListeners()
    }

    private fun setupGestureListeners() {
        gestureDetector = GestureDetector(context, GestureListener(), null, true)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        rotateDetector = RotationGestureDetector(RotateListener())
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            postScale(detector.scaleFactor, midPntX, midPntY)
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomImageToPosition(doubleTapTargetScale, e.x, e.y, DOUBLE_TAP_ZOOM_DURATION.toLong())
            return super.onDoubleTap(e)
        }

        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            postTranslate(-distanceX, -distanceY)
            return true
        }

    }

    private inner class RotateListener : RotationGestureDetector.SimpleOnRotationGestureListener() {

        override fun onRotation(rotationDetector: RotationGestureDetector): Boolean {
            postRotate(rotationDetector.angle, midPntX, midPntY)
            return true
        }

    }

}