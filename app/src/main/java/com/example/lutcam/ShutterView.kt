package com.example.lutcam

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The shutter button: a ring of dots around a white disc.
 * While busy, a bright trail of dots runs around the ring.
 */
class ShutterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dotCount = 40

    private var pressScale = 1f
    private var progress = 0f
    private var sweep: ValueAnimator? = null

    var busy: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) startSweep() else stopSweep()
            invalidate()
        }

    private fun startSweep() {
        sweep?.cancel()
        sweep = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopSweep() {
        sweep?.cancel()
        sweep = null
    }

    override fun onDetachedFromWindow() {
        stopSweep()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animatePress(0.86f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animatePress(1f)
        }
        return super.onTouchEvent(event)
    }

    private fun animatePress(target: Float) {
        ValueAnimator.ofFloat(pressScale, target).apply {
            duration = 90
            addUpdateListener {
                pressScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val half = minOf(width, height) / 2f
        val dotRadius = 2.2f * density
        val ringRadius = half - dotRadius - 1f * density
        val twoPi = 2f * PI.toFloat()
        val quarter = PI.toFloat() / 2f

        for (i in 0 until dotCount) {
            val angle = (i.toFloat() / dotCount) * twoPi - quarter
            var alpha = 0.85f
            if (busy) {
                var d = progress * dotCount - i
                if (d < 0f) d += dotCount
                alpha = (1f - d / (dotCount * 0.6f)).coerceIn(0.12f, 1f)
            }
            dotPaint.alpha = (alpha * 255f).toInt()
            canvas.drawCircle(
                cx + ringRadius * cos(angle),
                cy + ringRadius * sin(angle),
                dotRadius,
                dotPaint
            )
        }

        discPaint.alpha = if (busy) 110 else 255
        canvas.drawCircle(cx, cy, ringRadius * 0.74f * pressScale, discPaint)
    }
}
