package com.goosexgoose.llmit.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.goosexgoose.llmit.data.Const
import com.goosexgoose.llmit.data.OpenAiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenAiLoginActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var verifier: String = ""
    private var state: String = ""
    private var captured = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (v, challenge) = OpenAiProvider.generatePkce()
        verifier = v
        state = OpenAiProvider.generateState()
        val authUrl = OpenAiProvider.buildAuthUrl(challenge, state)
        Log.d("OpenAiLogin", "Auth URL: $authUrl")

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            }
        }

        val frame = FrameLayout(this).also { it.addView(webView) }
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                Log.d("OpenAiLogin", "shouldOverrideUrlLoading: $url")
                if (url.startsWith(Const.OPENAI_REDIRECT_URI)) {
                    if (captured) return true
                    captured = true
                    val parsed = Uri.parse(url)
                    val code = parsed.getQueryParameter("code")
                    val returnedState = parsed.getQueryParameter("state")
                    val error = parsed.getQueryParameter("error")
                    val errorDesc = parsed.getQueryParameter("error_description")
                    Log.d("OpenAiLogin", "Redirect captured: code=${code != null} state_ok=${returnedState == state} error=$error desc=$errorDesc")
                    if (code != null && returnedState == state) {
                        scope.launch {
                            val ok = OpenAiProvider.exchangeCode(this@OpenAiLoginActivity, code, verifier)
                            setResult(if (ok) RESULT_OK else RESULT_CANCELED)
                            finish()
                        }
                    } else {
                        Log.e("OpenAiLogin", "Auth failed: $error - $errorDesc")
                        android.widget.Toast.makeText(
                            this@OpenAiLoginActivity,
                            "Auth error: ${errorDesc ?: error ?: "no code returned"}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                    return true
                }
                return false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                Log.e("OpenAiLogin", "WebView error: ${error?.description} (${error?.errorCode}) url=${request?.url}")
                super.onReceivedError(view, request, error)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d("OpenAiLogin", "onPageFinished: $url")
                super.onPageFinished(view, url)
            }
        }

        Log.d("OpenAiLogin", "Loading auth URL: $authUrl")
        webView.loadUrl(authUrl)
    }
}
