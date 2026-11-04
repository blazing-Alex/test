package com.example.lutcam

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private class Tile(
        val root: LinearLayout,
        val swatch: SwatchView,
        val label: TextView,
        val style: Style
    )

    private lateinit var previewView: PreviewView
    private lateinit var viewfinder: View
    private lateinit var chipScroll: HorizontalScrollView
    private lateinit var chipRow: LinearLayout
    private lateinit var styleLabel: DotTextView
    private lateinit var captionText: TextView
    private lateinit var modeText: TextView
    private lateinit var modePill: View
    private lateinit var shutter: ShutterView
    private lateinit var reticle: ReticleView
    private lateinit var flashView: View
    private lateinit var statusDot: View
    private lateinit var store: StyleStore

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null

    // Capture modes this phone offers to our app: label and CameraX extension mode (null = standard).
    private var modes: List<Pair<String, Int?>> = listOf("STD" to null)
    private var modeIndex = 0

    private var styles: List<Style> = emptyList()
    private var selected: Style? = null
    private var busy = false

    // Live preview colour matrices, one per style id.
    private val matrices = ConcurrentHashMap<String, ColorMatrix>()
    private val tiles = ArrayList<Tile>()

    private val worker = Executors.newSingleThreadExecutor()
    private val restoreCaption = Runnable { captionText.text = CAPTION }
    private val mediumFont: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private companion object {
        const val CAPTION = "CAMERA PRESET"
    }

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
        // A TextureView based preview is needed so a colour filter can be put on it.
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        viewfinder = findViewById(R.id.viewfinder)
        chipScroll = findViewById(R.id.chipScroll)
        chipRow = findViewById(R.id.chipRow)
        styleLabel = findViewById(R.id.styleLabel)
        captionText = findViewById(R.id.captionText)
        modeText = findViewById(R.id.modeText)
        modePill = findViewById(R.id.modePill)
        shutter = findViewById(R.id.shutter)
        reticle = findViewById(R.id.reticle)
        flashView = findViewById(R.id.flashView)
        statusDot = findViewById(R.id.statusDot)
        store = StyleStore(this)

        // Rounded viewfinder.
        viewfinder.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpf(28f))
            }
        }
        viewfinder.clipToOutline = true

        // Dot-matrix lettering for the wordmark and the preset name.
        findViewById<DotTextView>(R.id.titleText).apply {
            pitch = dpf(3f)
            text = "LUT CAM"
        }
        styleLabel.pitch = dpf(3.6f)
        styleLabel.showUnlit = false
        modeText.text = modes[0].first

        // Swipe the viewfinder to change style, tap it to focus.
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                focusAt(e.x, e.y)
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (abs(velocityX) > abs(velocityY) && abs(velocityX) > 500f) {
                    stepStyle(if (velocityX < 0f) 1 else -1)
                    return true
                }
                return false
            }
        })
        viewfinder.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
        }

        modePill.setOnClickListener {
            if (busy) return@setOnClickListener
            if (modes.size == 1) {
                toast("This phone does not offer HDR or Night mode to third-party apps")
            } else {
                modeIndex = (modeIndex + 1) % modes.size
                updateModeLabel()
                bindCamera()
            }
        }
        shutter.setOnClickListener { takePhoto() }

        // Put the style on the preview again whenever the preview starts streaming.
        previewView.previewStreamState.observe(this) { state ->
            if (state == PreviewView.StreamState.STREAMING) applyPreview()
        }

        styles = store.all()
        selected = styles.first()
        rebuildTiles()
        updateLabel(0)
        ensureMatrices {
            rebuildTiles()
            applyPreview()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ---------------------------------------------------------------- camera

    private fun startCamera() {
        val mainExecutor = ContextCompat.getMainExecutor(this)
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider

            // Ask the phone which extra modes (HDR, Night, Auto) it allows for our app.
            val extFuture = ExtensionsManager.getInstanceAsync(this, provider)
            extFuture.addListener({
                try {
                    val manager = extFuture.get()
                    extensionsManager = manager
                    val base = CameraSelector.DEFAULT_BACK_CAMERA
                    val list = ArrayList<Pair<String, Int?>>()
                    list.add("STD" to null)
                    if (manager.isExtensionAvailable(base, ExtensionMode.AUTO)) list.add("AUTO" to ExtensionMode.AUTO)
                    if (manager.isExtensionAvailable(base, ExtensionMode.HDR)) list.add("HDR" to ExtensionMode.HDR)
                    if (manager.isExtensionAvailable(base, ExtensionMode.NIGHT)) list.add("NIGHT" to ExtensionMode.NIGHT)
                    modes = list
                } catch (e: Exception) {
                    extensionsManager = null
                }
                updateModeLabel()
                bindCamera()
            }, mainExecutor)
        }, mainExecutor)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun buildCapture(standard: Boolean): ImageCapture {
        val builder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(100)
        if (standard) {
            // Ask the camera for its best noise reduction and sharpening.
            Camera2Interop.Extender(builder)
                .setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                )
                .setCaptureRequestOption(
                    CaptureRequest.EDGE_MODE,
                    CaptureRequest.EDGE_MODE_HIGH_QUALITY
                )
        }
        return builder.build()
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val extension = modes[modeIndex].second
        val manager = extensionsManager

        var selector = CameraSelector.DEFAULT_BACK_CAMERA
        if (extension != null && manager != null) {
            selector = manager.getExtensionEnabledCameraSelector(selector, extension)
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val capture = buildCapture(extension == null)

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, capture)
            imageCapture = capture
        } catch (e: Exception) {
            toast("Could not start the camera: ${e.message}")
            if (extension != null) {
                // Fall back to the standard mode.
                modeIndex = 0
                updateModeLabel()
                bindCamera()
            }
        }
    }

    private fun updateModeLabel() {
        modeText.text = modes[modeIndex].first
    }

    private fun focusAt(x: Float, y: Float) {
        reticle.showAt(x, y)
        val cam = camera ?: return
        try {
            val point = previewView.meteringPointFactory.createPoint(x, y)
            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
        } catch (e: Exception) {
            // Focus is a nice extra, so a failure here is ignored.
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val style = selected ?: return
        if (busy) return
        setBusy(true)

        flashView.alpha = 0.75f
        flashView.animate().alpha(0f).setDuration(200).start()
        shutter.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        val temp = File(cacheDir, "capture.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(temp).build()

        capture.takePicture(options, worker, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val lut = store.loadLut(style)
                    PhotoProcessor.processAndSave(this@MainActivity, temp, style, lut)
                    runOnUiThread { showStatus("SAVED", 1500L) }
                } catch (e: Throwable) {
                    runOnUiThread { toast("Could not save the photo: ${e.message}") }
                } finally {
                    temp.delete()
                    runOnUiThread { setBusy(false) }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    setBusy(false)
                    toast("Capture failed: ${exception.message}")
                }
            }
        })
    }

    private fun setBusy(value: Boolean) {
        busy = value
        shutter.busy = value
        statusDot.alpha = if (value) 1f else 0.4f
        if (value) {
            showStatus("SAVING", 0L)
        } else if (captionText.text.toString() == "SAVING") {
            captionText.text = CAPTION
        }
    }

    /** Shows a short word in the caption line. holdMs = 0 keeps it until it is replaced. */
    private fun showStatus(text: String, holdMs: Long) {
        captionText.removeCallbacks(restoreCaption)
        captionText.text = text
        if (holdMs > 0L) captionText.postDelayed(restoreCaption, holdMs)
    }

    // ---------------------------------------------------------------- styles

    private fun selectStyle(style: Style) {
        val oldIndex = styles.indexOfFirst { it.id == selected?.id }
        val newIndex = styles.indexOfFirst { it.id == style.id }
        if (newIndex < 0 || newIndex == oldIndex) return
        selected = style
        updateSelection(true)
        applyPreview()
        updateLabel(if (newIndex > oldIndex) 1 else -1)
        viewfinder.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun stepStyle(direction: Int) {
        val index = styles.indexOfFirst { it.id == selected?.id }
        val next = index + direction
        if (index >= 0 && next in styles.indices) selectStyle(styles[next])
    }

    private fun updateLabel(direction: Int) {
        styleLabel.text = (selected?.name ?: "").uppercase().take(10)
        if (direction != 0) {
            styleLabel.alpha = 0f
            styleLabel.translationX = dpf(24f) * direction
            styleLabel.animate().alpha(1f).translationX(0f).setDuration(180).start()
        }
    }

    /** Makes sure every style has a preview matrix, then runs the action on the main thread. */
    private fun ensureMatrices(then: () -> Unit) {
        val missing = styles.filter { !matrices.containsKey(it.id) }
        if (missing.isEmpty()) {
            then()
            return
        }
        worker.execute {
            for (style in missing) {
                try {
                    matrices[style.id] = PreviewFilter.matrixFor(style, store.loadLut(style))
                } catch (e: Exception) {
                    matrices[style.id] = ColorMatrix()
                }
            }
            runOnUiThread { then() }
        }
    }

    private fun findTextureView(v: View): TextureView? {
        if (v is TextureView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val found = findTextureView(v.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    /** Puts the selected style's colour filter on the live preview. */
    private fun applyPreview() {
        val style = selected ?: return
        val target: View = findTextureView(previewView) ?: previewView
        val matrix = matrices[style.id]
        if (style.isIdentity || matrix == null) {
            target.setLayerType(View.LAYER_TYPE_NONE, null)
        } else {
            val paint = Paint()
            paint.colorFilter = ColorMatrixColorFilter(matrix)
            target.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
    }

    private fun filterFor(style: Style): ColorMatrixColorFilter? {
        if (style.isIdentity) return null
        val matrix = matrices[style.id] ?: return null
        return ColorMatrixColorFilter(matrix)
    }

    private fun rebuildTiles() {
        chipRow.removeAllViews()
        tiles.clear()
        for (style in styles) {
            val swatch = SwatchView(this)
            swatch.filter = filterFor(style)

            val label = makeTileLabel(style.name)

            val root = LinearLayout(this)
            root.orientation = LinearLayout.VERTICAL
            root.gravity = Gravity.CENTER_HORIZONTAL
            root.setPadding(dp(6), dp(4), dp(6), dp(4))
            root.addView(swatch, LinearLayout.LayoutParams(dp(52), dp(52)))
            val labelParams = LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT)
            labelParams.topMargin = dp(8)
            root.addView(label, labelParams)
            root.setOnClickListener { selectStyle(style) }
            if (!style.builtIn) {
                root.setOnLongClickListener {
                    confirmDelete(style)
                    true
                }
            }

            chipRow.addView(root)
            tiles.add(Tile(root, swatch, label, style))
        }

        // The last tile adds a new LUT.
        val addSwatch = SwatchView(this)
        addSwatch.isAdd = true
        val addLabel = makeTileLabel("Add LUT")
        addLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        val addRoot = LinearLayout(this)
        addRoot.orientation = LinearLayout.VERTICAL
        addRoot.gravity = Gravity.CENTER_HORIZONTAL
        addRoot.setPadding(dp(6), dp(4), dp(6), dp(4))
        addRoot.addView(addSwatch, LinearLayout.LayoutParams(dp(52), dp(52)))
        val addLabelParams = LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT)
        addLabelParams.topMargin = dp(8)
        addRoot.addView(addLabel, addLabelParams)
        addRoot.contentDescription = "Add a LUT"
        addRoot.setOnClickListener { lutPicker.launch(arrayOf("*/*")) }
        chipRow.addView(addRoot)

        updateSelection(false)
    }

    private fun makeTileLabel(name: String): TextView {
        val label = TextView(this)
        label.text = name.uppercase()
        label.typeface = mediumFont
        label.textSize = 11f
        label.letterSpacing = 0.06f
        label.includeFontPadding = false
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER
        label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        return label
    }

    private fun updateSelection(scroll: Boolean) {
        var activeTile: Tile? = null
        for (tile in tiles) {
            val active = tile.style.id == selected?.id
            tile.swatch.active = active
            tile.label.setTextColor(
                ContextCompat.getColor(this, if (active) R.color.text_primary else R.color.text_secondary)
            )
            if (active) activeTile = tile
        }
        if (scroll && activeTile != null) {
            val root = activeTile.root
            chipScroll.post {
                chipScroll.smoothScrollTo(root.left - (chipScroll.width - root.width) / 2, 0)
            }
        }
    }

    private fun confirmDelete(style: Style) {
        AlertDialog.Builder(this)
            .setMessage("Delete the style \"${style.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                store.removeStyle(style)
                matrices.remove(style.id)
                styles = store.all()
                selected = styles.first()
                rebuildTiles()
                applyPreview()
                updateLabel(0)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- LUT import

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
                    ensureMatrices {
                        rebuildTiles()
                        updateSelection(true)
                        applyPreview()
                        updateLabel(0)
                        toast("LUT added")
                    }
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

    // ---------------------------------------------------------------- small helpers

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dpf(value: Float): Float = value * resources.displayMetrics.density

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
    }
}
