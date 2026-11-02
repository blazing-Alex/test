package com.example.lutcam

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoProcessor {

    private const val MAX_SIDE = 4096

    /** Applies the style to the captured file and saves the result to Pictures/LutCam. */
    fun processAndSave(context: Context, file: File, style: Style, lut: CubeLut?): Uri {
        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "LUTCAM_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LutCam")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create the photo file")

        try {
            val out = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open the photo file")
            out.use { stream ->
                if (style.isIdentity && lut == null) {
                    // "Original": keep the camera file as it is, full resolution.
                    file.inputStream().use { it.copyTo(stream) }
                } else {
                    val bitmap = render(file, style, lut)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                    bitmap.recycle()
                }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun render(file: File, style: Style, lut: CubeLut?): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
        }
        val source = BitmapFactory.decodeFile(file.path, options)
            ?: throw IllegalStateException("Could not read the captured photo")

        applyLook(source, style, lut)

        val orientation = ExifInterface(file.path)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return source

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        source.recycle()
        return rotated
    }

    /** Changes the pixels of the bitmap in place, a strip of rows at a time to save memory. */
    private fun applyLook(bitmap: Bitmap, style: Style, lut: CubeLut?) {
        val width = bitmap.width
        val height = bitmap.height
        val stripRows = 128
        val pixels = IntArray(width * stripRows)
        val lutOut = FloatArray(3)
        val alpha = 255 shl 24

        var y = 0
        while (y < height) {
            val rows = minOf(stripRows, height - y)
            bitmap.getPixels(pixels, 0, width, 0, y, width, rows)

            for (i in 0 until width * rows) {
                val p = pixels[i]
                var r = ((p shr 16) and 255) / 255f
                var g = ((p shr 8) and 255) / 255f
                var b = (p and 255) / 255f

                if (style.contrast != 1f) {
                    r = (r - 0.5f) * style.contrast + 0.5f
                    g = (g - 0.5f) * style.contrast + 0.5f
                    b = (b - 0.5f) * style.contrast + 0.5f
                }
                if (style.saturation != 1f) {
                    val luma = 0.299f * r + 0.587f * g + 0.114f * b
                    r = luma + (r - luma) * style.saturation
                    g = luma + (g - luma) * style.saturation
                    b = luma + (b - luma) * style.saturation
                }
                if (style.warmth != 0f) {
                    r += style.warmth * 0.05f
                    b -= style.warmth * 0.05f
                }

                if (lut != null) {
                    lut.apply(r, g, b, lutOut)
                    r += (lutOut[0] - r) * style.intensity
                    g += (lutOut[1] - g) * style.intensity
                    b += (lutOut[2] - b) * style.intensity
                }

                pixels[i] = alpha or (to255(r) shl 16) or (to255(g) shl 8) or to255(b)
            }

            bitmap.setPixels(pixels, 0, width, 0, y, width, rows)
            y += rows
        }
    }

    private fun to255(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}
