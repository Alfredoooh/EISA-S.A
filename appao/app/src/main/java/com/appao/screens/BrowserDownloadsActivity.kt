package com.appao.screens

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.appao.AppAoToast
import com.appao.NativeM3Dialog
import com.appao.R
import com.appao.SystemBarHelper
import com.appao.ui.IconLoader
import com.appao.ui.NativePopupMenu
import java.util.Locale


/** Native list for downloads created by the in-app WebView. */
class BrowserDownloadsActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private var manager: DownloadManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        SystemBarHelper.sync(this)
        manager = getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@BrowserDownloadsActivity, R.color.bg))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val back = iconButton("arrow_left", "Voltar")
        back.setOnClickListener { finishWithMotion() }
        toolbar.addView(back)
        toolbar.addView(TextView(this).apply {
            text = "Downloads"
            textSize = 21f
            setTextColor(ContextCompat.getColor(this@BrowserDownloadsActivity, R.color.text))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        val clear = iconButton("delete_bin", "Remover registos")
        clear.setOnClickListener { confirmClear() }
        toolbar.addView(clear)
        root.addView(toolbar)

        empty = TextView(this).apply {
            text = "Os ficheiros descarregados aparecerão aqui."
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@BrowserDownloadsActivity, R.color.dim))
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(36), dp(24), dp(36))
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(6), dp(12), dp(20)) }
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(LinearLayout(this@BrowserDownloadsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(empty)
                addView(list)
            })
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshDownloads()
    }

    override fun onResume() { super.onResume(); if (::list.isInitialized) refreshDownloads() }

    private fun refreshDownloads() {
        val dm = manager ?: return
        list.removeAllViews()
        var count = 0
        val query = DownloadManager.Query()
        val cursor = runCatching { dm.query(query) }.getOrNull()
        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val title = c.getString(c.getColumnIndex(DownloadManager.COLUMN_TITLE)).orEmpty().ifBlank { "Ficheiro descarregado" }
                val uri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI)).orEmpty()
                val status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))
                val bytes = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).coerceAtLeast(0)
                val total = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).coerceAtLeast(0)
                val statusText = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Concluído · ${formatBytes(bytes)}"
                    DownloadManager.STATUS_FAILED -> "Falhou"
                    DownloadManager.STATUS_PAUSED -> "Em pausa"
                    else -> if (total > 0) "A transferir · ${formatBytes(bytes)} de ${formatBytes(total)}" else "A transferir"
                }
                list.addView(downloadRow(id, title, uri, statusText, status == DownloadManager.STATUS_SUCCESSFUL), rowParams())
                count++
            }
        }
        empty.visibility = if (count == 0) View.VISIBLE else View.GONE
        list.visibility = if (count == 0) View.GONE else View.VISIBLE
    }

    private fun downloadRow(id: Long, title: String, uri: String, status: String, finished: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@BrowserDownloadsActivity, R.drawable.bg_settings_single)
            setPadding(dp(14), dp(10), dp(8), dp(10))
            isClickable = true
            isFocusable = true
        }
        val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE; IconLoader.applyIcon(this, "download", R.color.iconTint) }
        row.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 14.5f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(ContextCompat.getColor(this@BrowserDownloadsActivity, R.color.text))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })
        labels.addView(TextView(this).apply {
            text = status
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@BrowserDownloadsActivity, R.color.dim))
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val more = iconButton("more_vert", "Opções")
        more.setOnClickListener { showItemMenu(more, id, title, uri, finished) }
        row.addView(more)
        row.setOnClickListener {
            if (finished) openDownload(id) else showItemMenu(more, id, title, uri, finished)
        }
        return row
    }

    private fun showItemMenu(anchor: View, id: Long, title: String, uri: String, finished: Boolean) {
        NativePopupMenu.show(anchor, listOf(
            NativePopupMenu.Item(1, if (finished) "Abrir ficheiro" else "Ver ligação", iconName = "external_link"),
            NativePopupMenu.Item(2, "Remover da lista", iconName = "delete_bin")
        ), alignLeft = false, placeAbove = true) { item ->
            when (item.id) {
                1 -> if (finished) openDownload(id) else runCatching {
                    val tab = BrowserTabStore.create(this, uri)
                    startActivity(Intent(this, AppViewerActivity::class.java).apply { putExtra("browser_mode", true); putExtra("browser_tab_id", tab.id); putExtra("url", tab.url) })
                    overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
                }
                2 -> { manager?.remove(id); refreshDownloads() }
            }
        }
    }

    private fun openDownload(id: Long) {
        val uri = runCatching { manager?.getUriForDownloadedFile(id) }.getOrNull()
        if (uri == null) { AppAoToast.show(this, "O ficheiro ainda não está disponível"); return }
        val mime = runCatching { contentResolver.getType(uri) }.getOrNull() ?: "*/*"
        runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            .onFailure { AppAoToast.show(this, "Não existe uma aplicação para abrir este ficheiro") }
    }

    private fun confirmClear() {
        NativeM3Dialog.confirm(this, "Limpar downloads", "Remover os registos de download do gestor do Android?") { ok ->
            if (ok) { manager?.remove(*allDownloadIds().toLongArray()); refreshDownloads() }
        }
    }

    private fun allDownloadIds(): List<Long> {
        val ids = mutableListOf<Long>()
        runCatching { manager?.query(DownloadManager.Query())?.use { c -> while (c.moveToNext()) ids += c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)) } }
        return ids
    }

    private fun iconButton(name: String, desc: String): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        background = ContextCompat.getDrawable(this@BrowserDownloadsActivity, R.drawable.bg_transparent_pressed)
        contentDescription = desc
        isClickable = true
        isFocusable = true
        val image = ImageView(this@BrowserDownloadsActivity)
        IconLoader.applyIcon(image, name, R.color.iconTint)
        addView(image, LinearLayout.LayoutParams(dp(24), dp(24)))
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(44))
    }

    private fun rowParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(3) }
    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024f)
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun finishWithMotion() { finish(); overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit) }
}