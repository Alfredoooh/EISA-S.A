package com.appao

import android.app.Activity
import android.view.ViewGroup
import android.webkit.WebView
import java.lang.ref.WeakReference

/** Keeps one live WebView per browser tab so tab selection preserves DOM, scroll position and back/forward state. */
object BrowserWebViewPool {
    private val views = LinkedHashMap<String, WebView>()
    private val owners = LinkedHashMap<String, WeakReference<Activity>>()

    @Synchronized fun get(tabId: String): WebView? = views[tabId]
    @Synchronized fun contains(tabId: String): Boolean = views.containsKey(tabId)

    @Synchronized fun put(tabId: String, view: WebView, owner: Activity) {
        views[tabId] = view
        owners[tabId] = WeakReference(owner)
    }

    @Synchronized fun setOwner(tabId: String, owner: Activity) {
        if (views.containsKey(tabId)) owners[tabId] = WeakReference(owner)
    }

    /** Moves an already-live WebView to a newly created tab id without resetting its state. */
    @Synchronized fun rekey(fromId: String, toId: String, view: WebView, owner: Activity) {
        if (fromId != toId) {
            if (views[fromId] === view) views.remove(fromId)
            owners.remove(fromId)
        }
        // If another stale view is registered under the destination key, detach it safely.
        val existing = views[toId]
        if (existing != null && existing !== view) {
            runCatching { (existing.parent as? ViewGroup)?.removeView(existing) }
            runCatching { existing.stopLoading(); existing.webViewClient = null; existing.webChromeClient = null; existing.destroy() }
        }
        views[toId] = view
        owners[toId] = WeakReference(owner)
    }

    @Synchronized fun isOwner(tabId: String, owner: Activity): Boolean = owners[tabId]?.get() === owner

    @Synchronized fun releaseOwner(tabId: String, owner: Activity) {
        if (isOwner(tabId, owner)) owners.remove(tabId)
    }

    @Synchronized fun removeAndDestroy(tabId: String) {
        owners.remove(tabId)
        val view = views.remove(tabId) ?: return
        runCatching { (view.parent as? ViewGroup)?.removeView(view) }
        runCatching { view.stopLoading(); view.webViewClient = null; view.webChromeClient = null; view.setDownloadListener(null); view.destroy() }
    }

    @Synchronized fun clearExcept(tabIdToKeep: String? = null) {
        views.keys.toList().filter { it != tabIdToKeep }.forEach(::removeAndDestroy)
    }
}
