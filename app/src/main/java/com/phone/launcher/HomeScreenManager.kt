package com.phone.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Loại 1 mục trên trang chủ: mở thẳng 1 trang web (WEB) hay mở 1 Activity trong app (ACTIVITY). */
enum class ShortcutType { WEB, ACTIVITY }

data class ShortcutItem(
    val key: String,
    val label: String,
    val type: ShortcutType,
    val target: String,
    val iconRes: Int
)

/** CHỈ CÒN ĐÚNG 2 MỤC CỐ ĐỊNH (YouTube + Ẩn danh) - mọi tính năng khác của trang chủ (DS ứng
 *  dụng, ghim thêm app, đổi kích cỡ/vị trí tile, đánh dấu sao, đổi màu giao diện...) đã được GỠ
 *  BỎ HOÀN TOÀN theo yêu cầu. */
object ShortcutsRepository {
    val ALL: LinkedHashMap<String, ShortcutItem> = linkedMapOf(
        "youtube" to ShortcutItem(
            "youtube", "YouTube", ShortcutType.WEB, "https://www.youtube.com", R.drawable.ic_shortcut_youtube
        ),
        "incognito" to ShortcutItem(
            "incognito", "Ẩn danh", ShortcutType.ACTIVITY, "IncognitoActivity", R.drawable.ic_shortcut_incognito
        )
    )
}

/** Trang chủ ĐÃ ĐƯỢC ĐƠN GIẢN HOÁ TỐI ĐA: CHỈ 1 màn hình duy nhất, hiện đúng 2 icon cố định
 *  (YouTube + Ẩn danh) canh giữa màn hình, giao diện Android chuẩn (icon vuông bo góc + tên bên
 *  dưới). Không còn: trang "DS Ứng Dụng" (danh sách toàn bộ app đã cài), không còn ghim thêm app
 *  khác vào màn hình chính, không còn lưới Live Tile / đổi kích cỡ / kéo-thả đổi vị trí, không
 *  còn đánh dấu sao app, không còn tuỳ biến màu giao diện - 2 nút NÀY LÀ TOÀN BỘ màn hình chính. */
class HomeScreenManager(
    private val context: Context,
    private val onOpenShortcut: (ShortcutItem) -> Unit
) {
    /** Giữ tham chiếu để [refreshPages] dựng lại nội dung khi cần (vd sau khi quay lại app). */
    private var rootRef: FrameLayout? = null

    /** Không còn trang nào khác nên goToStart() chỉ cần cuộn về đầu (thực ra luôn ở đầu, giữ lại
     *  cho tương thích với nơi gọi ở MainActivity). */
    fun goToStart() {
        (rootRef?.getChildAt(0) as? ScrollView)?.scrollTo(0, 0)
    }

    fun build(): FrameLayout {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        rootRef = root
        val startPage = buildStartPage()
        root.addView(startPage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        return root
    }

    /** PUBLIC để MainActivity.onResume() gọi được (giữ nguyên hành vi cũ - dựng lại màn hình
     *  chính mỗi khi quay lại app, dù giờ nội dung luôn cố định không đổi). */
    fun refreshPages() {
        val root = rootRef ?: return
        root.removeAllViews()
        val startPage = buildStartPage()
        root.addView(startPage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    /** Dựng màn hình chính: 2 icon cố định (YouTube/Ẩn danh) canh GIỮA màn hình theo cả chiều
     *  ngang lẫn chiều dọc - không còn lưới/tile/danh sách gì khác. */
    private fun buildStartPage(): View {
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(20), dp(8), dp(20), dp(4) + dp(WpNavBar.HEIGHT_DP))
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER }
        }

        // YouTube: LUÔN nền đỏ cố định (đúng màu thương hiệu YouTube thật).
        ShortcutsRepository.ALL["youtube"]?.let { item ->
            row.addView(buildSimpleAppIcon(item.label, item.iconRes, 0xFFE51400.toInt()) { onOpenShortcut(item) })
        }
        // Ẩn danh: màu tím than cố định, phân biệt rõ với YouTube.
        ShortcutsRepository.ALL["incognito"]?.let { item ->
            row.addView(buildSimpleAppIcon(item.label, item.iconRes, 0xFF4A4A9E.toInt()) { onOpenShortcut(item) })
        }

        content.addView(row)
        scrollView.addView(content)
        return scrollView
    }

    private fun applyWpTilePressAnim(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            false
        }
    }

    /** 1 nút icon ĐƠN GIẢN kiểu Android chuẩn (icon vuông bo góc + tên bên dưới, canh giữa) -
     *  dùng cho đúng 2 mục cố định (YouTube/Ẩn danh). CHỈ CHẠM để mở, không nhấn giữ, không menu,
     *  không đổi kích cỡ/vị trí. */
    private fun buildSimpleAppIcon(label: String, iconRes: Int, tileColor: Int, onClick: () -> Unit): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            foreground = pressedOverlay()
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setOnClickListener { onClick() }
        }
        applyWpTilePressAnim(column)

        val iconBg = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(tileColor)
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            val pad = dp(7)
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconBg.addView(icon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val labelView = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0xCC000000.toInt())
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(4)
            }
        }

        column.addView(iconBg)
        column.addView(labelView)
        return column
    }

    /** Hiệu ứng khi bấm: KHÔNG dùng ripple tròn Material mặc định, chỉ làm tối nhẹ toàn bộ ô
     *  hình chữ nhật - đúng cảm giác "bấm phẳng" của Metro UI. */
    private fun pressedOverlay(): Drawable {
        val pressedState = GradientDrawable().apply { setColor(0x33FFFFFF) }
        val normalState = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedState)
            addState(intArrayOf(), normalState)
        }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
