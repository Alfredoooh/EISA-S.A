package com.appao

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class SettingsActivity : AppCompatActivity() {

    private data class Row(val id: Int, val png: String, val title: String)

    private val rows = listOf(
        Row(R.id.rowTheme,   "light_bulb",      "Tema"),
        Row(R.id.rowLang,    "globe",           "Idioma"),
        Row(R.id.rowNotif,   "news_feed",       "Notificações"),
        Row(R.id.rowAuto,    "game_controller", "Reprodução automática"),
        Row(R.id.rowFeed,    "grid",            "Aparência do feed"),
        Row(R.id.rowRecent,  "recent",          "Histórico de leitura"),
        Row(R.id.rowLoc,     "location",        "Localização"),
        Row(R.id.rowOffline, "stack",           "Guardados offline"),
        Row(R.id.rowPriv,    "shield",          "Privacidade e segurança"),
        Row(R.id.rowVerified,"verified",        "Contas verificadas"),
        Row(R.id.rowClrHist, "clock",           "Limpar histórico de pesquisas"),
        Row(R.id.rowClrLib,  "bookmark",        "Limpar guardados"),
        Row(R.id.rowStore,   "store",            "Loja"),
        Row(R.id.rowAbout,   "heart_hands",     "Sobre o app ao")
    )

    private lateinit var root: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        SystemBarHelper.sync(this)

        root = findViewById(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        IconLoader.applySvg(findViewById(R.id.setBackIcon), "back", R.color.iconTint)
        findViewById<View>(R.id.setBack).setOnClickListener { finish() }

        rows.forEach { r ->
            val row = findViewById<View>(r.id)
            IconLoader.applyPng(row.findViewById(R.id.rowIcon), r.png)
            row.findViewById<TextView>(R.id.rowTitle).text = r.title
            row.setOnClickListener {
                when (r.id) {
                    R.id.rowTheme -> showThemePicker(row)
                    R.id.rowLang -> showLangPicker(row)
                    R.id.rowClrHist -> toast("Histórico limpo")
                    R.id.rowClrLib -> toast("Guardados limpos")
                    else -> toast("${r.title} — em breve")
                }
            }
        }
        refreshValues()
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        refreshValues()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        window.decorView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg))
        refreshValues()
    }

    private fun refreshValues() {
        val rootView = findViewById<View>(android.R.id.content)
        rootView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg))
        val scrollRoot = (findViewById<android.view.View>(android.R.id.content) as? android.view.ViewGroup)
        fun recolorTree(v: android.view.View) {
            when (v) {
                is TextView -> {
                    val text = v.text?.toString().orEmpty()
                    v.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            this, if (text == text.uppercase() && text.isNotBlank()) R.color.dim else R.color.text
                        )
                    )
                }
                is android.view.ViewGroup -> for (i in 0 until v.childCount) recolorTree(v.getChildAt(i))
            }
        }
        scrollRoot?.let(::recolorTree)
        rows.forEach { rowDef ->
            val row = findViewById<View>(rowDef.id)
            row.findViewById<TextView>(R.id.rowTitle)?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text))
            row.findViewById<TextView>(R.id.rowValue)?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.dim))
            IconLoader.applyPng(row.findViewById(R.id.rowIcon), rowDef.png)
        }
        findViewById<View>(R.id.rowTheme).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = ThemeManager.label(this@SettingsActivity)
        }
        findViewById<View>(R.id.rowLang).findViewById<TextView>(R.id.rowValue).apply {
            visibility = View.VISIBLE
            text = languageLabel()
        }
    }

    private fun showThemePicker(anchor: View) {
        val current = ThemeManager.current(this)
        HtmlStylePopup.show(
            anchor,
            listOf(
                HtmlStylePopup.Item("Escuro", "moon", current == "dark") {
                    applyThemeImmediately("Escuro")
                },
                HtmlStylePopup.Item("Claro", "sun", current == "light") {
                    applyThemeImmediately("Claro")
                },
                HtmlStylePopup.Item("Sistema", "phone", current == "system") {
                    applyThemeImmediately("Sistema")
                }
            )
        )
    }

    private fun applyThemeImmediately(label: String) {
        ThemeManager.applyFromLabel(this, label)
        SystemBarHelper.sync(this)
        findViewById<View>(R.id.rowTheme).findViewById<TextView>(R.id.rowValue).text = label
    }

    private fun showLangPicker(anchor: View) {
        val current = (getSharedPreferences("appao", MODE_PRIVATE).getString("lang", "pt") ?: "pt")
        val options = listOf(
            "pt" to "Português",
            "en" to "English",
            "es" to "Español",
            "fr" to "Français",
            "de" to "Deutsch"
        )
        HtmlStylePopup.show(anchor, options.map { (code, label) ->
            HtmlStylePopup.Item(label, "language", current == code) {
                getSharedPreferences("appao", MODE_PRIVATE).edit().putString("lang", code).apply()
                findViewById<View>(R.id.rowLang).findViewById<TextView>(R.id.rowValue).text = label
                toast(label)
            }
        })
    }

    private fun languageLabel(): String {
        return when (getSharedPreferences("appao", MODE_PRIVATE).getString("lang", "pt") ?: "pt") {
            "en" -> "English"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            else -> "Português"
        }
    }

    private fun toast(message: String) {
        AppAoToast.show(this, message)
    }
}
