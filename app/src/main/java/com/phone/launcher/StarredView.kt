package com.phone.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object StarredView {

    class Handle internal constructor(
        private val root: ViewGroup,
        private val overlay: View,
        private val renderList: (List<String>) -> Unit
    ) {
        val isShowing: Boolean get() = overlay.parent != null

        fun update(urls: List<String>) {
            if (isShowing) renderList(urls)
        }

        fun dismiss() {
            if (overlay.parent != null) root.removeView(overlay)
        }
    }

    private fun domainLabel(url: String): String {
        val host = try { Uri.parse(url).host } catch (e: Exception) { null }
        return (host ?: url).removePrefix("www.")
    }

    fun show(
        activity: Activity,
        root: FrameLayout,
        urls: List<String>,
        onOpen: (String) -> Unit,
        onRemove: (String) -> Unit
    ): Handle {
        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

        val selectedUrls = mutableSetOf<String>()

        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(0xFF000000.toInt())
            isClickable = true
        }

        // ── Action bar phía trên (chỉ hiện khi có >= 1 item được chọn) ──
        val actionBar = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A1A1A.toInt())
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Hàng 1: số lượng đã chọn + nút bỏ chọn
        val actionBarTop = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(8))
        }

        val selectedCountText = TextView(activity).apply {
            text = "0 đã chọn"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClearSelection = TextView(activity).apply {
            text = "✕"
            textSize = 20f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(dp(12), dp(4), dp(4), dp(4))
        }

        actionBarTop.addView(selectedCountText)
        actionBarTop.addView(btnClearSelection)

        // Hàng 2: nút hành động (chỉ hiện khi >= 2 item chọn: Chọn tất cả)
        val actionBarButtons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), dp(12))
        }

        fun makeActionBtn(label: String, bgColor: Int, onClick: () -> Unit): TextView {
            return TextView(activity).apply {
                text = label
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(bgColor)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(dp(4), 0, dp(4), 0) }
                setOnClickListener { onClick() }
            }
        }

        val btnSelectAll = makeActionBtn("Chọn tất cả", 0xFF333333.toInt()) {}
        val btnOpenSelected = makeActionBtn("Mở đã chọn", 0xFF0078D7.toInt()) {}
        val btnDeleteSelected = makeActionBtn("Xóa đã chọn", 0xFFCC0000.toInt()) {}

        actionBarButtons.addView(btnSelectAll)
        actionBarButtons.addView(btnOpenSelected)
        actionBarButtons.addView(btnDeleteSelected)

        // Divider
        val divider = View(activity).apply {
            setBackgroundColor(0xFF333333.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            )
        }

        actionBar.addView(actionBarTop)
        actionBar.addView(actionBarButtons)
        actionBar.addView(divider)

        // ── Nội dung cuộn ──
        val scrollView = ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }

        // Tiêu đề
        content.addView(TextView(activity).apply {
            text = "Đã gắn dấu"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(16), dp(40), dp(16), dp(10))
        })

        val emptyLabel = TextView(activity).apply {
            text = "Chưa có trang nào gắn dấu"
            textSize = 15f
            setTextColor(0xFF999999.toInt())
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(16), dp(20), dp(16), dp(2))
        }
        content.addView(emptyLabel)

        val listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(listContainer)

        // Map url -> row view để cập nhật trạng thái chọn
        val rowViews = mutableMapOf<String, View>()

        fun updateActionBar(allUrls: List<String>) {
            val count = selectedUrls.size
            if (count == 0) {
                actionBar.visibility = View.GONE
            } else {
                actionBar.visibility = View.VISIBLE
                selectedCountText.text = "$count đã chọn"
                // Nút "Chọn tất cả" và các nút khác chỉ hiện khi >= 2
                actionBarButtons.visibility = if (count >= 2) View.VISIBLE else View.GONE
            }
            // Cập nhật màu nền từng row
            rowViews.forEach { (url, row) ->
                row.setBackgroundColor(
                    if (selectedUrls.contains(url)) 0xFF1A3A5C.toInt()
                    else Color.TRANSPARENT
                )
            }
        }

        fun renderList(currentUrls: List<String>) {
            listContainer.removeAllViews()
            rowViews.clear()
            emptyLabel.visibility = if (currentUrls.isEmpty()) View.VISIBLE else View.GONE

            currentUrls.forEach { url ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    isClickable = true
                    isFocusable = true
                    isLongClickable = true
                    setBackgroundColor(
                        if (selectedUrls.contains(url)) 0xFF1A3A5C.toInt() else Color.TRANSPARENT
                    )
                }

                // Checkbox / radio indicator
                val checkBox = TextView(activity).apply {
                    textSize = 20f
                    text = if (selectedUrls.contains(url)) "✓" else ""
                    setTextColor(if (selectedUrls.contains(url)) 0xFF0078D7.toInt() else 0xFF555555.toInt())
                    visibility = if (selectedUrls.contains(url)) android.view.View.VISIBLE else android.view.View.INVISIBLE
                    layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).also {
                        it.rightMargin = dp(12)
                        it.gravity = Gravity.CENTER
                    }
                    gravity = Gravity.CENTER
                }

                // Icon ngôi sao
                val starIcon = ImageView(activity).apply {
                    setImageResource(R.drawable.ic_wp_star_filled)
                    imageTintList = android.content.res.ColorStateList.valueOf(0xFF0078D7.toInt())
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).also {
                        it.rightMargin = dp(14)
                        it.gravity = Gravity.CENTER
                    }
                }

                // Tên miền
                val label = TextView(activity).apply {
                    text = domainLabel(url)
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                // Nút xóa nhanh (1 item, chỉ hiện khi không ở chế độ chọn nhiều)
                val btnDelete = TextView(activity).apply {
                    text = "🗑"
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    setPadding(dp(8), dp(4), dp(4), dp(4))
                    setOnClickListener { onRemove(url) }
                }

                row.addView(checkBox)
                row.addView(starIcon)
                row.addView(label)
                row.addView(btnDelete)

                // Nhấn giữ -> vào chế độ chọn nhiều
                row.setOnLongClickListener {
                    if (selectedUrls.contains(url)) selectedUrls.remove(url)
                    else selectedUrls.add(url)
                    updateActionBar(currentUrls)
                    // Cập nhật dòng này
                    row.setBackgroundColor(
                        if (selectedUrls.contains(url)) 0xFF1A3A5C.toInt() else Color.TRANSPARENT
                    )
                    checkBox.text = if (selectedUrls.contains(url)) "✓" else ""
                    checkBox.visibility = if (selectedUrls.contains(url)) android.view.View.VISIBLE else android.view.View.INVISIBLE
                    true
                }

                // Bấm 1 lần: nếu đang ở chế độ chọn nhiều thì toggle chọn, nếu không thì mở
                row.setOnClickListener {
                    if (selectedUrls.isNotEmpty()) {
                        if (selectedUrls.contains(url)) selectedUrls.remove(url)
                        else selectedUrls.add(url)
                        updateActionBar(currentUrls)
                        row.setBackgroundColor(
                            if (selectedUrls.contains(url)) 0xFF1A3A5C.toInt() else Color.TRANSPARENT
                        )
                        checkBox.text = if (selectedUrls.contains(url)) "✓" else ""
                        checkBox.visibility = if (selectedUrls.contains(url)) android.view.View.VISIBLE else android.view.View.INVISIBLE
                    } else {
                        onOpen(url)
                    }
                }

                // Divider dưới mỗi row
                val rowDivider = View(activity).apply {
                    setBackgroundColor(0xFF222222.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                    ).also { it.setMargins(dp(16), 0, dp(16), 0) }
                }

                rowViews[url] = row
                listContainer.addView(row)
                listContainer.addView(rowDivider)
            }

            updateActionBar(currentUrls)
        }

        // Gán action cho các nút
        var currentUrlsRef = urls.toMutableList()

        btnClearSelection.setOnClickListener {
            selectedUrls.clear()
            updateActionBar(currentUrlsRef)
        }

        btnSelectAll.setOnClickListener {
            selectedUrls.addAll(currentUrlsRef)
            updateActionBar(currentUrlsRef)
        }

        btnOpenSelected.setOnClickListener {
            val toOpen = selectedUrls.toList()
            selectedUrls.clear()
            updateActionBar(currentUrlsRef)
            toOpen.forEach { onOpen(it) }
        }

        btnDeleteSelected.setOnClickListener {
            val toDelete = selectedUrls.toList()
            selectedUrls.clear()
            toDelete.forEach { onRemove(it) }
            // renderList sẽ được gọi lại từ bên ngoài qua Handle.update()
        }

        val outerLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        renderList(urls)

        scrollView.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        outerLayout.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        outerLayout.addView(actionBar)

        overlay.addView(outerLayout, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val wrappedRenderList: (List<String>) -> Unit = { newUrls ->
            currentUrlsRef = newUrls.toMutableList()
            selectedUrls.retainAll(newUrls.toSet())
            renderList(newUrls)
        }

        return Handle(root, overlay, wrappedRenderList)
    }
}
