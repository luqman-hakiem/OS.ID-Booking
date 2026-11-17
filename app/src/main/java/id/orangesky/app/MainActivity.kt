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
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        Tab("Beranda", R.drawable.ic_nav_home, homeUrl),
        Tab("Voucher", R.drawable.ic_nav_voucher, "$homeUrl?menu=voucher"),
        Tab("Bantuan", R.drawable.ic_nav_contact, "$homeUrl?menu=contactUs"),
        // Langsung ke My Booking (tab Upcoming aktif); tamu diarahkan ke login oleh situs.
        Tab("Booking", R.drawable.ic_nav_booking, "$homeUrl?menu=member&t=myBooking"),
        Tab("Akun", R.drawable.ic_nav_account, "$homeUrl?menu=member")
    )

    private lateinit var webView: WebView
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var statusSpacer: View
    private lateinit var progress: ProgressBar
    private lateinit var loadingCard: LinearLayout
    private var barVisible = true
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var rendererGone = false
    private var loadingTab = -1
    private var overlayShown = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Indikator tengah hanya muncul kalau halaman memang lambat (>= 300 ms). */
    private val showLoadingOverlay = Runnable { showOverlay() }

    private val tabIcons = ArrayList<ImageView>(tabs.size)
    private val tabLabels = ArrayList<TextView>(tabs.size)
    private val tabItems = ArrayList<LinearLayout>(tabs.size)
    private val tabPills = ArrayList<View>(tabs.size)
    private val tabSpinners = ArrayList<ProgressBar>(tabs.size)
    private var activeTab = 0

    /** Posisi scroll terakhir per tab, dipulihkan saat tab dibuka lagi. */
    private val tabScroll = IntArray(tabs.size)
    private var pendingRestoreTab = -1
    /** Posisi scroll halaman yang sedang tampil, dilaporkan oleh JS. */
    private var liveScrollY = 0

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
            setColorSchemeColors(getColor(R.color.brand_gold_text))
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

        // Garis progres tipis di bawah status bar: umpan balik saat halaman dimuat.
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            val gold = ColorStateList.valueOf(getColor(R.color.brand_gold_text))
            progressTintList = gold
            indeterminateTintList = gold
            visibility = View.INVISIBLE
        }

        // Kartu "Memuat" di tengah layar; hanya tampil kalau pemuatan memang lambat.
        val loadingSpinner = ProgressBar(this).apply {
            val gold = ColorStateList.valueOf(getColor(R.color.brand_gold_text))
            indeterminateTintList = gold
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
        }
        val loadingText = TextView(this).apply {
            text = "Memuat halaman…"
            textSize = 12f
            setTextColor(getColor(R.color.nav_inactive))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        loadingCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_loading_card)
            elevation = dp(6).toFloat()
            setPadding(dp(24), dp(18), dp(24), dp(18))
            visibility = View.GONE
            alpha = 0f
            addView(loadingSpinner)
            addView(loadingText)
        }

        // WebView mengisi layar; bottom navigation melayang DI ATAS konten supaya
        // area di sekitar pill memperlihatkan halaman, bukan pita putih.
        bottomBar = buildBottomBar()
        statusSpacer = View(this).apply {
            setBackgroundColor(getColor(R.color.ic_launcher_background))
        }
        val content = FrameLayout(this).apply {
            addView(
                refresh,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                loadingCard,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            addView(
                bottomBar,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                    bottomMargin = dp(8)
                }
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(
                statusSpacer,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0
                )
            )
            addView(
                progress,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(2)
                )
            )
            addView(
                content,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            )
        }
        setContentView(root)

        // targetSdk 35+ mewajibkan edge-to-edge: status bar navy dan navigation bar
        // putih digambar sendiri, ukurannya mengikuti insets perangkat.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val spacerParams = statusSpacer.layoutParams
            if (spacerParams.height != bars.top) {
                spacerParams.height = bars.top
                statusSpacer.layoutParams = spacerParams
            }
            // Kiri/kanan untuk notch saat landscape; bawah untuk keyboard.
            view.setPadding(bars.left, 0, bars.right, if (ime.bottom > bars.bottom) ime.bottom else 0)
            // Pill bottom bar melayang 8dp di atas navigation bar / keyboard.
            val barParams = bottomBar.layoutParams as FrameLayout.LayoutParams
            val wanted = bars.bottom + dp(8)
            if (barParams.bottomMargin != wanted) {
                barParams.bottomMargin = wanted
                bottomBar.layoutParams = barParams
            }
            insets
        }
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
            userAgentString = "$userAgentString OrangeSkyApp/1.6.3"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        // Jaga proses renderer tetap hidup supaya pindah tab tidak perlu start ulang.
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)

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
                val index = activeIndex(url)
                setActiveTab(index)
                setTabLoading(index)
                // Halaman baru selalu mulai dengan bar terlihat.
                setBarVisible(true)
                progress.progress = 5
                progress.visibility = View.VISIBLE
                liveScrollY = 0
                // Indikator tengah menunggu 300 ms supaya halaman cepat tidak berkedip.
                mainHandler.removeCallbacks(showLoadingOverlay)
                mainHandler.postDelayed(showLoadingOverlay, 300L)
            }

            // Hentikan indikator lebih awal supaya halaman terasa muncul lebih cepat.
            override fun onPageCommitVisible(view: WebView, url: String?) {
                refresh.isRefreshing = false
                progress.visibility = View.INVISIBLE
                clearTabLoading()
                hideOverlay()
                // Gaya + penundaan gambar di awal paint: header situs tidak berkedip
                // dan gambar di bawah layar tidak ikut diunduh.
                injectPageStyle(view)
                injectImageDefer(view)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                refresh.isRefreshing = false
                progress.visibility = View.INVISIBLE
                clearTabLoading()
                hideOverlay()
                injectPageStyle(view)
                injectImageDefer(view)
                injectPageEnhancements(view)
                installScrollWatcher(view)
                restoreScrollIfNeeded(view, url)
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    refresh.isRefreshing = false
                    progress.visibility = View.INVISIBLE
                    clearTabLoading()
                    hideOverlay()
                    view.loadDataWithBaseURL(null, offlineHtml(), "text/html", "UTF-8", null)
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
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (progress.visibility == View.VISIBLE) progress.progress = newProgress
            }

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

        val startUrl = deepLinkUrl(intent) ?: homeUrl
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(startUrl)
        }
    }

    /**
     * Bottom navigation ringan: pill melayang, ikon vektor, tanpa library tambahan.
     * Indikator aktif berupa pill gold transparan di belakang ikon.
     */
    private fun buildBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_bottom_nav)
            elevation = dp(8).toFloat()
            setPadding(dp(6), dp(5), dp(6), dp(5))
            // Saat bar tersembunyi (alpha 0) ketuk area ini untuk memunculkannya lagi.
            setOnClickListener { setBarVisible(true) }
        }
        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)

        tabs.forEachIndexed { index, tab ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = tab.label
                setBackgroundResource(ripple.resourceId)
                setPadding(dp(2), dp(2), dp(2), dp(2))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val iconWrap = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(32))
            }
            val pill = View(this).apply {
                setBackgroundResource(R.drawable.bg_nav_pill)
                alpha = 0f
                visibility = View.INVISIBLE
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val icon = ImageView(this).apply {
                setImageResource(tab.icon)
                layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            // Spinner kecil di tab yang sedang memuat; ikon disembunyikan sementara.
            val spinner = ProgressBar(this).apply {
                indeterminateTintList = ColorStateList.valueOf(getColor(R.color.brand_gold_text))
                layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
                visibility = View.GONE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            iconWrap.addView(pill)
            iconWrap.addView(icon)
            iconWrap.addView(spinner)

            val label = TextView(this).apply {
                text = tab.label
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(3) }
            }
            item.addView(iconWrap)
            item.addView(label)
            item.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onTabSelected(index)
            }

            tabIcons += icon
            tabLabels += label
            tabItems += item
            tabPills += pill
            tabSpinners += spinner
            bar.addView(item)
        }
        return bar
    }

    private fun onTabSelected(index: Int) {
        val target = tabs[index]
        // Simpan posisi tab yang sedang dibuka sebelum berpindah.
        tabScroll[activeTab] = liveScrollY
        // Sorot tab langsung: umpan balik instan walau halaman situs lambat.
        setActiveTab(index)
        if (isSameTab(webView.url, target.url)) {
            // Tekan ulang tab yang sama: kembali ke atas, bukan reload.
            webView.evaluateJavascript(
                "window.scrollTo({top:0,behavior:'smooth'});", null
            )
            return
        }
        pendingRestoreTab = index
        // Umpan balik instan: spinner di tab yang dituju, indikator tengah bila lambat.
        setTabLoading(index)
        mainHandler.removeCallbacks(showLoadingOverlay)
        mainHandler.postDelayed(showLoadingOverlay, 300L)
        webView.loadUrl(target.url)
    }

    private fun isSameTab(current: String?, target: String): Boolean {
        val a = current?.let { Uri.parse(it) } ?: return false
        val b = Uri.parse(target)
        val hostA = a.host.orEmpty().removePrefix("www.")
        val hostB = b.host.orEmpty().removePrefix("www.")
        if (hostA != hostB) return false
        if (a.path.orEmpty().ifEmpty { "/" } != b.path.orEmpty().ifEmpty { "/" }) return false
        if (a.getQueryParameter("menu") != b.getQueryParameter("menu")) return false
        if (a.getQueryParameter("t") != b.getQueryParameter("t")) return false
        return a.getQueryParameter("from") == b.getQueryParameter("from")
    }

    private fun restoreScrollIfNeeded(view: WebView, url: String?) {
        val index = pendingRestoreTab
        if (index < 0) return
        pendingRestoreTab = -1
        if (!isSameTab(url, tabs[index].url)) return
        val y = tabScroll[index]
        if (y > 0) {
            liveScrollY = y
            view.evaluateJavascript(
                "setTimeout(function(){window.scrollTo(0,$y);},80);", null
            )
        }
    }

    /** Tab aktif ditentukan dari URL, supaya tetap benar setelah back/forward. */
    private fun activeIndex(url: String?): Int {
        val uri = url?.let { Uri.parse(it) } ?: return 0
        if (!uri.host.orEmpty().endsWith("orangesky.id")) return activeTab
        val menu = uri.getQueryParameter("menu")
        return when {
            // Booking dan Akun sama-sama area member, dibedakan lewat parameter t.
            menu == "member" ->
                if (uri.getQueryParameter("t") == "myBooking") 3 else 4
            uri.path.orEmpty().startsWith("/login") ->
                if (uri.getQueryParameter("from") == "mybooking") 3 else 4
            menu == "voucher" -> 1
            menu == "contactUs" -> 2
            else -> 0
        }
    }

    private fun setActiveTab(index: Int) {
        activeTab = index
        val gold = getColor(R.color.brand_gold_text)
        val muted = getColor(R.color.nav_inactive)
        val goldState = ColorStateList.valueOf(gold)
        val mutedState = ColorStateList.valueOf(muted)
        tabIcons.forEachIndexed { i, icon ->
            icon.imageTintList = if (i == index) goldState else mutedState
            val scale = if (i == index) 1.08f else 1f
            icon.animate().cancel()
            icon.animate().scaleX(scale).scaleY(scale).setDuration(180L).start()
        }
        tabLabels.forEachIndexed { i, label ->
            label.setTextColor(if (i == index) gold else muted)
        }
        tabItems.forEachIndexed { i, item ->
            item.isSelected = i == index
        }
        tabPills.forEachIndexed { i, pill ->
            val active = i == index
            pill.animate().cancel()
            if (active) {
                pill.visibility = View.VISIBLE
                pill.alpha = 0f
                pill.scaleX = 0.85f
                pill.scaleY = 0.85f
                pill.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(180L).start()
            } else {
                pill.animate().alpha(0f).setDuration(120L).withEndAction {
                    if (i != activeTab) pill.visibility = View.INVISIBLE
                }.start()
            }
        }
    }

    /**
     * Jembatan kecil JavaScript -> UI: arah scroll, show/hide bar, dan posisi
     * scroll terakhir. Isinya hanya angka & show/hide, aman dipanggil dari halaman situs.
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

        @JavascriptInterface
        fun saveScroll(y: Int) {
            liveScrollY = y
        }
    }

    /**
     * Dipasang di semua halaman orangesky.id: melaporkan posisi scroll agar bisa
     * dipulihkan saat tab dibuka lagi. Auto-hide bar hanya aktif di Beranda & Voucher.
     */
    private fun installScrollWatcher(view: WebView) {
        val uri = view.url?.let { Uri.parse(it) } ?: return
        if (!uri.host.orEmpty().endsWith("orangesky.id")) return
        val autoHide = allowsBarAutoHide(view.url)
        if (!autoHide) setBarVisible(true)
        view.evaluateJavascript(
            SCROLL_WATCH_JS.replace("__AUTOHIDE__", if (autoHide) "true" else "false"),
            null
        )
    }

    private fun allowsBarAutoHide(url: String?): Boolean {
        val uri = url?.let { Uri.parse(it) } ?: return false
        if (!uri.host.orEmpty().endsWith("orangesky.id")) return false
        val path = uri.path.orEmpty()
        if (path.isNotEmpty() && path != "/") return false
        val menu = uri.getQueryParameter("menu")
        return menu == null || menu == "voucher"
    }

    /** Gaya halaman: menu header & back-to-top disembunyikan, kontras & kartu dirapikan. */
    private fun injectPageStyle(view: WebView) {
        view.evaluateJavascript(PAGE_CSS_JS, null)
    }

    /**
     * Menunda gambar yang jauh di bawah layar. Halaman voucher mengunduh 59 gambar
     * (~11 MB) sekaligus; dengan ini hanya gambar yang terlihat yang diunduh.
     */
    private fun injectImageDefer(view: WebView) {
        view.evaluateJavascript(DEFER_IMAGES_JS, null)
    }

    /** Perbaikan konten: format harga/tanggal dan perbaikan daftar voucher. */
    private fun injectPageEnhancements(view: WebView) {
        view.evaluateJavascript(PAGE_ENHANCE_JS, null)
    }

    /** Spinner kecil menggantikan ikon tab yang sedang memuat. */
    private fun setTabLoading(index: Int) {
        if (index < 0 || index >= tabSpinners.size) return
        loadingTab = index
        tabSpinners.forEachIndexed { i, spinner ->
            spinner.visibility = if (i == index) View.VISIBLE else View.GONE
        }
        tabIcons.forEachIndexed { i, icon ->
            icon.alpha = if (i == index) 0f else 1f
        }
    }

    private fun clearTabLoading() {
        if (loadingTab < 0) return
        loadingTab = -1
        tabSpinners.forEach { it.visibility = View.GONE }
        tabIcons.forEach { it.alpha = 1f }
    }

    /** Indikator "Memuat halaman…" hanya ditampilkan bila pemuatan melewati 300 ms. */
    private fun showOverlay() {
        if (overlayShown || isFinishing) return
        overlayShown = true
        loadingCard.animate().cancel()
        loadingCard.visibility = View.VISIBLE
        loadingCard.alpha = 0f
        loadingCard.animate().alpha(1f).setDuration(120L).start()
    }

    private fun hideOverlay() {
        mainHandler.removeCallbacks(showLoadingOverlay)
        if (!overlayShown) {
            loadingCard.visibility = View.GONE
            return
        }
        overlayShown = false
        loadingCard.animate().cancel()
        loadingCard.animate().alpha(0f).setDuration(120L).withEndAction {
            if (!overlayShown) loadingCard.visibility = View.GONE
        }.start()
    }

    /** Bar hilang saat konten di-scroll ke bawah, muncul lagi saat di-scroll ke atas. */
    private fun setBarVisible(visible: Boolean) {
        if (visible == barVisible) return
        barVisible = visible
        bottomBar.animate().cancel()
        // Tetap VISIBLE (alpha 0) supaya area bar bisa diketuk untuk memunculkannya.
        bottomBar.animate().alpha(if (visible) 1f else 0f).setDuration(140L).start()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun deepLinkUrl(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "https") return null
        return if (data.host.orEmpty().endsWith("orangesky.id")) data.toString() else null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUrl(intent)?.let { webView.loadUrl(it) }
    }

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

    /** Halaman offline bernuansa brand, bukan oranye generik. */
    private fun offlineHtml(): String = """
        <!DOCTYPE html>
        <html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
          *{box-sizing:border-box}
          body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
               background:#28293B;color:#fff;text-align:center;padding:32px 24px;
               font-family:-apple-system,"Segoe UI",Roboto,Arial,sans-serif}
          .card{max-width:420px;width:100%}
          .brand{color:#C0A751;font-weight:700;letter-spacing:1.5px;font-size:15px;margin-bottom:18px}
          h2{font-size:22px;margin:0 0 10px}
          p{color:#C9CCD8;line-height:1.6;margin:0 0 26px;font-size:15px}
          a{display:block;text-decoration:none;border-radius:12px;padding:14px 20px;
            font-weight:600;font-size:15px;margin-bottom:10px}
          .primary{background:#C0A751;color:#28293B}
          .ghost{border:1px solid #4A4D63;color:#fff}
        </style></head>
        <body><div class="card">
          <div class="brand">ORANGESKY.ID</div>
          <h2>Tidak ada koneksi</h2>
          <p>Periksa internet Anda, lalu coba lagi.</p>
          <a class="primary" href="https://orangesky.id/">Coba lagi</a>
          <a class="ghost" href="https://wa.me/628970058002">Hubungi via WhatsApp</a>
        </div></body></html>
    """.trimIndent()

    companion object {
        /**
         * Dipasang setiap halaman selesai dimuat. Melaporkan posisi scroll ke app
         * (untuk dipulihkan saat tab dibuka lagi) dan, bila __AUTOHIDE__ aktif,
         * membaca arah scroll (ambang 10 px) untuk menyembunyikan bottom bar.
         */
        private const val SCROLL_WATCH_JS = """
            (function () {
              if (window.__oskyScrollWatch) { return; }
              var bridge = window.OrangeSkyNav;
              if (!bridge) { return; }
              window.__oskyScrollWatch = true;
              var autoHide = __AUTOHIDE__;
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
                bridge.saveScroll(Math.round(y));
                if (!autoHide) { return; }
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
         * Menunda unduhan gambar yang jauh di bawah layar. Halaman voucher memuat
         * 59 gambar (~11 MB) sekaligus; dengan ini hanya gambar yang terlihat yang
         * diunduh. Gambar dipulihkan otomatis saat mendekati layar.
         */
        private const val DEFER_IMAGES_JS = """
            (function () {
              if (window.__oskyDefer) { return; }
              if (!document.body) { return; }
              if (!('IntersectionObserver' in window)) { return; }
              window.__oskyDefer = true;
              var limit = (window.innerHeight || 800) * 1.5;
              function restore(img) {
                var src = img.getAttribute('data-osky-src');
                if (!src) { return; }
                img.removeAttribute('data-osky-src');
                img.setAttribute('src', src);
              }
              var io = new IntersectionObserver(function (entries) {
                for (var i = 0; i < entries.length; i++) {
                  if (!entries[i].isIntersecting) { continue; }
                  restore(entries[i].target);
                  io.unobserve(entries[i].target);
                }
              }, { rootMargin: '1200px 0px' });
              var imgs = document.querySelectorAll('img[src]');
              for (var i = 0; i < imgs.length; i++) {
                var img = imgs[i];
                if (img.getAttribute('data-osky-src')) { continue; }
                if (img.getBoundingClientRect().top <= limit) { continue; }
                img.setAttribute('data-osky-src', img.getAttribute('src'));
                img.removeAttribute('src');
                io.observe(img);
              }
            })();
        """

        /**
         * Menu header situs dan tombol panah "back to top" disembunyikan karena
         * bottom navigation app sudah menggantikannya. Sekaligus merapikan kontras
         * gold, footer, dan kartu voucher. Hanya menambah satu <style>.
         */
        private const val PAGE_CSS_JS = """
            (function () {
              if (document.getElementById('osky-style')) { return; }
              var css = [
                'nav.navbar{display:none!important;}',
                'a.back-to-top{display:none!important;}',
                '.footer{padding-bottom:96px!important;}',
                '.fixed-bottom{bottom:96px!important;}',
                '.sticky-bottom{bottom:96px!important;}',
                '.text-primary{color:#8A6D1F!important;}',
                '.btn-outline-primary{color:#8A6D1F!important;border-color:#D8C88F!important;}',
                '.btn-outline-primary:hover,.btn-outline-primary:focus{background:#8A6D1F!important;border-color:#8A6D1F!important;color:#fff!important;}',
                '.footer-menu a{display:none!important;}',
                '.footer-menu a[href*="merchant"]{display:inline-block!important;}',
                '#osky-voucher-toolbar{position:sticky;top:0;z-index:40;background:#fff;padding:10px 12px 8px;margin:0 -12px 10px;box-shadow:0 2px 12px rgba(20,22,40,.07);}',
                '#osky-voucher-tools{display:flex;gap:8px;margin-bottom:8px;}',
                '#osky-voucher-search{flex:1;min-width:0;height:40px;border:1px solid #E6E8EE;border-radius:12px;padding:0 12px;font-size:14px;color:#28293B;background:#F7F8FA;outline:none;}',
                '#osky-voucher-sort{height:40px;border:1px solid #E6E8EE;border-radius:12px;padding:0 8px;font-size:13px;color:#28293B;background:#fff;}',
                '#osky-voucher-chips{display:flex;flex-wrap:nowrap;align-items:center;overflow-x:auto;gap:6px;-webkit-overflow-scrolling:touch;padding-bottom:2px;scrollbar-width:none;}',
                '#osky-voucher-chips::-webkit-scrollbar{display:none;}',
                '#osky-voucher-toolbar .filter-btn{flex:0 0 auto;border-radius:999px;border:1px solid #E6E8EE;background:#fff;color:#28293B;font-size:13px;line-height:1.2;padding:7px 14px;margin:0;white-space:nowrap;text-decoration:none;display:inline-block;}',
                '#osky-voucher-toolbar .filter-btn.osky-active{background:#28293B;border-color:#28293B;color:#fff;}',
                '#osky-voucher-count{font-size:12px;color:#6B7080;margin:6px 0 0;}',
                '#osky-voucher-more{display:none;width:100%;margin:6px 0 20px;height:46px;border:1px solid #D8C88F;border-radius:12px;background:#fff;color:#8A6D1F;font-weight:600;font-size:14px;}',
                '.portfolio-item .card{height:auto!important;border-radius:16px!important;overflow:hidden;box-shadow:0 6px 20px rgba(20,22,40,.06)!important;}',
                '.portfolio-item .card-img-top{height:180px!important;}',
                '.portfolio-item .card-title{font-size:15px;line-height:1.35;margin:0 0 6px;}',
                '.portfolio-item .description-box{height:auto!important;max-height:2.9em;overflow:hidden!important;line-height:1.45;position:relative;}',
                '.portfolio-item .description-box.osky-expanded{max-height:none!important;overflow:visible!important;}',
                '.osky-sk{display:inline-block;margin-top:6px;padding:0;border:0;background:none;color:#8A6D1F;font-size:12px;font-weight:600;}',
                '.portfolio-item .card-body{padding:12px 14px;}',
                '.portfolio-item{text-transform:none!important;}',
                '@media (min-width:700px){.portfolio-item{flex:0 0 50%;max-width:50%;}}',
                '@media (min-width:1000px){.portfolio-item{flex:0 0 33.3333%;max-width:33.3333%;}}'
              ].join('');
              function add() {
                if (document.getElementById('osky-style')) { return; }
                var style = document.createElement('style');
                style.id = 'osky-style';
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

        /**
         * Perbaikan isi halaman:
         * 1. "IDR 690.000" -> "Rp690.000" (hanya teks harga).
         * 2. Tanggal voucher dd/mm/yyyy -> d Mmm yyyy.
         * 3. Voucher: toolbar cari/urut, chip kategori lengket, S&K dilipat,
         *    muat lebih banyak, dan akses Merchant.
         */
        private const val PAGE_ENHANCE_JS = """
            (function () {
              if (window.__oskyEnhance) { return; }
              if (!document.body) { return; }
              var isVoucher = /[?&]menu=voucher(?:&|${'$'})/.test(location.search);
              if (isVoucher && !document.getElementById('portfolio-items')) { return; }
              window.__oskyEnhance = true;

              var bulan = ['Jan','Feb','Mar','Apr','Mei','Jun','Jul','Agu','Sep','Okt','Nov','Des'];
              var skipTags = {SCRIPT:1,STYLE:1,INPUT:1,TEXTAREA:1,OPTION:1,NOSCRIPT:1};

              function normalizePrices(root) {
                if (!root) { return; }
                var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
                var node;
                while ((node = walker.nextNode())) {
                  var parent = node.parentNode;
                  if (!parent || skipTags[parent.nodeName]) { continue; }
                  var text = node.nodeValue;
                  if (text && text.indexOf('IDR') >= 0) {
                    var fixed = text.replace(/\bIDR\s+(?=\d)/g, 'Rp');
                    if (fixed !== text) { node.nodeValue = fixed; }
                  }
                }
              }

              function normalizeDates(root) {
                var nodes = (root || document).querySelectorAll('.portfolio-item strong');
                for (var i = 0; i < nodes.length; i++) {
                  var m = /^\s*(\d{1,2})\/(\d{1,2})\/(\d{4})\s*${'$'}/.exec(nodes[i].textContent);
                  if (m) {
                    var d = parseInt(m[1], 10);
                    var mo = parseInt(m[2], 10) - 1;
                    if (mo >= 0 && mo < 12) {
                      nodes[i].textContent = d + ' ' + bulan[mo] + ' ' + m[3];
                    }
                  }
                }
              }

              function setupVoucher() {
                var grid = document.getElementById('portfolio-items');
                if (!grid || document.getElementById('osky-voucher-toolbar')) { return; }
                var items = [].slice.call(grid.querySelectorAll('.portfolio-item'));
                if (!items.length) { return; }

                var LIMIT = 12;
                var limit = LIMIT;
                var query = '';
                var order = 'default';

                var firstBtn = document.querySelector('.filter-btn');
                var filterRow = firstBtn ? firstBtn.parentNode : null;
                if (!filterRow) { return; }
                var container = filterRow.parentNode;

                var toolbar = document.createElement('div');
                toolbar.id = 'osky-voucher-toolbar';

                var tools = document.createElement('div');
                tools.id = 'osky-voucher-tools';
                var search = document.createElement('input');
                search.id = 'osky-voucher-search';
                search.type = 'search';
                search.placeholder = 'Cari voucher atau merchant';
                var sort = document.createElement('select');
                sort.id = 'osky-voucher-sort';
                sort.innerHTML = '<option value="default">Default</option>'
                  + '<option value="ending">Segera berakhir</option>'
                  + '<option value="az">Nama A-Z</option>';
                tools.appendChild(search);
                tools.appendChild(sort);

                var chips = document.createElement('div');
                chips.id = 'osky-voucher-chips';
                var merchant = document.createElement('a');
                merchant.href = 'merchant/';
                merchant.textContent = 'Merchant';
                merchant.className = 'filter-btn';
                chips.appendChild(merchant);
                // Pindahkan tombol filter satu per satu supaya semuanya sejajar dalam
                // satu baris gulir; kalau wrapper-nya ikut dipindah, chip Merchant
                // ikut meregang setinggi dua baris dan tampak seperti oval kosong.
                while (filterRow.firstElementChild) {
                  chips.appendChild(filterRow.firstElementChild);
                }
                if (filterRow.parentNode) { filterRow.parentNode.removeChild(filterRow); }

                var count = document.createElement('p');
                count.id = 'osky-voucher-count';

                toolbar.appendChild(tools);
                toolbar.appendChild(chips);
                toolbar.appendChild(count);
                container.insertBefore(toolbar, container.firstChild);

                var more = document.createElement('button');
                more.id = 'osky-voucher-more';
                more.type = 'button';
                more.textContent = 'Muat lebih banyak';
                grid.parentNode.insertBefore(more, grid.nextSibling);

                function dateValue(item) {
                  var strongs = item.querySelectorAll('strong');
                  for (var i = strongs.length - 1; i >= 0; i--) {
                    var m = /^\s*(\d{1,2})\s+([A-Za-z]+)\s+(\d{4})\s*${'$'}/.exec(strongs[i].textContent);
                    if (m) {
                      var idx = bulan.indexOf(m[2]);
                      if (idx >= 0) { return Date.UTC(+m[3], idx, +m[1]); }
                    }
                  }
                  return Number.MAX_SAFE_INTEGER;
                }

                function isMatch(item) {
                  if (item.classList.contains('hide')) { return false; }
                  if (!query) { return true; }
                  return (item.textContent || '').toLowerCase().indexOf(query) >= 0;
                }

                function apply() {
                  var list = items.filter(isMatch);
                  items.forEach(function (item) { item.style.display = 'none'; });
                  list.slice(0, limit).forEach(function (item) { item.style.display = ''; });
                  var left = list.length - limit;
                  more.style.display = left > 0 ? 'block' : 'none';
                  more.textContent = left > 0
                    ? ('Muat lebih banyak (' + left + ')')
                    : 'Muat lebih banyak';
                  count.textContent = list.length
                    ? (Math.min(limit, list.length) + ' dari ' + list.length + ' voucher')
                    : 'Tidak ada voucher yang cocok';
                }

                function sortItems() {
                  if (order === 'default') {
                    items.sort(function (a, b) {
                      return (+a.dataset.oskyIndex) - (+b.dataset.oskyIndex);
                    });
                  } else if (order === 'az') {
                    items.sort(function (a, b) {
                      return (a.textContent || '').trim()
                        .localeCompare((b.textContent || '').trim(), 'id');
                    });
                  } else {
                    items.sort(function (a, b) { return dateValue(a) - dateValue(b); });
                  }
                  items.forEach(function (item) { grid.appendChild(item); });
                }

                // Judul dipindah ke atas deskripsi, lalu S&K dilipat 2 baris.
                // Judul dipindah ke atas deskripsi (tulis), lalu ukur semua kotak
                // dalam satu pass (baca), baru pasang tombol S&K (tulis). Pola
                // baca-tulis bergantian per kartu memaksa 59x reflow.
                items.forEach(function (item) {
                  var box = item.querySelector('.description-box');
                  if (!box) { return; }
                  var title = box.querySelector('.card-title');
                  if (title) { box.parentNode.insertBefore(title, box); }
                });
                var folded = [];
                items.forEach(function (item) {
                  var box = item.querySelector('.description-box');
                  if (box && box.scrollHeight > box.clientHeight + 8) { folded.push(box); }
                });
                folded.forEach(function (box) {
                  var btn = document.createElement('button');
                  btn.type = 'button';
                  btn.className = 'osky-sk';
                  btn.textContent = 'Lihat S&K';
                  btn.addEventListener('click', function () {
                    var open = box.classList.toggle('osky-expanded');
                    btn.textContent = open ? 'Tutup S&K' : 'Lihat S&K';
                  });
                  box.parentNode.insertBefore(btn, box.nextSibling);
                });

                items.forEach(function (item, i) { item.dataset.oskyIndex = i; });
                normalizeDates(grid);

                search.addEventListener('input', function () {
                  query = (search.value || '').trim().toLowerCase();
                  limit = LIMIT;
                  apply();
                });
                sort.addEventListener('change', function () {
                  order = sort.value;
                  sortItems();
                  apply();
                });
                more.addEventListener('click', function () {
                  limit += LIMIT;
                  apply();
                });

                // Ikuti filter bawaan situs: reset paging & tandai chip aktif.
                [].forEach.call(document.querySelectorAll('.filter-btn'), function (btn) {
                  if (btn === merchant) { return; }
                  btn.addEventListener('click', function () {
                    limit = LIMIT;
                    [].forEach.call(document.querySelectorAll('.filter-btn'), function (b) {
                      b.classList.remove('osky-active');
                    });
                    btn.classList.add('osky-active');
                    setTimeout(apply, 0);
                  }, true);
                });
                var all = document.querySelector('.filter-btn[data-filter="all"]');
                if (all) { all.classList.add('osky-active'); }

                apply();
              }

              normalizePrices(document.body);
              if (isVoucher) { setupVoucher(); }
            })();
        """
    }
}
