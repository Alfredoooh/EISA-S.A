package com.appao.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.appao.AccentColorManager
import com.appao.BrowserPreferences
import com.appao.R
import com.appao.SystemBarHelper
import com.appao.ui.IconLoader
import com.appao.ui.NativePopupMenu
import com.google.android.material.materialswitch.MaterialSwitch

/** Dedicated, native settings page for browser-specific behaviour. */
class BrowserSettingsActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var searchEngineValue: TextView
    private lateinit var addressPositionValue: TextView
    private lateinit var popupPositionValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        SystemBarHelper.sync(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@BrowserSettingsActivity, R.color.bg))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(16), 0)
            minimumHeight = dp(56)
        }
        val back = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@BrowserSettingsActivity, R.drawable.bg_transparent_pressed)
            contentDescription = "Voltar"
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        val backIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            IconLoader.applyIcon(this, "arrow_left", R.color.iconTint)
        }
        back.addView(backIcon, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        toolbar.addView(back, LinearLayout.LayoutParams(dp(44), dp(52)))
        toolbar.addView(TextView(this).apply {
            text = "Definições do navegador"
            textSize = 18f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(this@BrowserSettingsActivity, R.color.text))
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(toolbar)
        root.addView(View(this).apply { setBackgroundColor(ContextCompat.getColor(this@BrowserSettingsActivity, R.color.line)) }, LinearLayout.LayoutParams(-1, dp(1)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        scroll.addView(content, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        section("Pesquisa e apresentação")
        searchEngineValue = addValueRow("search", "Motor de pesquisa", "Escolhe o serviço usado nas pesquisas")
        searchEngineValue.parentRow().setOnClickListener { showSearchEnginePicker(searchEngineValue.parentRow()) }
        addressPositionValue = addValueRow("options", "Posição da barra de endereço", "Define onde aparece a barra no navegador")
        addressPositionValue.parentRow().setOnClickListener { toggleAddressPosition() }
        popupPositionValue = addValueRow("apps", "Posição do menu", "Segue a barra de endereço ou fixa o menu")
        popupPositionValue.parentRow().setOnClickListener { cyclePopupPosition() }

        section("Privacidade e segurança")
        addToggle("shield_check", "Bloqueador de anúncios", "Bloqueia pedidos publicitários conhecidos", BrowserPreferences.adBlockEnabled(this)) {
            BrowserPreferences.setAdBlockEnabled(this, it)
        }
        addToggle("shield_check", "Bloqueio rígido", "Bloqueia também subdomínios e padrões de anúncios rotativos, incluindo em sites adultos", BrowserPreferences.adBlockStrictEnabled(this)) {
            BrowserPreferences.setAdBlockStrictEnabled(this, it)
        }
        addToggle("shield_check", "Proteção contra rastreadores", "Reduz pedidos de rastreio conhecidos", BrowserPreferences.trackingProtectionEnabled(this)) {
            BrowserPreferences.setTrackingProtectionEnabled(this, it)
        }
        addToggle("history", "Guardar histórico", "Guarda páginas visitadas neste dispositivo", BrowserPreferences.savePageHistory(this)) {
            BrowserPreferences.setSavePageHistory(this, it)
        }
        addToggle("notifications", "Notificações de sites", "Permite que os sites peçam para enviar notificações", BrowserPreferences.siteNotificationsEnabled(this)) { enabled ->
            BrowserPreferences.setSiteNotificationsEnabled(this, enabled)
            if (enabled) requestNotificationRuntimePermission()
        }

        section("Compatibilidade e aparência")
        addToggle("desktop", "Modo desktop", "Solicita a versão para computador dos sites", BrowserPreferences.desktopSiteEnabled(this)) {
            BrowserPreferences.setDesktopSiteEnabled(this, it)
        }
        addToggle("moon", "Tema escuro dos sites", "Aplica tema escuro quando o WebView permite", BrowserPreferences.forceDarkEnabled(this)) {
            BrowserPreferences.setForceDarkEnabled(this, it)
        }
        addToggle("contrast", "Cor adaptativa da barra", "Permite adaptar a barra à página aberta", BrowserPreferences.adaptiveBrowserBarColor(this)) {
            BrowserPreferences.setAdaptiveBrowserBarColor(this, it)
        }
        refreshValues()
    }

    /** The user must still grant the OS-level POST_NOTIFICATIONS permission (Android 13+) before
     *  any notification can actually be posted, even with the in-app toggle enabled. */
    private fun requestNotificationRuntimePermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(this, arrayOf("android.permission.POST_NOTIFICATIONS"), 7201)
    }

    private fun section(title: String) {
        content.addView(TextView(this).apply {
            text = title
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(this@BrowserSettingsActivity, R.color.dim))
            setPadding(dp(2), dp(18), dp(2), dp(8))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addValueRow(iconName: String, title: String, subtitle: String): TextView {
        val row = rowBase(iconName, title, subtitle)
        val value = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@BrowserSettingsActivity, R.color.dim))
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            maxLines = 1
        }
        row.addView(value, LinearLayout.LayoutParams(dp(104), dp(48)))
        val chevron = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            IconLoader.applyIcon(this, "chevron_down", R.color.dim)
        }
        row.addView(chevron, LinearLayout.LayoutParams(dp(20), dp(48)))
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
        return value
    }

    private fun addToggle(iconName: String, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = rowBase(iconName, title, subtitle)
        val toggle = MaterialSwitch(this).apply {
            isChecked = checked
            isClickable = false
            isFocusable = false
            contentDescription = title
            styleRowSwitch(this)
            setOnCheckedChangeListener { _, enabled -> onChange(enabled) }
        }
        row.addView(toggle, LinearLayout.LayoutParams(-2, dp(48)))
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    /** Matches the switch style used across the rest of the app (SettingsActivity, Profile, etc.). */
    private fun styleRowSwitch(switch: MaterialSwitch) {
        val checkedState = intArrayOf(android.R.attr.state_checked)
        val defaultState = intArrayOf()
        val accentColor = AccentColorManager.resolvedOr(this, R.color.pri)
        switch.thumbTintList = android.content.res.ColorStateList(
            arrayOf(checkedState, defaultState),
            intArrayOf(
                AccentColorManager.onPrimaryColor(this),
                ContextCompat.getColor(this, R.color.dim)
            )
        )
        switch.trackTintList = android.content.res.ColorStateList(
            arrayOf(checkedState, defaultState),
            intArrayOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(accentColor, 150),
                ContextCompat.getColor(this, R.color.card2)
            )
        )
    }

    private fun rowBase(iconName: String, title: String, subtitle: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            minimumHeight = dp(66)
            background = ContextCompat.getDrawable(this@BrowserSettingsActivity, R.drawable.bg_transparent_pressed)
            isClickable = true
            isFocusable = true
        }
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            IconLoader.applyIcon(this, iconName, R.color.iconTint)
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(42)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@BrowserSettingsActivity, R.color.text))
            maxLines = 1
        })
        labels.addView(TextView(this).apply {
            text = subtitle
            textSize = 11.5f
            setTextColor(ContextCompat.getColor(this@BrowserSettingsActivity, R.color.dim))
            maxLines = 2
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        return row
    }

    private fun TextView.parentRow(): LinearLayout = parent as LinearLayout

    private fun refreshValues() {
        searchEngineValue.text = BrowserPreferences.searchEngine(this)
        addressPositionValue.text = if (BrowserPreferences.addressBarPosition(this) == "bottom") "Em baixo" else "No topo"
        popupPositionValue.text = when (BrowserPreferences.popupPosition(this)) {
            "top" -> "No topo"
            "bottom" -> "Em baixo"
            else -> "Junto à barra"
        }
    }

    private fun showSearchEnginePicker(anchor: View) {
        val current = BrowserPreferences.searchEngine(this)
        NativePopupMenu.show(anchor, listOf(
            NativePopupMenu.Item(1, "Google", checked = current.equals("Google", true), iconResId = R.drawable.site_icon_google),
            NativePopupMenu.Item(2, "Bing", checked = current.equals("Bing", true), iconResId = R.drawable.site_icon_bing),
            NativePopupMenu.Item(3, "DuckDuckGo", checked = current.equals("DuckDuckGo", true), iconResId = R.drawable.site_icon_duckduckgo),
            NativePopupMenu.Item(4, "Brave Search", checked = current.equals("Brave", true), iconResId = R.drawable.asset_search_brave)
        ), placeAbove = false) { item ->
            BrowserPreferences.setSearchEngine(this, when (item.id) { 2 -> "Bing"; 3 -> "DuckDuckGo"; 4 -> "Brave"; else -> "Google" })
            refreshValues()
        }
    }

    private fun toggleAddressPosition() {
        BrowserPreferences.setAddressBarPosition(this, if (BrowserPreferences.addressBarPosition(this) == "bottom") "top" else "bottom")
        refreshValues()
    }

    private fun cyclePopupPosition() {
        val next = when (BrowserPreferences.popupPosition(this)) {
            "follow_address" -> "top"
            "top" -> "bottom"
            else -> "follow_address"
        }
        BrowserPreferences.setPopupPosition(this, next)
        refreshValues()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}