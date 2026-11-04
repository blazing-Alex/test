package com.example.lutcam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * A round swatch with four sample colours (skin, sky, leaf, grey).
 * The style's colour filter is applied, so the swatch shows what the style does.
 * With isAdd = true it draws a dashed circle with a plus instead (the "add LUT" tile).
 */
class SwatchView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFF4A4A4D.toInt()
        pathEffect = DashPathEffect(floatArrayOf(5f * density, 5f * density), 0f)
    }
    private val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFFB0B0B5.toInt()
    }
    private val dashPath = Path()
    private val oval = RectF()
    private val colors = intArrayOf(
        0xFFE3B08D.toInt(),
        0xFF5B9BD5.toInt(),
        0xFF6AAE5B.toInt(),
        0xFF8C8C8C.toInt()
    )
    private val starts = floatArrayOf(180f, 270f, 0f, 90f)

    var filter: ColorFilter? = null
        set(value) {
            field = value
            fill.colorFilter = value
            invalidate()
        }

    var active: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isAdd: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = minOf(width, height) / 2f - 1f * density

        if (isAdd) {
            dashPath.reset()
            dashPath.addCircle(cx, cy, outer, Path.Direction.CW)
            canvas.drawPath(dashPath, dashPaint)
            val arm = 7f * density
            canvas.drawLine(cx - arm, cy, cx + arm, cy, plusPaint)
            canvas.drawLine(cx, cy - arm, cx, cy + arm, plusPaint)
            return
        }

        val inner = outer - 5f * density
        ring.color = if (active) Color.WHITE else 0xFF3A3A3A.toInt()
        canvas.drawCircle(cx, cy, outer, ring)

        oval.set(cx - inner, cy - inner, cx + inner, cy + inner)
        for (i in 0..3) {
            fill.color = colors[i]
            canvas.drawArc(oval, starts[i], 90f, true, fill)
        }
    }
}
