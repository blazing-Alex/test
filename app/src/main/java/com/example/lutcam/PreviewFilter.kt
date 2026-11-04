package com.example.lutcam

import android.graphics.ColorMatrix
import kotlin.math.abs

/**
 * Fallback live preview for phones older than Android 13: the closest colour matrix to the LUT
 * (least squares fit). It catches the overall tint, contrast and saturation, not the fine curves.
 * The saved photo always uses the real LUT.
 */
object PreviewFilter {

    private const val GRID = 9

    fun matrixFor(lut: CubeLut, intensity: Float): ColorMatrix {
        val ata = Array(4) { DoubleArray(4) }
        val aty = Array(4) { DoubleArray(3) }
        val x = DoubleArray(4)
        val out = FloatArray(3)
        val last = (GRID - 1).toFloat()

        for (ri in 0 until GRID) {
            for (gi in 0 until GRID) {
                for (bi in 0 until GRID) {
                    val r = ri / last
                    val g = gi / last
                    val b = bi / last
                    lut.apply(r, g, b, out)
                    x[0] = r.toDouble()
                    x[1] = g.toDouble()
                    x[2] = b.toDouble()
                    x[3] = 1.0
                    for (i in 0..3) {
                        for (j in 0..3) ata[i][j] += x[i] * x[j]
                        for (c in 0..2) aty[i][c] += x[i] * out[c]
                    }
                }
            }
        }

        // Solve ata * w = aty with Gauss-Jordan elimination.
        val aug = Array(4) { i ->
            DoubleArray(7) { j -> if (j < 4) ata[i][j] else aty[i][j - 4] }
        }
        for (col in 0..3) {
            var pivot = col
            for (row in col + 1..3) {
                if (abs(aug[row][col]) > abs(aug[pivot][col])) pivot = row
            }
            val tmp = aug[col]
            aug[col] = aug[pivot]
            aug[pivot] = tmp

            val div = aug[col][col]
            if (abs(div) < 1e-9) return ColorMatrix()
            for (j in 0..6) aug[col][j] = aug[col][j] / div
            for (row in 0..3) {
                if (row != col) {
                    val f = aug[row][col]
                    for (j in 0..6) aug[row][j] = aug[row][j] - f * aug[col][j]
                }
            }
        }

        val t = intensity.toDouble()
        val rows = FloatArray(20)
        for (c in 0..2) {
            for (i in 0..2) {
                val identity = if (i == c) 1.0 else 0.0
                rows[c * 5 + i] = ((1.0 - t) * identity + t * aug[i][4 + c]).toFloat()
            }
            rows[c * 5 + 3] = 0f
            rows[c * 5 + 4] = (t * aug[3][4 + c] * 255.0).toFloat()
        }
        rows[18] = 1f
        return ColorMatrix(rows)
    }
}
