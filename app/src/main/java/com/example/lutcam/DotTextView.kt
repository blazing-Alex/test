package com.example.lutcam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil

/** A 5x7 dot-matrix font. Each glyph is 7 rows of 5 bits, left to right. */
object DotFont {
    private val raw = mapOf(
        'A' to "01110 10001 10001 11111 10001 10001 10001",
        'B' to "11110 10001 10001 11110 10001 10001 11110",
        'C' to "01110 10001 10000 10000 10000 10001 01110",
        'D' to "11110 10001 10001 10001 10001 10001 11110",
        'E' to "11111 10000 10000 11110 10000 10000 11111",
        'F' to "11111 10000 10000 11110 10000 10000 10000",
        'G' to "01110 10001 10000 10111 10001 10001 01111",
        'H' to "10001 10001 10001 11111 10001 10001 10001",
        'I' to "01110 00100 00100 00100 00100 00100 01110",
        'J' to "00111 00010 00010 00010 00010 10010 01100",
        'K' to "10001 10010 10100 11000 10100 10010 10001",
        'L' to "10000 10000 10000 10000 10000 10000 11111",
        'M' to "10001 11011 10101 10101 10001 10001 10001",
        'N' to "10001 11001 10101 10011 10001 10001 10001",
        'O' to "01110 10001 10001 10001 10001 10001 01110",
        'P' to "11110 10001 10001 11110 10000 10000 10000",
        'Q' to "01110 10001 10001 10001 10101 10010 01101",
        'R' to "11110 10001 10001 11110 10100 10010 10001",
        'S' to "01111 10000 10000 01110 00001 00001 11110",
        'T' to "11111 00100 00100 00100 00100 00100 00100",
        'U' to "10001 10001 10001 10001 10001 10001 01110",
        'V' to "10001 10001 10001 10001 10001 01010 00100",
        'W' to "10001 10001 10001 10101 10101 11011 10001",
        'X' to "10001 10001 01010 00100 01010 10001 10001",
        'Y' to "10001 10001 01010 00100 00100 00100 00100",
        'Z' to "11111 00001 00010 00100 01000 10000 11111",
        '0' to "01110 10001 10011 10101 11001 10001 01110",
        '1' to "00100 01100 00100 00100 00100 00100 01110",
        '2' to "01110 10001 00001 00010 00100 01000 11111",
        '3' to "11110 00001 00001 01110 00001 00001 11110",
        '4' to "00010 00110 01010 10010 11111 00010 00010",
        '5' to "11111 10000 11110 00001 00001 10001 01110",
        '6' to "00110 01000 10000 11110 10001 10001 01110",
        '7' to "11111 00001 00010 00100 01000 01000 01000",
        '8' to "01110 10001 10001 01110 10001 10001 01110",
        '9' to "01110 10001 10001 01111 00001 00010 01100",
        '-' to "00000 00000 00000 11111 00000 00000 00000",
        '+' to "00000 00100 00100 11111 00100 00100 00000",
        '.' to "00000 00000 00000 00000 00000 00000 00100",
        '/' to "00001 00010 00010 00100 01000 01000 10000"
    )

    private val glyphs: Map<Char, IntArray> = raw.mapValues { entry ->
        entry.value.split(" ").map { it.toInt(2) }.toIntArray()
    }

    private val blank = IntArray(7)

    fun glyph(c: Char): IntArray = glyphs[c.uppercaseChar()] ?: blank
}

/** Draws text as a grid of round dots, like an LED panel. */
class DotTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var text: String = ""
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    /** Distance between two dots, in pixels. */
    var pitch: Float = 4f * resources.displayMetrics.density
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var dotColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    /** When true, the dots that are off are drawn very faintly. */
    var showUnlit: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun columns(): Int = if (text.isEmpty()) 0 else text.length * 6 - 1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = ceil(columns() * pitch).toInt()
        val height = ceil(7 * pitch).toInt()
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val radius = pitch * 0.36f
        for ((index, ch) in text.withIndex()) {
            val rows = DotFont.glyph(ch)
            for (row in 0 until 7) {
                for (col in 0 until 5) {
                    val lit = ((rows[row] shr (4 - col)) and 1) == 1
                    if (!lit && !showUnlit) continue
                    paint.color = dotColor
                    if (!lit) paint.alpha = (paint.alpha * 0.1f).toInt()
                    val x = (index * 6 + col + 0.5f) * pitch
                    val y = (row + 0.5f) * pitch
                    canvas.drawCircle(x, y, radius, paint)
                }
            }
        }
    }
}
