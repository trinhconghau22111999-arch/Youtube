package com.phone.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ghi lại lỗi crash (exception không bắt được) VÀ nghi ngờ "treo máy" (ANR - main thread bị
 *  chặn, không phản hồi) vào 1 file trong bộ nhớ RIÊNG của app - file này SỐNG SÓT qua việc
 *  force-stop/thoát hẳn app (chỉ mất khi gỡ cài app hoặc xoá dữ liệu app), KHÁC với biến trong
 *  RAM (mất sạch khi tiến trình bị giết). Đúng ý đồ: khi app bị "đứng hình"/1 lớp phủ nào đó
 *  chặn hết chạm-lướt không rõ nguyên nhân, người dùng chỉ cần thoát hẳn app (vuốt khỏi Đa
 *  nhiệm hoặc buộc dừng trong Cài đặt) rồi mở lại - màn hình chính ([MainActivity]) sẽ tự đọc
 *  và hiện đúng lỗi/stack trace đã gây ra sự cố, để copy dán đi báo lỗi.
 *
 *  Gồm 2 cơ chế bắt lỗi riêng biệt, vì "đứng hình chặn chạm-lướt" có thể do 1 trong 2 nguyên
 *  nhân rất khác nhau:
 *   1. CRASH thật (1 luồng nào đó ném exception không ai bắt) - Android thường tự đóng app
 *      ngay khi việc này xảy ra ở main thread, [Thread.setDefaultUncaughtExceptionHandler] bắt
 *      được đầy đủ tên lỗi + stack trace TRƯỚC khi app bị đóng.
 *   2. ANR/treo THẬT SỰ (main thread bị kẹt trong 1 vòng lặp vô hạn/deadlock/tác vụ nặng chạy
 *      quá lâu chứ KHÔNG ném lỗi gì cả) - trường hợp này KHÔNG có exception nào để bắt, nên
 *      cần 1 "người canh chừng" ([startAnrWatchdog]) chạy trên luồng NỀN riêng, liên tục "chọc"
 *      main thread mỗi vài giây; nếu main thread không kịp phản hồi trong thời gian cho phép,
 *      tự ghi lại NGUYÊN VẸN stack trace của main thread NGAY TẠI THỜI ĐIỂM ĐÓ - đó chính xác
 *      là dòng code nào đang khiến main thread (và mọi chạm-lướt) bị kẹt cứng ở đó. */
object CrashReporter {
    private const val FILE_NAME = "last_crash.log"
    private const val ANR_TIMEOUT_MS = 4000L
    private const val ANR_COOLDOWN_MS = 15000L

    @Volatile private var watchdogRunning = false
    @Volatile private var lastAckTime = 0L

    /** Gọi ĐÚNG 1 LẦN ở [BrowserApplication.onCreate] - cài bộ bắt crash + bộ canh chừng ANR
     *  cho TOÀN BỘ tiến trình app. Không gọi lại nhiều lần (sẽ cài chồng handler không cần
     *  thiết, dù không hỏng gì nhưng lãng phí 1 luồng nền thêm). */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sb = StringBuilder()
                sb.append("=== CRASH (exception khong ai bat) ===\n")
                sb.append("Thoi gian: ").append(nowString()).append("\n")
                sb.append("Xay ra o luong: ").append(thread.name).append("\n\n")
                sb.append(throwableToString(throwable))
                writeLog(appContext, sb.toString())
            } catch (e: Exception) {
                // Tuyệt đối không để chính việc ghi log này gây thêm 1 crash thứ 2 chồng lên.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
        startAnrWatchdog(appContext)
    }

    private fun startAnrWatchdog(context: Context) {
        if (watchdogRunning) return
        watchdogRunning = true
        val mainHandler = Handler(Looper.getMainLooper())
        val mainThread = Looper.getMainLooper().thread
        Thread {
            while (watchdogRunning) {
                try {
                    val sentAt = System.currentTimeMillis()
                    mainHandler.post { lastAckTime = System.currentTimeMillis() }
                    Thread.sleep(ANR_TIMEOUT_MS)
                    if (lastAckTime < sentAt) {
                        // Quá thời hạn mà main thread vẫn CHƯA xử lý xong 1 việc ĐƠN GIẢN NHẤT
                        // có thể có (post 1 Runnable rỗng) -> chắc chắn đang bị chặn bởi việc
                        // gì đó khác chạy quá lâu/deadlock ngay trên main thread.
                        val sb = StringBuilder()
                        sb.append("=== NGHI NGO TREO MAY / ANR (main thread khong phan hoi) ===\n")
                        sb.append("Thoi gian: ").append(nowString()).append("\n")
                        sb.append("Main thread da khong xu ly xong 1 tac vu don gian sau ")
                            .append(ANR_TIMEOUT_MS).append("ms.\n")
                        sb.append("Stack trace cua main thread NGAY LUC NAY (dong code dang lam no ket):\n\n")
                        sb.append(stackTraceToString(mainThread.stackTrace))
                        writeLog(context, sb.toString())
                        // Đợi lâu hơn hẳn trước khi thử ghi log lần nữa, tránh spam file log nếu
                        // tình trạng treo kéo dài liên tục nhiều phút.
                        Thread.sleep(ANR_COOLDOWN_MS)
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    // Bỏ qua, thử lại ở vòng lặp kế tiếp.
                }
            }
        }.apply { isDaemon = true; name = "CrashReporter-AnrWatchdog"; start() }
    }

    private fun throwableToString(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun stackTraceToString(elements: Array<StackTraceElement>): String =
        if (elements.isEmpty()) "(khong lay duoc stack trace)"
        else elements.joinToString("\n") { "    at $it" }

    private fun nowString(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    @Synchronized
    private fun writeLog(context: Context, text: String) {
        try {
            File(context.filesDir, FILE_NAME).appendText(text + "\n\n")
        } catch (e: Exception) {
        }
    }

    /** Đọc log lỗi đã lưu (nếu có) rồi XOÁ LUÔN (đọc 1 lần là hết, không hiện lại lần mở app
     *  sau nếu người dùng đã xem qua) - gọi ở [MainActivity.onCreate] mỗi lần mở app. Trả về
     *  null nếu chưa từng có crash/ANR nào được ghi kể từ lần đọc trước. */
    fun readAndClear(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists() || file.length() == 0L) return null
        val content = try { file.readText() } catch (e: Exception) { return null }
        try { file.delete() } catch (e: Exception) { }
        return content.takeIf { it.isNotBlank() }
    }
}
