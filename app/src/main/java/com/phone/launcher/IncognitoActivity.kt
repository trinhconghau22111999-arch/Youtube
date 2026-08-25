package com.phone.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.res.ColorStateList
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Trình duyệt phụ "Duyệt web" - CHẠY CHUNG TIẾN TRÌNH với MainActivity (xem AndroidManifest.xml:
 *  đã bỏ android:process=":incognito"), nên CHIA SẺ CHUNG cookie/phiên đăng nhập với trình duyệt
 *  chính - đăng nhập ở đây vẫn còn khi mở YouTube ở MainActivity và ngược lại.
 *  THOÁT RA: danh sách URL các tab được LƯU LẠI (qua IncognitoSessionStore) và KHÔI PHỤC khi
 *  vào lại - người dùng tiếp tục đúng các trang đang mở.
 *  DẤU SAO: lưu VĨNH VIỄN qua IncognitoStarredStore, không mất khi đóng phiên.
 *  KHÔNG giới hạn số tab; TẤT CẢ các tab dùng CHUNG 1 phiên/cookie với nhau trong cùng 1 lần mở. */
class IncognitoActivity : AppCompatActivity() {

    /** Thoát màn này kèm hiệu ứng "trượt ra bên phải" kiểu Windows Phone, dù finish() được gọi
     *  từ đâu (nút Back nổi, mũi tên ◀, phím Back cứng...). */
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
    }


    // pendingLinkUrl/pendingLinkTapAt: dùng cho tính năng "phải chạm 2 lần liên tiếp mới mở
    // link" (xem shouldOverrideUrlLoading() trong setupWebViewCallbacks()) - lưu URL của lần
    // chạm ĐẦU TIÊN bị chặn và thời điểm chạm, để so khớp với lần chạm KẾ TIẾP.
    private data class Tab(
        val webView: WebView,
        var title: String = "Tab mới",
        var pendingLinkUrl: String? = null,
        var pendingLinkTapAt: Long = 0L
    )

    private val tabs = ArrayList<Tab>()
    private var activeIndex = 0
    // Màn hình gốc (chứa webContainer/tabBar) - lưu lại thành field để starredViewHandle có nơi
    // add overlay "đã gắn dấu" vào.
    private lateinit var overlayRoot: FrameLayout
    private var starredViewHandle: StarredView.Handle? = null

    // FIX "xem video trong Duyệt web không mở được toàn màn hình": trước đây WebChromeClient
    // KHÔNG hề override onShowCustomView()/onHideCustomView() - đây là 2 hàm BẮT BUỘC phải có để
    // WebView xử lý video HTML5 toàn màn hình (nút toàn màn hình trên trình phát video, hoặc
    // Fullscreen API của trang). Thiếu 2 hàm này, WebView không có nơi nào để đặt View toàn màn
    // hình của video vào -> bấm nút toàn màn hình không có tác dụng gì (im lặng, không lỗi, không
    // phản hồi). fullscreenContainer là nơi chứa View đó khi đang toàn màn hình.
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    private lateinit var tabBar: LinearLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var edtUrl: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStar: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Không ẩn status bar / navigation bar - hiển thị bình thường.

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(android.graphics.Color.BLACK) }
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        tabScroll.addView(tabBar)
        root.addView(tabScroll)

        val urlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(dp(10), dp(4), dp(10), dp(6))
        }
        edtUrl = EditText(this).apply {
            hint = "Hỏi google"
            setHintTextColor(0xFF888888.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setSingleLine(true)
            // Khai báo rõ kiểu URL để bàn phím chắc chắn hiện nút "Đi/Enter" đúng hành vi -
            // trước đây thiếu dòng này nên 1 số bàn phím (Samsung, SwiftKey...) không kích
            // hoạt được sự kiện Enter để tìm kiếm.
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    loadFromInput(); true
                } else false
            }
        }
        urlRow.addView(edtUrl)

        // Nút tìm kiếm/đi tới - dự phòng cho trường hợp bàn phím không kích hoạt Enter được,
        // bấm trực tiếp vào đây luôn hoạt động.
        val btnGo = ImageView(this).apply {
            setImageResource(R.drawable.ic_wp_search)
            imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(6), dp(4), dp(6))
            isClickable = true
            setOnClickListener { loadFromInput() }
        }
        urlRow.addView(btnGo, LinearLayout.LayoutParams(dp(34), dp(34)))

        btnStar = ImageView(this).apply {
            setImageResource(R.drawable.ic_wp_star_outline)
            imageTintList = ColorStateList.valueOf(0xFFCCCCCC.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(6), dp(4), dp(6))
            isClickable = true
            setOnClickListener { toggleStarCurrent() }
        }
        urlRow.addView(btnStar, LinearLayout.LayoutParams(dp(34), dp(34)))

        urlRow.addView(TextView(this).apply {
            text = "Đã gắn dấu"
            textSize = 12f
            setTextColor(0xFF0078D7.toInt())
            setPadding(dp(8), dp(6), dp(4), dp(6))
            // Icon sao dạng vector thay cho ký tự "★", đặt bên trái chữ, cùng màu accent.
            val star = ContextCompat.getDrawable(this@IncognitoActivity, R.drawable.ic_wp_star_filled)
            star?.setTintList(ColorStateList.valueOf(0xFF0078D7.toInt()))
            star?.setBounds(0, 0, dp(16), dp(16))
            setCompoundDrawables(star, null, null, null)
            compoundDrawablePadding = dp(4)
            isClickable = true
            setOnClickListener { toggleStarredView() }
        })
        root.addView(urlRow)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF0078D7.toInt())
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        }
        root.addView(progressBar)

        webContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(webContainer)

        val outer = FrameLayout(this).apply {
            addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        overlayRoot = outer
        // Container riêng cho video toàn màn hình (xem onShowCustomView/onHideCustomView bên
        // dưới) - thêm SAU "root" trong cùng FrameLayout "outer" nên tự động NẰM ĐÈ LÊN TRÊN toàn
        // bộ giao diện Duyệt web (thanh tab, thanh địa chỉ...) khi hiện ra, không cần ẩn "root"
        // đi. Mặc định GONE, chỉ hiện khi có video toàn màn hình đang phát.
        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            visibility = View.GONE
        }
        outer.addView(fullscreenContainer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(outer)
        // FIX khoảng đen dư ở trên/dưới màn hình - xem giải thích chi tiết trong MainActivity.kt.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(outer) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        // ĐÃ GỠ HẲN thanh điều hướng nổi Back/Start/Đa nhiệm (WpNavBar) theo yêu cầu.

        val startUrl = intent.getStringExtra("initial_url")
        if (startUrl != null) {
            // Được mở từ shortcut kèm URL cụ thể (vd bấm icon "Duyệt web" từ màn chính kèm URL)
            // -> mở URL đó làm tab đầu tiên, bỏ qua session đã lưu.
            newTab(startUrl)
        } else {
            // Khôi phục các tab của phiên trước (nếu có) để người dùng tiếp tục đúng chỗ đã xem.
            // Cookie/phiên đăng nhập trong process này KHÔNG được giữ (process riêng bị kill khi
            // thoát app), nhưng URL thì được lưu lại để mở lại đúng trang.
            val savedUrls = IncognitoSessionStore.loadUrls(this)
            if (savedUrls != null) {
                val savedActive = IncognitoSessionStore.loadActiveIndex(this)
                savedUrls.forEach { url -> newTab(url) }
                // Chuyển về đúng tab đang active lần trước (nếu index còn hợp lệ)
                if (savedActive in tabs.indices && savedActive != activeIndex) {
                    switchTab(savedActive)
                }
            } else {
                // Chưa có phiên nào được lưu -> mở Google làm trang mặc định
                newTab("https://www.google.com")
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Ẩn thanh trạng thái + thanh điều hướng hệ thống trong lúc video đang toàn màn hình - đúng
     *  hành vi toàn màn hình thật sự (không phải chỉ full khung WebView mà vẫn còn 2 thanh hệ
     *  thống che 1 phần). Bình thường (không xem video toàn màn hình) trang KHÔNG ẩn 2 thanh này
     *  (xem comment ở onCreate: "Không ẩn status bar / navigation bar - hiển thị bình thường") -
     *  chỉ ẩn tạm trong lúc xem toàn màn hình rồi trả lại y nguyên khi thoát. */
    private fun enterFullscreenImmersive() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun exitFullscreenImmersive() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    // ĐÃ GỠ HẲN thanh điều hướng nổi Back/Start/Đa nhiệm (WpNavBar) và màn "Đa nhiệm" toàn màn
    // hình (TaskView) theo yêu cầu. Back giờ dùng đúng cử chỉ/nút Back thật của hệ thống (vẫn xử
    // lý đầy đủ qua onBackPressed() bên dưới: lùi trang trong tab hiện tại, hết lịch sử thì thoát
    // hẳn Ẩn danh). Chuyển tab dùng thanh tab ngang (tabBar) có sẵn phía trên màn hình, không cần
    // màn Đa nhiệm dạng card toàn màn hình nữa.

    // ── Quản lý tab (KHÔNG giới hạn số lượng) ──
    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab(url: String) {
        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // ĐÃ XOÁ HẲN: UA di động tuỳ chỉnh (UserAgentManager) theo yêu cầu - dùng UA MẶC ĐỊNH
            // của hệ thống WebView.
            // Ẩn danh: không lưu mật khẩu/form đã điền, không cho tự động điền lại
            settings.saveFormData = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
            visibility = View.INVISIBLE
            // Nền ĐEN cho WebView - khi tab đang trống (about:blank, chưa gõ địa chỉ) sẽ hiện
            // màu đen thay vì màu trắng mặc định của WebView, đúng yêu cầu "tối đen luôn".
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        // Xoá sạch mọi dữ liệu gợi ý tìm kiếm/form đã lưu trước đó (nếu có sót lại), để Ẩn danh
        // KHÔNG bao giờ nhớ lịch sử tìm kiếm cục bộ trên máy.
        @Suppress("DEPRECATION")
        android.webkit.WebViewDatabase.getInstance(this).clearFormData()
        @Suppress("DEPRECATION")
        android.webkit.WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword()
        val tab = Tab(webView)
        val index = tabs.size
        tabs.add(tab)
        webContainer.addView(webView)
        setupWebViewCallbacks(webView, index)
        setupLongPress(webView)
        if (index == 0) {
            // Tab ĐẦU TIÊN: chưa có trang nào khác đang xem, phải chuyển sang xem ngay.
            switchTab(index)
        }
        // Các tab mở SAU tab đầu tiên: chỉ tải NỀN, KHÔNG tự chuyển sang xem - trang hiện tại
        // đứng yên, người dùng tự bấm vào tab đó trên thanh tab khi nào muốn xem.
        loadInTab(index, url)
        renderTabBar()
    }

    /** Nhấn giữ vào 1 link/ảnh trong trang -> hiện tuỳ chọn "Mở trong tab mới" */
    private fun setupLongPress(webView: WebView) {
        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            when (result.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.IMAGE_TYPE -> {
                    val targetUrl = result.extra
                    if (targetUrl != null) showOpenInNewTabDialog(targetUrl)
                }
                // QUAN TRỌNG: khi ảnh nằm TRONG 1 link (<a href="..."><img/></a>), result.extra
                // ở trên chỉ trả về URL của ẢNH, KHÔNG PHẢI url của link bao quanh - đây là lý do
                // "nhấn giữ link chọn mở tab mới nhưng nó chỉ mở hình ảnh". Phải dùng
                // requestFocusNodeHref() (API chính thức của WebView cho đúng trường hợp này) để
                // lấy đúng href của thẻ <a>, kết quả trả về BẤT ĐỒNG BỘ qua Handler/Message.
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val handler = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
                        val hrefUrl = msg.data.getString("url")
                        if (hrefUrl != null) showOpenInNewTabDialog(hrefUrl)
                        true
                    }
                    webView.requestFocusNodeHref(handler.obtainMessage())
                }
                else -> return@setOnLongClickListener false
            }
            true
        }
    }

    private fun showOpenInNewTabDialog(targetUrl: String) {
        AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle(targetUrl.take(60))
            .setItems(arrayOf("Mở trong tab mới", "Huỷ")) { _, which ->
                if (which == 0) newTab(targetUrl)
            }
            .show()
    }

    private fun setupWebViewCallbacks(webView: WebView, index: Int) {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (tabs.getOrNull(activeIndex)?.webView === webView) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (index < tabs.size) {
                    tabs[index].title = title?.take(14) ?: "Tab mới"
                    renderTabBar()
                }
            }
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false

            // Xem giải thích đầy đủ ở khai báo customView/fullscreenContainer phía trên. Được
            // WebView tự gọi khi trang yêu cầu hiện video (hoặc bất kỳ phần tử nào) toàn màn hình
            // (nút toàn màn hình trên trình phát video, hoặc Element.requestFullscreen() của
            // trang). [view] chính là nội dung cần hiện toàn màn hình (với video, thường là 1
            // VideoView/TextureView do chính WebView tự dựng).
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    // Đã có 1 customView khác đang hiện rồi (hiếm khi xảy ra) - báo hệ thống coi
                    // như huỷ ngay yêu cầu MỚI này, giữ nguyên cái đang hiện, tránh chồng 2 lớp.
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                fullscreenContainer.visibility = View.VISIBLE
                enterFullscreenImmersive()
            }

            override fun onHideCustomView() {
                val cv = customView ?: return
                fullscreenContainer.removeView(cv)
                fullscreenContainer.visibility = View.GONE
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                exitFullscreenImmersive()
            }

            // FIX (lý do "bấm mở mic là tắt liền"): trước đây CẤP LUÔN mọi quyền WebView yêu
            // cầu mà không kiểm tra quyền HỆ THỐNG (RECORD_AUDIO/CAMERA) có thực sự đã được
            // người dùng đồng ý hay chưa (xin ở MainActivity lúc mở app lần đầu) - nếu người
            // dùng từng bấm "Từ chối" hoặc thu hồi quyền sau đó trong Cài đặt, WebView vẫn
            // được báo "đã cấp" -> trang hiện UI ghi âm lên nhưng phần cứng mic bị hệ điều
            // hành CHẶN THẬT NGAY LẬP TỨC vì thiếu quyền hệ thống -> luồng ghi âm kết thúc tức
            // khắc, nhìn như "bấm mở là tắt liền". Giờ CHỈ cấp đúng resource nào có quyền hệ
            // thống tương ứng đã thực sự được cấp; thiếu quyền nào thì từ chối riêng resource
            // đó và báo cho người dùng biết.
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val granted = request.resources.filter { resource ->
                    when (resource) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                            ContextCompat.checkSelfPermission(this@IncognitoActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                            ContextCompat.checkSelfPermission(this@IncognitoActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        else -> true
                    }
                }
                if (granted.isNotEmpty()) {
                    request.grant(granted.toTypedArray())
                } else {
                    request.deny()
                }
                if (granted.size < request.resources.size) {
                    Toast.makeText(
                        this@IncognitoActivity,
                        "Chưa cấp quyền micro/camera cho ứng dụng - vào Cài đặt máy để cấp quyền",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host
                return if (AdBlocker.isAd(host)) AdBlocker.blockedResponse() else super.shouldInterceptRequest(view, request)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme ?: return false
                // Chặn scheme không phải http/https (tel:, intent:, v.v.)
                if (scheme != "http" && scheme != "https") return true

                // FIX/TÍNH NĂNG MỚI: "không cho chuyển trang bằng 1 chạm, trừ phi đang ở trang gg
                // hoặc bấm nút tìm kiếm". Callback shouldOverrideUrlLoading() này CHỈ được gọi
                // cho điều hướng do CHẠM VÀO LINK bên trong trang gây ra - KHÔNG bao giờ được gọi
                // cho các lần webView.loadUrl() ta tự gọi thẳng từ code (vd. loadFromInput() khi
                // bấm nút tìm kiếm/Enter trên thanh địa chỉ) - nên nhánh "nút tìm kiếm" ở đây
                // TỰ ĐỘNG được cho qua, không cần xử lý gì thêm.
                //
                // Đang Ở trang Google (trang HIỆN TẠI, không phải trang SẮP mở) -> luôn cho
                // chuyển trang bình thường ngay từ 1 chạm, không yêu cầu chạm 2 lần.
                val currentHost = view?.url?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                if (isGoogleHost(currentHost)) return false

                // Các trang KHÁC Google: phải chạm ĐÚNG 1 link này 2 LẦN LIÊN TIẾP (trong khoảng
                // thời gian ngắn) mới thực sự cho mở - để tránh chạm nhầm/lỡ tay vào link (quảng
                // cáo nguỵ trang thành nội dung, link dày đặc sát nhau...) mở trang ngay lập tức.
                val targetUrl = request.url.toString()
                val tab = tabs.firstOrNull { it.webView === view }
                val now = android.os.SystemClock.elapsedRealtime()
                if (tab != null && tab.pendingLinkUrl == targetUrl &&
                    (now - tab.pendingLinkTapAt) <= DOUBLE_TAP_WINDOW_MS
                ) {
                    // Đúng link này, chạm lần THỨ 2 trong khung thời gian cho phép -> mở thật.
                    tab.pendingLinkUrl = null
                    tab.pendingLinkTapAt = 0L
                    return false
                }
                // Chạm lần đầu (hoặc chạm link khác, hoặc đã quá thời gian chờ từ lần chạm trước)
                // -> CHẶN, chỉ ghi nhận làm mốc chờ lần chạm kế tiếp.
                if (tab != null) {
                    tab.pendingLinkUrl = targetUrl
                    tab.pendingLinkTapAt = now
                }
                return true
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (tabs.getOrNull(activeIndex)?.webView === webView) {
                    edtUrl.setText(if (url == null || url == "about:blank") "" else url)
                    refreshStarIcon()
                }
                // FIX "back trong tab không lùi về trang trước mà thoát/back ra luôn": trước đây
                // có gọi view?.clearHistory() ở đây sau MỖI lần trang tải xong. clearHistory()
                // KHÔNG phải xoá "lịch sử lưu trữ bên ngoài" như comment cũ lầm tưởng - đây là
                // API xoá THẲNG back/forward stack NỘI BỘ của chính WebView (theo tài liệu
                // Android: "Clears the internal back/forward list of this WebView"). Gọi nó sau
                // mỗi trang tải xong nghĩa là: vừa chuyển sang trang mới xong là lập tức xoá sạch
                // toàn bộ lịch sử điều hướng của tab đó, chỉ còn lại đúng trang vừa tải - nên
                // canGoBack() luôn = false, khiến onBackPressed() (xem bên dưới) không còn gì để
                // lùi về nữa và rơi thẳng xuống nhánh thoát/đóng tab, dù người dùng chỉ mới rời
                // khỏi trang ban đầu đúng 1 bước. Tính năng "Ẩn danh" của app này vốn KHÔNG hề
                // dựa vào clearHistory() để hoạt động: saveSession() (gọi ngay bên dưới) chỉ lưu
                // ĐÚNG 1 URL hiện tại của mỗi tab để khôi phục phiên làm việc, không hề lưu lại
                // toàn bộ lịch sử điều hướng ra bất kỳ đâu - nên xoá dòng clearHistory() này
                // không làm lộ thêm dấu vết nào cả, chỉ đơn thuần trả lại đúng hành vi Back bình
                // thường trong tab.
                view?.evaluateJavascript(ZoomEnabler.JS, null)
                view?.evaluateJavascript(AdOverlayBlocker.JS, null)
                if (YoutubeAdSkipper.isYoutube(url)) view?.evaluateJavascript(YoutubeAdSkipper.JS, null)
                saveSession()
            }
        }
    }

    private fun loadInTab(index: Int, url: String) {
        tabs.getOrNull(index)?.webView?.loadUrl(url)
    }

    // Khoảng thời gian tối đa giữa 2 lần chạm liên tiếp vào ĐÚNG 1 link để tính là "double-tap"
    // hợp lệ (xem shouldOverrideUrlLoading() ở trên) - 300ms: quá mốc này, link đã ghi nhớ ở lần
    // chạm đầu bị coi như "quên" - chạm lại sau đó tính lại từ đầu như lần chạm đầu tiên mới,
    // phải chạm thêm 1 lần nữa mới mở được. GIẢM từ 1000ms xuống 300ms theo yêu cầu: phải chạm
    // THẬT NHANH 2 lần liên tục (cả khoảng cách giữa 2 lần chạm LẪN thời gian "nhớ" lần chạm đầu
    // đều dùng chung đúng 1 mốc 300ms này - lần chạm đầu chỉ được "nhớ" tối đa 300ms).
    private companion object {
        const val DOUBLE_TAP_WINDOW_MS = 300L
    }

    /** Trang HIỆN TẠI có phải Google không (google.com, www.google.com, và các tên miền quốc
     *  gia của Google như google.com.vn, google.co.uk...) - dùng làm ngoại lệ cho tính năng
     *  "chạm 2 lần mới mở link" ở shouldOverrideUrlLoading(): đang ở trang Google thì luôn cho
     *  chuyển trang bình thường từ 1 chạm. */
    private fun isGoogleHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase()
        return h == "google.com" || h.endsWith(".google.com") || h.matches(Regex("^(www\\.)?google\\.[a-z.]{2,}$"))
    }

    private fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        activeIndex = index
        // Dùng INVISIBLE thay vì GONE cho các tab không active: GONE gỡ hẳn WebView khỏi layout
        // pass, và trên một số máy (Samsung/MIUI...) WebView không kịp redraw khi bật GONE ->
        // VISIBLE trở lại - màn hình vẫn đứng hình ở nội dung tab TRƯỚC đó dù activeIndex trong
        // code đã đổi (tạo cảm giác "mở tab mới nhưng vẫn xem tab hiện tại"). INVISIBLE vẫn giữ
        // WebView trong layout (các WebView đều match_parent chồng lên nhau trong webContainer
        // nên không lệch layout của view khác) nhưng tránh được lỗi redraw này.
        for ((i, t) in tabs.withIndex()) {
            t.webView.visibility = if (i == index) View.VISIBLE else View.INVISIBLE
        }
        // Trang trống (about:blank, null) -> để thanh địa chỉ TRỐNG, không điền sẵn gì cả,
        // đúng yêu cầu "thanh địa chỉ đừng điền sẵn để người dùng điền".
        val shownUrl = tabs[index].webView.url
        edtUrl.setText(if (shownUrl == null || shownUrl == "about:blank") "" else shownUrl)
        refreshStarIcon()
        renderTabBar()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        webContainer.removeView(tab.webView)
        tab.webView.destroy()
        if (tabs.isEmpty()) {
            saveSession()
            finish()
            return
        }
        val newActive = index.coerceAtMost(tabs.size - 1)
        switchTab(newActive)
        renderTabBar()
        saveSession()
    }

    private fun renderTabBar() {
        tabBar.removeAllViews()
        for ((i, tab) in tabs.withIndex()) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(android.graphics.Color.BLACK)
                setPadding(dp(12), dp(8), dp(8), dp(8))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = dp(6)
                layoutParams = lp
                isClickable = true
                setOnClickListener { switchTab(i) }
            }
            cell.addView(TextView(this).apply {
                text = tab.title
                textSize = 12f
                setTextColor(if (i == activeIndex) android.graphics.Color.WHITE else 0xFF888888.toInt())
            })
            cell.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_wp_close)
                imageTintList = ColorStateList.valueOf(0xFF888888.toInt())
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(4), dp(4), dp(4), dp(4))
                contentDescription = "Đóng tab"
                isClickable = true
                setOnClickListener { closeTab(i) }
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(2) })
            tabBar.addView(cell)
        }
        // KHÔNG còn giới hạn số tab - nút "+ Tab" luôn hiện
        tabBar.addView(TextView(this).apply {
            text = "+ Tab"
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            setOnClickListener { newTab("https://www.google.com") }
        })
    }

    // ── Gắn dấu sao (LƯU VĨNH VIỄN qua IncognitoStarredStore, không mất khi đóng phiên) ──
    private fun refreshStarIcon() {
        val url = tabs.getOrNull(activeIndex)?.webView?.url ?: ""
        val starred = url.isNotBlank() && IncognitoStarredStore.isStarred(this, url)
        btnStar.setImageResource(if (starred) R.drawable.ic_wp_star_filled else R.drawable.ic_wp_star_outline)
        btnStar.imageTintList = ColorStateList.valueOf(if (starred) 0xFFFFD700.toInt() else 0xFFCCCCCC.toInt())
    }

    private fun toggleStarCurrent() {
        val url = tabs.getOrNull(activeIndex)?.webView?.url ?: return
        if (url.isBlank()) return
        val nowStarred = IncognitoStarredStore.toggle(this, url)
        refreshStarIcon()
        Toast.makeText(this, if (nowStarred) "Đã gắn dấu sao" else "Đã bỏ dấu sao", Toast.LENGTH_SHORT).show()
    }

    /** Mở/đóng màn "đã gắn dấu" - lưới Live Tile giống hệt trang "start" (xem StarredView.kt),
     *  THAY CHO hành vi cũ (mở thẳng luôn TẤT CẢ các trang đã gắn dấu thành tab mới cùng lúc) -
     *  giờ người dùng thấy danh sách trước, bấm vào tile nào thì MỚI mở đúng trang đó. */
    private fun toggleStarredView() {
        val existing = starredViewHandle
        if (existing != null && existing.isShowing) {
            existing.dismiss()
            return
        }
        starredViewHandle = StarredView.show(
            activity = this,
            root = overlayRoot,
            urls = IncognitoStarredStore.getAll(this),
            onOpen = { url ->
                starredViewHandle?.dismiss()
                newTab(url)
            },
            onRemove = { url ->
                IncognitoStarredStore.toggle(this, url)
                refreshStarIcon()
                starredViewHandle?.update(IncognitoStarredStore.getAll(this))
            }
        )
    }

    private fun loadFromInput() {
        var input = edtUrl.text.toString().trim()
        if (input.isEmpty()) return
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = if (input.contains(".") && !input.contains(" ")) "https://$input"
            else "https://www.google.com/search?q=" + Uri.encode(input)
        }
        loadInTab(activeIndex, input)
    }

    // Lưu danh sách URL các tab hiện tại để khôi phục khi vào lại app (đúng như doc-comment
    // đầu class: "Duyệt web" giờ nhớ lại phiên làm việc, không còn xoá sạch khi thoát nữa).
    private fun saveSession() {
        // Lưu danh sách URL các tab hiện tại để khôi phục khi vào lại app.
        // Tab đang hiển thị about:blank hoặc chưa load gì được lưu là chuỗi rỗng (sẽ bị lọc bỏ
        // khi restore nếu không còn URL nào khác).
        val urls = tabs.map { it.webView.url }
        IncognitoSessionStore.save(this, urls, activeIndex)
    }

    override fun onBackPressed() {
        // Đang xem video toàn màn hình -> Back chỉ thoát toàn màn hình (đúng hành vi chuẩn của
        // trình duyệt), KHÔNG lùi trang/thoát Ẩn danh.
        if (customView != null) {
            tabs.getOrNull(activeIndex)?.webView?.webChromeClient?.onHideCustomView()
            return
        }
        // Đang mở màn "đã gắn dấu" -> Back chỉ ĐÓNG màn đó, KHÔNG lùi trang/thoát Ẩn danh.
        val sv = starredViewHandle
        if (sv != null && sv.isShowing) {
            sv.dismiss()
            return
        }
        val current = tabs.getOrNull(activeIndex)?.webView
        if (current != null && current.canGoBack()) {
            current.goBack()
        } else {
            saveSession()
            finish()
        }
    }

    // FIX: trước đây onPause() chỉ lưu phiên làm việc, không hề dừng video đang phát ở tab
    // nào cả -> thoát app (Home/chuyển app khác) xong video (vd. YouTube ẩn danh) vẫn tiếp tục
    // phát tiếng bình thường như chưa hề rời khỏi app. Dừng hẳn video ở TẤT CẢ tab khi rời app.
    override fun onPause() {
        super.onPause()
        saveSession()
        pauseAllVideosInAllTabs()
        for (t in tabs) t.webView.onPause()
        // Rời app trong lúc đang xem video toàn màn hình -> tự thoát toàn màn hình luôn, tránh
        // quay lại app mà vẫn kẹt ở trạng thái toàn màn hình cũ (customViewCallback của trang có
        // thể đã không còn hợp lệ sau khi WebView.onPause()/rời app).
        if (customView != null) {
            tabs.getOrNull(activeIndex)?.webView?.webChromeClient?.onHideCustomView()
        }
    }

    override fun onResume() {
        super.onResume()
        for (t in tabs) t.webView.onResume()
    }

    private fun pauseAllVideosInAllTabs() {
        val js = "(function(){" +
            "var vs=document.querySelectorAll('video');" +
            "for(var i=0;i<vs.length;i++){try{vs[i].pause();}catch(e){}}" +
            "})();"
        for (t in tabs) {
            t.webView.evaluateJavascript(js, null)
        }
    }

    override fun onDestroy() {
        saveSession()
        for (t in tabs) t.webView.destroy()
        starredViewHandle?.dismiss()
        super.onDestroy()
    }
}
