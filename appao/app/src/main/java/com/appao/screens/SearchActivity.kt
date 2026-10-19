package com.appao

import android.animation.ValueAnimator
import android.content.Intent
import android.database.Cursor
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.drawable.GradientDrawable
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
import android.widget.GridView
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
import com.appao.ui.IconLoader
import com.appao.ui.NativePopupMenu

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
    private var menuSearchMode = false
    private lateinit var cameraPreview: TextureView
    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var previewRequest: CaptureRequest.Builder? = null
    private var photoReader: ImageReader? = null
    private var cameraSensorOrientation = 0
    private var cameraPermissionPending = false
    private var galleryPermissionPending = false
    private data class GalleryImage(val uri: Uri, val folder: String, val dateAdded: Long)
    private var deviceImages: List<GalleryImage> = emptyList()
    private val selectedGalleryUris = linkedSetOf<String>()
    private var activeGalleryTab = 0 // 0 Recentes, 1 Todos, 2 Pastas
    private var openGalleryFolder: String? = null
    private var capturedPhotoBytes: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_search)
        SystemBarHelper.sync(this)

        root = findViewById(R.id.searchRoot)
        resultQuery = findViewById(R.id.searchQuery)
        composerBar = findViewById(R.id.searchComposerBar)
        composerPill = findViewById(R.id.searchComposerPill)
        menuSearchMode = intent.getBooleanExtra("menu_search", false)
        input = if (menuSearchMode) findViewById(R.id.menuSearchInput) else findViewById(R.id.searchInput)
        send = findViewById(R.id.searchSend)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            composerBar.updatePadding(bottom = maxOf(ime, bars.bottom) + dp(10))
            insets
        }

        IconLoader.applyIcon(findViewById(R.id.searchCloseIcon), "close", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.searchMoreIcon), "more_vert", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.searchAddIcon), "plus", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.searchSliderIcon), "filters", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.searchSendIcon), "arrow_up", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.menuSearchBackIcon), "arrow_left", R.color.iconTint)
        // This asset is intentionally user-supplied later; IconLoader shows its fallback until installed.
        IconLoader.applyIcon(findViewById(R.id.menuSearchClearIcon), "close_circle_mini", 0)

        galleryMode = intent.getBooleanExtra("gallery_mode", false)
        cameraMode = intent.getBooleanExtra("camera_mode", false)
        cameraPreview = findViewById(R.id.searchCameraPreview)
        findViewById<View>(R.id.searchGalleryRecentTab).setOnClickListener { setGalleryTab(0) }
        findViewById<View>(R.id.searchGalleryAllTab).setOnClickListener { setGalleryTab(1) }
        findViewById<View>(R.id.searchGalleryFoldersTab).setOnClickListener { setGalleryTab(2) }
        IconLoader.applyIcon(findViewById(R.id.searchCameraButtonIcon), "camera", R.color.iconTint)
        findViewById<View>(R.id.searchClose).setOnClickListener {
            if (galleryMode && activeGalleryTab == 2 && openGalleryFolder != null) {
                openGalleryFolder = null
                findViewById<TextView>(R.id.searchModeTitle).text = "Galeria"
                renderGalleryContent()
            } else finish()
        }
        findViewById<View>(R.id.menuSearchBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
        }
        findViewById<View>(R.id.menuSearchClear).setOnClickListener {
            input.setText("")
            input.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
        findViewById<View>(R.id.searchMore).setOnClickListener {
            when {
                galleryMode -> completeGallerySelection()
                cameraMode -> capturedPhotoBytes?.let { bytes -> saveCapturedPhoto(bytes) }
                else -> showMorePopup(it)
            }
        }
        findViewById<View>(R.id.searchAdd).setOnClickListener {
            AppsPopup.show(it) { action ->
                when (action) {
                    "file" -> startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    })
                    "image" -> {
                        startActivityForResult(
                            Intent(this, SearchActivity::class.java).putExtra("gallery_mode", true), 902
                        )
                        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
                    }
                    "camera" -> {
                        startActivityForResult(
                            Intent(this, SearchActivity::class.java).putExtra("camera_mode", true), 903
                        )
                        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
                    }
                    "think" -> {
                        val active = AppsPopup.toggleThink(this)
                        AppAoToast.show(this, if (active) "Pensar mais ativado" else "Pensar mais desativado")
                    }
                }
            }
        }
        findViewById<View>(R.id.searchSend).setOnClickListener { performSearch() }
        input.setOnEditorActionListener { _, _, _ -> performSearch(); true }

        input.setSingleLine(menuSearchMode)
        input.setHorizontallyScrolling(menuSearchMode)
        input.setLineSpacing(0f, if (menuSearchMode) 1f else 1.6f)
        input.gravity = if (menuSearchMode) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.TOP or Gravity.START
        input.includeFontPadding = true
        if (menuSearchMode) {
            input.setPadding(0, 0, 0, 0)
            input.maxLines = 1
        } else {
            input.setPadding(0, 0, 0, 0)
            input.maxLines = Int.MAX_VALUE
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (menuSearchMode) {
                    findViewById<View>(R.id.menuSearchClear).visibility =
                        if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                    updateConversationSearchResults(s?.toString().orEmpty())
                } else {
                    input.post { resizeInput(true) }
                    updateSendState()
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val initial = intent.getStringExtra("query").orEmpty()
        input.setText(initial)
        input.setSelection(input.text.length)
        resultQuery.text = initial

        if (menuSearchMode) {
            findViewById<View>(R.id.searchTopBar).visibility = View.GONE
            findViewById<View>(R.id.menuSearchBar).visibility = View.VISIBLE
            findViewById<View>(R.id.searchMore).visibility = View.GONE
            composerBar.visibility = View.GONE
            findViewById<View>(R.id.searchContent).setPadding(
                dp(20), dp(76), dp(20), dp(20)
            )
            input.hint = "Pesquisar conversas"
            findViewById<View>(R.id.menuSearchClear).visibility = if (input.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            updateConversationSearchResults(input.text.toString())
            input.post {
                input.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        if (galleryMode) {
            findViewById<TextView>(R.id.searchModeTitle).apply {
                text = "Galeria"
                visibility = View.VISIBLE
            }
            resultQuery.visibility = View.GONE
            composerBar.visibility = View.GONE
            findViewById<View>(R.id.searchContent).visibility = View.GONE
            findViewById<View>(R.id.searchTopBar).visibility = View.VISIBLE
            findViewById<View>(R.id.searchCameraPanel).visibility = View.GONE
            findViewById<GridView>(R.id.searchGalleryGrid).visibility = View.VISIBLE
            findViewById<View>(R.id.searchGalleryBottomBar).visibility = View.VISIBLE
            findViewById<View>(R.id.searchMediaEmpty).visibility = View.VISIBLE
            findViewById<View>(R.id.searchMore).visibility = View.GONE
            updateGalleryTabAppearance()
            ensureGalleryPermissionAndLoad()
        } else if (cameraMode) {
            findViewById<TextView>(R.id.searchModeTitle).apply {
                text = "Câmara"
                visibility = View.VISIBLE
            }
            resultQuery.visibility = View.GONE
            composerBar.visibility = View.GONE
            findViewById<View>(R.id.searchContent).visibility = View.GONE
            findViewById<View>(R.id.searchCameraPanel).visibility = View.VISIBLE
            findViewById<View>(R.id.searchGalleryGrid).visibility = View.GONE
            findViewById<View>(R.id.searchGalleryBottomBar).visibility = View.GONE
            findViewById<View>(R.id.searchMediaEmpty).visibility = View.GONE
            findViewById<View>(R.id.searchMore).visibility = View.GONE
            IconLoader.applyIcon(findViewById(R.id.searchCameraIcon), "camera", R.color.iconTint)
            IconLoader.applyIcon(findViewById(R.id.searchCameraButtonIcon), "camera", R.color.iconTint)
            val cameraPanel = findViewById<View>(R.id.searchCameraPanel)
            cameraPanel.setPadding(0, 0, 0, 0)
            val cameraFrame = findViewById<View>(R.id.searchCameraFrame)
            cameraFrame.layoutParams = cameraFrame.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = (resources.displayMetrics.widthPixels * 0.75f).toInt()
            }
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
        if (!galleryMode && !cameraMode && !menuSearchMode) input.post {
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
            text = "Permite o acesso às imagens para mostrar a galeria do dispositivo nesta tela."
            visibility = View.VISIBLE
        }
        findViewById<GridView>(R.id.searchGalleryGrid).visibility = View.GONE
        findViewById<View>(R.id.searchMore).visibility = View.GONE
    }

    private fun loadDeviceImages() {
        val grid = findViewById<GridView>(R.id.searchGalleryGrid)
        val empty = findViewById<TextView>(R.id.searchMediaEmpty)
        val images = ArrayList<GalleryImage>()
        var cursor: Cursor? = null
        try {
            val projection = mutableListOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection += MediaStore.Images.Media.RELATIVE_PATH
                MediaStore.Images.Media.RELATIVE_PATH
            } else {
                projection += MediaStore.Images.Media.DATA
                MediaStore.Images.Media.DATA
            }
            cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.let { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val bucketColumn = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val folderIndex = c.getColumnIndex(folderColumn)
                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    val bucket = if (bucketColumn >= 0) c.getString(bucketColumn).orEmpty() else ""
                    val rawFolder = if (folderIndex >= 0) c.getString(folderIndex).orEmpty() else ""
                    val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        rawFolder.trim('/').ifBlank { bucket.ifBlank { "Imagens" } }
                    } else {
                        rawFolder.substringBeforeLast('/', rawFolder).ifBlank { bucket.ifBlank { "Imagens" } }
                    }
                    val date = if (dateColumn >= 0) c.getLong(dateColumn) else 0L
                    images += GalleryImage(uri, folder, date)
                }
            }
        } catch (_: SecurityException) {
            // Scoped-media access can limit this grid to media the user authorized.
        } catch (_: Throwable) {
        } finally {
            cursor?.close()
        }

        deviceImages = images.distinctBy { it.uri.toString() }
        grid.visibility = View.VISIBLE
        renderGalleryContent()
    }

    private fun setGalleryTab(tab: Int) {
        activeGalleryTab = tab.coerceIn(0, 2)
        openGalleryFolder = null
        findViewById<TextView>(R.id.searchModeTitle).text = "Galeria"
        updateGalleryTabAppearance()
        renderGalleryContent()
    }

    private fun updateGalleryTabAppearance() {
        val tabIds = listOf(R.id.searchGalleryRecentTab, R.id.searchGalleryAllTab, R.id.searchGalleryFoldersTab)
        tabIds.forEachIndexed { index, id ->
            findViewById<TextView>(id).apply {
                val selected = index == activeGalleryTab
                background = if (selected) ContextCompat.getDrawable(this@SearchActivity, R.drawable.bg_auth_indicator)
                    else android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                setTextColor(ContextCompat.getColor(this@SearchActivity, if (selected) R.color.onpri else R.color.dim))
                alpha = if (selected) 1f else 0.92f
            }
        }
    }

    private fun renderGalleryContent() {
        if (!galleryMode) return
        val grid = findViewById<GridView>(R.id.searchGalleryGrid)
        val empty = findViewById<TextView>(R.id.searchMediaEmpty)
        val title = findViewById<TextView>(R.id.searchModeTitle)
        val showFolders = activeGalleryTab == 2 && openGalleryFolder == null
        val visibleImages = when (activeGalleryTab) {
            0 -> deviceImages.take(160)
            1 -> deviceImages
            else -> deviceImages.filter { it.folder == openGalleryFolder }
        }
        val folders = deviceImages.map { it.folder }.filter { it.isNotBlank() }.distinct()
        val isEmpty = if (showFolders) folders.isEmpty() else visibleImages.isEmpty()
        empty.text = when {
            !isEmpty -> ""
            showFolders -> "Não foram encontradas pastas de imagens."
            else -> "Ainda não há imagens para mostrar nesta seleção."
        }
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        grid.visibility = if (isEmpty) View.GONE else View.VISIBLE

        if (showFolders) {
            grid.adapter = object : android.widget.BaseAdapter() {
                override fun getCount(): Int = folders.size
                override fun getItem(position: Int): Any = folders[position]
                override fun getItemId(position: Int): Long = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val cell = (convertView as? LinearLayout) ?: LinearLayout(this@SearchActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(6), dp(8), dp(6), dp(8))
                        background = ContextCompat.getDrawable(this@SearchActivity, R.drawable.bg_pill_card)
                        val icon = ImageView(this@SearchActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                        IconLoader.applyIcon(icon, "folder", R.color.iconTint)
                        addView(icon)
                        addView(TextView(this@SearchActivity).apply {
                            tag = "folder_label"
                            gravity = Gravity.CENTER
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            textSize = 12f
                            setTextColor(ContextCompat.getColor(this@SearchActivity, R.color.text))
                            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) }
                        })
                    }
                    val label = cell.getChildAt(1) as TextView
                    label.text = folders[position].substringAfterLast('/').ifBlank { folders[position] }
                    cell.layoutParams = android.widget.AbsListView.LayoutParams(-1, galleryCellSize(parent))
                    cell.setOnClickListener {
                        openGalleryFolder = folders[position]
                        title.text = folders[position].substringAfterLast('/').ifBlank { folders[position] }
                        renderGalleryContent()
                    }
                    return cell
                }
            }
        } else {
            grid.adapter = object : android.widget.BaseAdapter() {
                override fun getCount(): Int = visibleImages.size
                override fun getItem(position: Int): Any = visibleImages[position]
                override fun getItemId(position: Int): Long = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val cell = (convertView as? FrameLayout) ?: FrameLayout(this@SearchActivity).apply {
                        val image = ImageView(this@SearchActivity).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(2).toFloat()
                                setColor(android.graphics.Color.TRANSPARENT)
                            }
                            clipToOutline = true
                            contentDescription = "Imagem da galeria"
                        }
                        addView(image, FrameLayout.LayoutParams(-1, -1))
                        val check = TextView(this@SearchActivity).apply {
                            text = "✓"
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setTextColor(android.graphics.Color.WHITE)
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(AccentColorManager.resolvedColor(this@SearchActivity) ?: ContextCompat.getColor(this@SearchActivity, R.color.pri))
                            }
                            visibility = View.GONE
                        }
                        addView(check, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END).apply {
                            topMargin = dp(6); marginEnd = dp(6)
                        })
                    }
                    val data = visibleImages[position]
                    val image = cell.getChildAt(0) as ImageView
                    val check = cell.getChildAt(1) as TextView
                    val selected = selectedGalleryUris.contains(data.uri.toString())
                    Glide.with(image).load(data.uri).centerCrop().into(image)
                    image.alpha = if (selected) 0.78f else 1f
                    check.visibility = if (selected) View.VISIBLE else View.GONE
                    check.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(AccentColorManager.resolvedColor(this@SearchActivity) ?: ContextCompat.getColor(this@SearchActivity, R.color.pri))
                    }
                    cell.layoutParams = android.widget.AbsListView.LayoutParams(-1, galleryCellSize(parent))
                    cell.setOnClickListener {
                        val key = data.uri.toString()
                        if (!selectedGalleryUris.add(key)) selectedGalleryUris.remove(key)
                        updateGalleryConfirmAction()
                        grid.invalidateViews()
                    }
                    return cell
                }
            }
        }
        grid.invalidateViews()
        updateGalleryConfirmAction()
    }

    private fun galleryCellSize(parent: ViewGroup): Int {
        val width = (parent.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) - dp(12)
        return ((width - dp(6)) / 3).coerceAtLeast(dp(84))
    }

    private fun updateGalleryConfirmAction() {
        if (!galleryMode) return
        val action = findViewById<View>(R.id.searchMore)
        val count = selectedGalleryUris.size
        action.visibility = if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) IconLoader.applyIcon(findViewById(R.id.searchMoreIcon), "check_mark", R.color.iconTint)
        else IconLoader.applyIcon(findViewById(R.id.searchMoreIcon), "more_vert", R.color.iconTint)
        findViewById<TextView>(R.id.searchModeTitle).text = if (openGalleryFolder != null) {
            openGalleryFolder!!.substringAfterLast('/').ifBlank { "Pasta" }
        } else if (count > 0) "Galeria ($count)" else "Galeria"
    }

    private fun completeGallerySelection() {
        if (selectedGalleryUris.isEmpty()) {
            AppAoToast.show(this, "Seleciona pelo menos uma imagem")
            return
        }
        val uris = ArrayList(selectedGalleryUris)
        val result = Intent().apply {
            putStringArrayListExtra("image_uris", uris)
            putExtra("image_uri", uris.first())
            data = Uri.parse(uris.first())
        }
        setResult(RESULT_OK, result)
        finish()
        overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
    }

    private fun captureOrStartCamera() {
        capturedPhotoBytes = null
        findViewById<View>(R.id.searchMore).visibility = View.GONE
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
                    if (bytes != null) runOnUiThread {
                        capturedPhotoBytes = bytes
                        IconLoader.applyIcon(findViewById(R.id.searchMoreIcon), "check_mark", R.color.iconTint)
                        findViewById<View>(R.id.searchMore).visibility = View.VISIBLE
                    }
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
                overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
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
            902, 903 -> {
                val selected = data.getStringArrayListExtra("image_uris").orEmpty()
                val single = data.getStringExtra("image_uri") ?: data.data?.toString()
                val all = selected.ifEmpty { listOfNotNull(single) }
                if (all.isEmpty()) return
                getSharedPreferences("appao", MODE_PRIVATE).edit()
                    .putString("pending_media_uri", all.first())
                    .putString("pending_media_uris_json", org.json.JSONArray(all).toString())
                    .apply()
                AppAoToast.show(this, if (requestCode == 903) "Imagem da câmara selecionada" else if (all.size > 1) "${all.size} imagens selecionadas" else "Imagem selecionada")
            }
        }
    }

    private fun showMorePopup(anchor: View) {
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Eliminar pesquisa", iconName = "trash_bin"),
                NativePopupMenu.Item(2, "Arquivar pesquisa", iconName = "archive"),
                NativePopupMenu.Item(3, "Iniciar nova pesquisa", iconName = "plus"),
                NativePopupMenu.Item(4, "Renomear pesquisa", iconName = "write")
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
        if (menuSearchMode) {
            updateConversationSearchResults(query)
            input.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken, 0)
            return
        }
        if (query.isBlank()) return
        ConversationHistoryStore.record(this, query)
        resultQuery.text = query
        input.clearFocus()
        AppAoToast.show(this, "Pesquisa atualizada")
    }

    /** Searches any locally stored conversation metadata without treating unrelated preferences as chats. */
    private fun updateConversationSearchResults(query: String) {
        if (!menuSearchMode) return
        val labels = collectStoredConversationLabels()
        val matches = if (query.isBlank()) labels else labels.filter { it.contains(query, ignoreCase = true) }
        resultQuery.text = when {
            query.isBlank() && labels.isEmpty() -> "As tuas conversas aparecerão aqui."
            matches.isEmpty() -> "Nenhuma conversa encontrada."
            else -> matches.distinct().take(12).joinToString("\n\n")
        }
    }

    private fun collectStoredConversationLabels(): List<String> {
        val prefs = getSharedPreferences("appao", MODE_PRIVATE)
        val relevantKeys = prefs.all.filterKeys { key ->
            val lower = key.lowercase(java.util.Locale.ROOT)
            (lower.contains("conversation") || lower.contains("conversa") || lower.contains("chat")) &&
                !lower.contains("article") && !lower.contains("comment")
        }
        val labels = mutableListOf<String>()
        ConversationHistoryStore.all(this).forEach { entry ->
            labels += entry.title
            if (entry.query.isNotBlank()) labels += entry.query
        }
        val field = Regex("\"(?:title|name|label|subject|preview|lastMessage|last_message)\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        relevantKeys.values.forEach { value ->
            val raw = value as? String ?: return@forEach
            field.findAll(raw).forEach { match ->
                val label = match.groupValues.getOrNull(1).orEmpty()
                    .replace("\\\"", "\"").trim()
                if (label.length >= 2 && label.length < 300) labels += label
            }
        }
        return labels.distinct()
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
            AccentColorManager.primaryShape(this, android.graphics.drawable.GradientDrawable.RECTANGLE, 12f)
        } else {
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
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
        findViewById<View>(R.id.menuSearchBar)?.setBackgroundColor(bg)
        findViewById<View>(R.id.menuSearchPill)?.background = ContextCompat.getDrawable(this, R.drawable.bg_pill_card)
        resultQuery.setTextColor(ContextCompat.getColor(this, R.color.dim))
        if (!menuSearchMode) updateSendState()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        applyTheme()
    }

    override fun onBackPressed() {
        if (galleryMode && activeGalleryTab == 2 && openGalleryFolder != null) {
            openGalleryFolder = null
            findViewById<TextView>(R.id.searchModeTitle).text = "Galeria"
            renderGalleryContent()
            return
        }
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
