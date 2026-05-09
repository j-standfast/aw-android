package net.activitywatch.android.watcher

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import net.activitywatch.android.RustInterface
import org.json.JSONObject
import org.threeten.bp.Duration
import org.threeten.bp.Instant

// Multi-browser URL watcher. The `browsers` table below is the source of
// truth for which apps are tracked; each entry produces its own
// `aw-watcher-android-web-<bucketSuffix>` bucket on aw-server-rust.
//
// Renamed from `ChromeWatcher` in QLI-621. The rename produces a new
// AccessibilityService ComponentName, which Android treats as a different
// service for permission purposes, so users upgrading from any pre-rename
// build must re-enable this service under Settings → Accessibility once
// after install. There is no Android API to migrate the grant.
class BrowserWatcher : AccessibilityService() {

    private val TAG = "BrowserWatcher"

    /**
     * Per-browser configuration. Each entry produces its own
     * `aw-watcher-android-web-<bucketSuffix>` bucket. Resource IDs are
     * Chromium-fork-flavoured (`<package>:id/url_bar`) for now; non-Chromium
     * browsers (Firefox/Fenix/Mull) need a different resource ID and are
     * out of scope for this initial Vanadium-focused slice — see QLI-556.
     */
    private data class BrowserConfig(
        val packageName: String,
        val urlBarResourceId: String,
        val bucketSuffix: String,
    ) {
        val bucketId: String get() = "aw-watcher-android-web-$bucketSuffix"
    }

    private val browsers = listOf(
        BrowserConfig(
            packageName = "com.android.chrome",
            urlBarResourceId = "com.android.chrome:id/url_bar",
            bucketSuffix = "chrome",
        ),
        BrowserConfig(
            packageName = "app.vanadium.browser",
            urlBarResourceId = "app.vanadium.browser:id/url_bar",
            bucketSuffix = "vanadium",
        ),
    )

    private val byPackage: Map<String, BrowserConfig> by lazy {
        browsers.associateBy { it.packageName }
    }

    private data class BrowserState(
        var lastUrlTimestamp: Instant? = null,
        var lastUrl: String? = null,
        var lastTitle: String? = null,
    )

    private val state: MutableMap<String, BrowserState> = mutableMapOf()

    private var ri: RustInterface? = null

    override fun onCreate() {
        ri = RustInterface(applicationContext)
        for (browser in browsers) {
            ri?.createBucketHelper(browser.bucketId, "web.tab.current")
            state[browser.packageName] = BrowserState()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {

        // TODO: This method is called very often, which might affect performance. Future optimizations needed.

        val pkg = event.packageName?.toString()
        val browser = pkg?.let { byPackage[it] }

        if (browser == null) {
            // Not a tracked browser. systemui events (and any other non-browser
            // foreground change) flush every browser's in-flight URL so the
            // last visit gets logged when the user navigates away.
            if (pkg == "com.android.systemui") {
                for (b in browsers) onUrl(b, null)
            }
            return
        }

        try {
            if (event.source != null) {
                // Get URL
                val urlBars = event.source!!.findAccessibilityNodeInfosByViewId(browser.urlBarResourceId)
                if (urlBars.any()) {
                    val newUrl = "http://" + urlBars[0].text.toString() // TODO: We can't access the URI scheme, so we assume HTTP.
                    onUrl(browser, newUrl)
                }

                // Get title
                val webView = findWebView(event.source!!)
                if (webView != null) {
                    val title = webView.text.toString()
                    val s = state.getValue(browser.packageName)
                    if (title != s.lastTitle) {
                        s.lastTitle = title
                        Log.i(TAG, "[${browser.bucketSuffix}] Title: ${s.lastTitle}")
                    }
                }
            }
        }
        catch(ex : Exception) {
            Log.e(TAG, ex.message!!)
        }
    }

    fun findWebView(info : AccessibilityNodeInfo) : AccessibilityNodeInfo? {
        if(info.className == "android.webkit.WebView" && info.text != null)
            return info

        for (i in 0 until info.childCount) {
            val child = info.getChild(i)
            val webView = findWebView(child)
            if (webView != null) {
                return webView
            }
            child?.recycle()
        }

        return null
    }

    private fun onUrl(browser: BrowserConfig, newUrl: String?) {
        val s = state.getValue(browser.packageName)
        if (newUrl != s.lastUrl) { // URL changed
            if (newUrl != null) {
                Log.i(TAG, "[${browser.bucketSuffix}] Url: $newUrl")
            }
            if (s.lastUrl != null) {
                // Log last URL and title as a completed browser event.
                // We wait for the event to complete (marked by a change in URL) to ensure that
                // we had a chance to receive the title of the page, which often only arrives after
                // the page loads completely and/or the user interacts with the page.
                logBrowserEvent(browser, s.lastUrl!!, s.lastTitle ?: "", s.lastUrlTimestamp!!)
            }

            s.lastUrlTimestamp = Instant.ofEpochMilli(System.currentTimeMillis())
            s.lastUrl = newUrl
            s.lastTitle = null
        }
    }

    private fun logBrowserEvent(browser: BrowserConfig, url: String, title: String, lastUrlTimestamp: Instant) {
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        val start = lastUrlTimestamp
        val end = now
        val duration = Duration.between(start, end)

        val data = JSONObject()
        data.put("url", url)
        data.put("title", title)
        // `audible` and `incognito` are always false — preserved from upstream
        // for schema parity with desktop aw-watcher-web. Real detection from an
        // AccessibilityService would require probing accessibility-tree markers
        // (incognito icon resource ID, "Incognito" in window content-description)
        // per browser. Skipped for now; downstream consumers should treat both
        // fields as unreliable.
        data.put("audible", false)
        data.put("incognito", false)

        ri?.heartbeatHelper(browser.bucketId, start, duration.seconds.toDouble(), data, 1.0)
    }

    override fun onInterrupt() {
        TODO("not implemented")
    }
}
