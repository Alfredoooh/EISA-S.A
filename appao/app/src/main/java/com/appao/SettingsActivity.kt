package com.appao

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

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
        Row(R.id.rowStore,   "store",          "Loja"),
        Row(R.id.rowAbout,   "heart_hands",    "Sobre o app ao")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        IconLoader.applySvg(findViewById(R.id.setBackIcon), "back", R.color.iconTint)
        findViewById<View>(R.id.setBack).setOnClickListener { finish() }

        for (r in rows) {
            val row = findViewById<View>(r.id)
            IconLoader.applyPng(row.findViewById(R.id.rowIcon), r.png)
            row.findViewById<TextView>(R.id.rowTitle).text = r.title
            row.setOnClickListener {
                when (r.title) {
                    "Tema" -> showThemePicker()
                    "Idioma" -> toast("Idioma")
                    "Limpar histórico de pesquisas" -> toast("Histórico limpo")
                    "Limpar guardados" -> toast("Guardados limpos")
                    else -> toast("${r.title} — em breve")
                }
            }
        }

        findViewById<View>(R.id.rowTheme).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = currentThemeLabel()
        }
        findViewById<View>(R.id.rowLang).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = "Português"
        }
    }

    private fun currentThemeLabel(): String {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> "Escuro"
            AppCompatDelegate.MODE_NIGHT_NO -> "Claro"
            else -> "Sistema"
        }
    }

    private fun showThemePicker() {
        val options = arrayOf("Escuro", "Claro", "Sistema")
        AlertDialog.Builder(this, R.style.AppAo_AlertDialog)
            .setTitle("Tema")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
                findViewById<View>(R.id.rowTheme).findViewById<TextView>(R.id.rowValue).text = options[which]
            }
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
