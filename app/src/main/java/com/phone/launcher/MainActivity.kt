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
import android.webkit.WebStorage
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

    /** Nền ô địa chỉ kiểu Windows Phone: nền đen phẳng + 1 gạch chân màu NHẤN ở đáy (đặc trưng
     *  của TextBox trên WP/Windows 10 Mobile) - vẽ bằng code (thay vì @drawable/edit_url_bg cố
     *  định trong XML) để luôn dùng đúng màu nhấn người dùng vừa chọn ở Cài đặt, không cần build
     *  lại app mỗi lần đổi màu. */
    private fun buildUrlBarBackground(): android.graphics.drawable.Drawable {
        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.BLACK)
        }
        val underline = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF0078D7.toInt())
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(bg, underline)).apply {
            setLayerGravity(1, Gravity.BOTTOM)
            setLayerHeight(1, dp(2))
        }
    }

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
        // Dùng WindowInsetsControllerCompat của androidx để hoạt động đúng trên mọi phiên bản
        // Android (kể cả các máy Android cũ hơn không có API ẩn thanh điều hướng mới).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
            if (!imeVisible) enableImmersiveMode()
        }
    }

    private lateinit var webView: WebView
    private lateinit var edtUrl: EditText
    private lateinit var toolbarUrl: View
    private var progressBar: ProgressBar? = null
    private lateinit var homeOverlay: View
    private var edtHomeSearch: EditText? = null
    private lateinit var homeScreenManager: HomeScreenManager

    // Giữ TẠM yêu cầu quyền của trang web (camera/mic hoặc vị trí) trong lúc chờ người dùng trả
    // lời hộp thoại xin quyền HỆ THỐNG vừa bật lên (xem onPermissionRequest/
    // onGeolocationPermissionsShowPrompt bên dưới và onRequestPermissionsResult) - KHÔNG xin
    // quyền sẵn lúc mở app nữa, chỉ xin ĐÚNG LÚC trang web thực sự cần.
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: android.webkit.GeolocationPermissions.Callback? = null

    // Đánh dấu điều hướng do chính app gọi (từ thanh địa chỉ / menu đề xuất / mở lại tab)
    // để KHÔNG hỏi xác nhận, chỉ hỏi khi người dùng bấm link ngay trên trang.
    private var programmaticLoad = false

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

    companion object {
        const val REQ_PERMISSIONS = 101
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
        webView.setBackgroundColor(android.graphics.Color.BLACK) // tránh WebView chớp trắng lúc mới vào/đang tải trang (bề mặt render riêng của WebView mặc định trắng, đặt màu nền XML không đủ)
        edtUrl = findViewById(R.id.edtUrl)
        // Gạch chân màu nhấn của ô địa chỉ trước đây cố định trong edit_url_bg.xml (không đổi
        // được lúc chạy) - giờ vẽ lại bằng code theo đúng màu nhấn người dùng đã chọn ở Cài đặt
        // > Giao diện, để đổi màu ở đó là ô địa chỉ lên màu mới ngay từ lần mở app kế tiếp.
        edtUrl.background = buildUrlBarBackground()
        toolbarUrl = findViewById(R.id.toolbarUrl)
        // Thanh tiến trình tải trang mỏng kiểu Windows Phone (IE Mobile/Edge): 1 vạch phẳng màu
        // NHẤN nằm sát cạnh trên màn hình, không bo góc, không đổ bóng - trước đây bị xoá hẳn
        // khỏi layout (progressBar = null cứng) nên lúc tải trang KHÔNG còn dấu hiệu gì cho biết
        // trang đang load, thiếu hẳn 1 chi tiết đặc trưng của trình duyệt WP thật. Dựng lại bằng
        // code (giống cách AccountBrowserActivity/IncognitoActivity đã làm) để luôn lên đúng màu
        // nhấn người dùng vừa chọn ở Cài đặt, không cần build lại app.
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF0078D7.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
            visibility = View.GONE
        }
        findViewById<FrameLayout>(R.id.rootFrame).addView(
            progressBar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).also {
                it.gravity = Gravity.TOP
            }
        )
        homeOverlay = findViewById(R.id.homeOverlay)
        edtHomeSearch = null  // đã xoá khỏi layout

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

        edtUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                loadFromInput()
                true
            } else {
                false
            }
        }
        edtHomeSearch?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                loadFromHomeSearch()
                true
            } else {
                false
            }
        }

        showHomeOverlay()
        // Nếu Activity được mở kèm extra "initial_url" (vd shortcut YouTube + Ẩn danh), điều
        // hướng thẳng qua navigateTo() thay vì dừng ở Start (hàm này tự ẩn homeOverlay giúp).
        intent.getStringExtra("initial_url")?.let { url -> navigateTo(url) }
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

    private fun showHomeOverlay() {
        homeOverlay.visibility = View.VISIBLE
        toolbarUrl.visibility = View.GONE
        progressBar?.visibility = View.GONE
        pauseAllVideos()
        // Chỉ còn gọi ĐÚNG 1 LẦN lúc mới mở app (onCreate) - "thoát khỏi app con là thoát HẲN
        // app luôn" (xem doBack()/openShortcutByKey()) nên không còn tình huống "quay lại" màn
        // Start giữa phiên nữa; webView lúc này vừa tạo, chưa tải gì nên không cần tự xoá thêm.
        homeScreenManager.goToStart()
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

    private fun loadFromHomeSearch() {
        val search = edtHomeSearch ?: return
        var input = search.text.toString().trim()
        if (input.isEmpty()) return
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = if (input.contains(".") && !input.contains(" ")) "https://$input"
            else "https://www.google.com/search?q=" + Uri.encode(input)
        }
        search.setText("")
        navigateTo(input)
    }

    // ĐÃ XOÁ HẲN: chuyển đổi bản máy tính/di động (User-Agent tuỳ chỉnh) theo yêu cầu - WebView
    // giờ luôn dùng User-Agent MẶC ĐỊNH của hệ thống, không còn ép UA riêng cho bất kỳ trang nào.

    /** Chia sẻ địa chỉ trang đang xem qua app khác (Zalo, Messenger, email...) bằng hộp thoại
     *  chia sẻ HỆ THỐNG - hộp thoại này KHÔNG thuộc app nên để hệ thống tự vẽ theo ROM máy, không
     *  can thiệp theme (giống cách app xử lý các Intent mở ứng dụng ngoài khác). */
    private fun shareCurrentPage() {
        val url = webView.url ?: return
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, url)
        }
        try {
            startActivity(android.content.Intent.createChooser(send, "Chia sẻ trang qua"))
        } catch (e: Exception) { }
    }

    /** Xoá cookie/cache duyệt web - giống hệt lệnh cùng tên ở [SettingsActivity], đặt lại đây để
     *  bấm được ngay từ App Bar mà không cần rời khỏi trang đang xem. */
    private fun clearBrowsingData() {
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
        Toast.makeText(this, "Đã xoá dữ liệu duyệt web", Toast.LENGTH_SHORT).show()
    }

    // ĐÃ XOÁ HẲN: xin quyền (camera/mic/vị trí/thông báo...) ngay lúc mở app - không còn hộp
    // thoại xin quyền nào bật lên khi vừa vào app nữa (xem giải thích ở onCreate()).

    // ---------- Điều hướng ----------

    private fun navigateTo(url: String) {
        hideHomeOverlay()
        programmaticLoad = true
        webView.loadUrl(url)
    }

    private fun loadFromInput() {
        var input = edtUrl.text.toString().trim()
        if (input.isEmpty()) return
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = if (input.contains(".") && !input.contains(" ")) {
                "https://$input"
            } else {
                "https://www.google.com/search?q=" + Uri.encode(input)
            }
        }
        navigateTo(input)
    }

    // Màn hình chính (MainActivity) chỉ có ĐÚNG 1 WebView, không có khái niệm "nhiều tab".

    private fun clearAllSessionData() {
        webView.clearHistory()
        webView.clearCache(true)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        Toast.makeText(this, "Đã xoá toàn bộ lịch sử", Toast.LENGTH_SHORT).show()
    }

    // ---------- Menu đề xuất trang (tam giác) ----------

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                edtUrl.setText(url)
                updateOffButtonVisibility(url)
                view?.evaluateJavascript(AdOverlayBlocker.JS, null)
                if (YoutubeAdSkipper.isYoutube(url)) {
                    // AdOverlayBlocker KHÔNG chạy trên YouTube - nó dùng querySelectorAll('body *')
                    // quét toàn bộ DOM mỗi 700ms, YouTube có hàng nghìn element -> gây lag nặng.
                    // YoutubeAdSkipper đã xử lý overlay quảng cáo YouTube rồi, không cần thêm.
                    view?.evaluateJavascript(YoutubeAdSkipper.JS, null)
                } else {
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
    // các trang con đã xem) thay vì lùi từng bước. NẾU đang có video phát -> trước khi chuyển,
    // THU NHỎ video đang xem thành cửa sổ nổi (mini-player) kéo/di chuyển được, để video vẫn
    // tiếp tục phát trong lúc duyệt trang chủ chọn video khác. Đã ở trang chủ YouTube rồi thì
    // back tiếp theo xử lý bình thường như các trang khác (lùi tiếp lịch sử trước khi vào
    // YouTube, hoặc về màn hình chính app).
    fun doBack() {
        // Đang ở fullscreen HTML5 THẬT (người dùng tự bấm nút fullscreen của YouTube, hoặc
        // trang tự bật khi xoay ngang) -> Back chỉ thoát fullscreen bình thường.
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
            return
        }
        val currentUrl = webView.url
        when {
            webView.canGoBack() && YoutubeAdSkipper.isYoutube(currentUrl) && !YoutubeAdSkipper.isYoutubeHome(currentUrl) -> {
                programmaticLoad = true
                webView.loadUrl("https://www.youtube.com")
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
            // Hết lịch sử để lùi (hoặc đang ở sẵn Start) -> "thoát khỏi app con là thoát HẲN app
            // luôn" theo yêu cầu - KHÔNG còn dừng lại ở màn Start trước, thoát thẳng.
            else -> {
                super.onBackPressed()
            }
        }
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
        if (targetIndex >= 0) {
            programmaticLoad = true
            webView.goBackOrForward(targetIndex - currentIndex)
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
    }

    override fun onStop() {
        super.onStop()
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
        } else {
            pane1.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            root.addView(pane1, 0)
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
            onTap = { FakeScreenOff.show(this, webView) },
            id = "off",
            icon = "⏻",
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

    // Thoát app -> xoá sạch mọi dấu vết phiên làm việc
    override fun onDestroy() {
        clearAllSessionData()
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

    fun blockedCount(): Int = domains.size
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
            setInterval(function() {
                try {
                    var skipBtn = document.querySelector(
                        '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton'
                    );
                    if (skipBtn) { skipBtn.click(); }

                    var video = document.querySelector('video');
                    var adShowing = document.querySelector('.ad-showing, .ad-interrupting');
                    if (adShowing && video) {
                        video.muted = true;
                        if (video.duration && isFinite(video.duration)) {
                            video.currentTime = video.duration;
                        }
                        video.playbackRate = 30;
                    }

                    var overlays = document.querySelectorAll(
                        '.ytp-ad-overlay-container, .ytp-ad-text-overlay, .ytp-ad-image-overlay, ' +
                        '.video-ads, ytd-promoted-sparkles-web-renderer, ' +
                        'ytd-display-ad-renderer, ytd-in-feed-ad-layout-renderer, ytd-ad-slot-renderer, ' +
                        'ytd-banner-promo-renderer, ytd-mealbar-promo-renderer, #open-app, .app-promo, ' +
                        'tp-yt-paper-dialog.ytd-popup-container, ' +
                        'ytm-open-in-app-button, ytm-app-promo-banner-renderer, ' +
                        '.mobile-topbar-header-open-app-button-container, ' +
                        'yt-open-in-app-button-renderer, [id*="open-in-app" i], [class*="open-in-app" i]'
                    );
                    overlays.forEach(function(el) { el.style.display = 'none'; });

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
            }, 500);
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
