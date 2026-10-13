package com.appao

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.google.android.material.materialswitch.MaterialSwitch

class DataSaverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_data_saver)
        SystemBarHelper.sync(this)

        IconLoader.applySvg(findViewById(R.id.dataBackIcon), "arrow_left", R.color.iconTint)
        findViewById<android.view.View>(R.id.dataBack).setOnClickListener { finish() }

        bindSwitch(R.id.dataSaverSwitch, "data_saving")
        bindSwitch(R.id.dataVideoSwitch, "data_disable_video")
        bindSwitch(R.id.dataImageSwitch, "data_reduce_images")
        refreshSwitchColors()
    }

    private fun bindSwitch(id: Int, key: String) {
        val switch = findViewById<MaterialSwitch>(id)
        val prefs = getSharedPreferences("appao", MODE_PRIVATE)
        switch.isChecked = prefs.getBoolean(key, false)
        switch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(key, checked).apply()
        }
    }

    private fun refreshSwitchColors() {
        val checked = intArrayOf(android.R.attr.state_checked)
        val unchecked = intArrayOf(-android.R.attr.state_checked)
        val primary = AccentColorManager.resolvedOr(this, R.color.pri)
        val onPrimary = AccentColorManager.onPrimaryColor(this)
        val offThumb = ContextCompat.getColor(this, R.color.dim)
        val offTrack = ContextCompat.getColor(this, R.color.card2)
        val onTrack = ColorUtils.setAlphaComponent(primary, 150)
        listOf(R.id.dataSaverSwitch, R.id.dataVideoSwitch, R.id.dataImageSwitch).forEach { id ->
            findViewById<MaterialSwitch>(id).apply {
                thumbTintList = ColorStateList(arrayOf(checked, unchecked), intArrayOf(onPrimary, offThumb))
                trackTintList = ColorStateList(arrayOf(checked, unchecked), intArrayOf(onTrack, offTrack))
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        IconLoader.applySvg(findViewById(R.id.dataBackIcon), "arrow_left", R.color.iconTint)
        refreshSwitchColors()
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        refreshSwitchColors()
    }
}
