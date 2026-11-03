package com.example.lutcam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/** A frame that keeps a 3:4 (portrait) shape, as large as the space allows. */
class RatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        val maxHeight = MeasureSpec.getSize(heightMeasureSpec)
        var w = maxWidth
        var h = w * 4 / 3
        if (maxHeight > 0 && h > maxHeight) {
            h = maxHeight
            w = h * 3 / 4
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
    }
}

/** The focus mark shown where the person taps: four white corners and a red dot. */
class ReticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD71921.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = 3f * density
        val l = inset
        val t = inset
        val r = width - inset
        val b = height - inset
        val len = minOf(width, height) * 0.26f

        canvas.drawLine(l, t, l + len, t, line)
        canvas.drawLine(l, t, l, t + len, line)
        canvas.drawLine(r, t, r - len, t, line)
        canvas.drawLine(r, t, r, t + len, line)
        canvas.drawLine(l, b, l + len, b, line)
        canvas.drawLine(l, b, l, b - len, line)
        canvas.drawLine(r, b, r - len, b, line)
        canvas.drawLine(r, b, r, b - len, line)

        canvas.drawCircle(width / 2f, height / 2f, 2.5f * density, dot)
    }

    /** Shows the mark centred on (x, y), then fades it out. */
    fun showAt(x: Float, y: Float) {
        translationX = x - width / 2f
        translationY = y - height / 2f
        animate().cancel()
        alpha = 1f
        scaleX = 1.4f
        scaleY = 1.4f
        animate()
            .setStartDelay(0)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(160)
            .withEndAction {
                animate().setStartDelay(700).alpha(0f).setDuration(250).start()
            }
            .start()
    }
}
