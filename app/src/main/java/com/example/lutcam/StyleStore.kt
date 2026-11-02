package com.example.lutcam

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One style template: an optional LUT plus a few simple adjustments. */
data class Style(
    val id: String,
    val name: String,
    val lutFile: String? = null,
    val intensity: Float = 1f,
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    val warmth: Float = 0f,
    val builtIn: Boolean = false
) {
    val isIdentity: Boolean
        get() = lutFile == null && saturation == 1f && contrast == 1f && warmth == 0f
}

class StyleStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("styles", Context.MODE_PRIVATE)
    private val lutCache = HashMap<String, CubeLut>()

    fun lutDir(): File = File(context.filesDir, "luts").also { it.mkdirs() }

    private fun builtIns(): List<Style> = listOf(
        Style("original", "Original", builtIn = true),
        Style("mono", "Mono", saturation = 0f, contrast = 1.15f, builtIn = true),
        Style("warm", "Warm", saturation = 1.1f, warmth = 1f, builtIn = true),
        Style("cool", "Cool", contrast = 1.05f, warmth = -1f, builtIn = true),
        Style("punch", "Punch", saturation = 1.35f, contrast = 1.2f, builtIn = true)
    )

    private fun custom(): List<Style> {
        val json = prefs.getString("custom", "[]") ?: "[]"
        val array = JSONArray(json)
        val result = ArrayList<Style>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            result.add(
                Style(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    lutFile = o.optString("lut", "").ifEmpty { null },
                    intensity = o.optDouble("intensity", 1.0).toFloat()
                )
            )
        }
        return result
    }

    private fun saveCustom(list: List<Style>) {
        val array = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("lut", s.lutFile ?: "")
            o.put("intensity", s.intensity.toDouble())
            array.put(o)
        }
        prefs.edit().putString("custom", array.toString()).apply()
    }

    fun all(): List<Style> = builtIns() + custom()

    fun addStyle(style: Style) {
        saveCustom(custom() + style)
    }

    fun removeStyle(style: Style) {
        saveCustom(custom().filter { it.id != style.id })
        style.lutFile?.let {
            File(lutDir(), it).delete()
            lutCache.remove(it)
        }
    }

    /** Loads the LUT of a style, or null if the style has none. */
    fun loadLut(style: Style): CubeLut? {
        val name = style.lutFile ?: return null
        lutCache[name]?.let { return it }
        val lut = File(lutDir(), name).inputStream().use { CubeLut.parse(it) }
        lutCache[name] = lut
        return lut
    }
}
