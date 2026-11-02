package com.example.lutcam

import java.io.InputStream

/**
 * A 3D LUT read from a .cube file.
 * data holds size*size*size colours, 3 floats each. Red changes fastest, then green, then blue.
 */
class CubeLut(val size: Int, private val data: FloatArray) {

    /** Looks up one colour (values 0..1) and writes the result into out[0..2]. */
    fun apply(r: Float, g: Float, b: Float, out: FloatArray) {
        val s = size - 1
        val fr = r.coerceIn(0f, 1f) * s
        val fg = g.coerceIn(0f, 1f) * s
        val fb = b.coerceIn(0f, 1f) * s

        val r0 = minOf(fr.toInt(), s - 1)
        val g0 = minOf(fg.toInt(), s - 1)
        val b0 = minOf(fb.toInt(), s - 1)
        val dr = fr - r0
        val dg = fg - g0
        val db = fb - b0

        val i000 = ((b0 * size + g0) * size + r0) * 3
        val i100 = i000 + 3
        val i010 = i000 + size * 3
        val i110 = i010 + 3
        val i001 = i000 + size * size * 3
        val i101 = i001 + 3
        val i011 = i001 + size * 3
        val i111 = i011 + 3

        for (c in 0..2) {
            val c00 = data[i000 + c] + (data[i100 + c] - data[i000 + c]) * dr
            val c10 = data[i010 + c] + (data[i110 + c] - data[i010 + c]) * dr
            val c01 = data[i001 + c] + (data[i101 + c] - data[i001 + c]) * dr
            val c11 = data[i011 + c] + (data[i111 + c] - data[i011 + c]) * dr
            val c0 = c00 + (c10 - c00) * dg
            val c1 = c01 + (c11 - c01) * dg
            out[c] = c0 + (c1 - c0) * db
        }
    }

    companion object {
        fun parse(input: InputStream): CubeLut {
            var size = 0
            var values = FloatArray(0)
            var count = 0

            input.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue

                    val first = line[0]
                    if (first.isLetter()) {
                        val parts = line.split(Regex("\\s+"))
                        when (parts[0].uppercase()) {
                            "LUT_3D_SIZE" -> {
                                size = parts[1].toInt()
                                if (size < 2 || size > 128) throw IllegalArgumentException("Unsupported LUT size $size")
                                values = FloatArray(size * size * size * 3)
                            }
                            "LUT_1D_SIZE" -> throw IllegalArgumentException("1D LUTs are not supported, use a 3D .cube file")
                            else -> { /* TITLE, DOMAIN_MIN, DOMAIN_MAX are ignored */ }
                        }
                    } else {
                        if (size == 0) throw IllegalArgumentException("LUT_3D_SIZE line is missing")
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size < 3) continue
                        if (count + 3 > values.size) throw IllegalArgumentException("Too many values in LUT")
                        values[count++] = parts[0].toFloat()
                        values[count++] = parts[1].toFloat()
                        values[count++] = parts[2].toFloat()
                    }
                }
            }

            if (size == 0 || count != values.size) {
                throw IllegalArgumentException("LUT data is incomplete")
            }
            return CubeLut(size, values)
        }
    }
}
