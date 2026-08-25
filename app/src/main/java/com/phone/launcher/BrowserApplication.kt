package com.phone.launcher

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import android.webkit.WebView

/** Class Application chung của app. Đặt WebView.setDataDirectorySuffix() SỚM NHẤT có thể (ngay
 *  đầu Application.onCreate(), trước khi bất kỳ WebView/CookieManager nào được đụng tới) để MỌI
 *  tiến trình PHỤ (tên process có dấu ":") tự có thư mục dữ liệu WebView riêng, không đụng thư
 *  mục của tiến trình chính - đây là điều BẮT BUỘC theo tài liệu Android nếu 2 tiến trình của
 *  CÙNG 1 app dùng WebView, nếu không sẽ crash ngay khi WebView thứ 2 khởi tạo với lỗi
 *  "WebView cannot be used from more than one process".
 *
 *  HIỆN TẠI trong bản này, IncognitoActivity chạy CHUNG tiến trình với MainActivity (không còn
 *  android:process=":incognito" trong Manifest) và không còn Activity nào khác khai báo process
 *  riêng - nên nhánh `if (!suffix.isNullOrEmpty())` dưới đây thực tế KHÔNG chạy (processName
 *  không có dấu ":"). Cứ để nguyên logic này: nó không tốn chi phí gì đáng kể, và nếu sau này có
 *  Activity nào cần chạy process riêng trở lại (ví dụ khôi phục lại chế độ Ẩn danh cách ly cookie
 *  hoàn toàn), code này tự động bảo vệ đúng ngay mà không cần sửa gì thêm. */
class BrowserApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = currentProcessName()
            // processName dạng "com.phone.launcher:<tên process phụ>" - tiến trình CHÍNH không
            // có dấu ":" nên bỏ qua (đã là mặc định, không cần suffix).
            val suffix = processName?.substringAfter(':', "")
            if (!suffix.isNullOrEmpty()) {
                try {
                    WebView.setDataDirectorySuffix(suffix)
                } catch (e: Exception) {
                    // Nếu vì lý do nào đó đã có WebView chạm vào trước (không nên xảy ra), bỏ
                    // qua an toàn thay vì crash cứng ngay tại đây.
                }
            }
        }
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        val pid = Process.myPid()
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        return am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
    }
}
