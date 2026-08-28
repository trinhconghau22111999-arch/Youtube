package com.phone.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    // ĐÃ XOÁ HẲN: buildUrlBarBackground()/edtUrl/toolbarUrl (thanh nhập URL ẩn) theo yêu cầu -
    // không còn cách nào gõ tay 1 địa chỉ web trong màn hình chính, chỉ còn 2 icon cố định
    // (YouTube, Ẩn danh). Nếu sau này cần lại, phải dựng lại từ đầu.

    private fun enableImmersiveMode() {
        // Ẩn CẢ thanh trạng thái (giờ/mạng/pin) LẪN thanh điều hướng hệ thống (3 phím
        // Back/Home/Recent hoặc gesture bar) - toàn màn hình thật sự, đúng tinh thần Windows
        // Phone (bản thân WP không có thanh điều hướng phần mềm của Android). ĐÃ GỠ HẲN thanh
        // điều hướng nổi riêng của app (WpNavBar/TaskView, theo yêu cầu) - back/thoát app giờ
        // dùng đúng cử chỉ/nút Back THẬT của hệ thống (vuốt từ mép hoặc hiện tạm thanh điều
        // hướng ẩn) - vẫn được xử lý đầy đủ qua onBackPressed()/doBack() như trước, chỉ là
        // không còn nút nổi riêng của app che lên màn hình nữa.
        //
        // TRƯỚC ĐÂY hàm này CHỈ ẩn thanh trạng thái, CỐ Ý giữ nguyên thanh điều hướng hệ thống -
        // đã đổi lại theo yêu cầu (3 phím điều hướng Android vẫn lộ ra phá vỡ giao diện WP).
        //
        // NGOẠI LỆ (mới): xem applySystemBarsForCurrentState() bên dưới - khi đang ở trang
        // YouTube VÀ máy đang xoay dọc, KHÔNG gọi hàm này nữa, thay vào đó gọi showSystemBars()
        // để luôn lộ ra cả thanh trạng thái lẫn 3 phím điều hướng, theo yêu cầu.
        //
        // Dùng WindowInsetsControllerCompat của androidx để hoạt động đúng trên mọi phiên bản
        // Android (kể cả các máy Android cũ hơn không có API ẩn thanh điều hướng mới).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /** Ngược lại với enableImmersiveMode(): hiện lại HẲN thanh trạng thái + 3 phím điều hướng hệ
     *  thống (không phải kiểu "vuốt ra tạm" như BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, mà hiện
     *  LUÔN LUÔN, chiếm chỗ thật trên màn hình) - dùng setDecorFitsSystemWindows(true) để hệ
     *  thống tự chừa đúng khoảng trống cho 2 thanh này, tránh nội dung app bị che khuất sau
     *  chúng trên các máy có notch/gesture bar khác nhau. */
    private fun showSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    /** YÊU CẦU MỚI: khi đang xem YouTube VÀ máy đang cầm DỌC (portrait) -> LUÔN hiện thanh trạng
     *  thái ở trên + 3 phím điều hướng ở dưới (showSystemBars()), không ẩn đi như chế độ toàn
     *  màn hình mặc định của app nữa. Mọi trường hợp khác (đang ở trang khác không phải YouTube,
     *  hoặc YouTube nhưng đang xoay NGANG để xem video toàn màn hình) vẫn giữ nguyên hành vi cũ:
     *  ẩn hết 2 thanh này (enableImmersiveMode()).
     *
     *  Được gọi lại ở MỌI thời điểm trạng thái có thể đổi: xoay máy (onConfigurationChanged),
     *  cửa sổ lấy lại focus (onWindowFocusChanged), trang web tải xong - đổi URL
     *  (onPageFinished). */
    private fun applySystemBarsForCurrentState() {
        // MỚI: đang "tắt màn hình ảo" (FakeScreenOff, xem addFloatingOffButton() bên dưới) ->
        // LUÔN ẩn hết thanh trạng thái + hàng nút đa nhiệm (3 phím điều hướng), bất kể đang ở
        // trang nào/xoay hướng nào - kể cả khi đúng lẽ ra phải hiện CỐ ĐỊNH theo quy tắc
        // YouTube+dọc ngay bên dưới. Ưu tiên cao nhất, kiểm tra và return NGAY ĐẦU hàm để không
        // rơi vào nhánh showSystemBars() phía dưới. Hàm này được gọi lại từ nhiều nơi (xoay máy,
        // cửa sổ lấy lại focus, trang tải xong...) nên đặt điều kiện ở đây là đủ, không cần chặn
        // riêng ở từng nơi gọi.
        if (FakeScreenOff.isShowing()) {
            enableImmersiveMode()
            return
        }
        val isPortrait = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        val isOnYoutubePage = ::webView.isInitialized &&
            homeOverlay.visibility != View.VISIBLE &&
            YoutubeAdSkipper.isYoutube(webView.url)
        if (isPortrait && isOnYoutubePage) {
            showSystemBars()
        } else {
            enableImmersiveMode()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // MainActivity khai báo configChanges="orientation|..." trong Manifest (để không bị huỷ
        // và dựng lại Activity mỗi lần xoay máy) -> phải TỰ cập nhật lại thanh trạng thái/điều
        // hướng ở đây mỗi khi xoay, hệ thống không tự làm giúp trong trường hợp này.
        applySystemBarsForCurrentState()

        // FIX "sáng/tối không đồng bộ hệ thống, lúc được lúc không": trước đây "uiMode" KHÔNG
        // có trong configChanges -> Android tự huỷ + dựng lại Activity mỗi lần đổi theme. App
        // này còn được khai báo làm launcher màn hình chính (category.HOME), mà các Activity
        // đóng vai trò Home bị hệ thống trì hoãn việc huỷ/dựng lại tới khi thật sự hiện lên lại
        // trên màn hình - nên đôi khi theme đổi ngay lập tức, đôi khi phải mở lại app mới thấy,
        // tuỳ app đang mở nền hay tiến trình có bị hệ thống giữ nguyên hay không. Giờ đã thêm
        // "uiMode" vào configChanges -> Android KHÔNG tự huỷ Activity nữa mà gọi thẳng vào đây
        // mỗi lần đổi theme, dù app đang mở hay đang chạy nền -> ta tự chủ động vẽ lại ngay lập
        // tức, không phụ thuộc vào thời điểm hệ thống quyết định recreate nữa.
        if (::webView.isInitialized) {
            applyDayNightBackgrounds()
        }
    }

    /** Vẽ lại các phần nền/màu phụ thuộc theme (sáng/tối) mà trước đây chỉ được nạp ĐÚNG 1 LẦN
     *  lúc onCreate() qua @color/app_bg (values/ vs values-night/) - vì giờ Activity không còn
     *  bị huỷ+dựng lại khi đổi theme (xem onConfigurationChanged ở trên) nên các resource này sẽ
     *  KHÔNG tự đổi màu nữa nếu không gọi tay ở đây. */
    private fun applyDayNightBackgrounds() {
        val appBg = androidx.core.content.ContextCompat.getColor(this, R.color.app_bg)
        findViewById<FrameLayout>(R.id.rootFrame).setBackgroundColor(appBg)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(appBg))
        // Nút nổi (FloatingBackButton.attach, hiện dùng cho nút "Off" giả tắt màn hình) tự đọc
        // uiMode mỗi lần được tạo (xem FloatingBackButton.kt) nhưng KHÔNG tự vẽ lại khi theme
        // đổi vì nó là 1 window riêng ngoài cây view Activity - gọi tay ở đây để cập nhật ngay.
        floatingOffButtonHandle?.refreshIconColorForCurrentTheme(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Chế độ ẩn toàn màn hình có thể bị huỷ khi bàn phím hiện lên, chuyển app đi rồi quay
        // lại... - áp dụng lại mỗi lần cửa sổ được lấy focus để luôn giữ đúng trạng thái ẩn.
        //
        // LỖI ĐÃ SỬA (lần 1): bấm vào ô địa chỉ (edtUrl) để gõ chữ thì bàn phím ảo KHÔNG bật lên
        // được, vì enableImmersiveMode() bị gọi lại đúng lúc IME đang hiện lên -> huỷ mất.
        // Sửa lần 1 bằng cách loại trừ theo LOẠI VIEW đang giữ focus (currentFocus !is EditText).
        //
        // LỖI ĐÃ SỬA (lần 2): cách loại trừ theo loại View ở lần 1 không bắt được trường hợp ô
        // nhập liệu NẰM TRONG WebView (currentFocus lúc đó là WebView, không phải EditText) ->
        // vẫn bị gọi enableImmersiveMode() giữa lúc bàn phím đang hiện cho web -> vẫn lỗi.
        //
        // LỖI ĐÃ SỬA (lần 3 - NGUYÊN NHÂN GỐC, đây mới là lý do lần 2 sửa xong vẫn không hết
        // lỗi): đoán qua LOẠI VIEW (currentFocus) là cách không đáng tin - còn có nút Back nổi
        // (FloatingBackButton, xem file đó) là 1 WINDOW RIÊNG luôn hiện sẵn, trước đây tranh
        // giành input focus với window chính, khiến chính bản thân hasFocus/currentFocus báo cáo
        // sai lệch. Đã sửa tận gốc ở FloatingBackButton.kt (thêm FLAG_NOT_FOCUSABLE). Ở ĐÂY sửa
        // thêm lớp bảo vệ thứ 2, ĐÁNG TIN CẬY HƠN NHIỀU: hỏi THẲNG hệ thống "bàn phím ảo (IME) có
        // đang thật sự hiển thị không" qua WindowInsetsCompat, thay vì đoán qua loại View đang
        // giữ focus - cách này đúng với MỌI trường hợp (EditText, ô nhập trong WebView, hay bất
        // kỳ ô nhập nào khác sau này), không cần liệt kê từng loại View một nữa.
        if (hasFocus) {
            val imeVisible = androidx.core.view.ViewCompat
                .getRootWindowInsets(window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (!imeVisible) applySystemBarsForCurrentState()
        }
    }

    private lateinit var webView: WebView
    private var progressBar: ProgressBar? = null
    private lateinit var homeOverlay: View
    private lateinit var homeScreenManager: HomeScreenManager

    // Giữ TẠM yêu cầu quyền của trang web (camera/mic hoặc vị trí) trong lúc chờ người dùng trả
    // lời hộp thoại xin quyền HỆ THỐNG vừa bật lên (xem onPermissionRequest/
    // onGeolocationPermissionsShowPrompt bên dưới và onRequestPermissionsResult) - KHÔNG xin
    // quyền sẵn lúc mở app nữa, chỉ xin ĐÚNG LÚC trang web thực sự cần.
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: android.webkit.GeolocationPermissions.Callback? = null

    // Bấm nút "🖥 Bản máy tính" nổi -> ép trang HIỆN TẠI sang UA máy tính tới khi tắt lại.

    // ĐÃ BỎ (theo yêu cầu): tính năng "cửa sổ nổi trong app" (mini-player WebView riêng tự动
    // mở khi xem YouTube) - gây lỗi bàn phím ảo không bật lên được (WebView nổi tự cướp focus)
    // và lỗi "153 - Lỗi cấu hình trình phát video" (tải embed sai cách). Không đáng công sửa
    // tiếp vì tính năng "phát nền thật" khi thoát hẳn app (bấm Home vật lý) vốn không làm được
    // bằng WebView (xem giải thích trong hội thoại) - giữ app đơn giản, ổn định hơn.
    // ĐÃ XOÁ HẲN: thanh điều hướng nổi Back/Start/Search (WpNavBar), App Bar kiểu WP theo yêu
    // cầu - không còn nút Back/Start nổi nào che màn hình nữa (dùng đúng cử chỉ/nút Back thật
    // của hệ thống, xem doBack()).

    // Nút "Off" nổi - BỔ SUNG LẠI theo yêu cầu (đã từng bị xoá cùng đợt xoá lớn trước đó), cùng
    // kiểu nút tròn nổi kéo-thả tự "hít" vào cạnh (xem FloatingBackButton), nhưng bấm vào sẽ
    // phủ màn hình "giả tắt" (FakeScreenOff) thay vì lùi trang - dùng khi đang xem video
    // (YouTube...) muốn "tắt màn hình" tạm thời (video/nhạc vẫn phát, chỉ chặn chạm nhầm) mà
    // không phải tắt màn hình thật của máy (tắt thật thì YouTube tự dừng video).
    private var floatingOffButtonHandle: FloatingBackButton.Handle? = null


    // Video/trang toàn màn hình HTML5 (xem onShowCustomView/onHideCustomView) - dùng chung với
    // logic chia 3 màn hình: khi đang ngang, customView (nếu có) sẽ là ô đầu tiên của chia 3
    // thay cho webView; khi đang dọc, customView hiện toàn màn hình bình thường trong
    // fullscreenContainer.
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    // Cờ đánh dấu lần loadUrl() TIẾP THEO là do CODE tự gọi (navigateTo(), mở lại tab đã lưu...)
    // chứ không phải do người dùng bấm link/gõ địa chỉ trong trang - dùng ở
    // shouldOverrideUrlLoading/onPageStarted để phân biệt 2 trường hợp này (ví dụ: không lưu lại
    // "địa chỉ đang xem" hoặc không chạy 1 số logic chỉ áp dụng cho điều hướng do NGƯỜI DÙNG chủ
    // động bấm). ĐÃ BỊ XOÁ NHẦM lúc gộp companion object trùng (dọn code) - thêm lại đúng chỗ cũ.
    private var programmaticLoad = false

    companion object {
        const val REQ_PERMISSIONS = 101
        const val REQ_SPEECH = 201
        const val DOWNLOAD_FOLDER = "AdBlockBrowser"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // FIX khoảng đen dư ở trên cùng: dù đã setDecorFitsSystemWindows(false), 1 số máy vẫn tự
        // đệm (padding) view gốc theo chiều cao thanh trạng thái/điều hướng theo cơ chế insets
        // kiểu cũ, tạo ra khoảng đen trống phía trên/dưới nội dung thật. Gắn listener KHÔNG áp
        // dụng padding gì cả (chỉ trả nguyên insets) để ép nội dung tràn hết viền màn hình.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootFrame)) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        enableImmersiveMode()

        // Khoá ứng dụng (PIN/Hình) đã được GỠ BỎ theo yêu cầu - vào thẳng màn chính, không cần
        // mở khoá gì cả.
        initAfterUnlock()
    }

    private fun initAfterUnlock() {
        AdBlocker.init(applicationContext)
        AdBlocker.enabled = true // luôn bật, không cho tắt

        webView = findViewById(R.id.webView)
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT) // Trong suốt: để nền rootFrame (app_bg, đổi theo theme) lộ ra thay vì đen cứng - tránh nháy đen/trắng khi tải trang
        // Thanh tiến trình tải trang mỏng kiểu Windows Phone (IE Mobile/Edge): 1 vạch phẳng màu
        // NHẤN nằm sát cạnh trên màn hình, không bo góc, không đổ bóng - trước đây bị xoá hẳn
        // khỏi layout (progressBar = null cứng) nên lúc tải trang KHÔNG còn dấu hiệu gì cho biết
        // trang đang load, thiếu hẳn 1 chi tiết đặc trưng của trình duyệt WP thật. Dựng lại bằng
        // code (giống cách IncognitoActivity đã làm) để luôn lên đúng màu
        // nhấn người dùng vừa chọn ở Cài đặt, không cần build lại app.
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF0078D7.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            visibility = View.GONE
        }
        findViewById<FrameLayout>(R.id.rootFrame).addView(
            progressBar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).also {
                it.gravity = Gravity.TOP
            }
        )
        homeOverlay = findViewById(R.id.homeOverlay)

        // Khởi tạo màn hình chính - chỉ còn 2 icon cố định (YouTube, Ẩn danh)
        homeScreenManager = HomeScreenManager(
            this,
            onOpenShortcut = { item -> openShortcutByKey(item.key) }
        )
        val homeContainer = homeOverlay as android.widget.FrameLayout
        homeContainer.addView(homeScreenManager.build())

        // ĐÃ BỎ xin quyền (camera/mic/vị trí...) ngay lúc mở app - không còn hộp thoại xin quyền
        // nào bật lên khi vừa vào app nữa. Các quyền này giờ CHỈ có tác dụng nếu người dùng tự
        // vào Cài đặt máy > Ứng dụng > cấp tay; nếu chưa cấp, trang web nào xin camera/mic/vị
        // trí sẽ tự bị từ chối lặng lẽ (xem onPermissionRequest/onGeolocationPermissionsShowPrompt
        // bên dưới - đã có sẵn logic kiểm tra quyền hệ thống trước khi cấp cho trang web).
        setupWebView()
        fullscreenContainer = FrameLayout(this).apply { visibility = View.GONE }
        findViewById<FrameLayout>(R.id.rootFrame).addView(
            fullscreenContainer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        // ĐÃ GỠ HẲN thanh điều hướng nổi Back/Start/Search (WpNavBar) theo yêu cầu - dùng đúng
        // cử chỉ/nút Back thật của hệ thống, xem onBackPressed()/doBack().
        addFloatingOffButton()

        // YÊU CẦU MỚI: ẨN HẲN trang Start (màn hình chọn YouTube/Duyệt web) - mỗi lần mở app vào
        // THẲNG YouTube luôn, không còn dừng ở Start nữa (homeOverlay vẫn được dựng ở trên,
        // nhưng từ giờ không còn được hiện ra nữa - kể cả nhánh dự phòng khi crash cũng đã đổi
        // sang vào lại YouTube, xem recreateWebViewAfterCrash()). Nếu Activity
        // được mở kèm extra "initial_url" (vd shortcut ngoài màn hình gắn sẵn 1 URL cụ thể) thì
        // ưu tiên dùng đúng URL đó; không có thì mặc định luôn vào thẳng trang chủ YouTube.
        val startUrl = intent.getStringExtra("initial_url") ?: "https://www.youtube.com"
        navigateTo(startUrl)
    }

    override fun onResume() {
        super.onResume()
        // Cho WebView chạy lại bình thường (đối xứng với onPause() ở dưới).
        if (::webView.isInitialized) webView.onResume()
        if (::homeScreenManager.isInitialized) homeScreenManager.refreshPages()
        // Đọc lại vị trí nút "Off" nổi mới nhất (có thể vừa bị kéo sang chỗ khác trong lúc màn
        // hình này ở nền) - xem giải thích đồng bộ ở FloatingBackButton.kt.
        floatingOffButtonHandle?.resync()
    }

    /** Dựng lại WebView mới TOÀN BỘ để thay cho WebView cũ đã bị crash renderer (xem
     *  onRenderProcessGone() ở setupWebView()) - KHÔNG được gọi lại bất kỳ hàm nào trên WebView
     *  cũ vì tiến trình render của nó đã chết, gọi vào sẽ tự app crash tiếp. Gỡ WebView cũ khỏi
     *  rootFrame, huỷ hẳn nó, tạo WebView mới đúng vị trí/kích thước cũ, gắn lại đầy đủ
     *  settings/WebViewClient/WebChromeClient qua setupWebView() rồi tải lại đúng trang đang xem
     *  (nếu có) - người dùng chỉ thấy trang tải lại chứ KHÔNG bị đá về màn hình Start/mất cả app. */
    private fun recreateWebViewAfterCrash(crashedUrl: String?) {
        val rootFrame = findViewById<FrameLayout>(R.id.rootFrame)
        val oldWebView = webView
        val index = rootFrame.indexOfChild(oldWebView).let { if (it < 0) 0 else it }
        val params = oldWebView.layoutParams

        rootFrame.removeView(oldWebView)
        // destroy() phải gọi SAU KHI đã gỡ khỏi cây view, và bọc try/catch vì WebView có renderer
        // đã chết đôi khi tự ném lỗi ngay trong lúc dọn dẹp.
        try { oldWebView.destroy() } catch (e: Exception) { }

        webView = WebView(this).apply { id = R.id.webView }
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        rootFrame.addView(webView, index, params)

        setupWebView()

        Toast.makeText(
            this, "Trang web vừa gặp sự cố khi đổi giao diện, đang tải lại...", Toast.LENGTH_SHORT
        ).show()

        if (crashedUrl != null && homeOverlay.visibility != View.VISIBLE) {
            // Đang xem 1 trang (không phải màn hình Start) lúc crash -> tải lại đúng trang đó.
            navigateTo(crashedUrl)
        } else {
            // Không rõ trang đang xem (hiếm khi xảy ra) -> Start đã bị ẩn hẳn theo yêu cầu, vào
            // thẳng lại trang chủ YouTube cho an toàn thay vì hiện Start.
            navigateTo("https://www.youtube.com")
        }
    }

    private val pauseRetryHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Dừng phát TẤT CẢ thẻ <video> VÀ <audio> đang có trên trang hiện tại trong WebView (kể cả
     *  video YouTube dạng bình thường chưa fullscreen, và các trang nghe nhạc dùng thẻ <audio>
     *  như Zing MP3, SoundCloud, NhacCuaTui...) - dùng mỗi khi người dùng rời khỏi trang/rời
     *  khỏi app để tiếng không tiếp tục phát ngầm ngoài ý muốn.
     *  FIX (lỗi #5): trước đây chỉ pause thẻ <video>, bỏ sót thẻ <audio>.
     *  FIX (YouTube vẫn phát sau khi rời app): evaluateJavascript() ở đây là lệnh "bắn rồi
     *  thôi" (bất đồng bộ) - nếu webView.onPause() được gọi ngay sau đó (ở onPause()/
     *  onUserLeaveHint()) làm WebView tạm ngưng xử lý ĐÚNG lúc lệnh JS này chưa kịp chạy tới,
     *  video/nhạc có thể lỡ không bị dừng dù code trông như đã gọi pause(). Gọi lại 1 lần nữa
     *  sau 150ms (khi WebView chắc chắn đã xử lý xong lượt đầu) để đảm bảo chắc chắn dừng hẳn. */
    private fun pauseAllVideos() {
        if (!::webView.isInitialized) return
        val js = "(function(){" +
            "var els=document.querySelectorAll('video,audio');" +
            "for(var i=0;i<els.length;i++){try{els[i].pause();}catch(e){}}" +
            "})();"
        webView.evaluateJavascript(js, null)
        pauseRetryHandler.postDelayed({
            if (::webView.isInitialized) webView.evaluateJavascript(js, null)
        }, 150)
    }

    private fun hideHomeOverlay() {
        homeOverlay.visibility = View.GONE
        // Đã ẩn hẳn thanh địa chỉ dưới cùng theo yêu cầu - không hiện lại kể cả khi đang xem
        // trang web. Điều hướng dùng trang chủ (icon/tìm kiếm) + nút Back tròn nổi.
    }

    // ĐÃ XOÁ HẲN: chuyển đổi bản máy tính/di động (User-Agent tuỳ chỉnh) theo yêu cầu - WebView
    // giờ luôn dùng User-Agent MẶC ĐỊNH của hệ thống, không còn ép UA riêng cho bất kỳ trang nào.

    // ĐÃ XOÁ HẲN: chia sẻ trang đang xem (shareCurrentPage) và xoá dữ liệu duyệt web
    // (clearBrowsingData) - 2 hàm này không được gọi từ đâu cả (không còn App Bar/menu nào gắn
    // vào chúng trong bản rút gọn hiện tại của app, và [SettingsActivity] mà comment cũ nhắc tới
    // cũng không tồn tại trong app này).

    // ĐÃ XOÁ HẲN: xin quyền (camera/mic/vị trí/thông báo...) ngay lúc mở app - không còn hộp
    // thoại xin quyền nào bật lên khi vừa vào app nữa (xem giải thích ở onCreate()).

    // ---------- Điều hướng ----------

    private fun navigateTo(url: String) {
        hideHomeOverlay()
        programmaticLoad = true
        webView.loadUrl(url)
    }

    // Màn hình chính (MainActivity) chỉ có ĐÚNG 1 WebView, không có khái niệm "nhiều tab".

    // ĐÃ XOÁ HẲN: clearAllSessionData() (xoá lịch sử/cache/cookie) - hàm này không được gọi từ
    // đâu cả trong app này (comment cũ nói "gọi qua nút Xoá dữ liệu trong Settings" nhưng app
    // không có màn Settings nào).

    // ---------- Menu đề xuất trang (tam giác) ----------

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Kết quả nhận dạng giọng nói từ RecognizerIntent (YouTube bấm icon mic tìm kiếm):
        // lấy văn bản nhận dạng được rồi inject vào ô tìm kiếm YouTube qua JS.
        if (requestCode == REQ_SPEECH && resultCode == RESULT_OK && data != null) {
            val text = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return
            // Điền text vào input tìm kiếm YouTube và trigger search
            // Escape text để dùng an toàn trong JS string
            val textEscaped = text.replace("\\", "\\\\").replace("'", "\\'")
            val js = "(function(){" +
                "var q=document.querySelector('input#search,input[name=search_query],ytm-searchbox input');" +
                "if(q){" +
                "q.value='" + textEscaped + "';" +
                "q.dispatchEvent(new Event('input',{bubbles:true}));" +
                "var f=q.closest('form');if(f){f.submit();}else{" +
                "q.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',keyCode:13,bubbles:true}));}" +
                "}else{" +
                "window.location.href='https://www.youtube.com/results?search_query='+encodeURIComponent('" + textEscaped + "');" +
                "}})();"
            if (::webView.isInitialized) webView.evaluateJavascript(js, null)
        }
    }

    // Kết quả hộp thoại xin quyền HỆ THỐNG vừa bật lên ĐÚNG LÚC trang web cần (camera/mic/vị
    // trí) - xem onPermissionRequest/onGeolocationPermissionsShowPrompt ở setupWebView(). Đọc
    // lại quyền THẬT sự đã được cấp (không tin thẳng mảng grantResults - có thể sai lệch nếu
    // vượt quá 1 hộp thoại chồng nhau) rồi trả lời cho đúng callback đang chờ (nếu có).
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSIONS) return

        pendingWebPermissionRequest?.let { request ->
            pendingWebPermissionRequest = null
            val granted = request.resources.filter { resource ->
                when (resource) {
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    else -> true
                }
            }
            if (granted.isNotEmpty()) request.grant(granted.toTypedArray()) else request.deny()
            if (granted.size < request.resources.size) {
                Toast.makeText(this, "Chưa cấp quyền micro/camera - trang web sẽ không dùng được tính năng này", Toast.LENGTH_LONG).show()
            }
        }

        if (pendingGeoCallback != null) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
            if (!granted) {
                Toast.makeText(this, "Chưa cấp quyền vị trí - trang web sẽ không dùng được tính năng này", Toast.LENGTH_LONG).show()
            }
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }
    }

    private fun openShortcutByKey(key: String) {
        val item = ShortcutsRepository.ALL[key] ?: return
        if (item.type == ShortcutType.WEB) {
            navigateTo(item.target)
        } else {
            // "YouTube + Ẩn danh" (nếu có) mã hoá target dạng "IncognitoActivity:<url ban đầu>"
            // để mở luôn tab YouTube ngay khi vào Ẩn danh, thay vì phải tự gõ địa chỉ. Các
            // shortcut ACTIVITY khác chỉ cần đúng tên class, không có phần ":<url>".
            val parts = item.target.split(":", limit = 2)
            val activityName = parts[0]
            val initialUrl = parts.getOrNull(1)
            val activityClass = when (activityName) {
                "IncognitoActivity" -> IncognitoActivity::class.java
                else -> null
            }
            if (activityClass != null) {
                val intent = Intent(this, activityClass)
                if (initialUrl != null) intent.putExtra("initial_url", initialUrl)
                startActivityWp(intent)
                // "Thoát khỏi app con là thoát HẲN app luôn" - MainActivity tự đóng NGAY sau khi
                // mở Ẩn danh, để Ẩn danh là activity DUY NHẤT còn lại trong ngăn xếp; bấm Back
                // thoát Ẩn danh (hết lịch sử trang, xem onBackPressed() ở IncognitoActivity) sẽ
                // đóng LUÔN cả app - không còn quay lại được màn Start của MainActivity nữa. Lần
                // sau mở lại app (từ icon ngoài màn hình) sẽ tạo MainActivity MỚI, về đúng Start
                // sạch từ đầu.
                finish()
            }
        }
    }

    // ---------- (Đã bỏ hộp thoại xác nhận mở liên kết - link bấm trong trang mở luôn) ----------

    // ---------- Tải video đang xem về máy ----------

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim().take(50)
        return if (cleaned.isBlank()) "video_${System.currentTimeMillis()}" else cleaned
    }

    inner class VideoDownloadBridge {
        @JavascriptInterface
        fun downloadVideo(url: String, title: String) {
            runOnUiThread {
                if (url.isBlank()) {
                    Toast.makeText(this@MainActivity, "Không tìm thấy video trên trang này", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (url.startsWith("blob:")) {
                    Toast.makeText(
                        this@MainActivity,
                        "Trang này (vd. YouTube) mã hoá luồng video, không thể tải trực tiếp kiểu này",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }
                try {
                    val fileName = sanitizeFileName(title) + ".mp4"
                    val request = DownloadManager.Request(Uri.parse(url))
                    request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    request.addRequestHeader("User-Agent", webView.settings.userAgentString)
                    request.setTitle(fileName)
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_MOVIES, "$DOWNLOAD_FOLDER/$fileName"
                    )
                    val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(
                        this@MainActivity,
                        "Đang tải về thư mục Movies/$DOWNLOAD_FOLDER...",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Không tải được video này", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---------- WebView ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        // Cho phép trang web (Google Maps...) xin vị trí thật của máy - mặc định WebView chặn
        // hoàn toàn API định vị của trình duyệt (navigator.geolocation) nếu không bật dòng này.
        webView.settings.setGeolocationEnabled(true)

        webView.addJavascriptInterface(VideoDownloadBridge(), "AndroidDownloader")
        // Bridge nhận dạng giọng nói: YouTube bấm icon mic -> JS gọi AndroidSpeech.startListening()
        // -> app launch RecognizerIntent hệ thống -> kết quả trả về qua onActivityResult ->
        // evaluateJavascript điền text vào ô tìm kiếm YouTube.
        webView.addJavascriptInterface(SpeechBridge(), "AndroidSpeech")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar?.progress = newProgress
                progressBar?.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            // Chặn popup / tab mới do quảng cáo tự mở (window.open)
            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?
            ): Boolean = false

            // ── Video/trang toàn màn hình HTML5 (nút phóng to của YouTube, hoặc YouTube TỰ
            // BẬT fullscreen khi xoay ngang) ── QUAN TRỌNG: nếu không bắt sự kiện này, trang sẽ
            // tự xử lý fullscreen theo kiểu riêng của nó (phóng to video trong khung WebView cũ),
            // hoàn toàn bỏ qua logic chia 3 màn hình của app. Bắt lấy customView này rồi đưa vào
            // refreshLayoutMode() để nó được xử lý CHUNG với webView chính (chia 3 nếu đang
            // ngang, toàn màn hình nếu đang dọc).
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                refreshLayoutMode()
            }

            override fun onHideCustomView() {
                if (customView == null) return
                (customView?.parent as? ViewGroup)?.removeView(customView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                refreshLayoutMode()
            }

            // KHÔNG còn xin quyền sẵn lúc mở app - lúc trang web xin camera/mic, kiểm tra quyền
            // HỆ THỐNG đã có chưa: có rồi thì cấp luôn; CHƯA có thì tự bật hộp thoại xin quyền hệ
            // thống ngay lúc này (xem onRequestPermissionsResult - quyết định cấp/từ chối cho
            // trang web SAU khi có kết quả, không cấp khống như trước để tránh lỗi "mở mic là
            // tắt liền" do thiếu quyền hệ thống đứng sau).
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val neededPerms = LinkedHashSet<String>()
                request.resources.forEach { resource ->
                    when (resource) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> neededPerms.add(Manifest.permission.RECORD_AUDIO)
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> neededPerms.add(Manifest.permission.CAMERA)
                    }
                }
                val missing = neededPerms.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    request.grant(request.resources)
                } else {
                    pendingWebPermissionRequest = request
                    ActivityCompat.requestPermissions(this@MainActivity, missing.toTypedArray(), REQ_PERMISSIONS)
                }
            }

            // Tự cấp quyền VỊ TRÍ THẬT cho trang web (Google Maps...) khi trang yêu cầu qua
            // navigator.geolocation - có quyền hệ thống rồi thì cấp luôn; CHƯA có thì tự bật
            // hộp thoại xin quyền vị trí ngay lúc này thay vì chỉ từ chối lặng lẽ như trước.
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    callback?.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        REQ_PERMISSIONS
                    )
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val host = request?.url?.host
                return if (AdBlocker.isAd(host)) {
                    AdBlocker.blockedResponse()
                } else {
                    super.shouldInterceptRequest(view, request)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val scheme = request.url.scheme ?: ""

                // Điều hướng do chính app gọi (thanh địa chỉ, menu, mở lại tab...) -> cho qua luôn
                if (programmaticLoad) {
                    programmaticLoad = false
                    return if (scheme == "http" || scheme == "https") false else true
                }

                // Link không phải web (intent://, market://, tel:, mailto:, app riêng...) ->
                // MỞ THẬT bằng app tương ứng trên máy (Intent.ACTION_VIEW) thay vì chặn âm thầm
                // không làm gì cả như trước.
                if (scheme != "http" && scheme != "https") {
                    try {
                        val intent = if (scheme == "intent") {
                            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        } else {
                            Intent(Intent.ACTION_VIEW, request.url)
                        }
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Không có app nào mở được link này -> bỏ qua, không làm gì thêm
                    }
                    return true
                }

                // Link do người dùng bấm trong trang -> mở thẳng luôn, KHÔNG hỏi xác nhận
                return false
            }

            // FIX "màn hình đen" khi mở YouTube (hoặc trang bất kỳ): trước đây KHÔNG bắt lỗi tải
            // trang nào cả -> nếu trang tải thất bại (mất mạng, DNS lỗi, timeout, lỗi chứng chỉ...)
            // WebView chỉ đứng im, không hiển thị gì, mà nền WebView lại bị đặt cứng màu ĐEN (xem
            // initAfterUnlock() - để tránh chớp trắng lúc mới vào) -> kết quả là 1 màn hình đen
            // tuyệt đối, không có bất kỳ thông báo lỗi nào cho người dùng biết chuyện gì đã xảy ra.
            // Giờ bắt lỗi ở KHUNG CHÍNH (isForMainFrame - bỏ qua lỗi của các tài nguyên phụ như
            // ảnh/quảng cáo bị chặn) và báo rõ nguyên nhân + tự thử tải lại 1 lần.
            private var lastErrorReloadAt = 0L

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Không tải được trang (${error?.description ?: "lỗi mạng"}). Đang thử lại...",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // Tự thử tải lại 1 lần (tối đa 1 lần mỗi 3 giây để tránh lặp vô hạn nếu mất mạng
                // hẳn) - nhiều trường hợp chỉ là lỗi mạng thoáng qua lúc mới bật WebView.
                val now = System.currentTimeMillis()
                if (now - lastErrorReloadAt > 3000) {
                    lastErrorReloadAt = now
                    view?.postDelayed({ view.reload() }, 800)
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val code = errorResponse?.statusCode ?: return
                if (request?.isForMainFrame == true && code >= 400) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity, "Máy chủ trả lỗi $code khi tải trang", Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                // KHÔNG tự ý bỏ qua lỗi chứng chỉ (mất an toàn) - chỉ báo rõ cho người dùng biết
                // vì sao trang không tải được thay vì để màn hình đen im lặng như trước.
                Toast.makeText(
                    this@MainActivity,
                    "Lỗi chứng chỉ bảo mật khi tải trang - đã chặn để an toàn",
                    Toast.LENGTH_LONG
                ).show()
                handler?.cancel()
            }

            // FIX "đổi màu nền văng ra màn hình start": khi người dùng đổi theme (sáng/tối) trên
            // YouTube, tiến trình render Chromium của WebView đôi khi bị crash (renderer process
            // gone) do phải vẽ lại toàn bộ theme cùng lúc. TRƯỚC ĐÂY không override hàm này ->
            // hành vi MẶC ĐỊNH của Android là kill LUÔN CẢ APP khi renderer chết -> app khởi động
            // lại từ đầu -> người dùng thấy "văng" thẳng về màn hình Start. Giờ tự bắt sự kiện
            // này: gỡ WebView cũ (đã hỏng, không dùng lại được) ra khỏi layout, hủy nó, tạo lại
            // WebView mới rồi tải lại đúng trang đang xem - app không bị kill, người dùng chỉ
            // thấy trang tải lại chứ không bị đá về Start.
            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                val crashedUrl = view?.url
                runOnUiThread {
                    recreateWebViewAfterCrash(crashedUrl)
                }
                // Trả về true để báo cho hệ thống là app ĐÃ TỰ XỬ LÝ xong sự cố này rồi -
                // không cần hệ thống can thiệp/kill app nữa.
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateOffButtonVisibility(url)
                // Đổi trang xong (có thể vừa vào/rời YouTube) -> cập nhật lại ngay thanh trạng
                // thái/điều hướng hệ thống có nên hiện hay ẩn (xem applySystemBarsForCurrentState()).
                applySystemBarsForCurrentState()
                // XOÁ LỊCH SỬ ngay khi trang chủ YouTube load xong: dù người dùng về đây bằng
                // cách nào (back từ video, back từ tìm kiếm, hay tự gõ URL...) thì đến trang chủ
                // là lịch sử bị xoá sạch ngay lập tức - không còn trang tìm kiếm/video nào có
                // thể quay lại được nữa kể cả khi vào Lịch sử/Đã lưu rồi back nhiều lần.
                if (YoutubeAdSkipper.isYoutubeHome(url)) {
                    view?.post { webView.clearHistory() }
                }
                // BUG ĐÃ SỬA: trước đây dòng evaluateJavascript(AdOverlayBlocker.JS) này nằm
                // NGOÀI nhánh if/else bên dưới nên chạy TRÊN CẢ YouTube, dù comment ngay dưới ghi
                // rõ ý định "AdOverlayBlocker KHÔNG chạy trên YouTube" - code và comment MÂU
                // THUẪN nhau. Hậu quả thực tế: killOverlays() (quét mọi 2 giây, ẩn + tắt
                // pointer-events mọi phần tử phủ ≥85% màn hình có z-index cao) coi luôn lớp phủ
                // mờ (scrim) của hộp thoại YouTube (ví dụ hộp thoại "Lưu vào xem sau" mở từ nút
                // 3 chấm ở trang chủ) là "quảng cáo full-screen" nên tự ý ẩn nó đi - khiến hộp
                // thoại không tài nào tắt được nữa (chạm ra ngoài, vuốt xuống đều vô tác dụng vì
                // lớp nhận chạm để đóng hộp thoại đã bị pointer-events:none). Nay chuyển đúng vào
                // nhánh else bên dưới (chỉ trang KHÔNG PHẢI YouTube) như comment đã mô tả.
                if (YoutubeAdSkipper.isYoutube(url)) {
                    // AdOverlayBlocker KHÔNG chạy trên YouTube - nó dùng querySelectorAll('body *')
                    // quét toàn bộ DOM mỗi 2 giây, YouTube có hàng nghìn element -> gây lag nặng,
                    // lại còn dễ ẩn nhầm chính giao diện thật của YouTube (menu/hộp thoại...) như
                    // lỗi đã tìm thấy ở trên. YoutubeAdSkipper đã xử lý overlay quảng cáo YouTube
                    // rồi, không cần AdOverlayBlocker chạy thêm.
                    view?.evaluateJavascript(YoutubeAdSkipper.JS, null)
                    // Inject bridge mic: gắn AndroidSpeech.startListening() vào nút mic của YouTube
                    view?.evaluateJavascript(YoutubeMicBridge.JS, null)
                } else {
                    view?.evaluateJavascript(AdOverlayBlocker.JS, null)
                    // Nút "Tải về" (VideoDownloadUI) KHÔNG chèn trên YouTube - vì YouTube mã
                    // hoá luồng video dạng blob: nên nút này bấm vào không tải được gì cả (xem
                    // giải thích ở VideoDownloadUI), chỉ án ngữ giao diện vô ích. Các trang khác
                    // có video link file trực tiếp (mp4/webm...) vẫn hiện nút bình thường.
                    view?.evaluateJavascript(VideoDownloadUI.JS, null)
                }
            }
        }
    }

    // Logic Back DÙNG CHUNG cho cả nút Back trên thanh điều hướng nổi VÀ nút/cử chỉ back vật lý
    // của điện thoại, để 2 nơi luôn nhất quán: lùi từng trang web đã xem -> hết thì về TRANG CHỦ
    // của app (không nhảy thẳng qua app khác) -> chỉ khi đã ở sẵn trang chủ rồi, bấm back thêm
    // lần nữa mới thực sự thoát app.
    //
    // RIÊNG YouTube: các trang con (xem video, kênh, tìm kiếm...) thường xếp chồng RẤT NHIỀU
    // trang trong lịch sử (mỗi lần chuyển video/kết quả tìm kiếm là 1 trang mới), lùi từng trang
    // một sẽ phải bấm Back rất nhiều lần mới ra khỏi YouTube. Nếu đang ở 1 trang con YouTube (và
    // KHÔNG ở sẵn trang chủ rồi) -> back NHẢY THẲNG về trang chủ YouTube (bỏ qua toàn bộ lịch sử
    // các trang con đã xem) thay vì lùi từng bước - NGOẠI TRỪ nếu trong lịch sử có 1 trang "ĐIỂM
    // DỪNG" gần nhất (trang KẾT QUẢ TÌM KIẾM đã gõ từ khoá, hoặc trang XEM SAU/LỊCH SỬ - xem
    // isYoutubeStopPage()) thì back sẽ dừng lại ở đúng trang đó TRƯỚC (xem findYoutubeStopIndex()),
    // để không mất kết quả tìm/danh sách đang xem dở; muốn về hẳn trang chủ YouTube thì bấm back
    // thêm 1 lần nữa từ trang đó. Đã ở trang chủ YouTube rồi thì back tiếp theo = THOÁT HẲN khỏi
    // YouTube (xem exitYoutube() - hàm này tự bỏ qua LUÔN mọi trang con YouTube còn sót lại,
    // bao gồm cả trang Xem sau/Lịch sử vừa dừng ở trên, nên back từ đây về sau sẽ KHÔNG bao giờ
    // quay lại trang Xem sau/Lịch sử đó nữa - đúng yêu cầu "xoá lịch sử, không back về nó nữa").
    fun doBack() {
        // Đang ở fullscreen HTML5 THẬT (người dùng tự bấm nút fullscreen của YouTube, hoặc
        // trang tự bật khi xoay ngang) -> Back chỉ thoát fullscreen bình thường.
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
            return
        }
        // Đọc trang hiện tại từ copyBackForwardList() - CÙNG NGUỒN DỮ LIỆU mà
        // findYoutubeStopIndex()/exitYoutube() ở dưới đã dùng - để không bao giờ bị lệch giữa
        // các hàm cùng xử lý 1 luồng logic Back (webView.url có thể cập nhật trễ hơn danh sách
        // back-forward thật lúc chuyển trang kiểu SPA chỉ đổi URL bằng pushState).
        val list = webView.copyBackForwardList()
        val currentUrl = list.currentItem?.url ?: webView.url
        when {
            webView.canGoBack() && YoutubeAdSkipper.isYoutube(currentUrl) && !YoutubeAdSkipper.isYoutubeHome(currentUrl) -> {
                val stopIndex = findYoutubeStopIndex()
                programmaticLoad = true
                if (stopIndex >= 0) {
                    // Có trang "điểm dừng" (tìm kiếm, hoặc Xem sau/Lịch sử) gần nhất trong lịch
                    // sử -> lùi ĐÚNG về trang đó (không tải lại từ đầu, chỉ tra danh sách
                    // back-forward có sẵn), thay vì nhảy thẳng qua luôn về trang chủ.
                    webView.goBackOrForward(stopIndex - list.currentIndex)
                } else {
                    webView.loadUrl("https://www.youtube.com")
                }
            }
            // ĐÃ Ở SẴN trang chủ YouTube (vd. do back lần trước vừa nhảy về đây) -> back lần
            // NÀY = THOÁT HẲN khỏi YouTube, không chỉ lùi 1 bước lịch sử (lùi 1 bước dễ vẫn còn
            // là 1 trang con khác của YouTube, ví dụ trang kênh/tìm kiếm còn sót trong lịch sử,
            // khiến người dùng cảm giác "chưa ra khỏi YouTube"). Xem exitYoutube().
            YoutubeAdSkipper.isYoutube(currentUrl) && YoutubeAdSkipper.isYoutubeHome(currentUrl) -> {
                exitYoutube()
            }
            webView.canGoBack() -> {
                programmaticLoad = true
                webView.goBack()
            }
            // Vẫn đang ở YouTube nhưng hết lịch sử (vd. sau clearHistory() ở trang chủ) ->
            // load lại trang chủ YouTube thay vì thoát app, tránh "back 1 lần nữa là văng app".
            YoutubeAdSkipper.isYoutube(currentUrl) -> {
                programmaticLoad = true
                webView.loadUrl("https://www.youtube.com")
            }
            // Hết lịch sử, không ở YouTube -> thoát app.
            else -> {
                super.onBackPressed()
            }
        }
    }

    /** Dò lịch sử NGƯỢC (không tải lại trang, chỉ tra danh sách back-forward có sẵn) từ vị trí
     *  hiện tại để tìm trang "ĐIỂM DỪNG" YouTube gần nhất - dùng cho doBack() ở trên: nếu có,
     *  back sẽ dừng ở đúng trang đó trước khi về hẳn trang chủ, thay vì nhảy thẳng qua. "Điểm
     *  dừng" gồm: trang KẾT QUẢ TÌM KIẾM (đã gõ từ khoá), hoặc trang XEM SAU/LỊCH SỬ (yêu cầu
     *  thêm: mở video từ Xem sau/Lịch sử rồi back phải quay lại ĐÚNG danh sách đó trước, không
     *  nhảy tọt qua luôn về trang chủ) - xem isYoutubeStopPage(). Trả về -1 nếu không có trang
     *  điểm dừng nào trong lịch sử phía trước trang hiện tại. */
    private fun findYoutubeStopIndex(): Int {
        val list = webView.copyBackForwardList()
        val currentIndex = list.currentIndex
        for (i in currentIndex - 1 downTo 0) {
            val url = list.getItemAtIndex(i)?.url
            if (YoutubeAdSkipper.isYoutubeStopPage(url)) return i
        }
        return -1
    }

    /** Thoát hẳn khỏi YouTube khi đang đứng ở trang chủ YouTube: duyệt lịch sử NGƯỢC (không tải
     *  lại trang, chỉ tra danh sách có sẵn) để tìm trang GẦN NHẤT KHÔNG PHẢI YouTube - nếu có,
     *  nhảy thẳng về đúng trang đó (bỏ qua mọi trang con YouTube còn sót ở giữa: kênh, tìm
     *  kiếm, video khác đã xem...). Nếu TOÀN BỘ lịch sử phía trước đều là YouTube (vd. mở
     *  YouTube ngay từ trang chủ app, chưa từng duyệt trang nào khác trước đó) -> không còn gì
     *  để lùi về nữa, "thoát khỏi YouTube" = thoát HẲN app luôn (không dừng ở Start). */
    private fun exitYoutube() {
        val list = webView.copyBackForwardList()
        val currentIndex = list.currentIndex
        var targetIndex = -1
        for (i in currentIndex - 1 downTo 0) {
            val url = list.getItemAtIndex(i)?.url
            if (!YoutubeAdSkipper.isYoutube(url)) {
                targetIndex = i
                break
            }
        }
        // XOÁ LỊCH SỬ YouTube ngay khi thoát khỏi YouTube (về trang chủ app hoặc thoát app):
        // tránh lỗi "vào lịch sử/đã lưu xem video, back vài lần lại về trang tìm kiếm cũ" -
        // vì trang tìm kiếm vẫn còn sót trong back-forward list dù người dùng đã về trang chủ
        // YouTube rồi back thêm 1 lần. Xoá ngay tại đây để lần vào YouTube kế tiếp bắt đầu sạch.
        webView.clearHistory()
        if (targetIndex >= 0) {
            programmaticLoad = true
            webView.loadUrl(list.getItemAtIndex(targetIndex)?.url ?: "https://www.youtube.com")
        } else {
            super.onBackPressed()
        }
    }

    override fun onBackPressed() {
        doBack()
    }

    // Gọi TỰ ĐỘNG bởi Android ngay trước khi Activity bị đưa xuống nền do HÀNH ĐỘNG CỦA NGƯỜI
    // DÙNG (bấm nút Home, vuốt sang app khác, mở Recents...).
    //
    // Ý ĐỊNH: nhạc/video đang phát PHẢI tiếp tục chạy nền như 1 app nghe nhạc bình thường khi
    // rời app (tắt màn hình, bấm Home, chuyển app khác) - CHỈ dừng khi người dùng tự bấm dừng
    // trên trang, hoặc khi tiến trình app bị dọn hẳn (vuốt xoá khỏi Recents). Vì vậy KHÔNG gọi
    // pauseAllVideos()/webView.onPause() ở đây - để mặc WebView tiếp tục xử lý JS/video/audio
    // bình thường dù activity không còn hiển thị.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }

    // Gọi khi Activity bị đưa xuống nền vì BẤT KỲ lý do gì (Home, chuyển app khác, tắt màn
    // hình, mở app khác đè lên...). KHÔNG dừng video/audio ở đây (xem giải thích ở
    // onUserLeaveHint() phía trên) - để nhạc/video tiếp tục phát nền đúng như yêu cầu.
    override fun onPause() {
        super.onPause()
        // Flush cookie xuống disk ngay khi app ra nền - tránh mất cookie nếu OS kill process
        // trước khi Android kịp tự flush (mặc định Android flush lazy, không đảm bảo thời điểm).
        android.webkit.CookieManager.getInstance().flush()
    }

    override fun onStop() {
        super.onStop()
        // XOÁ LỊCH SỬ YouTube khi app bị đưa hẳn xuống nền (thoát app, vuốt khỏi recents...):
        // nếu đang xem video rồi thoát thẳng app (không bấm back về trang chủ YouTube), lịch sử
        // tìm kiếm vẫn còn trong WebView -> vào lại app lần sau back vài lần lại về trang tìm
        // kiếm cũ. Xoá ở đây để mỗi lần mở lại app luôn bắt đầu lịch sử sạch.
        if (::webView.isInitialized) webView.clearHistory()
    }

    /** Bố cục "ô đầu tiên" (pane1) LUÔN LUÔN là customView (video HTML5 đang toàn màn hình,
     *  nếu có) HOẶC webView chính (bình thường). Hàm này XÂY LẠI layout cho khớp với việc có
     *  đang fullscreen video hay không, mỗi khi bật/tắt fullscreen video. */
    private fun refreshLayoutMode() {
        val root = findViewById<FrameLayout>(R.id.rootFrame)
        val pane1: View = customView ?: webView
        (pane1.parent as? ViewGroup)?.removeView(pane1)
        fullscreenContainer.removeAllViews()
        fullscreenContainer.visibility = View.GONE

        if (customView != null) {
            pane1.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            fullscreenContainer.addView(pane1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            fullscreenContainer.visibility = View.VISIBLE
            // FIX "không bấm được nút cài đặt (bánh răng) góc phải phía trên lúc phóng to video":
            // app dùng BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE (xem enableImmersiveMode()) để ẩn
            // thanh trạng thái/điều hướng - kiểu ẩn này khiến Android tự dành riêng vài dp SÁT
            // MÉP MÀN HÌNH (đặc biệt mép TRÊN) làm vùng cử chỉ "vuốt để hiện lại thanh hệ thống"
            // - MỌI lượt chạm rơi vào vùng đó bị hệ thống giữ lại cho cử chỉ này, KHÔNG bao giờ
            // truyền xuống tới trang web. Nút cài đặt của YouTube khi phóng to nằm rất sát mép
            // trên bên phải -> luôn rơi đúng vào vùng bị hệ thống chặn. setSystemGestureExclusionRects
            // báo cho Android biết "vùng này app tự xử lý, đừng cướp cử chỉ ở đây" - loại trừ
            // hẳn dải mép trên (nơi các nút điều khiển video như cài đặt/CC/toàn màn hình nằm)
            // để chạm luôn tới được trang web như bình thường.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                fullscreenContainer.post {
                    val w = fullscreenContainer.width
                    val h = fullscreenContainer.height
                    if (w > 0 && h > 0) {
                        val topBandPx = (56 * resources.displayMetrics.density).toInt()
                        fullscreenContainer.systemGestureExclusionRects = listOf(
                            android.graphics.Rect(0, 0, w, minOf(topBandPx, h))
                        )
                    }
                }
            }
        } else {
            pane1.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            root.addView(pane1, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                fullscreenContainer.systemGestureExclusionRects = emptyList()
            }
        }
    }


    // ĐÃ GỠ HẲN thanh điều hướng nổi Back/Start/Search (WpNavBar) theo yêu cầu - không còn nút
    // Back/Start nổi riêng của app nữa. Back giờ dùng đúng cử chỉ/nút Back thật của hệ thống
    // (vẫn được xử lý đầy đủ qua onBackPressed()/doBack()); hết lịch sử trang là thoát hẳn app
    // luôn, đúng tinh thần "thoát khỏi app con là thoát hẳn app luôn" đã yêu cầu.

    // Nút "Off" nổi - BỔ SUNG LẠI theo yêu cầu, bấm vào sẽ phủ màn hình "giả tắt" (xem
    // FakeScreenOff) thay vì lùi trang. Icon "⏻" (nút nguồn) để phân biệt rõ với mũi tên back.
    //
    // fixed = false: nút NỔI và KÉO-THẢ được tự do - kéo đi đâu tuỳ ý, thả tay tự "hít" vào cạnh
    // trái/phải gần nhất, vị trí được nhớ riêng (key theo id "off"). defaultYFraction = 0.7f để
    // lần đầu xuất hiện không trùng vị trí mặc định của các nút khác nếu có.
    //
    // CHỈ HIỆN KHI ĐANG Ở TRANG YOUTUBE: nút này dùng để giả tắt màn hình lúc nghe video/nhạc
    // YouTube chạy nền, không có ý nghĩa gì ở các trang khác -> ẩn đi (setVisible(false) ngay
    // sau khi attach) để đỡ chiếm chỗ/gây rối màn hình chính và các trang web khác.
    // updateOffButtonVisibility() (gọi ở onPageFinished) sẽ tự hiện lại đúng lúc vào YouTube.
    //
    // doubleTapOnly = false: chỉ cần CHẠM 1 LẦN là kích hoạt ngay (không cần double-tap) - vì
    // nút Off chỉ hiện đúng lúc đang ở YouTube nên ít nguy cơ chạm nhầm hơn.
    private fun addFloatingOffButton() {
        val root = findViewById<FrameLayout>(R.id.rootFrame)
        floatingOffButtonHandle = FloatingBackButton.attach(
            activity = this,
            root = root,
            // Truyền [webView] để FakeScreenOff tự hạ chất lượng video xuống thấp nhất lúc bật
            // (tiết kiệm CPU/GPU giải mã -> đỡ pin hơn khi không ai nhìn hình), và tự phục hồi
            // đúng chất lượng cũ lúc tắt lớp phủ - xem giải thích chi tiết ở FakeScreenOff.kt.
            //
            // onHide: sau khi lớp phủ tắt (chạm đúp vào đồng hồ), tự áp dụng lại ĐÚNG trạng thái
            // thanh trạng thái/điều hướng hệ thống của trang đang xem (có thể cần hiện lại CỐ
            // ĐỊNH nếu đang ở YouTube + máy đang dọc, xem applySystemBarsForCurrentState()) -
            // không tự làm gì thì 2 thanh này sẽ vẫn ở trạng thái ẩn cứng do FakeScreenOff áp
            // đặt lúc nãy, dù đáng lẽ phải hiện lại theo đúng quy tắc của trang hiện tại.
            onTap = {
                FakeScreenOff.show(this, webView, onHide = { applySystemBarsForCurrentState() })
                // Ẩn ngay lập tức thanh trạng thái + hàng nút đa nhiệm ngay khi chạm nút Off -
                // không đợi tới lần applySystemBarsForCurrentState() kế tiếp được gọi tự nhiên
                // (xoay máy/đổi focus/tải trang), vì lúc chạm nút Off không có sự kiện nào trong
                // số đó tự xảy ra.
                applySystemBarsForCurrentState()
            },
            id = "off",
            // ĐÃ ĐỔI từ icon chữ Unicode "⏻" sang hình vẽ cố định (useRectangleIcon) - ký tự "⏻"
            // hiển thị KHÔNG ổn định giữa các máy do phụ thuộc font hệ thống (có máy ra đúng
            // hình, có máy ra ô vuông dấu X ("tofu"), có máy không hiện gì) - xem giải thích đầy
            // đủ ở tham số [useRectangleIcon] trong FloatingBackButton.attach().
            useRectangleIcon = true,
            defaultIsRight = false,
            defaultYFraction = 0.7f,
            fixed = false,
            doubleTapOnly = false
        )
        floatingOffButtonHandle?.setVisible(false)
    }

    /** Hiện nút "Off" giả tắt màn hình CHỈ khi đang ở trang YouTube, ẩn đi ở mọi trang khác.
     *  Gọi mỗi khi trang tải xong (onPageFinished) để luôn khớp đúng trang hiện tại. */
    private fun updateOffButtonVisibility(url: String?) {
        floatingOffButtonHandle?.setVisible(YoutubeAdSkipper.isYoutube(url))
    }

    /** Bridge JS cho tính năng tìm kiếm giọng nói YouTube: trang web gọi
     *  AndroidSpeech.startListening() -> app mở hộp thoại nghe giọng nói hệ thống ->
     *  kết quả về onActivityResult -> inject vào ô tìm kiếm YouTube. */
    inner class SpeechBridge {
        @android.webkit.JavascriptInterface
        fun startListening() {
            runOnUiThread {
                try {
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                        putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Tìm kiếm trên YouTube")
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQ_SPEECH)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Máy chưa hỗ trợ nhận dạng giọng nói", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Thoát app -> xoá sạch mọi dấu vết phiên làm việc
    override fun onDestroy() {
        // KHÔNG xoá cookie/session khi thoát app - cookie phải được GIỮ LẠI để tài khoản
        // YouTube/Google (và mọi trang web khác) vẫn còn đăng nhập lần sau mở app.
        // Chỉ dọn view/window để tránh leak (không liên quan đến session).
        floatingOffButtonHandle?.detach()
        FakeScreenOff.hide()
        super.onDestroy()
    }
}

/**
 * Chặn quảng cáo bằng cách so khớp host của request với danh sách domain trong assets/blocklist.txt.
 */
object AdBlocker {

    private var domains: HashSet<String> = HashSet()
    private var loaded = false
    var enabled: Boolean = true

    fun init(context: android.content.Context) {
        if (loaded) return
        try {
            context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val d = line.trim().lowercase()
                    if (d.isNotEmpty() && !d.startsWith("#")) {
                        domains.add(d)
                    }
                }
            }
            loaded = true
        } catch (e: Exception) {
            loaded = true
        }
    }

    fun isAd(host: String?): Boolean {
        if (!enabled || host.isNullOrEmpty()) return false
        val h = host.lowercase()
        if (domains.contains(h)) return true
        var idx = h.indexOf('.')
        while (idx != -1) {
            val suffix = h.substring(idx + 1)
            if (domains.contains(suffix)) return true
            idx = h.indexOf('.', idx + 1)
        }
        return false
    }

    fun blockedResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}

