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

    private data class Row(val id: Int, val svg: String, val title: String)

    private val rows = listOf(
        Row(R.id.rowTheme, "settings_theme", "Tema"),
        Row(R.id.rowLang, "settings_language", "Idioma"),
        Row(R.id.rowData, "settings_data_saver", "Poupança de dados"),
        Row(R.id.rowUsage, "settings_usage", "Atividade de uso"),
        Row(R.id.rowLoc, "settings_location", "Localização"),
        Row(R.id.rowAccount, "settings_account", "Definições de conta"),
        Row(R.id.rowTerms, "settings_terms", "Termos e políticas de uso")
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
        IconLoader.applySvg(findViewById(R.id.setBackIcon), "nav_back", R.color.iconTint)
        findViewById<View>(R.id.setBack).background = ContextCompat.getDrawable(this, R.drawable.bg_circle_card)
        applyRowGroup(listOf(R.id.rowTheme, R.id.rowLang, R.id.rowData))
        applyRowGroup(listOf(R.id.rowUsage, R.id.rowLoc))
        applyRowGroup(listOf(R.id.rowAccount, R.id.rowTerms))
        rows.forEach { rowDef ->
            val row = findViewById<View>(rowDef.id)
            IconLoader.applySvgThin(row.findViewById(R.id.rowIcon), rowDef.svg, R.color.iconTint)
            row.findViewById<TextView>(R.id.rowTitle).text = rowDef.title
            row.setOnClickListener {
                when (rowDef.id) {
                    R.id.rowTheme -> showThemePicker(row)
                    R.id.rowLang -> showLangPicker(row)
                    R.id.rowData -> openDataSaver()
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
                it.topMargin = dp(2)
                it.bottomMargin = dp(2)
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
            IconLoader.applySvgThin(row.findViewById(R.id.rowIcon), rowDef.svg, R.color.iconTint)
        }
        findViewById<View>(R.id.rowTheme).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = ThemeManager.label(this@SettingsActivity)
        }
        findViewById<View>(R.id.rowLang).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = languageLabel()
        }
        findViewById<View>(R.id.rowData).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = if (dataSavingEnabled()) "Ligada" else "Desligada"
        }
    }

    private fun showThemePicker(anchor: View) {
        val current = ThemeManager.current(this)
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Escuro", svg = "theme_dark"),
                NativePopupMenu.Item(2, "Claro", svg = "theme_light"),
                NativePopupMenu.Item(3, "Sistema", svg = "theme_system")
            )
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

    private fun showLangPicker(anchor: View) {
        val current = getSharedPreferences("appao", MODE_PRIVATE).getString("lang", "pt") ?: "pt"
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Português", png = "flags/pt"),
                NativePopupMenu.Item(2, "English", png = "flags/en"),
                NativePopupMenu.Item(3, "Español", png = "flags/es"),
                NativePopupMenu.Item(4, "Français", png = "flags/fr"),
                NativePopupMenu.Item(5, "Deutsch", png = "flags/de")
            )
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
