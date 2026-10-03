package com.appao

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class SettingsActivity : AppCompatActivity() {

    private data class Row(val id: Int, val png: String, val title: String)

    private val rows = listOf(
        Row(R.id.rowTheme,   "light_bulb",     "Tema"),
        Row(R.id.rowLang,    "globe",          "Idioma"),
        Row(R.id.rowNotif,   "news_feed",      "Notificações"),
        Row(R.id.rowAuto,    "game_controller","Reprodução automática"),
        Row(R.id.rowFeed,    "grid",           "Aparência do feed"),
        Row(R.id.rowRecent,  "recent",         "Histórico de leitura"),
        Row(R.id.rowLoc,     "location",       "Localização"),
        Row(R.id.rowOffline, "stack",           "Guardados offline"),
        Row(R.id.rowPriv,    "shield",         "Privacidade e segurança"),
        Row(R.id.rowVerified,"verified",       "Contas verificadas"),
        Row(R.id.rowClrHist, "clock",          "Limpar histórico de pesquisas"),
        Row(R.id.rowClrLib,  "bookmark",       "Limpar guardados"),
        Row(R.id.rowStore,   "store",           "Loja"),
        Row(R.id.rowAbout,   "heart_hands",     "Sobre o app ao")
    )

    private lateinit var root: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.syncSystemBars(this)
        setContentView(R.layout.activity_settings)

        bindScreen()
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.syncSystemBars(this)
        refreshValues()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeManager.syncSystemBars(this)
        setContentView(R.layout.activity_settings)
        bindScreen()
    }

    private fun bindScreen() {
        root = findViewById(android.R.id.content)
        IconLoader.applySvg(findViewById(R.id.setBackIcon), "back", R.color.iconTint)
        findViewById<View>(R.id.setBack).setOnClickListener { finish() }
        rows.forEach { rowDef ->
            val row = findViewById<View>(rowDef.id) ?: return@forEach
            IconLoader.applyPng(row.findViewById(R.id.rowIcon), rowDef.png)
            row.findViewById<TextView>(R.id.rowTitle).text = rowDef.title
            row.setOnClickListener {
                when (rowDef.title) {
                    "Tema" -> showThemePicker(row)
                    "Idioma" -> showLangPicker(row)
                    "Limpar histórico de pesquisas" -> snack("Histórico limpo")
                    "Limpar guardados" -> snack("Guardados limpos")
                    else -> snack("${rowDef.title} — em breve")
                }
            }
        }
        refreshValues()
    }

    private fun bindIcons() {
        IconLoader.applySvg(findViewById(R.id.setBackIcon), "back", R.color.iconTint)
        rows.forEach { rowDef ->
            val row = findViewById<View>(rowDef.id) ?: return@forEach
            IconLoader.applyPng(row.findViewById(R.id.rowIcon), rowDef.png)
        }
    }

    private fun refreshValues() {
        findViewById<View>(R.id.rowTheme).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = ThemeManager.label(this@SettingsActivity)
        }
        findViewById<View>(R.id.rowLang).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = langLabel()
        }
    }

    private fun showThemePicker(anchor: View) {
        val current = ThemeManager.label(this)
        val options = listOf(
            NativePopup.Choice("Escuro", "moon", current == "Escuro"),
            NativePopup.Choice("Claro", "sun", current == "Claro"),
            NativePopup.Choice("Sistema", "phone", current == "Sistema")
        )
        NativePopup.showChoice(anchor, null, options) { index ->
            val value = when (index) {
                0 -> "Escuro"
                1 -> "Claro"
                else -> "Sistema"
            }
            ThemeManager.applyFromLabel(this, value)
            setContentView(R.layout.activity_settings)
            bindScreen()
        }
    }

    private fun showLangPicker(anchor: View) {
        val current = langLabel()
        val labels = listOf("Português", "English", "Español", "Français", "Deutsch")
        val icons = listOf("language", "language", "language", "language", "language")
        NativePopup.showChoice(
            anchor, null, labels.mapIndexed { index, label ->
                NativePopup.Choice(label, icons[index], label == current)
            }
        ) { index ->
            val prefs = getSharedPreferences("appao", MODE_PRIVATE)
            prefs.edit().putString("lang", listOf("pt", "en", "es", "fr", "de")[index]).apply()
            refreshValues()
            snack(labels[index])
        }
    }

    private fun langLabel(): String {
        return when (getSharedPreferences("appao", MODE_PRIVATE).getString("lang", "pt")) {
            "en" -> "English"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            else -> "Português"
        }
    }

    private fun snack(msg: String) {
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show()
    }
}