/**
 * Tự động skip/tua qua quảng cáo trên YouTube bằng JS injection, và ẩn banner
 * "Mở trong ứng dụng YouTube".
 */
object YoutubeAdSkipper {

    const val JS = """
        (function() {
            if (window.__adSkipperRunning) return;
            window.__adSkipperRunning = true;

            // Lưu lại tốc độ phát do NGƯỜI DÙNG tự chọn (khác với tốc độ 30x do CHÍNH SCRIPT này
            // ép tạm để tua nhanh qua quảng cáo bên dưới) - để khôi phục lại ĐÚNG Ý người dùng
            // bất cứ khi nào bị lệch, không chỉ ngay sau quảng cáo. Ngoài lưu tạm trong biến JS
            // (mất khi tải lại trang), còn lưu thêm vào localStorage (giữ được cả khi Android hệ
            // thống thu hồi bộ nhớ nền rồi WebView tự tải lại trang lúc mở app trở lại) để khắc
            // phục 2 lỗi đã gặp: (1) lâu lâu tự rớt về 1x không rõ lý do, (2) bấm Home rồi mở lại
            // app cũng tự về 1x.
            var RATE_KEY = 'ytAdSkipperUserRate';
            var userRate = null;
            var userMuted = null;
            var scriptChanging = false;
            var lastAdShowing = false;
            // FIX "tốc độ 2x lâu lâu tự về 1x": trước đây biến "adShowing" (có quảng cáo đang
            // phát không) chỉ tồn tại CỤC BỘ bên trong setInterval, listener 'ratechange' bên
            // dưới KHÔNG biết được lúc rate đổi có phải đang giữa quảng cáo hay không - nên khi
            // CHÍNH YOUTUBE tự ép video về 1x lúc quảng cáo bắt đầu (hành vi chuẩn: quảng cáo
            // luôn phát 1x), listener tưởng nhầm đây là NGƯỜI DÙNG tự đổi -> lưu đè userRate=1
            // vào localStorage, làm mất tốc độ 2x đã chọn VĨNH VIỄN dù quảng cáo đã hết từ lâu.
            // Đưa "đang có quảng cáo hay không" ra biến CHUNG (isAdCurrentlyShowing) để listener
            // đọc được, bỏ qua rate change xảy ra đúng lúc đang có quảng cáo.
            var isAdCurrentlyShowing = false;

            // FIX MỚI - 3 tình huống rate bị "cướp" và lưu sai thành 1x dù người dùng KHÔNG hề tự
            // chọn 1x, đều có chung 1 gốc: listener 'ratechange' bên dưới coi MỌI thay đổi rate
            // không phải do scriptChanging/quảng cáo là "người dùng tự chọn" - nhưng thực tế
            // trình duyệt/chính YouTube còn tự đổi rate (không do người dùng bấm) ở nhiều tình
            // huống khác nữa:
            //   (1) Tua tới/lui (seeking): 1 số đoạn video tải lại qua MSE có thể tự trả rate về
            //       1 ngay khi tua, dù ô chọn tốc độ trên giao diện vẫn hiện đúng "2x".
            //   (2) Chuyển từ quảng cáo sang video thật: dù đã có cờ isAdCurrentlyShowing, thời
            //       điểm NGAY LÚC chuyển tiếp (video nội bộ đang tải lại nguồn) vẫn có thể lọt 1
            //       sự kiện ratechange rơi đúng khoảng cờ đã tắt nhưng UI chưa kịp ổn định.
            //   (3) Giữ tay trên video để tăng tốc tạm thời (cử chỉ có sẵn của YouTube) rồi buông
            //       ra: YouTube tự đổi rate về "tốc độ gốc" mà NÓ nhớ - nhưng vì tốc độ 2x của
            //       người dùng được SCRIPT NÀY ép bằng DOM trực tiếp (video.playbackRate = ...),
            //       không đi qua đúng luồng chọn tốc độ nội bộ của YouTube, nên YouTube tưởng
            //       "tốc độ gốc" là 1x (mặc định) chứ không phải 2x - buông tay ra là rate thật
            //       bị chính YouTube trả về 1x, và listener bên dưới lại tưởng NGƯỜI DÙNG vừa tự
            //       chọn 1x, lưu đè mất luôn tuỳ chọn 2x.
            // Giải pháp CHUNG cho cả 3: thêm 1 "vùng đệm tạm ngưng ghi nhận" (suppressRateCapture)
            // - trong lúc đang tua, đang giữ tay trên video, hoặc vài trăm ms sau khi các hành
            // động đó kết thúc, listener 'ratechange' TẠM NGƯNG coi thay đổi là do người dùng
            // chọn (giống hệt cách isAdCurrentlyShowing đã che chắn cho quảng cáo) - rate lệch đi
            // trong lúc này sẽ được applySavedRate() ở vòng lặp kế tiếp tự sửa lại đúng ý người
            // dùng, không bị hiểu nhầm và lưu đè mất.
            var suppressRateCapture = false;
            var suppressTimer = null;
            function suppressRateCaptureFor(ms) {
                suppressRateCapture = true;
                if (suppressTimer) clearTimeout(suppressTimer);
                suppressTimer = setTimeout(function() { suppressRateCapture = false; }, ms);
            }

            try {
                var savedRate = parseFloat(localStorage.getItem(RATE_KEY));
                if (!isNaN(savedRate) && savedRate > 0) userRate = savedRate;
            } catch (e) {}

            // FIX "video bị tắt tiếng vĩnh viễn sau khi qua quảng cáo": bọc mọi lần script TỰ
            // đổi playbackRate/muted bằng hàm này thay vì bật rồi tắt cờ scriptChanging ngay
            // trong cùng 1 lượt code như trước. Lý do: sự kiện 'ratechange'/'volumechange' do
            // trình duyệt bắn ra KHÔNG chạy đồng bộ - nó được xếp vào hàng đợi và chạy SAU khi
            // đoạn code hiện tại chạy xong. Nếu tắt cờ ngay thì tới lúc listener thực thi, cờ đã
            // về false từ trước -> bị hiểu NHẦM là người dùng tự đổi. Cụ thể với mute: script tự
            // mute quảng cáo, sự kiện volumechange bắn ra sau khi cờ đã tắt, bị ghi nhận nhầm
            // thành "người dùng chọn tắt tiếng" (userMuted = true), nên lúc khôi phục lại sau
            // quảng cáo, video bị mute lại y như vậy dù người dùng không hề bấm mute. Dùng
            // setTimeout(0) để giữ cờ bật cho tới khi các sự kiện do chính lần đổi này gây ra đã
            // kịp bắn xong mới tắt cờ đi.
            function scriptSet(fn) {
                scriptChanging = true;
                fn();
                setTimeout(function() { scriptChanging = false; }, 0);
            }

            // FIX "tốc độ phát thực tế đúng nhưng trong menu Cài đặt > Tốc độ phát không đồng
            // bộ" (dấu ✓ vẫn nằm ở "1x" hay tốc độ cũ dù video đang thực sự phát nhanh/chậm hơn
            // đúng như đã chọn): trước đây applySavedRate() chỉ gán thẳng video.playbackRate -
            // đây là thao tác DOM trực tiếp, video phát đúng tốc độ thật, NHƯNG YouTube dùng 1
            // biến trạng thái NỘI BỘ RIÊNG (không tự đọc lại video.playbackRate) để quyết định
            // tốc độ nào có dấu ✓ khi mở menu Cài đặt - biến đó CHỈ được cập nhật khi tốc độ được
            // đổi ĐÚNG QUA API của trình phát, không phải qua DOM. Nay ưu tiên gọi
            // #movie_player.setPlaybackRate(rate) (API chính thức của YouTube, cùng lúc cập nhật
            // cả video thật LẪN biến nội bộ dùng để hiển thị menu) - chỉ khi player chưa sẵn sàng
            // (API chưa tồn tại, ví dụ lúc trang vừa tải xong) mới rơi về gán thẳng DOM như cũ để
            // tốc độ vẫn đúng ngay, dù khi đó menu có thể tạm chưa đồng bộ tới khi API sẵn sàng ở
            // lượt kế tiếp.
            function setPlayerRate(video, rate) {
                var player = document.getElementById('movie_player');
                if (player && typeof player.setPlaybackRate === 'function') {
                    try {
                        player.setPlaybackRate(rate);
                        // 1 số phiên bản trình phát cập nhật <video> thật có độ trễ nhỏ sau khi
                        // gọi API - đảm bảo khớp ngay, không đợi thêm.
                        if (video.playbackRate !== rate) video.playbackRate = rate;
                        return;
                    } catch (e) {}
                }
                video.playbackRate = rate;
            }

            function applySavedRate(video) {
                if (!video || userRate === null) return;
                if (video.playbackRate !== userRate) {
                    scriptSet(function() { setPlayerRate(video, userRate); });
                }
            }

            // Đồng bộ menu Cài đặt > Tốc độ phát ĐÚNG 1 LẦN cho mỗi <video> mới (không phụ
            // thuộc video.playbackRate đã khớp userRate hay chưa): applySavedRate() ở trên CHỈ
            // gọi qua API player khi phát hiện video.playbackRate LỆCH với userRate - nhưng nếu
            // tốc độ thật đã khớp sẵn từ trước (ví dụ đã được gán thẳng DOM ở 1 bản cũ trước khi
            // sửa lỗi này, hoặc trình duyệt tự giữ nguyên rate cũ khi chuyển sang video kế tiếp
            // trong danh sách phát) thì applySavedRate() sẽ KHÔNG BAO GIỜ gọi qua API player nữa
            // (vì không thấy chênh lệch để sửa) - khiến biến trạng thái nội bộ mà YouTube dùng để
            // hiện dấu ✓ trong menu vẫn mãi kẹt ở giá trị cũ dù tốc độ thật đã đúng từ lâu. Gọi
            // 1 lần CHẮC CHẮN qua API ngay khi gắn vào <video> mới, bất kể có lệch hay không, để
            // đảm bảo biến nội bộ luôn được đồng bộ theo tốc độ thật ngay từ đầu.
            function syncMenuRateOnce(video) {
                if (!video || userRate === null || video.__ytFixMenuSynced) return;
                video.__ytFixMenuSynced = true;
                scriptSet(function() { setPlayerRate(video, userRate); });
            }

            function attachListeners(video) {
                if (!video) return;
                // Video mới tải xong metadata (video khác, hoặc trang vừa được tải lại) -> áp lại
                // tốc độ đã lưu ngay, không đợi tới lượt setInterval kế tiếp.
                if (!video.__adSkipperListenersAttached) {
                    video.__adSkipperListenersAttached = true;
                    // Chỉ ghi nhận là "người dùng chọn" khi thay đổi KHÔNG PHẢI do chính script
                    // này gây ra (script luôn bật cờ scriptChanging=true lúc nó tự đổi rate/mute),
                    // KHÔNG phải lúc đang có quảng cáo (YouTube tự ép rate=1 lúc này, không phải
                    // người dùng bấm), và KHÔNG rơi vào vùng đệm tạm ngưng (đang tua/đang giữ tay
                    // tăng tốc/vừa buông ra - xem giải thích đầy đủ ở suppressRateCapture). Nếu
                    // đang trong 2 trường hợp bị chặn này mà rate LỠ bị đổi rồi (do chính YouTube
                    // tự ý đổi) - SỬA LẠI NGAY LẬP TỨC tại đây thay vì im lặng bỏ qua rồi đợi tới
                    // lượt setInterval (200ms) kế tiếp mới sửa: khoảng hở vài trăm ms đó là đúng
                    // lúc dễ bị 1 sự kiện khác (vd 'seeking' do buông tay gây ra) đọc nhầm giá trị
                    // rate SAI này rồi lưu đè mất - sửa NGAY tại chỗ triệt tiêu hẳn khoảng hở này.
                    video.addEventListener('ratechange', function() {
                        if (scriptChanging) return;
                        if (isAdCurrentlyShowing || suppressRateCapture) {
                            applySavedRate(video);
                            return;
                        }
                        userRate = video.playbackRate;
                        try { localStorage.setItem(RATE_KEY, String(userRate)); } catch (e) {}
                    });
                    video.addEventListener('volumechange', function() {
                        if (scriptChanging) return;
                        userMuted = video.muted;
                    });
                    video.addEventListener('loadedmetadata', function() {
                        applySavedRate(video);
                    });
                    // Tua tới/lui: mở vùng đệm tạm ngưng NGAY lúc bắt đầu tua, và giữ thêm 1 chút
                    // sau khi tua xong (seeked) vì rate có thể chỉ lệch đúng lúc video tải lại
                    // đoạn mới, không lệch ngay tại thời điểm 'seeking' bắn ra.
                    video.addEventListener('seeking', function() { suppressRateCaptureFor(700); });
                    video.addEventListener('seeked', function() {
                        suppressRateCaptureFor(700);
                        applySavedRate(video);
                    });
                    if (userMuted === null) userMuted = video.muted;
                }
                if (userRate === null) userRate = video.playbackRate;
                applySavedRate(video);
                syncMenuRateOnce(video);
            }

            // Giữ tay trên khu vực video để tăng tốc tạm thời (cử chỉ có sẵn của YouTube) rồi
            // buông ra - xem giải thích đầy đủ ở tình huống (3) của suppressRateCapture phía
            // trên. CHỈ mở vùng đệm khi thời lượng chạm đủ LÂU (>= 350ms, đúng kiểu giữ tay, khác
            // hẳn 1 cái CHẠM NHANH bình thường như chọn tốc độ trong menu Cài đặt hay bấm hiện/ẩn
            // thanh điều khiển) - để KHÔNG lỡ chặn luôn việc người dùng CHỦ ĐỘNG chọn tốc độ mới
            // qua menu (chạm nhanh vào 1 mục trong danh sách cũng là touchstart/touchend, nếu mở
            // vùng đệm cho MỌI lần chạm thì việc chọn tốc độ mới qua menu sẽ không bao giờ được
            // lưu lại nữa - phản tác dụng). Gắn ở document (capture: true) thay vì trực tiếp trên
            // video/player vì phần tử player có thể bị YouTube dựng lại (video mới) mà không kích
            // hoạt lại attachListeners ngay - gắn 1 lần duy nhất ở document là đủ.
            if (!window.__adSkipperHoldListenersAttached) {
                window.__adSkipperHoldListenersAttached = true;
                var touchStartAt = 0;
                // Gắn CẢ 2 loại sự kiện (Touch Events VÀ Pointer Events): các bản YouTube khác
                // nhau (và các bản cập nhật theo thời gian) có thể dùng loại nào cũng được cho cử
                // chỉ giữ tay - nếu chỉ bắt 1 loại mà YouTube lại dùng loại kia thì suppression
                // hoàn toàn không kích hoạt, coi như vá vô tác dụng. Bắt cả 2 không sao vì trên
                // màn cảm ứng, trình duyệt thường bắn CẢ 2 loại sự kiện cho CÙNG 1 lần chạm thật -
                // hàm xử lý dưới đây đọc/ghi lại touchStartAt vô hại dù bị gọi trùng vài lần.
                var onHoldStart = function() { touchStartAt = Date.now(); };
                var onHoldEnd = function() {
                    if (touchStartAt && (Date.now() - touchStartAt) >= 350) {
                        suppressRateCaptureFor(700);
                    }
                };
                document.addEventListener('touchstart', onHoldStart, { passive: true, capture: true });
                document.addEventListener('touchend', onHoldEnd, { passive: true, capture: true });
                document.addEventListener('pointerdown', onHoldStart, { passive: true, capture: true });
                document.addEventListener('pointerup', onHoldEnd, { passive: true, capture: true });
            }

            setInterval(function() {
                try {
                    var skipBtn = document.querySelector(
                        '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton'
                    );
                    if (skipBtn) { skipBtn.click(); }

                    var video = document.querySelector('video');
                    attachListeners(video);
                    var adShowing = document.querySelector('.ad-showing, .ad-interrupting');
                    isAdCurrentlyShowing = !!adShowing;

                    if (adShowing && video) {
                        scriptSet(function() {
                            video.muted = true;
                            if (video.duration && isFinite(video.duration)) {
                                video.currentTime = video.duration;
                            } else {
                                // FIX "back quảng cáo hơi lâu hơn trước": ngay lúc quảng cáo VỪA
                                // bắt đầu, video.duration thường chưa kịp có giá trị (NaN/Infinity
                                // trong vài trăm ms đầu) nên nhánh nhảy currentTime=duration ở
                                // trên bị BỎ QUA hoàn toàn - quảng cáo phát bình thường ở tốc độ
                                // 1x suốt khoảng đó, không có gì tua nhanh giúp cả (đây chính là
                                // khoảng "chờ lâu hơn" cảm nhận được). Tăng tạm playbackRate lên
                                // cao làm phao dự phòng trong đúng lúc này - ngay khi duration có
                                // giá trị ở tick kế tiếp (200ms sau), nhánh currentTime=duration ở
                                // trên sẽ nhảy thẳng tới cuối như cũ; nếu 1 số ít quảng cáo đặc
                                // biệt không bao giờ báo duration thì ít nhất video vẫn được tua
                                // nhanh 16x (mức trần thật của Chromium/WebView, không phải số ảo)
                                // thay vì kẹt ở 1x. Tự khôi phục về đúng tốc độ người dùng chọn
                                // ngay khi quảng cáo kết thúc, xem applySavedRate() ở nhánh dưới.
                                video.playbackRate = 16;
                            }
                        });
                        lastAdShowing = true;
                    } else if (video) {
                        if (lastAdShowing) {
                            // Quảng cáo VỪA kết thúc (tick trước còn quảng cáo, tick này đã hết)
                            // -> khôi phục lại trạng thái tắt tiếng người dùng đã chọn (tốc độ
                            // phát được khôi phục chung ở nhánh applySavedRate() ngay bên dưới).
                            if (userMuted !== null && video.muted !== userMuted) {
                                scriptSet(function() { video.muted = userMuted; });
                            }
                            lastAdShowing = false;
                        }
                        // TỰ SỬA LẠI mỗi 500ms nếu tốc độ phát bị lệch so với lựa chọn người dùng,
                        // phòng các trường hợp KHÁC quảng cáo cũng khiến YouTube tự ý reset về 1x
                        // (vd. lâu lâu tự rớt về 1x không rõ lý do, hoặc app bị hệ thống thu hồi
                        // bộ nhớ nền rồi WebView tự tải lại trang khi mở lại).
                        applySavedRate(video);
                    }

                    // ĐÃ BỎ selector 'tp-yt-paper-dialog.ytd-popup-container' từng có ở đây:
                    // đây là class CONTAINER CHUNG mà YouTube dùng cho RẤT NHIỀU hộp thoại hợp lệ
                    // (Lưu vào playlist/xem sau, Chia sẻ, menu 3 chấm...), không riêng gì banner
                    // "Mở ứng dụng". Cứ mỗi 200ms selector này lại ẩn (display:none) BẤT KỲ hộp
                    // thoại nào đang mở khớp class đó giữa chừng animation đóng/mở của chính nó -
                    // khiến vòng đời quản lý bởi thư viện Polymer của YouTube bị rối, để lại lớp
                    // phủ mờ (backdrop) kẹt cứng không tài nào chạm để tắt được (đúng lỗi đã gặp:
                    // bấm "Lưu vào xem sau" xong hộp thoại/lớp phủ đen kẹt lại, chạm ra ngoài hay
                    // vuốt xuống đều không tắt được). Banner "Mở ứng dụng" thật ra ĐÃ được xử lý
                    // AN TOÀN hơn ở đoạn dò theo NỘI DUNG CHỮ ngay bên dưới rồi, không cần thêm
                    // selector CSS chung chung dễ bắt nhầm này nữa.
                    var overlays = document.querySelectorAll(
                        '.ytp-ad-overlay-container, .ytp-ad-text-overlay, .ytp-ad-image-overlay, ' +
                        '.video-ads, ytd-promoted-sparkles-web-renderer, ' +
                        'ytd-display-ad-renderer, ytd-in-feed-ad-layout-renderer, ytd-ad-slot-renderer, ' +
                        'ytd-banner-promo-renderer, ytd-mealbar-promo-renderer, #open-app, .app-promo, ' +
                        'ytm-open-in-app-button, ytm-app-promo-banner-renderer, ' +
                        '.mobile-topbar-header-open-app-button-container, ' +
                        'yt-open-in-app-button-renderer, [id*="open-in-app" i], [class*="open-in-app" i]'
                    );
                    overlays.forEach(function(el) { el.style.display = 'none'; });

                    // FIX "lớp phủ đen kẹt lại sau khi đóng hộp thoại" (vd bấm 3 chấm > Lưu vào
                    // xem sau > vuốt xuống đóng hộp thoại): lớp phủ mờ (backdrop) đứng SAU hộp
                    // thoại của YouTube (thư viện Polymer, class "tp-yt-iron-overlay-backdrop")
                    // có lúc bị KẸT LẠI - hộp thoại đã đóng xong nhưng backdrop quên tự dọn theo,
                    // che kín gần hết màn hình (trừ hàng nút điều hướng riêng của YouTube ở đáy)
                    // và chặn luôn mọi thao tác. KHÔNG rõ chắc chắn nguyên nhân gốc (có thể do
                    // chính YouTube, hoặc do script này can thiệp gián tiếp), nên xử lý theo
                    // hướng "tự phát hiện & tự dọn" thay vì cố né 1 nguyên nhân cụ thể: mỗi
                    // backdrop có thuộc tính/class "opened" do Polymer gắn vào ĐÚNG lúc nó đang
                    // thực sự phục vụ 1 hộp thoại - backdrop nào TRÔNG như đang hiện (không
                    // display:none, opacity > 0) mà LẠI THIẾU "opened" liên tục hơn 1.5 giây (đủ
                    // dài để không nhầm với hiệu ứng mờ dần/hiện dần bình thường của chính nó,
                    // vốn chỉ mất vài trăm ms) thì coi là kẹt mồ côi thật, ép ẩn + tắt pointer-
                    // events để trả lại thao tác cho người dùng.
                    var stuckBackdrops = document.querySelectorAll(
                        'tp-yt-iron-overlay-backdrop, iron-overlay-backdrop'
                    );
                    for (var bk = 0; bk < stuckBackdrops.length; bk++) {
                        var bd = stuckBackdrops[bk];
                        var bdStyle = window.getComputedStyle(bd);
                        var looksActive = bdStyle.display !== 'none' && parseFloat(bdStyle.opacity) > 0;
                        var hasOpenedFlag = (bd.classList && bd.classList.contains('opened')) ||
                            bd.hasAttribute('opened');
                        if (looksActive && !hasOpenedFlag) {
                            if (!bd.__ytFixSuspectSince) bd.__ytFixSuspectSince = Date.now();
                            if (Date.now() - bd.__ytFixSuspectSince > 1500) {
                                bd.style.setProperty('display', 'none', 'important');
                                bd.style.setProperty('pointer-events', 'none', 'important');
                            }
                        } else {
                            bd.__ytFixSuspectSince = null;
                        }
                    }

                    // Nút/thẻ "Mở ứng dụng" (mời cài/mở app YouTube thật) ở đầu trang - selector
                    // CSS của YouTube hay đổi tên class nên KHÔNG đáng tin cậy 100%, dò thêm theo
                    // NỘI DUNG CHỮ (đa ngôn ngữ) để chắc chắn bắt được, dù YouTube đổi class.
                    var promoTexts = ['mở ứng dụng', 'open app', 'open the app', 'open in app'];
                    var candidates = document.querySelectorAll('a, button, ytd-button-renderer, tp-yt-paper-button');
                    for (var j = 0; j < candidates.length; j++) {
                        var elx = candidates[j];
                        var txt = (elx.innerText || elx.textContent || '').trim().toLowerCase();
                        if (promoTexts.indexOf(txt) !== -1) {
                            // Ẩn cả khối cha gần nhất (thường là 1 thanh/khung chứa icon + chữ)
                            // thay vì chỉ ẩn mỗi chữ, để không để lại khoảng trống/icon mồ côi.
                            var target = elx.closest('ytm-open-in-app-button, .mobile-topbar-header-open-app-button-container, ytm-app-promo-banner-renderer') || elx;
                            target.style.display = 'none';
                        }
                    }
                } catch (e) {}
            }, 200);
        })();
    """

