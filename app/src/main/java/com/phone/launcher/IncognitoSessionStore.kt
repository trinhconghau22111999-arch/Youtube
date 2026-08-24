package com.phone.launcher

import android.content.Context

/** Lưu và khôi phục danh sách URL tab của phiên "Duyệt web" (IncognitoActivity) để người dùng
 *  không mất các tab đang mở khi thoát app rồi vào lại.
 *
 *  Lưu ý: đây là "Duyệt web" (trình duyệt phụ có đa tab), KHÔNG phải ẩn danh thật sự -
 *  cookie/phiên đăng nhập trong tiến trình này vẫn bị hệ thống xoá khi process kết thúc
 *  (do android:process=":incognito" cô lập hoàn toàn); chỉ có DANH SÁCH URL là được lưu lại
 *  qua SharedPreferences để mở lại đúng trang khi vào lại app. */
object IncognitoSessionStore {
    private const val PREFS = "incognito_session"
    private const val KEY_URLS = "urls"
    private const val KEY_ACTIVE = "active_index"

    /** Lưu danh sách URL hiện tại + index tab đang active. [urls] là URL của mỗi tab theo thứ tự;
     *  URL null/blank được thay bằng chuỗi rỗng (không lưu about:blank ra file). */
    fun save(context: Context, urls: List<String?>, activeIndex: Int) {
        val joined = urls.joinToString("\n") { (it ?: "").trim() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URLS, joined)
            .putInt(KEY_ACTIVE, activeIndex.coerceAtLeast(0))
            .apply()
    }

    /** Trả về danh sách URL đã lưu (lọc bỏ chuỗi rỗng). Trả về null nếu chưa có dữ liệu nào. */
    fun loadUrls(context: Context): List<String>? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URLS, null) ?: return null
        val list = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        return if (list.isEmpty()) null else list
    }

    /** Trả về index tab active đã lưu (mặc định 0 nếu chưa có). */
    fun loadActiveIndex(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ACTIVE, 0)

    /** Xoá sạch dữ liệu đã lưu - gọi khi người dùng chủ động đóng hết tab hoặc xoá dữ liệu. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
