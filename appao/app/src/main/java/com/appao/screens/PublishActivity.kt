package com.appao

import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PublishActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publish)
        SystemBarHelper.sync(this)

        IconLoader.applySvg(findViewById(R.id.publishBackIcon), "nav_back", R.color.iconTint)

        findViewById<View>(R.id.publishBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
        }
        findViewById<View>(R.id.publishAction).setOnClickListener {
            val text = findViewById<EditText>(R.id.publishText).text.toString().trim()
            AppAoToast.show(this, if (text.isBlank()) "Escreve alguma coisa antes de publicar" else "Publicação preparada")
        }
    }

    override fun onBackPressed() {
        finish()
        overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
    }
}