    fun isYoutube(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.contains("youtube.com") || url.contains("youtu.be")
    }

    /** Trang chủ YouTube (không có path/chỉ có "/") - dùng để biết khi nào KHÔNG cần nhảy về
     *  nữa (đã ở sẵn trang chủ rồi) trong logic Back thông minh của YouTube. */
    fun isYoutubeHome(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: return false
            val path = uri.path ?: ""
            host.endsWith("youtube.com") && (path.isEmpty() || path == "/")
        } catch (e: Exception) {
            false
        }
    }

    /** Trang KẾT QUẢ TÌM KIẾM YouTube (path "/results", có kèm tham số search_query) - dùng để
     *  logic Back "dừng lại" ở đây 1 lần trước khi về hẳn trang chủ, thay vì nhảy thẳng từ video
     *  về trang chủ và bỏ qua mất bước tìm kiếm đã thực hiện. */
    fun isYoutubeSearch(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: return false
            val path = uri.path ?: ""
            host.endsWith("youtube.com") && path == "/results" && uri.getQueryParameter("search_query") != null
        } catch (e: Exception) {
            false
        }
    }

    /** Trang "Xem sau" (Watch Later, path "/playlist?list=WL") hoặc "Lịch sử" (History, path
     *  "/feed/history") - dùng để logic Back "dừng lại" ở đây 1 lần trước khi về hẳn trang chủ
     *  (xem isYoutubeStopPage()), giống hệt cách xử lý trang tìm kiếm: mở video từ 1 trong 2
     *  trang này rồi bấm back phải quay lại ĐÚNG danh sách đó trước, không nhảy tọt qua luôn về
     *  trang chủ - làm mất vị trí đang xem dở trong danh sách. */
    fun isYoutubeWatchLaterOrHistory(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: return false
            if (!host.endsWith("youtube.com")) return false
            val path = uri.path ?: ""
            val isWatchLater = path == "/playlist" && uri.getQueryParameter("list") == "WL"
            val isHistory = path == "/feed/history"
            isWatchLater || isHistory
        } catch (e: Exception) {
            false
        }
    }

    /** Trang "điểm dừng" YouTube dùng chung cho logic Back thông minh ở doBack()/
     *  findYoutubeStopIndex(): gồm trang kết quả tìm kiếm VÀ trang Xem sau/Lịch sử - back sẽ
     *  dừng lại đúng 1 lần ở trang gần nhất thuộc nhóm này trước khi nhảy tiếp về trang chủ. */
    fun isYoutubeStopPage(url: String?): Boolean {
        return isYoutubeSearch(url) || isYoutubeWatchLaterOrHistory(url)
    }

}

