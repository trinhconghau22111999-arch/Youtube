package com.phone.launcher

import android.content.Context

/** ĐÚNG NGHĨA Ẩn danh: KHÔNG lưu lại danh sách tab của phiên trước - object này giờ chỉ còn tác
 *  dụng xoá sạch dữ liệu phiên cũ (nếu sót lại từ bản trước khi hành vi này chưa tồn tại) mỗi khi
 *  đóng màn Ẩn danh, đảm bảo không để lại dấu vết cho lần mở sau (xem IncognitoActivity.saveSession()). */
object IncognitoSessionStore {
    private const val PREFS = "incognito_session"
    private const val KEY = "urls"

    /** Xoá sạch danh sách tab đã lưu (nếu có sót từ phiên bản cũ hơn) - dùng khi thoát Ẩn danh để
     *  KHÔNG để lại dấu vết cho lần mở sau. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
