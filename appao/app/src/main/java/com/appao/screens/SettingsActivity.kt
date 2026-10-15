package com.appao

import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class SettingsActivity : AppCompatActivity() {

    private data class Row(val id: Int, val icon: String, val title: String)

    private val rows = listOf(
        Row(R.id.rowTheme, "contrast", "Tema"),
        Row(R.id.rowAccent, "", "Cor de destaque"),
        Row(R.id.rowLang, "language", "Idioma"),
        Row(R.id.rowData, "chart_pie", "Poupança de dados"),
        Row(R.id.rowAdBlock, "shield_check", "Bloqueador de anúncios"),
        Row(R.id.rowSiteDark, "moon", "Forçar tema escuro em sites"),
        Row(R.id.rowDownloads, "download", "Downloads"),
        Row(R.id.rowUsage, "chart_bars", "Atividade de uso"),
        Row(R.id.rowLoc, "location", "Localização"),
        Row(R.id.rowAccount, "profile", "Definições de conta"),
        Row(R.id.rowTerms, "terms", "Termos e políticas de uso")
    )

    private lateinit var root: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_settings)
        root = findViewById(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(bottom = ime)
            insets
        }
        findViewById<View>(R.id.setBack).setOnClickListener { finish() }
        renderRows()
        refreshValues()
        SystemBarHelper.sync(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        refreshValues()
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        refreshValues()
    }

    private fun renderRows() {
        IconLoader.applyIcon(findViewById(R.id.setBackIcon), "arrow_left", R.color.iconTint)
        findViewById<View>(R.id.setBack).background = ContextCompat.getDrawable(this, R.drawable.bg_circle_card)
        applyRowGroup(listOf(R.id.rowTheme, R.id.rowAccent, R.id.rowLang, R.id.rowData))
        applyRowGroup(listOf(R.id.rowAdBlock, R.id.rowSiteDark, R.id.rowDownloads))
        applyRowGroup(listOf(R.id.rowUsage, R.id.rowLoc))
        applyRowGroup(listOf(R.id.rowAccount, R.id.rowTerms))
        rows.forEach { rowDef ->
            val row = findViewById<View>(rowDef.id)
            if (rowDef.id == R.id.rowAccent) {
                styleAccentSwatch(row.findViewById(R.id.rowIcon))
            } else {
                IconLoader.applyIconThin(row.findViewById(R.id.rowIcon), rowDef.icon, R.color.iconTint)
            }
            row.findViewById<TextView>(R.id.rowTitle).text = rowDef.title
            row.setOnClickListener {
                when (rowDef.id) {
                    R.id.rowTheme -> showThemePicker(row)
                    R.id.rowAccent -> showAccentPicker(row)
                    R.id.rowLang -> showLangPicker(row)
                    R.id.rowData -> openDataSaver()
                    R.id.rowAdBlock -> {
                        val enabled = !BrowserPreferences.adBlockEnabled(this)
                        BrowserPreferences.setAdBlockEnabled(this, enabled)
                        refreshValues()
                        AppAoToast.show(this, if (enabled) "Bloqueador de anúncios ativado" else "Bloqueador de anúncios desativado")
                    }
                    R.id.rowSiteDark -> {
                        val enabled = !BrowserPreferences.forceDarkEnabled(this)
                        BrowserPreferences.setForceDarkEnabled(this, enabled)
                        refreshValues()
                        AppAoToast.show(this, if (enabled) "Tema escuro de sites ativado" else "Tema escuro de sites desativado")
                    }
                    R.id.rowDownloads -> {
                        startActivity(Intent(this, BrowserDownloadsActivity::class.java))
                        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
                    }
                    R.id.rowUsage -> openUsage()
                    R.id.rowLoc -> openLocationSettings()
                    R.id.rowAccount -> startActivity(Intent(this, AuthActivity::class.java))
                    R.id.rowTerms -> showTerms()
                }
            }
        }
    }

    private fun applyRowGroup(ids: List<Int>) {
        ids.forEachIndexed { index, id ->
            val row = findViewById<View>(id)
            val drawable = when {
                ids.size == 1 -> R.drawable.bg_settings_single
                index == 0 -> R.drawable.bg_settings_top
                index == ids.lastIndex -> R.drawable.bg_settings_bottom
                else -> R.drawable.bg_settings_middle
            }
            row.background = ContextCompat.getDrawable(this, drawable)
            val params = row.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            params?.let {
                it.topMargin = dp(1)
                it.bottomMargin = dp(1)
                row.layoutParams = it
            }
        }
    }

    private fun refreshValues() {
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        val textColor = ContextCompat.getColor(this, R.color.text)
        val dim = ContextCompat.getColor(this, R.color.dim)
        findViewById<TextView>(R.id.setTitle).setTextColor(textColor)
        rows.forEach { rowDef ->
            val row = findViewById<View>(rowDef.id)
            row.findViewById<TextView>(R.id.rowTitle).setTextColor(textColor)
            row.findViewById<TextView>(R.id.rowValue).setTextColor(dim)
            if (rowDef.id == R.id.rowAccent) styleAccentSwatch(row.findViewById(R.id.rowIcon))
            else IconLoader.applyIconThin(row.findViewById(R.id.rowIcon), rowDef.icon, R.color.iconTint)
        }
        findViewById<View>(R.id.rowTheme).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = ThemeManager.label(this@SettingsActivity)
        }
        findViewById<View>(R.id.rowAccent).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = AccentColorManager.label(this@SettingsActivity)
        }
        findViewById<View>(R.id.rowLang).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = languageLabel()
        }
        findViewById<View>(R.id.rowData).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = if (dataSavingEnabled()) "Ligada" else "Desligada"
        }
        findViewById<View>(R.id.rowAdBlock).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = if (BrowserPreferences.adBlockEnabled(this@SettingsActivity)) "Ligado" else "Desligado"
        }
        findViewById<View>(R.id.rowSiteDark).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = if (BrowserPreferences.forceDarkEnabled(this@SettingsActivity)) "Ligado" else "Desligado"
        }
        findViewById<View>(R.id.rowDownloads).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = "Abrir"
        }
    }

    private fun styleAccentSwatch(icon: android.widget.ImageView) {
        val swatch = AccentColorManager.resolvedColor(this)
            ?: ContextCompat.getColor(this, R.color.pri)
        icon.setImageDrawable(null)
        icon.clearColorFilter()
        icon.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(swatch)
        }
    }

    private fun showThemePicker(anchor: View) {
        val current = ThemeManager.current(this)
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Escuro", checked = current == "dark"),
                NativePopupMenu.Item(2, "Claro", checked = current == "light"),
                NativePopupMenu.Item(3, "Sistema", checked = current == "system")
            ),
            alignLeft = false,
            placeAbove = false
        ) { item ->
            val value = when (item.id) {
                1 -> "dark"
                2 -> "light"
                else -> "system"
            }
            if (value != current) {
                ThemeManager.applyImmediately(this, value)
            } else {
                refreshValues()
                SystemBarHelper.sync(this)
            }
        }
    }

    private fun showAccentPicker(anchor: View) {
        val current = AccentColorManager.current(this)
        val menuItems = buildList {
            add(
                NativePopupMenu.Item(
                    id = 0,
                    title = "Padrão",
                    iconColor = AccentColorManager.resolvedOr(this@SettingsActivity, R.color.pri),
                    checked = current == AccentColorManager.NONE
                )
            )
            AccentColorManager.options.forEach { option ->
                add(
                    NativePopupMenu.Item(
                        id = option.id + 1,
                        title = option.label,
                        iconColor = option.color,
                        checked = current == option.id
                    )
                )
            }
            if (current == AccentColorManager.CUSTOM) {
                add(NativePopupMenu.Item(
                    id = 998,
                    title = "Cor personalizada",
                    iconColor = AccentColorManager.resolvedColor(this@SettingsActivity),
                    checked = true
                ))
            }
            add(NativePopupMenu.Item(id = 999, title = "Criar cor"))
        }

        NativePopupMenu.show(
            anchor,
            menuItems,
            alignLeft = false,
            placeAbove = false
        ) { item ->
            when {
                item.id == 0 -> {
                    AccentColorManager.save(this, AccentColorManager.NONE)
                    refreshValues()
                    AppAoToast.show(this, "Cor padrão aplicada")
                }
                item.id == 999 || item.id == 998 -> showCustomAccentDialog()
                else -> {
                    val option = AccentColorManager.options.firstOrNull { it.id == item.id - 1 }
                    if (option != null) {
                        AccentColorManager.save(this, option.id)
                        refreshValues()
                        AppAoToast.show(this, option.label)
                    }
                }
            }
        }
    }

    private fun showCustomAccentDialog() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density + .5f).toInt()
        val initial = AccentColorManager.resolvedColor(this) ?: android.graphics.Color.rgb(15, 108, 189)
        var red = android.graphics.Color.red(initial)
        var green = android.graphics.Color.green(initial)
        var blue = android.graphics.Color.blue(initial)

        val content = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        val preview = android.view.View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(android.graphics.Color.rgb(red, green, blue))
            }
        }
        content.addView(preview, android.widget.LinearLayout.LayoutParams(-1, dp(52)).apply {
            bottomMargin = dp(12)
        })
        val hexLabel = android.widget.TextView(this).apply {
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(androidx.core.content.ContextCompat.getColor(this@SettingsActivity, R.color.text))
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
        }
        content.addView(hexLabel, android.widget.LinearLayout.LayoutParams(-1, dp(28)))
        fun syncPreview() {
            val color = android.graphics.Color.rgb(red, green, blue)
            preview.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(color)
            }
            hexLabel.text = String.format(java.util.Locale.US, "#%02X%02X%02X", red, green, blue)
        }
        fun addChannel(labelText: String, get: () -> Int, set: (Int) -> Unit) {
            content.addView(android.widget.TextView(this).apply {
                text = labelText
                textSize = 13f
                setTextColor(androidx.core.content.ContextCompat.getColor(this@SettingsActivity, R.color.dim))
                setPadding(dp(2), dp(6), dp(2), 0)
            })
            val seek = android.widget.SeekBar(this).apply { max = 255; progress = get() }
            seek.progressTintList = android.content.res.ColorStateList.valueOf(AccentColorManager.resolvedOr(this@SettingsActivity, R.color.pri))
            seek.thumbTintList = android.content.res.ColorStateList.valueOf(AccentColorManager.resolvedOr(this@SettingsActivity, R.color.pri))
            seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                    set(value); syncPreview()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            })
            content.addView(seek, android.widget.LinearLayout.LayoutParams(-1, dp(38)))
        }
        addChannel("Vermelho", { red }, { red = it })
        addChannel("Verde", { green }, { green = it })
        addChannel("Azul", { blue }, { blue = it })
        syncPreview()
        NativeM3Dialog.content(
            this,
            "Criar cor de destaque",
            content,
            onPositive = {
                AccentColorManager.saveCustom(this, android.graphics.Color.rgb(red, green, blue))
                refreshValues()
                AppAoToast.show(this, "Cor personalizada aplicada")
            },
            positive = "Aplicar",
            negative = "Cancelar"
        )
    }

    private fun showLangPicker(anchor: View) {
        val current = getSharedPreferences("appao", MODE_PRIVATE).getString("lang", "pt") ?: "pt"
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Português", checked = current == "pt"),
                NativePopupMenu.Item(2, "English", checked = current == "en"),
                NativePopupMenu.Item(3, "Español", checked = current == "es"),
                NativePopupMenu.Item(4, "Français", checked = current == "fr"),
                NativePopupMenu.Item(5, "Deutsch", checked = current == "de")
            ),
            alignLeft = false,
            placeAbove = false
        ) { item ->
            val code = when (item.id) {
                1 -> "pt"
                2 -> "en"
                3 -> "es"
                4 -> "fr"
                else -> "de"
            }
            if (code != current) {
                getSharedPreferences("appao", MODE_PRIVATE).edit().putString("lang", code).apply()
                refreshValues()
                AppAoToast.show(this, languageLabel())
            }
        }
    }

    private fun dataSavingEnabled(): Boolean =
        getSharedPreferences("appao", MODE_PRIVATE).getBoolean("data_saving", false)

    private fun openDataSaver() {
        startActivity(Intent(this, DataSaverActivity::class.java))
    }

    private fun openUsage() {
        startActivity(Intent(this, UsageActivity::class.java))
    }

    private fun openLocationSettings() {
        runCatching { startActivity(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            .onFailure { AppAoToast.show(this, "Não foi possível abrir a localização") }
    }

    private fun showTerms() {
        NativeM3Dialog.content(this, "Termos e políticas de uso", TextView(this).apply {
            text = "Consulte aqui os termos de uso, a política de privacidade e as regras de utilização da aplicação."
            setTextColor(ContextCompat.getColor(context, R.color.text))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(20), dp(4), dp(20), dp(12))
        }, negative = "Fechar")
    }

    private fun languageLabel(): String = when (
        getSharedPreferences("appao", MODE_PRIVATE).getString("lang", "pt") ?: "pt"
    ) {
        "en" -> "English"
        "es" -> "Español"
        "fr" -> "Français"
        "de" -> "Deutsch"
        else -> "Português"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
