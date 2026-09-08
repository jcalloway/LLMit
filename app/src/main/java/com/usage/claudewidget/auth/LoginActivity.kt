package com.usage.claudewidget.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.usage.claudewidget.data.Const
import com.usage.claudewidget.data.CookieHarvester
import com.usage.claudewidget.data.Storage
import com.usage.claudewidget.widget.updateAllWidgets
import com.usage.claudewidget.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * One-time interactive login. Loads claude.ai in a WebView; once a sessionKey cookie
 * appears, harvests cookies + the WebView's User-Agent, then finishes.
 */
class LoginActivity : Activity() {

    private lateinit var webView: WebView
    private var popupWebView: WebView? = null
    private val storage by lazy { Storage.get(this) }
    private var captured = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)

        webView = WebView(this).apply {
            cm.setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Google's account-chooser / consent screen opens as a JS popup (window.open);
            // without multi-window support the popup is silently dropped and the page
            // underneath is left blank.
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true

            // Google's Sign-In JS (the "Continue with Google" button) runs in THIS main frame,
            // not only the popup, and refuses to initialize under Android WebView's "; wv" UA
            // marker — the button hangs on "Loading…". So strip "; wv" here as well.
            // NOTE: this is NOT the cause of the slow-fetch issue. That slowness persisted under
            // both the stripped and the honest "; wv" UA, so it's a separate problem (being
            // diagnosed via the timed "Test fetch now" readout), not a UA/cf_clearance mismatch.
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            // Persist the exact UA so cf_clearance stays valid for headless fetches.
            storage.userAgent = settings.userAgentString

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    tryCapture()
                }
            }
            webChromeClient = object : WebChromeClient() {
                // Handles window.open() popups (Google's account chooser / consent screen).
                // The popup runs as a second WebView in the same app, so it shares the same
                // CookieManager/session as the main WebView — cookies it sets are still
                // visible to tryCapture() once control returns to claude.ai.
                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message,
                ): Boolean {
                    val popup = WebView(this@LoginActivity).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Inherit the main WebView's (already "; wv"-stripped) UA so Google's
                        // OAuth popup presents consistently as a normal browser.
                        settings.userAgentString = webView.settings.userAgentString
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(v: WebView, url: String) {
                                tryCapture()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onCloseWindow(window: WebView) {
                                val wrapper = window.parent as? ViewGroup
                                (wrapper?.parent as? ViewGroup)?.removeView(wrapper)
                                if (window === popupWebView) popupWebView = null
                                window.destroy()
                            }
                        }
                    }
                    popupWebView = popup
                    val popupWrapper = FrameLayout(this@LoginActivity).apply { addView(popup) }
                    applyBarInsetPadding(popupWrapper)
                    addContentView(
                        popupWrapper,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    (resultMsg.obj as WebView.WebViewTransport).webView = popup
                    resultMsg.sendToTarget()
                    return true
                }
            }
            loadUrl(Const.LOGIN_URL)
        }
        // Android 15+ forces edge-to-edge for apps targeting SDK 35+; targeting SDK 36 (this
        // app) additionally drops the windowOptOutEdgeToEdgeEnforcement theme escape hatch, so
        // there's no way to just opt back into the old "system reserves the status bar" layout.
        // Instead pad a wrapper FrameLayout for the status/nav bar insets — padding the WebView
        // itself directly was unreliable (its Chromium compositor doesn't consistently respect
        // View.setPadding()), so keep the WebView's own bounds untouched and let the wrapper
        // absorb the inset instead.
        val root = FrameLayout(this).apply { addView(webView) }
        applyBarInsetPadding(root)
        setContentView(root)
    }

    private fun applyBarInsetPadding(view: android.view.View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun tryCapture() {
        if (captured) return
        val cookies = CookieManager.getInstance().getCookie(Const.BASE) ?: return
        if (!cookies.contains("${Const.COOKIE_SESSION}=")) return

        captured = true
        CookieManager.getInstance().flush()
        CookieHarvester.harvest(storage)

        // Discover org + first snapshot, then schedule periodic refresh.
        CoroutineScope(Dispatchers.Main).launch {
            RefreshScheduler.ensurePeriodic(this@LoginActivity)
            RefreshScheduler.refreshNow(this@LoginActivity)
            updateAllWidgets(this@LoginActivity)
            Toast.makeText(this@LoginActivity, "Signed in", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onDestroy() {
        popupWebView?.destroy()
        popupWebView = null
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
