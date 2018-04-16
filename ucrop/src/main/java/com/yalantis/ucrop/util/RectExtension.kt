package com.yalantis.ucrop.util

import android.graphics.RectF

/**
 * Gets a float array of the 2D coordinates representing a rectangles
 * corners.
 * The order of the corners in the float array is:
 * 0------->1
 * ^        |
 * |        |
 * |        v
 * 3<-------2
 *
 * @return the float array of corners (8 floats)
 */
fun RectF.getCornersFromRect() = floatArrayOf(left, top,
        right, top,
        right, bottom,
        left, bottom)


/**
 * Takes an array of 2D coordinates representing corners and returns the
 * smallest rectangle containing those coordinates.
 *
 * @return smallest rectangle containing coordinates
 */
fun FloatArray.trapToRect(): RectF {
    val r = RectF(java.lang.Float.POSITIVE_INFINITY, java.lang.Float.POSITIVE_INFINITY,
            java.lang.Float.NEGATIVE_INFINITY, java.lang.Float.NEGATIVE_INFINITY)
    var i = 1
    while (i < size) {
        val x = Math.round(get(i - 1) * 10) / 10f
        val y = Math.round(get(i) * 10) / 10f
        r.left = if (x < r.left) x else r.left
        r.top = if (y < r.top) y else r.top
        r.right = if (x > r.right) x else r.right
        r.bottom = if (y > r.bottom) y else r.bottom
        i += 2
    }
    r.sort()
    return r
}


/**
 * Gets a float array of two lengths representing a rectangles width and height
 * The order of the corners in the input float array is:
 * 0------->1
 * ^        |
 * |        |
 * |        v
 * 3<-------2
 *
 * @return the float array of width and height (2 floats)
 */
fun FloatArray.getRectSidesFromCorners(): FloatArray =
        floatArrayOf(Math.sqrt(Math.pow((get(0) - get(2)).toDouble(), 2.0)
                + Math.pow((get(1) - get(3)).toDouble(), 2.0)).toFloat(),
                Math.sqrt(Math.pow((get(2) - get(4).toDouble()), 2.0)
                        + Math.pow((get(3) -get(5).toDouble()), 2.0)).toFloat())

fun RectF.getCenterFromRect(): FloatArray = floatArrayOf(centerX(), centerY())
