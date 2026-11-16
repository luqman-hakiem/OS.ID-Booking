package id.orangesky.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.roundToInt

/**
 * Pembungkus ringan orangesky.id.
 *
 * Sengaja tanpa AppCompat/splash library supaya cold start dan ukuran APK
 * sedekat mungkin dengan "buka website langsung di browser".
 */
class MainActivity : ComponentActivity() {

    private val homeUrl = "https://orangesky.id/"
    private val externalHosts = listOf(
        "instagram.com", "wa.me", "api.whatsapp.com", "facebook.com", "youtube.com", "tiktok.com"
    )

    /** Tab bottom navigation, mengikuti menu di navbar orangesky.id. */
    private data class Tab(val label: String, val icon: Int, val url: String)

    private val tabs = listOf(
        Tab("Home", R.drawable.ic_nav_home, homeUrl),
        Tab("Voucher", R.drawable.ic_nav_voucher, "$homeUrl?menu=voucher"),
        Tab("Contact Us", R.drawable.ic_nav_contact, "$homeUrl?menu=contactUs"),
        // My Booking ada di dalam area akun; parameter dipakai untuk menandai tab aktif.
        Tab("My Booking", R.drawable.ic_nav_booking, "${homeUrl}login/?from=mybooking"),
        Tab("Account", R.drawable.ic_nav_account, "${homeUrl}login/")
    )

    private lateinit var webView: WebView
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var bottomBar: LinearLayout
    private var barVisible = true
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var rendererGone = false

