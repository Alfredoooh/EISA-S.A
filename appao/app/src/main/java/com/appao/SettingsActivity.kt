package com.appao

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        Row(R.id.rowOffline, "stack",          "Guardados offline"),
        Row(R.id.rowPriv,    "shield",         "Privacidade e segurança"),
        Row(R.id.rowVerified,"verified",       "Contas verificadas"),
        Row(R.id.rowClrHist, "clock",          "Limpar histórico de pesquisas"),
        Row(R.id.rowClrLib,  "bookmark",       "Limpar guardados"),
        Row(R.id.rowStore,   "store",           "Loja"),
        Row(R.id.rowAbout,   "heart_hands",    "Sobre o app ao")
    )

    private lateinit var root: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        root = findViewById(android.R.id.content)
        IconLoader.applySvg(findViewById(R.id.setBackIcon), "back", R.color.iconTint)
        findViewById<View>(R.id.setBack).setOnClickListener { finish() }

        for (r in rows) {
            val row = findViewById<View>(r.id)
            IconLoader.applyPng(row.findViewById(R.id.rowIcon), r.png)
            row.findViewById<TextView>(R.id.rowTitle).text = r.title
            row.setOnClickListener {
                when (r.title) {
                    "Tema" -> showThemePicker()
                    "Idioma" -> showLangPicker()
                    "Limpar histórico de pesquisas" -> snack("Histórico limpo")
                    "Limpar guardados" -> snack("Guardados limpos")
                    else -> snack("${r.title} — em breve")
                }
            }
        }

        findViewById<View>(R.id.rowTheme).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = ThemeManager.label(this@SettingsActivity)
        }
        findViewById<View>(R.id.rowLang).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = "Português"
        }
    }

    private fun showThemePicker() {
        val options = arrayOf("Escuro", "Claro", "Sistema")
        val current = ThemeManager.label(this)
        val checked = options.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle("Tema")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                val chosen = options[which]
                ThemeManager.applyFromLabel(this, chosen)
                findViewById<View>(R.id.rowTheme)
                    .findViewById<TextView>(R.id.rowValue).text = chosen
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showLangPicker() {
        val options = arrayOf("Português", "English", "Español", "Français", "Deutsch")
        MaterialAlertDialogBuilder(this)
            .setTitle("Idioma")
            .setItems(options) { _, which ->
                findViewById<View>(R.id.rowLang)
                    .findViewById<TextView>(R.id.rowValue).text = options[which]
                snack(options[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun snack(msg: String) {
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show()
    }
}
