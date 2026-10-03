package com.appao

import android.view.View
import android.widget.PopupWindow

object AppsPopup {
    data class App(val name: String, val png: String, val url: String?)

    val APPS = listOf(
        App("IA",          "magic_wand",       "apps/ai.html"),
        App("Loja",        "store",            "apps/store.html"),
        App("Notícias",    "news_feed",        "apps/news.html"),
        App("Jogos",       "game_controller",  "apps/games.html"),
        App("Verificados", "verified",         "apps/verified.html"),
        App("Guardados",   "stack",            "apps/saved.html")
    )

    fun show(anchor: View, onPick: (App) -> Unit): PopupWindow {
        return HtmlStylePopup.show(
            anchor,
            APPS.map { app ->
                HtmlStylePopup.Item(app.name, app.png) { onPick(app) }
            },
            placeAbove = true
        )
    }
}
