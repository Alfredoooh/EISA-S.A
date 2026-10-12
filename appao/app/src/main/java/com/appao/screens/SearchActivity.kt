package com.appao

import android.animation.ValueAnimator
import android.content.Intent
import android.database.Cursor
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.text.Editable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.view.animation.PathInterpolator
import java.io.File

/** Internal native search surface. It uses the exact same composer geometry as the main page. */
class SearchActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var resultQuery: TextView
    private lateinit var composerBar: View
    private lateinit var composerPill: View
    private lateinit var input: EditText
    private lateinit var send: FrameLayout
    private var heightAnimator: ValueAnimator? = null
    private var galleryMode = false
    private var cameraMode = false
    private lateinit var cameraPreview: TextureView
    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var previewRequest: CaptureRequest.Builder? = null
    private var photoReader: ImageReader? = null
    private var cameraSensorOrientation = 0
    private var cameraPermissionPending = false
    private var galleryPermissionPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_search)
        SystemBarHelper.sync(this)

        root = findViewById(R.id.searchRoot)
        resultQuery = findViewById(R.id.searchQuery)
        composerBar = findViewById(R.id.searchComposerBar)
        composerPill = findViewById(R.id.searchComposerPill)
        input = findViewById(R.id.searchInput)
        send = findViewById(R.id.searchSend)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            composerBar.updatePadding(bottom = maxOf(ime, bars.bottom) + dp(10))
            insets
        }

        IconLoader.applySvg(findViewById(R.id.searchCloseIcon), "x", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.searchMoreIcon), "more-horizontal", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.searchAddIcon), "plus", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.searchSliderIcon), "map-pin", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.searchSendIcon), "arrow-up", R.color.iconTint)

        galleryMode = intent.getBooleanExtra("gallery_mode", false)
        cameraMode = intent.getBooleanExtra("camera_mode", false)
        cameraPreview = findViewById(R.id.searchCameraPreview)
        findViewById<View>(R.id.searchClose).setOnClickListener { finish() }
        findViewById<View>(R.id.searchMore).setOnClickListener {
            when {
                galleryMode -> openNativeImagePicker()
                cameraMode -> captureOrStartCamera()
                else -> showMorePopup(it)
            }
        }
        findViewById<View>(R.id.searchAdd).setOnClickListener {
            AppsPopup.show(it) { action ->
                when (action) {
                    "file" -> startActivity(Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    })
                    "image" -> startActivity(Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    })
                    "camera" -> startActivityForResult(android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), 901)
                    "think" -> AppAoToast.show(this, "Pensar mais")
                }
            }
        }
        findViewById<View>(R.id.searchSend).setOnClickListener { performSearch() }
        input.setOnEditorActionListener { _, _, _ -> performSearch(); true }

        input.setSingleLine(false)
        input.setHorizontallyScrolling(false)
        input.setLineSpacing(0f, 1.6f)
        input.gravity = Gravity.TOP or Gravity.START
        input.includeFontPadding = true
        input.setPadding(0, 0, 0, 0)
        input.maxLines = Int.MAX_VALUE

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                input.post { resizeInput(true) }
                updateSendState()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val initial = intent.getStringExtra("query").orEmpty()
        input.setText(initial)
        input.setSelection(input.text.length)
        resultQuery.text = initial

        if (intent.getBooleanExtra("menu_search", false)) {
            findViewById<View>(R.id.searchMore).visibility = View.GONE
            composerBar.layoutParams = (composerBar.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.TOP
                topMargin = dp(56)
                bottomMargin = 0
            }
            findViewById<View>(R.id.searchContent).setPadding(
                dp(20), dp(150), dp(20), dp(20)
            )
            input.hint = "Pesquisar conversas e projetos"
            input.post {
                input.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        if (galleryMode) {
            resultQuery.visibility = View.GONE
            composerBar.visibility = View.GONE
            findViewById<View>(R.id.searchContent).visibility = View.GONE
            findViewById<View>(R.id.searchTopBar).visibility = View.VISIBLE
            findViewById<View>(R.id.searchCameraPanel).visibility = View.GONE
            findViewById<android.widget.GridView>(R.id.searchGalleryGrid).visibility = View.VISIBLE
            findViewById<View>(R.id.searchMediaEmpty).visibility = View.VISIBLE
            findViewById<View>(R.id.searchMore).contentDescription = "Abrir galeria do dispositivo"
            IconLoader.applySvg(findViewById(R.id.searchMoreIcon), "image", R.color.iconTint)
            ensureGalleryPermissionAndLoad()
        } else if (cameraMode) {
            resultQuery.visibility = View.GONE
            composerBar.visibility = View.GONE
            findViewById<View>(R.id.searchContent).visibility = View.GONE
            findViewById<View>(R.id.searchCameraPanel).visibility = View.VISIBLE
            findViewById<View>(R.id.searchGalleryGrid).visibility = View.GONE
            findViewById<View>(R.id.searchMediaEmpty).visibility = View.GONE
            findViewById<View>(R.id.searchMore).visibility = View.GONE
            IconLoader.applySvg(findViewById(R.id.searchCameraIcon), "camera", R.color.iconTint)
            findViewById<View>(R.id.searchCameraButton).setOnClickListener { captureOrStartCamera() }
            cameraPreview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCameraPreview()
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            }
            if (cameraPreview.isAvailable) openCameraPreview()
        }

        applyTheme()
        if (!galleryMode && !cameraMode) input.post {
            resizeInput(false)
            updateSendState()
        }

        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun ensureGalleryPermissionAndLoad() {
        val requiredPermissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val hasAccess = requiredPermissions.any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasAccess) {
            loadDeviceImages()
            return
        }
        galleryPermissionPending = true
        runCatching { requestPermissions(requiredPermissions, 907) }
            .onFailure {
                galleryPermissionPending = false
                showGalleryPermissionFallback()
            }
    }

    private fun showGalleryPermissionFallback() {
        findViewById<TextView>(R.id.searchMediaEmpty).apply {
            text = "Permite o acesso às imagens ou usa o botão de galeria para escolher uma imagem."
            visibility = View.VISIBLE
        }
        findViewById<android.widget.GridView>(R.id.searchGalleryGrid).visibility = View.GONE
    }

    private fun loadDeviceImages() {
        val grid = findViewById<android.widget.GridView>(R.id.searchGalleryGrid)
        val empty = findViewById<TextView>(R.id.searchMediaEmpty)
        val images = ArrayList<Uri>()
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.let { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && images.size < 240) {
                    val id = c.getLong(idColumn)
                    images += Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                }
            }
        } catch (_: SecurityException) {
            // On Android versions that require scoped media permission, the native picker remains available.
        } catch (_: Throwable) {
        } finally {
            cursor?.close()
        }

        empty.text = if (images.isEmpty()) "Ainda não há imagens para mostrar. Usa o botão de galeria para abrir o seletor do dispositivo." else ""
        empty.visibility = if (images.isEmpty()) View.VISIBLE else View.GONE
        grid.adapter = object : android.widget.BaseAdapter() {
            override fun getCount(): Int = images.size
            override fun getItem(position: Int): Any = images[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val image = (convertView as? ImageView) ?: ImageView(this@SearchActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = ContextCompat.getDrawable(this@SearchActivity, R.drawable.bg_circle_card)
                    clipToOutline = true
                    layoutParams = android.widget.AbsListView.LayoutParams(-1, dp(132))
                    contentDescription = "Selecionar imagem"
                }
                Glide.with(image).load(images[position]).centerCrop().into(image)
                image.setOnClickListener {
                    setResult(RESULT_OK, Intent().setData(images[position]).putExtra("image_uri", images[position].toString()))
                    finish()
                }
                return image
            }
        }
    }

    private fun openNativeImagePicker() {
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }, 904)
        }.onFailure { AppAoToast.show(this, "Não foi possível abrir a galeria") }
    }

    private fun captureOrStartCamera() {
        if (cameraDevice == null || cameraSession == null || photoReader == null) {
            openCameraPreview()
            return
        }
        try {
            val device = cameraDevice ?: return
            val session = cameraSession ?: return
            val reader = photoReader ?: return
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                val rotation = windowManager.defaultDisplay.rotation
                val displayDegrees = when (rotation) {
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> 0
                }
                set(CaptureRequest.JPEG_ORIENTATION, (cameraSensorOrientation + displayDegrees + 270) % 360)
            }.build()
            session.capture(request, object : CameraCaptureSession.CaptureCallback() {}, Handler(Looper.getMainLooper()))
        } catch (_: SecurityException) {
            AppAoToast.show(this, "Permite o acesso à câmara para tirar uma fotografia")
        } catch (_: Throwable) {
            AppAoToast.show(this, "Não foi possível tirar a fotografia")
        }
    }

    private fun openCameraPreview() {
        if (!cameraMode || isFinishing || isDestroyed || !cameraPreview.isAvailable) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!cameraPermissionPending) {
                cameraPermissionPending = true
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 906)
            }
            return
        }
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = manager.cameraIdList.firstOrNull { cameraId ->
                manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull() ?: run {
                AppAoToast.show(this, "Não foi encontrada uma câmara")
                return
            }
            val chars = manager.getCameraCharacteristics(id)
            cameraSensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSize = (map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty())
                .filter { it.width > 0 && it.height > 0 }
                .minByOrNull { kotlin.math.abs((it.width.toFloat() / it.height) - 1.33f) * 1000 + it.width * it.height / 1000000f }
                ?: Size(640, 480)
            val photoSize = (map?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty())
                .filter { it.width > 0 && it.height > 0 }
                .minByOrNull { kotlin.math.abs((it.width * it.height).toLong() - 1280L * 960L) }
                ?: Size(1280, 960)
            val previewTexture = cameraPreview.surfaceTexture ?: return
            previewTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(previewTexture)
            photoReader?.close()
            photoReader = ImageReader.newInstance(photoSize.width, photoSize.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
                    val buffer = image.planes.firstOrNull()?.buffer
                    val bytes = if (buffer != null) ByteArray(buffer.remaining()).also { buffer.get(it) } else null
                    image.close()
                    if (bytes != null) saveCapturedPhoto(bytes)
                }, Handler(Looper.getMainLooper()))
            }
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession(surface)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                    AppAoToast.show(this@SearchActivity, "A câmara não pôde ser iniciada")
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: SecurityException) {
            AppAoToast.show(this, "Permite o acesso à câmara para continuar")
        } catch (_: Throwable) {
            AppAoToast.show(this, "Não foi possível iniciar a câmara")
        }
    }

    private fun createPreviewSession(previewSurface: Surface) {
        val device = cameraDevice ?: return
        val reader = photoReader ?: return
        try {
            previewRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            }
            device.createCaptureSession(listOf(previewSurface, reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraSession = session
                    try {
                        previewRequest?.build()?.let { session.setRepeatingRequest(it, null, Handler(Looper.getMainLooper())) }
                        findViewById<View>(R.id.searchCameraIcon).visibility = View.GONE
                        findViewById<TextView>(R.id.searchCameraButton).text = "Tirar fotografia"
                    } catch (_: Throwable) { }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    AppAoToast.show(this@SearchActivity, "Não foi possível preparar a câmara")
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
            AppAoToast.show(this, "Não foi possível preparar a câmara")
        }
    }

    private fun saveCapturedPhoto(bytes: ByteArray) {
        runCatching {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "AppAo_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AppAo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val contentUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Não foi possível guardar a fotografia")
                contentResolver.openOutputStream(contentUri)?.use { it.write(bytes) } ?: error("Não foi possível escrever a fotografia")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(contentUri, values, null, null)
                contentUri
            } else {
                val file = File(cacheDir, "AppAo_${System.currentTimeMillis()}.jpg")
                file.writeBytes(bytes)
                Uri.fromFile(file)
            }
            runOnUiThread {
                setResult(RESULT_OK, Intent().setData(uri).putExtra("image_uri", uri.toString()))
                finish()
            }
        }.onFailure {
            runOnUiThread { AppAoToast.show(this, "Não foi possível guardar a fotografia") }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 906) {
            cameraPermissionPending = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCameraPreview()
            else AppAoToast.show(this, "O acesso à câmara foi recusado")
        } else if (requestCode == 907) {
            galleryPermissionPending = false
            val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            if (granted) loadDeviceImages() else showGalleryPermissionFallback()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            904 -> {
                val uri = data.data ?: return
                setResult(RESULT_OK, Intent().setData(uri).putExtra("image_uri", uri.toString()))
                finish()
            }
        }
    }

    private fun showMorePopup(anchor: View) {
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Eliminar pesquisa", svg = "trash-2"),
                NativePopupMenu.Item(2, "Arquivar pesquisa", svg = "archive"),
                NativePopupMenu.Item(3, "Iniciar nova pesquisa", svg = "plus"),
                NativePopupMenu.Item(4, "Renomear pesquisa", svg = "pencil")
            )
        ) { item ->
            when (item.id) {
                1 -> {
                    input.setText("")
                    resultQuery.text = ""
                }
                2 -> AppAoToast.show(this, "Pesquisa arquivada")
                3 -> {
                    input.setText("")
                    resultQuery.text = ""
                    input.requestFocus()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
                4 -> NativeM3Dialog.prompt(
                    this,
                    "Renomear pesquisa",
                    "",
                    initial = resultQuery.text.toString()
                ) { renamed ->
                    if (!renamed.isNullOrBlank()) resultQuery.text = renamed
                }
            }
        }
    }

    private fun performSearch() {
        val query = input.text.toString().trim()
        if (query.isBlank()) return
        resultQuery.text = query
        input.clearFocus()
        AppAoToast.show(this, "Pesquisa atualizada")
    }

    private fun resizeInput(animate: Boolean) {
        if (input.width <= 0) return
        val minHeight = dp(26)
        val maxHeight = dp(200)
        val measured = if (input.text.isEmpty()) {
            minHeight
        } else {
            StaticLayout.Builder
                .obtain(input.text, 0, input.text.length, input.paint, input.width.coerceAtLeast(1))
                .setIncludePad(true)
                .setLineSpacing(0f, 1.6f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build().height.coerceAtLeast(minHeight)
        }
        val target = measured.coerceAtMost(maxHeight)
        val current = input.height.takeIf { it > 0 } ?: minHeight
        heightAnimator?.cancel()
        if (animate && current != target) {
            heightAnimator = ValueAnimator.ofInt(current, target).apply {
                duration = 300L
                interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
                addUpdateListener { a ->
                    input.layoutParams = input.layoutParams.apply { height = a.animatedValue as Int }
                }
                start()
            }
        } else {
            input.layoutParams = input.layoutParams.apply { height = target }
        }
        if (target < maxHeight) input.scrollTo(0, 0)
    }

    private fun updateSendState() {
        val active = input.text?.isNotBlank() == true
        send.background = if (active) {
            AccentColorManager.primaryShape(this, android.graphics.drawable.GradientDrawable.OVAL)
        } else {
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@SearchActivity, R.color.card2))
            }
        }
        findViewById<ImageView>(R.id.searchSendIcon).setColorFilter(
            if (active) AccentColorManager.onPrimaryColor(this) else ContextCompat.getColor(this, R.color.dim),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    private fun applyTheme() {
        val bg = ContextCompat.getColor(this, R.color.bg)
        root.setBackgroundColor(bg)
        composerBar.setBackgroundColor(bg)
        composerPill.background = ContextCompat.getDrawable(this, R.drawable.bg_pill_card)
        resultQuery.setTextColor(ContextCompat.getColor(this, R.color.dim))
        updateSendState()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        applyTheme()
    }

    override fun onBackPressed() {
        finish()
        overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
    }

    override fun onDestroy() {
        heightAnimator?.cancel()
        try { cameraSession?.close() } catch (_: Throwable) {}
        try { cameraDevice?.close() } catch (_: Throwable) {}
        try { photoReader?.close() } catch (_: Throwable) {}
        cameraSession = null
        cameraDevice = null
        photoReader = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
