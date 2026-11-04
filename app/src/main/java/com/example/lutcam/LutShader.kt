package com.example.lutcam

import android.graphics.BitmapShader
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

/**
 * The real LUT, applied to the live preview on the graphics chip (Android 13 and newer).
 * The LUT is read from the strip picture made by CubeLut.toStrip().
 */
@RequiresApi(33)
object LutShader {

    private const val AGSL = """
        uniform shader content;
        uniform shader lut;
        uniform float size;
        uniform float intensity;

        half4 main(float2 coord) {
            half4 c = content.eval(coord);
            float3 rgb = clamp(c.rgb, 0.0, 1.0);
            float scale = size - 1.0;
            float b = rgb.b * scale;
            float b0 = floor(b);
            float b1 = min(b0 + 1.0, scale);
            float fb = b - b0;
            float y = rgb.g * scale + 0.5;
            float x = rgb.r * scale + 0.5;
            half3 c0 = lut.eval(float2(b0 * size + x, y)).rgb;
            half3 c1 = lut.eval(float2(b1 * size + x, y)).rgb;
            half3 graded = mix(c0, c1, half(fb));
            return half4(mix(c.rgb, graded, half(intensity)), c.a);
        }
    """

    fun effect(lut: CubeLut, intensity: Float): RenderEffect {
        val shader = RuntimeShader(AGSL)
        val strip = BitmapShader(lut.toStrip(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        strip.filterMode = BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("lut", strip)
        shader.setFloatUniform("size", lut.size.toFloat())
        shader.setFloatUniform("intensity", intensity)
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}