/**
 * Nhiều trang tự đặt thẻ <meta viewport> với user-scalable=no để chặn zoom trên
 * mobile. Ghi đè lại để cho phép pinch-to-zoom, kể cả khi đang xem video.
 */
object ZoomEnabler {
    const val JS = """
        (function() {
            try {
                var content = 'width=device-width, initial-scale=1.0, maximum-scale=6.0, user-scalable=yes';
                var meta = document.querySelector('meta[name=viewport]');
                if (meta) {
                    meta.setAttribute('content', content);
                } else {
                    var m = document.createElement('meta');
                    m.name = 'viewport';
                    m.content = content;
                    document.head.appendChild(m);
                }
            } catch (e) {}
        })();
    """
}

/**
 * Chặn kiểu quảng cáo "phủ toàn màn hình vô hình" hay dùng để bẫy người dùng
 * bấm vào đâu cũng bị điều hướng sang trang khác.
 */
object AdOverlayBlocker {
    const val JS = """
        (function() {
            if (window.__overlayBlockerRunning) return;
            window.__overlayBlockerRunning = true;
            function killOverlays() {
                try {
                    var all = document.querySelectorAll('body *');
                    for (var i = 0; i < all.length; i++) {
                        var el = all[i];
                        var tag = el.tagName;
                        if (tag === 'VIDEO' || tag === 'SCRIPT' || tag === 'STYLE') continue;
                        var style = window.getComputedStyle(el);
                        if (style.position !== 'fixed' && style.position !== 'absolute') continue;
                        var z = parseInt(style.zIndex) || 0;
                        if (z < 999) continue;
                        var rect = el.getBoundingClientRect();
                        var coversScreen = rect.width >= window.innerWidth * 0.85 &&
                                            rect.height >= window.innerHeight * 0.85;
                        if (coversScreen) {
                            el.style.setProperty('display', 'none', 'important');
                            el.style.setProperty('pointer-events', 'none', 'important');
                        }
                    }
                } catch (e) {}
            }
            setInterval(killOverlays, 2000);
        })();
    """
}

