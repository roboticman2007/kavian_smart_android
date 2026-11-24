package ir.kaviancopytrade.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var topProgress: ProgressBar
    private lateinit var splashOverlay: LinearLayout
    private lateinit var offlineOverlay: LinearLayout
    private lateinit var lockOverlay: LinearLayout
    private lateinit var offlineDesc: TextView
    private lateinit var btnRetry: Button
    private lateinit var btnUnlock: Button

    private val handler = Handler(Looper.getMainLooper())
    private var splashDismissed = false
    private var lastBackPressMs = 0L
    private var isUnlockedThisSession = false
    private var hasLoadError = false

    private val prefs by lazy { getSharedPreferences("kavian_app_prefs", Context.MODE_PRIVATE) }
    private val appHeaders = mapOf("X-Kavian-App" to "android-${BuildConfig.VERSION_NAME}")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            insets
        }

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        topProgress = findViewById(R.id.topProgress)
        splashOverlay = findViewById(R.id.splashOverlay)
        offlineOverlay = findViewById(R.id.offlineOverlay)
        lockOverlay = findViewById(R.id.lockOverlay)
        offlineDesc = findViewById(R.id.offlineDesc)
        btnRetry = findViewById(R.id.btnRetry)
        btnUnlock = findViewById(R.id.btnUnlock)

        swipeRefresh.setColorSchemeColors(Color.parseColor("#F0B90B"), Color.parseColor("#00D084"))
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.parseColor("#0F1727"))
        swipeRefresh.setOnRefreshListener {
            if (isOnline()) {
                offlineOverlay.visibility = View.GONE
                hasLoadError = false
                webView.reload()
            } else {
                swipeRefresh.isRefreshing = false
                showOfflineState(getString(R.string.offline_desc))
            }
        }
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }

        btnRetry.setOnClickListener {
            if (isOnline()) {
                offlineOverlay.visibility = View.GONE
                hasLoadError = false
                val cur = webView.url
                if (cur.isNullOrBlank() || cur == "about:blank") {
                    webView.loadUrl(BuildConfig.BASE_URL, appHeaders)
                } else {
                    webView.reload()
                }
            } else {
                Toast.makeText(this, getString(R.string.offline_title), Toast.LENGTH_SHORT).show()
            }
        }

        btnUnlock.setOnClickListener { promptBiometricUnlock() }

        setupWebView()
        setupBackNavigation()

        if (isOnline()) {
            webView.loadUrl(BuildConfig.BASE_URL, appHeaders)
        } else {
            dismissSplash()
            showOfflineState(getString(R.string.offline_desc))
        }

        // Safety fallback so splash never stays stuck
        handler.postDelayed({ dismissSplash() }, 5500L)

        if (isBiometricLockEnabled() && isBiometricAvailable()) {
            lockOverlay.visibility = View.VISIBLE
            promptBiometricUnlock()
        } else {
            isUnlockedThisSession = true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setBackgroundColor(Color.parseColor("#060911"))
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString = "${settings.userAgentString} KavianCopyTradeApp/${BuildConfig.VERSION_NAME}"

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(KavianJsBridge(), "KavianAndroid")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    topProgress.visibility = View.VISIBLE
                    topProgress.progress = newProgress
                } else {
                    topProgress.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: ""
                val host = uri.host?.lowercase() ?: ""
                val path = uri.path?.lowercase() ?: ""

                // Always route non-HTTP schemes (tg://, tel:, mailto:) externally
                if (scheme != "http" && scheme != "https") {
                    openExternal(uri)
                    return true
                }

                // Own domain check
                val isOwnHost = (host == BuildConfig.ALLOWED_HOST || host == "www.${BuildConfig.ALLOWED_HOST}")
                if (isOwnHost) {
                    // Strict User-Only Guard: block /admin routes completely inside the user app
                    if (path == "/admin" || path.startsWith("/admin/")) {
                        view?.loadUrl("${BuildConfig.BASE_URL}/dashboard", appHeaders)
                        return true
                    }
                    return false
                }

                // External links (TradingView, Telegram, Exchanges, etc.) open in native browser/app
                openExternal(uri)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hasLoadError = false
                val u = url ?: return
                val parsed = Uri.parse(u)
                val path = parsed.path?.lowercase() ?: ""
                val host = parsed.host?.lowercase() ?: ""
                if ((host == BuildConfig.ALLOWED_HOST || host == "www.${BuildConfig.ALLOWED_HOST}") &&
                    (path == "/admin" || path.startsWith("/admin/"))
                ) {
                    view?.stopLoading()
                    view?.loadUrl("${BuildConfig.BASE_URL}/dashboard", appHeaders)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                swipeRefresh.isRefreshing = false
                topProgress.visibility = View.GONE
                if (!hasLoadError) {
                    offlineOverlay.visibility = View.GONE
                }
                dismissSplash()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    hasLoadError = true
                    swipeRefresh.isRefreshing = false
                    dismissSplash()
                    showOfflineState(getString(R.string.offline_desc))
                }
            }
        }

        // Clean CSV / report file downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val req = DownloadManager.Request(Uri.parse(url))
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrEmpty()) req.addRequestHeader("Cookie", cookies)
                req.addRequestHeader("User-Agent", userAgent)
                val fname = URLUtil.guessFileName(url, contentDisposition, mimetype)
                req.setTitle(fname)
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fname)
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(req)
                Toast.makeText(this, "در حال دانلود فایل: $fname", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                openExternal(Uri.parse(url))
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (lockOverlay.visibility == View.VISIBLE) {
                    finish()
                    return
                }
                webView.evaluateJavascript(
                    "(function(){ return (typeof window.__kavianAppBack === 'function' && window.__kavianAppBack()) ? '1' : '0'; })()"
                ) { res ->
                    if (res == "\"1\"" || res == "1") return@evaluateJavascript
                    if (webView.canGoBack()) {
                        val curPath = Uri.parse(webView.url ?: "").path ?: ""
                        if (curPath == "/dashboard" || curPath == "/" || curPath == "/login") {
                            handleDoubleBackExit()
                        } else {
                            webView.goBack()
                        }
                    } else {
                        handleDoubleBackExit()
                    }
                }
            }
        })
    }

    private fun handleDoubleBackExit() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressMs < 2000L) {
            finish()
        } else {
            lastBackPressMs = now
            Toast.makeText(this, getString(R.string.exit_confirm), Toast.LENGTH_SHORT).show()
        }
    }

    private fun dismissSplash() {
        if (splashDismissed) return
        splashDismissed = true
        splashOverlay.animate()
            .alpha(0f)
            .setDuration(260L)
            .withEndAction { splashOverlay.visibility = View.GONE }
            .start()
    }

    private fun showOfflineState(message: String) {
        offlineDesc.text = message
        offlineOverlay.visibility = View.VISIBLE
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "برنامه‌ای برای باز کردن این لینک پیدا نشد.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isBiometricAvailable(): Boolean {
        val bm = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun isBiometricLockEnabled(): Boolean = prefs.getBoolean("biometric_lock", false)

    private fun promptBiometricUnlock() {
        if (!isBiometricAvailable()) {
            isUnlockedThisSession = true
            lockOverlay.visibility = View.GONE
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isUnlockedThisSession = true
                lockOverlay.visibility = View.GONE
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                lockOverlay.visibility = View.VISIBLE
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.bio_title))
            .setSubtitle(getString(R.string.bio_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    inner class KavianJsBridge {
        @JavascriptInterface
        fun getAppVersion(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun getVersionCode(): Int = BuildConfig.VERSION_CODE

        @JavascriptInterface
        fun isBiometricAvailable(): Boolean = this@MainActivity.isBiometricAvailable()

        @JavascriptInterface
        fun isBiometricEnabled(): Boolean = this@MainActivity.isBiometricLockEnabled()

        @JavascriptInterface
        fun setBiometricEnabled(enabled: Boolean): Boolean {
            if (enabled && !this@MainActivity.isBiometricAvailable()) {
                return false
            }
            prefs.edit().putBoolean("biometric_lock", enabled).apply()
            return enabled
        }

        @JavascriptInterface
        fun reloadApp() {
            runOnUiThread { webView.reload() }
        }
    }
}
