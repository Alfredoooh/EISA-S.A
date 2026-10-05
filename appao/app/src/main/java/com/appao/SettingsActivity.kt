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
        Row(R.id.rowTheme, "theme", "Tema"),
        Row(R.id.rowLang, "language", "Idioma"),
        Row(R.id.rowData, "data_saver", "Poupança de dados"),
        Row(R.id.rowUsage, "activity", "Atividade de uso"),
        Row(R.id.rowLoc, "location", "Localização"),
        Row(R.id.rowAccount, "profile", "Definições de conta"),
        Row(R.id.rowTerms, "document", "Termos e políticas de uso")
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

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        refreshValues()
    }

    private fun renderRows() {
        IconLoader.applySvg(findViewById(R.id.setBackIcon), "back", R.color.iconTint)
        rows.forEach { rowDef ->
            val row = findViewById<View>(rowDef.id)
            IconLoader.applySvg(row.findViewById(R.id.rowIcon), rowDef.svg, R.color.iconTint)
            row.findViewById<TextView>(R.id.rowTitle).text = rowDef.title
            row.setOnClickListener {
                when (rowDef.id) {
                    R.id.rowTheme -> showThemePicker(row)
                    R.id.rowLang -> showLangPicker(row)
                    R.id.rowData -> toggleDataSaving()
                    R.id.rowUsage -> showUsage()
                    R.id.rowLoc -> openLocationSettings()
                    R.id.rowAccount -> startActivity(Intent(this, AuthActivity::class.java))
                    R.id.rowTerms -> showTerms()
                }
            }
        }
    }

    private fun refreshValues() {
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        val text = ContextCompat.getColor(this, R.color.text)
        val dim = ContextCompat.getColor(this, R.color.dim)
        findViewById<TextView>(R.id.setTitle).setTextColor(text)
        rows.forEach { rowDef ->
            val row = findViewById<View>(rowDef.id)
            row.findViewById<TextView>(R.id.rowTitle).setTextColor(text)
            row.findViewById<TextView>(R.id.rowValue).setTextColor(dim)
            IconLoader.applySvg(row.findViewById(R.id.rowIcon), rowDef.svg, R.color.iconTint)
        }
        findViewById<View>(R.id.rowTheme).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE; text = ThemeManager.label(this@SettingsActivity)
        }
        findViewById<View>(R.id.rowLang).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE; text = languageLabel()
        }
        findViewById<View>(R.id.rowData).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE; text = if (dataSavingEnabled()) "Ligada" else "Desligada"
        }
    }

    private fun showThemePicker(anchor: View) {
        val options = listOf("Escuro" to "dark", "Claro" to "light", "Sistema" to "system")
        val current = ThemeManager.current(this)
        HtmlStylePopup.show(anchor, options.map { (label, value) ->
            HtmlStylePopup.Item(label = label, iconName = "theme", checked = value == current, useSvg = true) {
                ThemeManager.applyImmediately(this, value)
            }
        })
    }

    private fun showLangPicker(anchor: View) {
        val options = listOf("pt" to "Português", "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch")
        val current = getSharedPreferences("appao", MODE_PRIVATE).getString("lang", "pt") ?: "pt"
        HtmlStylePopup.show(anchor, options.map { (code, label) ->
            HtmlStylePopup.Item(label = label, iconName = "language", checked = code == current, useSvg = true) {
                getSharedPreferences("appao", MODE_PRIVATE).edit().putString("lang", code).apply()
                refreshValues()
            AppAoToast.show(this, label)
            }
        })
    }

    private fun dataSavingEnabled(): Boolean =
        getSharedPreferences("appao", MODE_PRIVATE).getBoolean("data_saving", false)

    private fun toggleDataSaving() {
        val next = !dataSavingEnabled()
        getSharedPreferences("appao", MODE_PRIVATE).edit().putBoolean("data_saving", next).apply()
        refreshValues()
    }

    private fun showUsage() {
        NativeM3Dialog.content(this, "Atividade de uso", TextView(this).apply {
            text = "Atividade de uso da aplicação\n\nConsultas, leituras e interações recentes ficam nesta secção."
            setTextColor(ContextCompat.getColor(context, R.color.text))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(20), dp(4), dp(20), dp(12))
        }, negative = "Fechar")
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
        "en" -> "English"; "es" -> "Español"; "fr" -> "Français"; "de" -> "Deutsch"; else -> "Português"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
