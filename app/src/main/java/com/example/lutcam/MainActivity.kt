package com.example.lutcam

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var chipRow: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var store: StyleStore

    private var imageCapture: ImageCapture? = null
    private var styles: List<Style> = emptyList()
    private var selected: Style? = null
    private var busy = false
    private val worker = Executors.newSingleThreadExecutor()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else toast("Camera permission is needed to take photos")
        }

    private val lutPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importLut(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        chipRow = findViewById(R.id.chipRow)
        statusText = findViewById(R.id.statusText)
        store = StyleStore(this)

        styles = store.all()
        selected = styles.first()
        refreshChips()

        findViewById<View>(R.id.importButton).setOnClickListener {
            lutPicker.launch(arrayOf("*/*"))
        }
        findViewById<View>(R.id.shutter).setOnClickListener { takePhoto() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                imageCapture = capture
            } catch (e: Exception) {
                toast("Could not start the camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val style = selected ?: return
        if (busy) return
        busy = true
        setStatus("CAPTURING")

        val temp = File(cacheDir, "capture.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(temp).build()

        capture.takePicture(options, worker, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    runOnUiThread { setStatus("PROCESSING") }
                    val lut = store.loadLut(style)
                    PhotoProcessor.processAndSave(this@MainActivity, temp, style, lut)
                    runOnUiThread { toast("Saved to Pictures/LutCam") }
                } catch (e: Throwable) {
                    runOnUiThread { toast("Could not save the photo: ${e.message}") }
                } finally {
                    temp.delete()
                    runOnUiThread {
                        busy = false
                        setStatus(style.name)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    busy = false
                    setStatus(style.name)
                    toast("Capture failed: ${exception.message}")
                }
            }
        })
    }

    private fun importLut(uri: Uri) {
        worker.execute {
            try {
                val displayName = queryName(uri) ?: "My LUT.cube"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not open the file")
                CubeLut.parse(bytes.inputStream()) // checks that the file is a usable LUT

                val id = UUID.randomUUID().toString().take(8)
                val fileName = "$id.cube"
                File(store.lutDir(), fileName).writeBytes(bytes)
                store.addStyle(Style(id, displayName.substringBeforeLast('.'), fileName))

                runOnUiThread {
                    styles = store.all()
                    selected = styles.firstOrNull { it.id == id } ?: styles.first()
                    refreshChips()
                    toast("LUT added")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Could not use that LUT: ${e.message}") }
            }
        }
    }

    private fun queryName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }
    }

    private fun refreshChips() {
        chipRow.removeAllViews()
        for (style in styles) {
            val active = style.id == selected?.id
            val chip = TextView(this).apply {
                text = style.name.uppercase()
                typeface = Typeface.MONOSPACE
                textSize = 12f
                letterSpacing = 0.1f
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setTextColor(if (active) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(100).toFloat()
                    setStroke(dp(1), Color.WHITE)
                    setColor(if (active) Color.WHITE else Color.TRANSPARENT)
                }
                setOnClickListener {
                    selected = style
                    refreshChips()
                }
                if (!style.builtIn) {
                    setOnLongClickListener {
                        confirmDelete(style)
                        true
                    }
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(4), dp(8), dp(4), dp(8))
            }
            chipRow.addView(chip, params)
        }
        setStatus(selected?.name ?: "")
    }

    private fun confirmDelete(style: Style) {
        AlertDialog.Builder(this)
            .setMessage("Delete the style \"${style.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                store.removeStyle(style)
                styles = store.all()
                selected = styles.first()
                refreshChips()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setStatus(text: String) {
        statusText.text = text.uppercase()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
    }
}
