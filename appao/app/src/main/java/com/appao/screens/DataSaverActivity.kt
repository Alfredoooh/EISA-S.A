package com.appao

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class DataSaverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_data_saver)
        SystemBarHelper.sync(this)

        val root = findViewById<android.view.View>(R.id.dataSaverRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(top = bars.top, bottom = maxOf(bars.bottom, ime))
            insets
        }

        IconLoader.applySvg(findViewById(R.id.dataBackIcon), "nav_back", R.color.iconTint)
        findViewById<android.view.View>(R.id.dataBack).setOnClickListener { finish() }

        bindSwitch(R.id.dataSaverSwitch, "data_saving")
        bindSwitch(R.id.dataVideoSwitch, "data_disable_video")
        bindSwitch(R.id.dataImageSwitch, "data_reduce_images")
    }

    private fun bindSwitch(id: Int, key: String) {
        val switch = findViewById<MaterialSwitch>(id)
        val prefs = getSharedPreferences("appao", MODE_PRIVATE)
        switch.isChecked = prefs.getBoolean(key, false)
        switch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(key, checked).apply()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        IconLoader.applySvg(findViewById(R.id.dataBackIcon), "nav_back", R.color.iconTint)
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
    }
}
