package com.appao

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class SettingsActivity : AppCompatActivity() {

    private data class Row(
        val id: Int,
        val png: String,
        val title: String
    )

    private val rows = listOf(
        Row(R.id.rowTheme, "light_bulb", "Tema"),
        Row(R.id.rowLang, "globe", "Idioma"),
        Row(R.id.rowNotif, "news_feed", "Notificações"),
        Row(R.id.rowAuto, "game_controller", "Reprodução automática"),
        Row(R.id.rowFeed, "grid", "Aparência do feed"),
        Row(R.id.rowRecent, "recent", "Histórico de leitura"),
        Row(R.id.rowLoc, "location", "Localização"),
        Row(R.id.rowOffline, "stack", "Guardados offline"),
        Row(R.id.rowPriv, "shield", "Privacidade e segurança"),
        Row(R.id.rowVerified, "verified", "Contas verificadas"),
        Row(R.id.rowClrHist, "clock", "Limpar histórico de pesquisas"),
        Row(R.id.rowClrLib, "bookmark", "Limpar guardados"),
        Row(R.id.rowStore, "store", "Loja"),
        Row(R.id.rowAbout, "heart_hands", "Sobre o app ao")
    )

    private lateinit var root: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        setContentView(
            R.layout.activity_settings
        )

        root = findViewById(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            view.updatePadding(
                top = bars.top,
                bottom = bars.bottom
            )
            insets
        }

        findViewById<View>(R.id.setBack)
            .setOnClickListener {
                finish()
            }

        renderRows()
        refreshValues()
        SystemBarHelper.sync(this)
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        refreshValues()
    }

    override fun onConfigurationChanged(
        newConfig: android.content.res.Configuration
    ) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        refreshValues()
    }

    private fun renderRows() {
        IconLoader.applySvg(
            findViewById(R.id.setBackIcon),
            "back",
            R.color.iconTint
        )

        rows.forEach { rowDef ->
            val row = findViewById<View>(rowDef.id)

            IconLoader.applyPng(
                row.findViewById(R.id.rowIcon),
                rowDef.png
            )

            row.findViewById<TextView>(
                R.id.rowTitle
            ).text = rowDef.title

            row.setOnClickListener {
                when (rowDef.id) {
                    R.id.rowTheme -> showThemePicker(row)
                    R.id.rowLang -> showLangPicker(row)
                    R.id.rowClrHist -> {
                        getSharedPreferences(
                            "appao",
                            MODE_PRIVATE
                        ).edit()
                            .remove("search_history")
                            .apply()
                        toast("Histórico limpo")
                    }
                    R.id.rowClrLib -> {
                        getSharedPreferences(
                            "appao",
                            MODE_PRIVATE
                        ).edit()
                            .remove("library_json")
                            .apply()
                        toast("Guardados limpos")
                    }
                    else -> toast(
                        "${rowDef.title} — em breve"
                    )
                }
            }
        }
    }

    private fun refreshValues() {
        root.setBackgroundColor(
            ContextCompat.getColor(
                this,
                R.color.bg
            )
        )

        val textColor = ContextCompat.getColor(
            this,
            R.color.text
        )
        val dimColor = ContextCompat.getColor(
            this,
            R.color.dim
        )

        findViewById<TextView>(R.id.setTitle)
            ?.setTextColor(textColor)

        rows.forEach { rowDef ->
            val row = findViewById<View>(
                rowDef.id
            )
            row.setBackgroundResource(
                R.drawable.bg_drawer_item
            )
            row.findViewById<TextView>(
                R.id.rowTitle
            )?.setTextColor(textColor)
            row.findViewById<TextView>(
                R.id.rowValue
            )?.setTextColor(dimColor)

            IconLoader.applyPng(
                row.findViewById(R.id.rowIcon),
                rowDef.png
            )
        }

        findViewById<View>(R.id.rowTheme)
            .findViewById<TextView>(R.id.rowValue)
            .apply {
                visibility = View.VISIBLE
                text = ThemeManager.label(
                    this@SettingsActivity
                )
            }

        findViewById<View>(R.id.rowLang)
            .findViewById<TextView>(R.id.rowValue)
            .apply {
                visibility = View.VISIBLE
                text = languageLabel()
            }
    }

    private fun showThemePicker(
        anchor: View
    ) {
        val options = listOf(
            "Escuro" to "dark",
            "Claro" to "light",
            "Sistema" to "system"
        )
        val current = ThemeManager.current(this)
        val checked = options.indexOfFirst { it.second == current }
            .coerceAtLeast(0)

        NativeM3Dialog.showSelection(
            this,
            "Tema",
            options.map { it.first },
            checked
        ) { index ->
            val value = options.getOrNull(index)?.second ?: return@showSelection
            selectTheme(value)
        }
    }

    private fun selectTheme(
        value: String
    ) {
        if (ThemeManager.current(this) == value) {
            refreshValues()
            return
        }

        ThemeManager.save(this, value)

        // uiMode is declared in the manifest's configChanges, so AppCompat
        // updates the resolved resources without destroying the Activity.
        AppCompatDelegate.setDefaultNightMode(
            ThemeManager.mode(this)
        )

        delegate.applyDayNight()
        refreshValues()
        SystemBarHelper.sync(this)
    }

    private fun showLangPicker(
        anchor: View
    ) {
        val current =
            getSharedPreferences("appao", MODE_PRIVATE)
                .getString("lang", "pt") ?: "pt"

        val options = listOf(
            "pt" to "Português",
            "en" to "English",
            "es" to "Español",
            "fr" to "Français",
            "de" to "Deutsch"
        )

        val checked = options.indexOfFirst { it.first == current }
            .coerceAtLeast(0)

        NativeM3Dialog.showSelection(
            this,
            "Idioma",
            options.map { it.second },
            checked
        ) { index ->
            val code = options.getOrNull(index)?.first ?: return@showSelection

            getSharedPreferences(
                "appao",
                MODE_PRIVATE
            )
                .edit()
                .putString("lang", code)
                .apply()

            refreshValues()
            toast(
                options.getOrNull(index)?.second ?: "Português"
            )
        }
    }

    private fun languageLabel(): String =
        when (
            getSharedPreferences(
                "appao",
                MODE_PRIVATE
            ).getString("lang", "pt") ?: "pt"
        ) {
            "en" -> "English"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            else -> "Português"
        }

    private fun toast(
        message: String
    ) {
        AppAoToast.show(
            this,
            message
        )
    }
}
