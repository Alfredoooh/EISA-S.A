package com.appao.screens

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
import com.appao.BrowserPreferences
import com.appao.BrowsingHistoryStore
import com.appao.ConversationHistoryStore
import com.appao.NativeM3Dialog
import com.appao.R
import com.appao.SystemBarHelper
import com.appao.ui.IconLoader
import com.google.android.material.materialswitch.MaterialSwitch


/** History-specific controls, intentionally separate from global app settings. */
class HistorySettingsActivity : AppCompatActivity() {
    private lateinit var pagesSwitch: MaterialSwitch
    private lateinit var conversationsSwitch: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        SystemBarHelper.sync(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(20))
            setBackgroundColor(ContextCompat.getColor(this@HistorySettingsActivity, R.color.bg))
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = iconButton("arrow_left", "Voltar")
        back.setOnClickListener { finishWithMotion() }
        toolbar.addView(back)
        toolbar.addView(TextView(this).apply {
            text = "Definições do histórico"
            textSize = 19f
            setTextColor(ContextCompat.getColor(this@HistorySettingsActivity, R.color.text))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(toolbar)
        root.addView(TextView(this).apply {
            text = "PRIVACIDADE"
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@HistorySettingsActivity, R.color.dim))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(18), dp(4), dp(8))
        })

        pagesSwitch = MaterialSwitch(this).apply {
            text = "Guardar páginas visitadas"
            textSize = 14.5f
            setTextColor(ContextCompat.getColor(this@HistorySettingsActivity, R.color.text))
            isChecked = BrowserPreferences.savePageHistory(this@HistorySettingsActivity)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnCheckedChangeListener { _, checked -> BrowserPreferences.setSavePageHistory(this@HistorySettingsActivity, checked) }
        }
        conversationsSwitch = MaterialSwitch(this).apply {
            text = "Guardar pesquisas e conversas"
            textSize = 14.5f
            setTextColor(ContextCompat.getColor(this@HistorySettingsActivity, R.color.text))
            isChecked = BrowserPreferences.saveConversationHistory(this@HistorySettingsActivity)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnCheckedChangeListener { _, checked -> BrowserPreferences.setSaveConversationHistory(this@HistorySettingsActivity, checked) }
        }
        val switchGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@HistorySettingsActivity, R.drawable.bg_settings_single)
            addView(pagesSwitch, LinearLayout.LayoutParams(-1, -2))
            addView(View(this@HistorySettingsActivity).apply { setBackgroundColor(ContextCompat.getColor(this@HistorySettingsActivity, R.color.line)) }, LinearLayout.LayoutParams(-1, dp(1)))
            addView(conversationsSwitch, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(switchGroup, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        root.addView(TextView(this).apply {
            text = "GERIR DADOS"
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@HistorySettingsActivity, R.color.dim))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(6), dp(4), dp(8))
        })
        root.addView(actionRow("Limpar páginas visitadas", "history") { confirmClearPages() }, rowParams())
        root.addView(actionRow("Limpar conversas", "chat_history") { confirmClearConversations() }, rowParams())
        root.addView(actionRow("Limpar páginas, conversas e dados guardados", "delete_bin") { confirmClearAll() }, rowParams())
        setContentView(root)
    }

    private fun actionRow(title: String, iconName: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@HistorySettingsActivity, R.drawable.bg_settings_single)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        row.addView(ImageView(this).apply { IconLoader.applyIcon(this, iconName, R.color.iconTint) }, LinearLayout.LayoutParams(dp(22), dp(22)))
        row.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@HistorySettingsActivity, R.color.text))
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun confirmClearPages() {
        NativeM3Dialog.confirm(this, "Limpar páginas", "Apagar todas as páginas visitadas guardadas?") { ok -> if (ok) { BrowsingHistoryStore.clear(this); AppAoToast.show(this, "Histórico de páginas limpo") } }
    }
    private fun confirmClearConversations() {
        NativeM3Dialog.confirm(this, "Limpar conversas", "Apagar o histórico de pesquisas e conversas?") { ok -> if (ok) { ConversationHistoryStore.clear(this); AppAoToast.show(this, "Histórico de conversas limpo") } }
    }
    private fun confirmClearAll() {
        NativeM3Dialog.confirm(this, "Limpar histórico", "Apagar páginas visitadas, pesquisas e cookies/cache das próximas sessões?") { ok ->
            if (ok) {
                BrowsingHistoryStore.clear(this)
                ConversationHistoryStore.clear(this)
                BrowserTabStore.clear(this)
                runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null); android.webkit.CookieManager.getInstance().flush() }
                runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }
                runCatching { android.webkit.WebView(this).apply { clearCache(true); destroy() } }
                AppAoToast.show(this, "Histórico e dados guardados limpos")
            }
        }
    }
    private fun iconButton(name: String, label: String): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        background = ContextCompat.getDrawable(this@HistorySettingsActivity, R.drawable.bg_transparent_pressed)
        contentDescription = label
        isClickable = true
        isFocusable = true
        addView(ImageView(this@HistorySettingsActivity).apply { IconLoader.applyIcon(this, name, R.color.iconTint) }, LinearLayout.LayoutParams(dp(21), dp(21)))
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(46))
    }
    private fun rowParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(2) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun finishWithMotion() { finish(); overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit) }
}
