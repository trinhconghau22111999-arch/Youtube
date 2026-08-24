package com.phone.launcher

import android.app.Activity
import android.content.Intent

/** Mở 1 màn hình MỚI trong app kèm hiệu ứng chuyển màn kiểu Windows Phone (màn mới trượt vào từ
 *  bên phải, màn cũ trượt ra bên trái - xem wp_slide_in_right.xml/wp_slide_out_left.xml) thay vì
 *  hiệu ứng mặc định của hệ thống (mờ dần trên hầu hết máy, không giống WP). Dùng hàm này thay
 *  cho startActivity() thường ở MỌI nơi điều hướng sang màn hình KHÁC CỦA CHÍNH APP (không dùng
 *  cho intent mở app/màn hình ngoài như Cài đặt hệ thống, trình xem tệp, trình duyệt... vì những
 *  màn đó không thuộc app này, để hệ thống tự quyết định hiệu ứng của nó). */
fun Activity.startActivityWp(intent: Intent) {
    startActivity(intent)
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.wp_slide_in_right, R.anim.wp_slide_out_left)
}
