package com.example.lutcam

/**
 * A colour look described by a few simple controls.
 * It is turned into a real 3D LUT (the same thing as a .cube file) by Looks.buildLut.
 */
class Recipe(
    val gain: FloatArray = floatArrayOf(1f, 1f, 1f),     // per-channel multiplier (warmth / tint)
    val exposure: Float = 0f,                             // brighter (+) or darker (-), in stops
    val mix: FloatArray? = null,                          // 3x3 channel mixer, row by row
    val mono: FloatArray? = null,                         // black and white, with these weights
    val contrast: Float = 0f,                             // S-curve strength
    val saturation: Float = 1f,
    val shadowTint: FloatArray = floatArrayOf(0f, 0f, 0f),
    val highlightTint: FloatArray = floatArrayOf(0f, 0f, 0f),
    val fade: Float = 0f                                  // lifts the blacks
)

object Looks {

    class Look(val id: String, val name: String, val recipe: Recipe)

    private fun c(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

    val all: List<Look> = listOf(
        // The eight looks from the example cards.
        Look("cinematic1", "Cinematic V1", Recipe(
            gain = c(0.88f, 1.0f, 1.08f), exposure = -0.15f, contrast = 0.30f, saturation = 0.78f,
            shadowTint = c(-0.03f, 0.03f, 0.05f), highlightTint = c(0.02f, 0.0f, -0.02f))),
        Look("madmax", "Mad Max", Recipe(
            gain = c(1.10f, 1.0f, 0.82f), contrast = 0.38f, saturation = 1.45f,
            shadowTint = c(-0.03f, 0.02f, 0.06f), highlightTint = c(0.06f, 0.03f, -0.04f))),
        Look("roses", "Roses Are Blue", Recipe(
            mix = floatArrayOf(0.35f, 0.0f, 0.65f, 0.0f, 0.55f, 0.0f, 0.85f, 0.15f, 0.0f),
            contrast = 0.30f, saturation = 1.5f)),
        Look("macro", "Macro World", Recipe(
            mono = floatArrayOf(0.30f, 0.59f, 0.11f), exposure = 0.05f, contrast = 0.45f,
            highlightTint = c(-0.005f, 0.0f, 0.012f))),
        Look("retro", "Retro", Recipe(
            gain = c(1.05f, 1.0f, 0.90f), contrast = 0.10f, saturation = 0.60f, fade = 0.12f,
            shadowTint = c(0.03f, 0.02f, 0.0f))),
        Look("fightclub", "Fight Club", Recipe(
            gain = c(0.95f, 1.05f, 0.80f), contrast = 0.35f, saturation = 0.70f,
            shadowTint = c(0.0f, 0.03f, 0.0f), highlightTint = c(0.04f, 0.05f, -0.03f))),
        Look("cinematic2", "Cinematic V2", Recipe(
            gain = c(0.80f, 1.05f, 0.95f), exposure = -0.20f, contrast = 0.30f, saturation = 0.90f,
            shadowTint = c(-0.04f, 0.05f, 0.03f), highlightTint = c(-0.02f, 0.03f, 0.02f))),
        Look("darkmoody", "Dark And Moody", Recipe(
            gain = c(0.92f, 0.98f, 1.08f), exposure = -0.35f, contrast = 0.35f, saturation = 0.65f,
            fade = 0.03f, shadowTint = c(-0.01f, 0.0f, 0.03f))),

        // Classic film and grading looks.
        Look("portra", "Portra 400", Recipe(
            gain = c(1.05f, 1.0f, 0.94f), contrast = 0.12f, saturation = 0.90f, fade = 0.04f,
            highlightTint = c(0.02f, 0.01f, -0.01f))),
        Look("gold", "Kodak Gold", Recipe(
            gain = c(1.08f, 1.02f, 0.85f), contrast = 0.20f, saturation = 1.25f, fade = 0.02f)),
        Look("velvia", "Velvia", Recipe(
            contrast = 0.30f, saturation = 1.60f, shadowTint = c(0.0f, 0.01f, 0.03f))),
        Look("cinestill", "Cinestill 800T", Recipe(
            gain = c(1.0f, 0.97f, 1.02f), contrast = 0.20f, saturation = 1.05f,
            shadowTint = c(-0.02f, 0.02f, 0.05f), highlightTint = c(0.07f, 0.0f, -0.03f))),
        Look("tealorange", "Teal And Orange", Recipe(
            contrast = 0.25f, saturation = 1.15f,
            shadowTint = c(-0.05f, 0.03f, 0.07f), highlightTint = c(0.07f, 0.02f, -0.06f))),
        Look("bleach", "Bleach Bypass", Recipe(contrast = 0.50f, saturation = 0.45f)),
        Look("trix", "Tri-X 400", Recipe(
            mono = floatArrayOf(0.50f, 0.40f, 0.10f), contrast = 0.40f, fade = 0.02f)),
        Look("golden", "Golden Hour", Recipe(
            gain = c(1.12f, 1.0f, 0.80f), exposure = 0.08f, saturation = 1.20f, fade = 0.02f))
    )

    fun find(id: String): Look? = all.firstOrNull { it.id == id }

    private fun clamp(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    private fun curve(x: Float, k: Float): Float = x + k * (x * x * (3f - 2f * x) - x)

    /** Turns a recipe into a size x size x size LUT. Red changes fastest, then green, then blue. */
    fun buildLut(recipe: Recipe, size: Int = 33): CubeLut {
        val data = FloatArray(size * size * size * 3)
        val last = (size - 1).toFloat()
        val exposureScale = Math.pow(2.0, recipe.exposure.toDouble()).toFloat()
        var i = 0
        for (bi in 0 until size) {
            for (gi in 0 until size) {
                for (ri in 0 until size) {
                    var r = ri / last * recipe.gain[0] * exposureScale
                    var g = gi / last * recipe.gain[1] * exposureScale
                    var b = bi / last * recipe.gain[2] * exposureScale

                    val m = recipe.mix
                    if (m != null) {
                        val nr = m[0] * r + m[1] * g + m[2] * b
                        val ng = m[3] * r + m[4] * g + m[5] * b
                        val nb = m[6] * r + m[7] * g + m[8] * b
                        r = nr; g = ng; b = nb
                    }
                    val w = recipe.mono
                    if (w != null) {
                        val y = w[0] * r + w[1] * g + w[2] * b
                        r = y; g = y; b = y
                    }
                    r = clamp(r); g = clamp(g); b = clamp(b)

                    if (recipe.contrast != 0f) {
                        r = curve(r, recipe.contrast)
                        g = curve(g, recipe.contrast)
                        b = curve(b, recipe.contrast)
                    }
                    if (recipe.saturation != 1f) {
                        val l = 0.299f * r + 0.587f * g + 0.114f * b
                        r = l + (r - l) * recipe.saturation
                        g = l + (g - l) * recipe.saturation
                        b = l + (b - l) * recipe.saturation
                    }

                    val luma = clamp(0.299f * r + 0.587f * g + 0.114f * b)
                    val sw = (1f - luma) * (1f - luma)
                    val hw = luma * luma
                    r += recipe.shadowTint[0] * sw + recipe.highlightTint[0] * hw
                    g += recipe.shadowTint[1] * sw + recipe.highlightTint[1] * hw
                    b += recipe.shadowTint[2] * sw + recipe.highlightTint[2] * hw

                    if (recipe.fade != 0f) {
                        r = recipe.fade + r * (1f - recipe.fade)
                        g = recipe.fade + g * (1f - recipe.fade)
                        b = recipe.fade + b * (1f - recipe.fade)
                    }

                    data[i++] = clamp(r)
                    data[i++] = clamp(g)
                    data[i++] = clamp(b)
                }
            }
        }
        return CubeLut(size, data)
    }
}