/**
 * Hiện thẻ xanh lá "Tải về" ở góc trên-trái khi trang có video, bấm vào sẽ gọi
 * xuống Kotlin (qua AndroidDownloader) để lưu video vào bộ nhớ máy.
 * Lưu ý: chỉ hoạt động với video có link file trực tiếp (mp4/webm...). Với các
 * trang mã hoá luồng video dạng blob: (ví dụ YouTube) sẽ không tải được, vì đó
 * là giới hạn kỹ thuật/điều khoản của các trang đó, không phải lỗi app - nên
 * KHÔNG chèn script này trên YouTube nữa (xem điều kiện gọi ở onPageFinished),
 * để tránh hiện 1 nút vô dụng (bấm không tải được gì) đè lên logo/UI YouTube.
 */
object VideoDownloadUI {
    const val JS = """
        (function() {
            if (window.__downloadUIRunning) return;
            window.__downloadUIRunning = true;
            var btn = null;
            function ensureButton() {
                if (btn) return;
                btn = document.createElement('div');
                btn.innerText = '⬇ Tải về';
                btn.style.position = 'fixed';
                btn.style.top = '10px';
                btn.style.left = '10px';
                btn.style.zIndex = '2147483647';
                btn.style.background = '#22c55e';
                btn.style.color = '#ffffff';
                btn.style.padding = '6px 14px';
                btn.style.borderRadius = '6px';
                btn.style.fontFamily = 'sans-serif';
                btn.style.fontSize = '13px';
                btn.style.fontWeight = 'bold';
                btn.style.boxShadow = '0 2px 6px rgba(0,0,0,0.5)';
                btn.style.cursor = 'pointer';
                document.body.appendChild(btn);
                btn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    var v = document.querySelector('video');
                    var src = v ? (v.currentSrc || v.src || '') : '';
                    if (window.AndroidDownloader) {
                        window.AndroidDownloader.downloadVideo(src, document.title || 'video');
                    }
                });
            }
            setInterval(function() {
                try {
                    var v = document.querySelector('video');
                    if (v) {
                        ensureButton();
                        btn.style.display = 'block';
                    } else if (btn) {
                        btn.style.display = 'none';
                    }
                } catch (e) {}
            }, 2000);
        })();
    """
}

