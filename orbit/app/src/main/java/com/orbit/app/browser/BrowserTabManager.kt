package com.orbit.app.browser

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView
import com.orbit.app.model.BrowserTab

/** Keeps each tab's native WebView alive while another tab is selected. */
class BrowserTabManager(
    private val webViewHost: FrameLayout,
    private val webViewFactory: (BrowserTab) -> WebView,
    private val onActiveTabChanged: (BrowserTab?) -> Unit,
) {
    private val items = mutableListOf<BrowserTab>()
    private var nextId = 1L

    val tabs: List<BrowserTab> get() = items
    var activeIndex: Int = -1
        private set
    val activeTab: BrowserTab? get() = items.getOrNull(activeIndex)
    val size: Int get() = items.size

    fun createTab(activate: Boolean = true): BrowserTab {
        val tab = BrowserTab(nextId++)
        val webView = webViewFactory(tab)
        tab.webView = webView
        webView.visibility = View.INVISIBLE
        webViewHost.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        items.add(tab)
        if (activate || activeIndex < 0) {
            switchTo(items.lastIndex)
        }
        return tab
    }

    fun switchTo(index: Int): BrowserTab? {
        val target = items.getOrNull(index) ?: return null
        if (activeIndex == index && target.webView.visibility == View.VISIBLE) {
            onActiveTabChanged(target)
            return target
        }
        activeTab?.let { old ->
            if (old !== target) {
                old.webView.onPause()
                old.webView.visibility = View.INVISIBLE
            }
        }
        activeIndex = index
        target.webView.visibility = View.VISIBLE
        target.webView.onResume()
        target.webView.requestLayout()
        target.webView.invalidate()
        onActiveTabChanged(target)
        return target
    }

    /** Returns the closed tab. The caller decides whether an empty tab should be created. */
    fun closeTab(index: Int): BrowserTab? {
        val closing = items.getOrNull(index) ?: return null
        val wasActive = index == activeIndex
        items.removeAt(index)
        try {
            closing.webView.stopLoading()
        } catch (_: Throwable) {
        }
        (closing.webView.parent as? ViewGroup)?.removeView(closing.webView)
        try {
            closing.webView.removeAllViews()
            closing.webView.destroy()
        } catch (_: Throwable) {
        }

        when {
            items.isEmpty() -> activeIndex = -1
            index < activeIndex -> activeIndex--
            wasActive -> activeIndex = minOf(index, items.lastIndex)
        }

        val active = activeTab
        items.forEachIndexed { itemIndex, tab ->
            val isCurrent = itemIndex == activeIndex
            tab.webView.visibility = if (isCurrent) View.VISIBLE else View.INVISIBLE
            if (isCurrent) tab.webView.onResume() else tab.webView.onPause()
        }
        onActiveTabChanged(active)
        return closing
    }

    /** Replaces a renderer-dead WebView without disturbing the other tabs. */
    fun replaceWebView(tab: BrowserTab, oldView: WebView, newView: WebView) {
        if (items.none { it === tab }) {
            try { newView.destroy() } catch (_: Throwable) {}
            return
        }
        (oldView.parent as? ViewGroup)?.removeView(oldView)
        tab.webView = newView
        try {
            oldView.stopLoading()
            oldView.removeAllViews()
            oldView.destroy()
        } catch (_: Throwable) {
            // The renderer may already be gone; the dead view is still discarded.
        }
        val current = items.indexOf(tab) == activeIndex
        newView.visibility = if (current) View.VISIBLE else View.INVISIBLE
        webViewHost.addView(
            newView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        if (current) {
            newView.onResume()
            onActiveTabChanged(tab)
        }
    }

    fun dispose() {
        val snapshot = items.toList()
        items.clear()
        activeIndex = -1
        snapshot.forEach { tab ->
            try { tab.webView.stopLoading() } catch (_: Throwable) {}
            (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
            try {
                tab.webView.removeAllViews()
                tab.webView.destroy()
            } catch (_: Throwable) {}
        }
    }
}
