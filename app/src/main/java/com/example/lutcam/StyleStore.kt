package com.example.lutcam

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One look: a built-in recipe, an imported .cube file, or nothing (Original). */
data class Style(
    val id: String,
    val name: String,
    val lutFile: String? = null,
    val look: String? = null,
    val intensity: Float = 1f,
    val builtIn: Boolean = false
) {
    val isIdentity: Boolean
        get() = lutFile == null && look == null
}

class StyleStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("styles", Context.MODE_PRIVATE)
    private val lutCache = HashMap<String, CubeLut>()

    fun lutDir(): File = File(context.filesDir, "luts").also { it.mkdirs() }

    private fun builtIns(): List<Style> {
        val list = ArrayList<Style>()
        list.add(Style("original", "Original", builtIn = true))
        for (look in Looks.all) {
            list.add(Style(look.id, look.name, look = look.id, builtIn = true))
        }
        return list
    }

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

    /** Loads the LUT of a style, or null for Original. Call this from the background thread. */
    fun loadLut(style: Style): CubeLut? {
        val key = style.look ?: style.lutFile ?: return null
        lutCache[key]?.let { return it }
        val lut = if (style.look != null) {
            val look = Looks.find(style.look) ?: return null
            Looks.buildLut(look.recipe)
        } else {
            File(lutDir(), style.lutFile!!).inputStream().use { CubeLut.parse(it) }
        }
        lutCache[key] = lut
        return lut
    }
}