/**
 * Gắn nút mic YouTube vào AndroidSpeech.startListening() (JavascriptInterface):
 * khi người dùng bấm icon mic trên thanh tìm kiếm YouTube, thay vì dùng
 * webkitSpeechRecognition (không hoạt động trong WebView), app sẽ mở hộp thoại
 * nhận dạng giọng nói hệ thống Android, lấy kết quả rồi điền vào ô tìm kiếm.
 */
object YoutubeMicBridge {
    const val JS = """
        (function() {
            if (window.__micBridgeRunning) return;
            window.__micBridgeRunning = true;
            // Override webkitSpeechRecognition để bắt mọi trang web dùng Web Speech API
            if (window.AndroidSpeech) {
                window.SpeechRecognition = window.webkitSpeechRecognition = function() {
                    this.start = function() { window.AndroidSpeech.startListening(); };
                    this.stop = function() {};
                    this.abort = function() {};
                };
            }
            // Gắn thêm vào nút mic YouTube (phòng YouTube không dùng SpeechRecognition API chuẩn)
            setInterval(function() {
                try {
                    var micBtns = document.querySelectorAll(
                        'button.ytSearchboxComponentMicButton, ' +
                        '[aria-label*="mic" i], [aria-label*="voice" i], [aria-label*="giọng nói" i]'
                    );
                    micBtns.forEach(function(btn) {
                        if (!btn.__micHooked && window.AndroidSpeech) {
                            btn.__micHooked = true;
                            btn.addEventListener('click', function(e) {
                                e.preventDefault();
                                e.stopPropagation();
                                window.AndroidSpeech.startListening();
                            }, true);
                        }
                    });
                } catch(e) {}
            }, 1000);
        })();
    """
}