    private val tabIcons = ArrayList<ImageView>(tabs.size)
    private val tabLabels = ArrayList<TextView>(tabs.size)
    private val tabItems = ArrayList<LinearLayout>(tabs.size)
    private var activeTab = 0

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            fileCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(false)

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            overScrollMode = WebView.OVER_SCROLL_NEVER
        }

        refresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(getColor(R.color.brand_gold))
            setProgressBackgroundColorSchemeColor(Color.WHITE)
            setOnRefreshListener { webView.reload() }
            // Tarik-untuk-refresh hanya saat halaman benar-benar di posisi paling atas.
            setOnChildScrollUpCallback { _, _ -> webView.canScrollVertically(-1) }
            addView(
                webView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        // WebView mengisi sisa layar, bottom navigation menempel di bawah.
        bottomBar = buildBottomBar()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                refresh,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            )
            addView(
                bottomBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(root)
        setActiveTab(0)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            // Ikut ukuran font sistem (Setelan > Tampilan > Ukuran font).
            textZoom = textZoomPercent(resources.configuration.fontScale)
            // Cubit untuk memperbesar/memperkecil, tanpa tombol +/- yang makan tempat.
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            // Tanpa autoplay: hemat data, CPU, dan baterai.
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = "$userAgentString OrangeSkyApp/1.2"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Jembatan kecil JavaScript -> UI: sembunyikan/tampilkan bottom bar saat scroll.
        webView.addJavascriptInterface(ScrollBridge(), "OrangeSkyNav")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: return false
                val host = uri.host.orEmpty()
                val external = (scheme != "http" && scheme != "https") ||
                    externalHosts.any { host == it || host.endsWith(".$it") }
                if (!external) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (e: ActivityNotFoundException) {
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                setActiveTab(activeIndex(url))
                // Halaman baru selalu mulai dengan bar terlihat.
                setBarVisible(true)
            }

            // Hentikan indikator lebih awal supaya halaman terasa muncul lebih cepat.
            override fun onPageCommitVisible(view: WebView, url: String?) {
                refresh.isRefreshing = false
                hideHeaderMenu(view)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                refresh.isRefreshing = false
                hideHeaderMenu(view)
                installScrollWatcher(view)
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    refresh.isRefreshing = false
                    view.loadDataWithBaseURL(
                        null,
                        """
                        <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                        <body style="font-family:sans-serif;text-align:center;padding:48px 24px;color:#333">
                        <h2 style="color:#F57C00">Tidak ada koneksi</h2>
                        <p>Periksa internet Anda, lalu coba lagi.</p>
                        <button onclick="location.href='$homeUrl'"
                          style="background:#F57C00;color:#fff;border:0;padding:12px 28px;border-radius:8px;font-size:16px">
                          Coba Lagi</button>
                        </body></html>
                        """.trimIndent(),
                        "text/html", "UTF-8", null
                    )
                }
            }

            // Renderer WebView bisa dimatikan sistem di HP memori kecil; buat ulang alih-alih crash.
            override fun onRenderProcessGone(
                view: WebView, detail: RenderProcessGoneDetail
            ): Boolean {
                rendererGone = true
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                recreate()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    fileChooser.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }

            // Link target="_blank" tetap dibuka di app ini, bukan diabaikan.
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val popup = WebView(this@MainActivity)
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        popupView: WebView, request: WebResourceRequest
                    ): Boolean {
                        webView.loadUrl(request.url.toString())
                        popupView.destroy()
                        return true
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }

        webView.setDownloadListener { url, _, _, _, _ ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: ActivityNotFoundException) {
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(homeUrl)
        }
    }

    /**
     * Bottom navigation ringan: LinearLayout + ikon vektor, tanpa library tambahan.
     */
    private fun buildBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_bottom_nav)
            elevation = dp(6).toFloat()
        }
        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)

        tabs.forEachIndexed { index, tab ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = tab.label
                setBackgroundResource(ripple.resourceId)
                setPadding(dp(2), dp(7), dp(2), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val icon = ImageView(this).apply {
                setImageResource(tab.icon)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val label = TextView(this).apply {
                text = tab.label
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(2) }
            }
            item.addView(icon)
            item.addView(label)
            item.setOnClickListener { onTabSelected(index) }

            tabIcons += icon
            tabLabels += label
            tabItems += item
            bar.addView(item)
        }
        return bar
    }

    private fun onTabSelected(index: Int) {
        val url = tabs[index].url
        // Sorot tab langsung: umpan balik instan walau halaman situs lambat.
        setActiveTab(index)
        if (index == 0 && isHomePage(webView.url)) {
            webView.scrollTo(0, 0)
        } else {
            webView.loadUrl(url)
        }
    }

    private fun isHomePage(url: String?): Boolean {
        val uri = url?.let { Uri.parse(it) } ?: return false
        return uri.host.orEmpty().endsWith("orangesky.id") &&
            uri.path.orEmpty().let { it.isEmpty() || it == "/" } &&
            uri.query.isNullOrEmpty()
    }

    /** Tab aktif ditentukan dari URL, supaya tetap benar setelah back/forward. */
    private fun activeIndex(url: String?): Int {
        val uri = url?.let { Uri.parse(it) } ?: return 0
        if (!uri.host.orEmpty().endsWith("orangesky.id")) return activeTab
        val menu = uri.getQueryParameter("menu")
        return when {
            // My Booking dan Account sama-sama di area /login/, dibedakan lewat parameter.
            uri.path.orEmpty().startsWith("/login") ->
                if (uri.getQueryParameter("from") == "mybooking") 3 else 4
            menu == "voucher" -> 1
            menu == "contactUs" -> 2
            else -> 0
        }
    }

    private fun setActiveTab(index: Int) {
        activeTab = index
        val gold = getColor(R.color.brand_gold)
        val muted = getColor(R.color.nav_inactive)
        val goldState = ColorStateList.valueOf(gold)
        val mutedState = ColorStateList.valueOf(muted)
        tabIcons.forEachIndexed { i, icon ->
            icon.imageTintList = if (i == index) goldState else mutedState
        }
        tabLabels.forEachIndexed { i, label ->
            label.setTextColor(if (i == index) gold else muted)
        }
        tabItems.forEachIndexed { i, item ->
            item.isSelected = i == index
        }
    }

    /**
     * Jembatan kecil JavaScript -> UI native untuk mengikuti arah scroll halaman.
     * Isinya hanya show/hide bar, jadi aman dipanggil dari halaman situs.
     */
    private inner class ScrollBridge {
        @JavascriptInterface
        fun hideBar() {
            runOnUiThread { setBarVisible(false) }
        }

        @JavascriptInterface
        fun showBar() {
            runOnUiThread { setBarVisible(true) }
        }
    }

    private fun installScrollWatcher(view: WebView) {
        view.evaluateJavascript(SCROLL_WATCH_JS, null)
    }

    /** Menu header situs dihapus karena navigasi sudah ada di bottom bar app. */
    private fun hideHeaderMenu(view: WebView) {
        view.evaluateJavascript(HIDE_HEADER_MENU_JS, null)
    }

    /** Bar hilang saat konten di-scroll ke bawah, muncul lagi saat di-scroll ke atas. */
    private fun setBarVisible(visible: Boolean) {
        if (visible == barVisible) return
        barVisible = visible
        bottomBar.animate().cancel()
        if (visible) {
            if (bottomBar.visibility != View.VISIBLE) {
                bottomBar.alpha = 0f
                bottomBar.visibility = View.VISIBLE
            }
            bottomBar.animate().alpha(1f).setDuration(140L).start()
        } else {
            bottomBar.animate().alpha(0f).setDuration(140L).withEndAction {
                if (!barVisible) bottomBar.visibility = View.GONE
            }.start()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    /**
     * Ukuran font sistem bisa berubah saat app berjalan. Layout WebView langsung
     * disesuaikan tanpa reload halaman (fontScale terdaftar di configChanges).
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::webView.isInitialized) {
            webView.settings.textZoom = textZoomPercent(newConfig.fontScale)
        }
    }

    private fun textZoomPercent(fontScale: Float): Int =
        (fontScale * 100).roundToInt().coerceIn(85, 200)

    override fun onSaveInstanceState(outState: Bundle) {
        if (!rendererGone) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        webView.onPause()
        // Hentikan timer/animasi saat app di belakang: hemat CPU & baterai.
        webView.pauseTimers()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.resumeTimers()
        webView.onResume()
    }

    override fun onDestroy() {
        if (!rendererGone) {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        /**
         * Dipasang setiap halaman selesai dimuat. Hanya membaca arah scroll
         * (ambang 10 px) lalu memanggil OrangeSkyNav; isi situs tidak diubah.
         */
        private const val SCROLL_WATCH_JS = """
            (function () {
              if (window.__oskyScrollWatch) { return; }
              var bridge = window.OrangeSkyNav;
              if (!bridge) { return; }
              window.__oskyScrollWatch = true;
              var lastY = window.pageYOffset || document.documentElement.scrollTop || 0;
              var hidden = false;
              var queued = false;
              function currentY() {
                return window.pageYOffset || document.documentElement.scrollTop
                  || document.body.scrollTop || 0;
              }
              function apply() {
                queued = false;
                var y = currentY();
                var dy = y - lastY;
                lastY = y;
                if (y <= 50) {
                  if (hidden) { hidden = false; bridge.showBar(); }
                } else if (dy > 10) {
                  if (!hidden) { hidden = true; bridge.hideBar(); }
                } else if (dy < -10) {
                  if (hidden) { hidden = false; bridge.showBar(); }
                }
              }
              window.addEventListener('scroll', function () {
                if (queued) { return; }
                queued = true;
                window.requestAnimationFrame(apply);
              }, { passive: true });
            })();
        """

        /**
         * Menu header situs (Home/Voucher/Contact/Currency/Login) disembunyikan karena
         * bottom navigation app sudah menggantikannya. Hanya menambah satu <style>,
         * HTML/CSS situs tidak diubah.
         */
        private const val HIDE_HEADER_MENU_JS = """
            (function () {
              var css = 'nav.navbar{display:none!important;}';
              function add() {
                if (document.getElementById('osky-hide-header')) { return; }
                var style = document.createElement('style');
                style.id = 'osky-hide-header';
                style.appendChild(document.createTextNode(css));
                (document.head || document.documentElement).appendChild(style);
              }
              if (document.head) { add(); return; }
              if (!document.documentElement) { return; }
              var obs = new MutationObserver(function () {
                if (document.head) { obs.disconnect(); add(); }
              });
              obs.observe(document.documentElement, { childList: true, subtree: true });
            })();
        """
    }
}
