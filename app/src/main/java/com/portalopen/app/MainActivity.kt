package com.portalopen.app

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Unfold Home v0.5: a real, selectable HOME launcher.
 * Uses actual installed launchable activities, without an overlay or private system API.
 * Fold transitions animate app icons between their old/new relative grid positions.
 */
class MainActivity : Activity() {
    private data class AppEntry(
        val key: String,
        val label: String,
        val component: ComponentName,
        val icon: Drawable
    )
    private data class RelPosition(val x: Float, val y: Float)

    private val prefs by lazy { getSharedPreferences("unfold_home", MODE_PRIVATE) }
    private var appEntries: List<AppEntry> = emptyList()
    private var favorites: MutableList<String> = mutableListOf()
    private lateinit var frame: FrameLayout
    private lateinit var home: LinearLayout
    private var drawer: FrameLayout? = null
    private var iconViews: MutableMap<String, View> = linkedMapOf()
    private var lastWide = false
    private var lastPoints: Map<String, RelPosition> = emptyMap()
    private val ease = PathInterpolator(0.18f, 0.80f, 0.20f, 1f)
    private val white = Color.WHITE
    private val subWhite = Color.rgb(221, 231, 242)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
    private fun bg(fill: Int, radius: Int = 20, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun text(value: String, sp: Float, bold: Boolean = false, color: Int = white): TextView =
        TextView(this).apply {
            this.text = value
            textSize = sp
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun wide(): Boolean {
        val c = resources.configuration
        val smaller = min(c.screenWidthDp, c.screenHeightDp)
        // Based on physical expansion, but no model-specific API or guessed hinge event.
        return c.screenWidthDp >= 560 || c.smallestScreenWidthDp >= 560 || smaller >= 560
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.rgb(13, 22, 33)
        lastWide = wide()
        appEntries = loadApps()
        readFavorites()
        buildHome(emptyMap(), false)
    }

    override fun onResume() {
        super.onResume()
        if (::frame.isInitialized && drawer == null) {
            val loaded = loadApps()
            if (loaded.map { it.key } != appEntries.map { it.key }) {
                val old = rememberPositions()
                appEntries = loaded
                favorites.retainAll(appEntries.map { it.key }.toSet())
                seedFavorites()
                saveFavorites()
                buildHome(old, false)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val from = rememberPositions()
        super.onConfigurationChanged(newConfig)
        val expanded = wide()
        val changed = expanded != lastWide
        lastWide = expanded
        buildHome(from, changed)
    }

    private fun loadApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(intent, 0)
        val unique = linkedMapOf<String, AppEntry>()
        for (ri in resolved) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == packageName) continue
            val component = ComponentName(ai.packageName, ai.name)
            val key = component.flattenToString()
            try {
                unique[key] = AppEntry(
                    key = key,
                    label = ri.loadLabel(packageManager).toString(),
                    component = component,
                    icon = ri.loadIcon(packageManager)
                )
            } catch (_: Exception) { /* Bad or removed external activity */ }
        }
        return unique.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun readFavorites() {
        val stored = prefs.getString("favorites", null)
        favorites = if (stored.isNullOrBlank()) mutableListOf()
        else stored.split("|").filter { it.isNotBlank() }.toMutableList()
        favorites.retainAll(appEntries.map { it.key }.toSet())
        seedFavorites()
        saveFavorites()
    }

    private fun seedFavorites() {
        if (favorites.isNotEmpty()) return
        val packages = listOf(
            "com.sec.android.app.camera", "com.samsung.android.dialer",
            "com.google.android.dialer", "com.kakao.talk", "com.android.chrome",
            "com.sec.android.app.sbrowser", "com.google.android.youtube",
            "com.google.android.apps.photos", "com.samsung.android.messaging",
            "com.google.android.apps.messaging", "com.sec.android.gallery3d",
            "com.samsung.android.app.notes", "com.android.settings",
            "com.sec.android.app.myfiles"
        )
        for (p in packages) {
            val found = appEntries.firstOrNull { it.component.packageName == p }
            if (found != null && found.key !in favorites) favorites.add(found.key)
        }
        for (entry in appEntries) {
            if (favorites.size >= 12) break
            if (entry.key !in favorites) favorites.add(entry.key)
        }
    }

    private fun saveFavorites() {
        prefs.edit().putString("favorites", favorites.joinToString("|")).apply()
    }

    private fun rememberPositions(): Map<String, RelPosition> {
        if (!::frame.isInitialized || frame.width <= 0 || frame.height <= 0) return lastPoints
        val rootXY = IntArray(2)
        frame.getLocationOnScreen(rootXY)
        val result = mutableMapOf<String, RelPosition>()
        for ((key, view) in iconViews) {
            if (!view.isShown || view.width <= 0) continue
            val xy = IntArray(2)
            view.getLocationOnScreen(xy)
            val x = (xy[0] - rootXY[0] + view.width * 0.5f) / frame.width
            val y = (xy[1] - rootXY[1] + view.height * 0.5f) / frame.height
            result[key] = RelPosition(x, y)
        }
        if (result.isNotEmpty()) lastPoints = result
        return if (result.isNotEmpty()) result else lastPoints
    }

    private fun buildHome(previous: Map<String, RelPosition>, foldChange: Boolean) {
        drawer = null
        iconViews = linkedMapOf()
        frame = FrameLayout(this).apply {
            // Wallpaper from Android sits beneath this window.
            setBackgroundColor(Color.TRANSPARENT)
        }
        setContentView(frame)

        val scrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(116, 8, 20, 35), Color.argb(55, 10, 18, 28), Color.argb(157, 9, 15, 25))
            )
        }
        frame.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val wide = wide()
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (wide) 34 else 19), dp(if (wide) 22 else 16),
                dp(if (wide) 34 else 19), dp(16))
        }
        frame.addView(home, FrameLayout.LayoutParams(-1, -1))
        buildHeader(wide)
        buildSearch()
        buildFeature(wide)

        home.addView(View(this), LinearLayout.LayoutParams(1, 0, if (wide) 0.7f else 1.0f))
        buildGrid(wide)
        home.addView(View(this), LinearLayout.LayoutParams(1, 0, if (wide) 0.12f else 0.28f))
        buildDock(wide)
        buildFooter()

        if (foldChange || previous.isNotEmpty()) {
            frame.post {
                if (frame.width == 0 || frame.height == 0) return@post
                val rootXY = IntArray(2)
                frame.getLocationOnScreen(rootXY)
                iconViews.entries.forEachIndexed { index, (key, view) ->
                    val to = IntArray(2)
                    view.getLocationOnScreen(to)
                    val old = previous[key]
                    if (old != null) {
                        val targetX = to[0] - rootXY[0] + view.width * 0.5f
                        val targetY = to[1] - rootXY[1] + view.height * 0.5f
                        view.translationX = (old.x * frame.width - targetX).coerceIn(-frame.width.toFloat(), frame.width.toFloat())
                        view.translationY = (old.y * frame.height - targetY).coerceIn(-frame.height.toFloat(), frame.height.toFloat())
                    } else {
                        view.translationY = dp(25).toFloat()
                    }
                    view.alpha = if (old != null) 0.75f else 0f
                    view.scaleX = if (foldChange) 0.87f else 0.96f
                    view.scaleY = if (foldChange) 0.87f else 0.96f
                    view.animate().translationX(0f).translationY(0f)
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(if (foldChange) 620L else 380L)
                        .setStartDelay((index % 6 * 22).toLong())
                        .setInterpolator(ease).start()
                }
                if (foldChange) {
                    scrim.alpha = 0.38f
                    scrim.animate().alpha(1f).setDuration(670L).setInterpolator(ease).start()
                }
            }
        }
    }

    private fun buildHeader(wide: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        home.addView(row, LinearLayout.LayoutParams(-1, -2))
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        left.addView(text("UNFOLD   /   HOME", 11f, true, Color.rgb(226, 238, 249)).apply {
            letterSpacing = 0.18f
        })
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = if (wide) 54f else 48f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
            includeFontPadding = false
            setPadding(0, dp(8), 0, 0)
        }
        left.addView(clock)
        val dateString = SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(Date())
        left.addView(text(dateString, 14f, false, subWhite).apply { setPadding(0, dp(8), 0, 0) })
        val settings = chip("⚙", 45) { requestHomeRole() }
        settings.contentDescription = "기본 홈 앱 설정"
        row.addView(settings)
    }

    private fun buildSearch() {
        val search = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg(Color.argb(155, 246, 249, 255), 24)
            setPadding(dp(17), 0, dp(12), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { openDrawer(true) }
        }
        val lp = LinearLayout.LayoutParams(-1, dp(48))
        lp.topMargin = dp(22)
        home.addView(search, lp)
        search.addView(text("⌕", 28f, false, Color.rgb(41, 62, 82)))
        search.addView(text("  앱 검색", 14f, false, Color.rgb(61, 77, 96)),
            LinearLayout.LayoutParams(0, -1, 1f))
        search.addView(text("▦", 20f, false, Color.rgb(61, 77, 96)))
    }

    private fun buildFeature(wide: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg(Color.argb(76, 227, 236, 255), 19, Color.argb(82, 255, 255, 255))
            setPadding(dp(16), dp(if (wide) 15 else 13), dp(16), dp(if (wide) 15 else 13))
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        home.addView(row, lp)
        val bullet = text("✦", 23f, true, Color.rgb(232, 245, 255))
        row.addView(bullet)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(13) })
        col.addView(text(if (wide) "WIDE SPACE" else "COVER SPACE", 12f, true))
        col.addView(text(if (wide) "넓어진 화면, 이어지는 움직임" else "펼치는 순간을 위한 홈", 12f, false, subWhite))
        row.addView(text(if (wide) "6 ×" else "4 ×", 13f, true, subWhite))
    }

    private fun buildGrid(wide: Boolean) {
        val cols = if (wide) 6 else 4
        val limit = if (wide) 18 else 12
        val chosen = favorites.drop(4).take(limit)
            .mapNotNull { key -> appEntries.firstOrNull { it.key == key } }
        if (chosen.isEmpty()) {
            home.addView(text("앱 서랍에서 아이콘을 길게 눌러 홈에 추가해봐.", 12f, false, subWhite))
            return
        }
        val rows = (chosen.size + cols - 1) / cols
        for (r in 0 until rows) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            home.addView(line, LinearLayout.LayoutParams(-1, dp(if (wide) 92 else 91)))
            for (c in 0 until cols) {
                val entry = chosen.getOrNull(r * cols + c)
                if (entry == null) {
                    line.addView(View(this), LinearLayout.LayoutParams(0, -1, 1f))
                } else {
                    addIcon(line, entry, false)
                }
            }
        }
    }

    private fun buildDock(wide: Boolean) {
        val selected = favorites.take(4).mapNotNull { k -> appEntries.firstOrNull { it.key == k } }
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(if (wide) 22 else 10), dp(10), dp(if (wide) 22 else 10), dp(8))
            background = bg(Color.argb(103, 242, 249, 255), 25, Color.argb(90, 255, 255, 255))
        }
        home.addView(dock, LinearLayout.LayoutParams(-1, dp(87)))
        for (entry in selected) addIcon(dock, entry, true)
        val missing = 4 - selected.size
        repeat(missing) { dock.addView(View(this), LinearLayout.LayoutParams(0, -1, 1f)) }
    }

    private fun addIcon(parent: LinearLayout, entry: AppEntry, dock: Boolean) {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = entry.label
            setOnClickListener { launch(entry) }
            setOnLongClickListener { showPinDialog(entry); true }
        }
        parent.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
        val iconSize = if (dock) 48 else 51
        cell.addView(ImageView(this).apply {
            setImageDrawable(entry.icon.constantState?.newDrawable()?.mutate() ?: entry.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
        if (!dock) {
            val label = text(entry.label, 11f, false, white).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setShadowLayer(3f, 0f, 2f, Color.BLACK)
            }
            cell.addView(label, LinearLayout.LayoutParams(-1, dp(23)))
        }
        iconViews[entry.key] = cell
    }

    private fun buildFooter() {
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        home.addView(footer, LinearLayout.LayoutParams(-1, dp(47)).apply { topMargin = dp(3) })
        val all = text("⌃   전체 앱 보기", 13f, true, subWhite).apply {
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { openDrawer(false) }
        }
        footer.addView(all, LinearLayout.LayoutParams(0, -1, 1f))
        val settings = text("기본 홈 설정  ⚙", 12f, false, subWhite).apply {
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { requestHomeRole() }
        }
        footer.addView(settings, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun chip(label: String, size: Int, onClick: () -> Unit): TextView =
        text(label, 19f, false, white).apply {
            gravity = Gravity.CENTER
            background = bg(Color.argb(90, 231, 244, 255), 17)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun launch(entry: AppEntry) {
        try {
            val i = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = entry.component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(i)
        } catch (_: Exception) {
            Toast.makeText(this, "앱을 열지 못했어. 앱 목록을 새로 확인해 줘.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPinDialog(entry: AppEntry) {
        val pinned = entry.key in favorites
        val options = arrayOf(if (pinned) "홈 화면에서 제거" else "홈 화면에 추가", "앱 열기")
        AlertDialog.Builder(this)
            .setTitle(entry.label)
            .setItems(options) { _, which ->
                if (which == 1) launch(entry)
                else {
                    val old = rememberPositions()
                    if (pinned) favorites.remove(entry.key) else favorites.add(entry.key)
                    saveFavorites()
                    buildHome(old, false)
                }
            }.show()
    }

    private fun openDrawer(focusSearch: Boolean) {
        if (drawer != null) return
        val panel = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(21, 31, 45))
            alpha = 0f
            translationY = dp(50).toFloat()
        }
        drawer = panel
        frame.addView(panel, FrameLayout.LayoutParams(-1, -1))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(21), dp(18), dp(21), dp(15))
        }
        panel.addView(body, FrameLayout.LayoutParams(-1, -1))
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        body.addView(head, LinearLayout.LayoutParams(-1, dp(53)))
        head.addView(text("전체 앱", 26f, true), LinearLayout.LayoutParams(0, -1, 1f))
        val close = chip("×", 42) { closeDrawer() }
        head.addView(close)

        val search = EditText(this).apply {
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.rgb(23, 35, 47))
            setHintTextColor(Color.rgb(115, 126, 141))
            hint = "앱 이름으로 검색"
            setPadding(dp(18), 0, dp(14), 0)
            background = bg(Color.rgb(245, 249, 255), 18)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        body.addView(search, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(13) })
        val count = text("", 12f, false, subWhite)
        body.addView(count, LinearLayout.LayoutParams(-1, dp(40)))
        val scroll = ScrollView(this).apply { isFillViewport = false }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        fun populate(filter: String) {
            list.removeAllViews()
            val filtered = appEntries.filter { it.label.contains(filter, ignoreCase = true) }
            count.text = "설치된 앱 ${filtered.size}개   ·   길게 누르면 홈에 추가"
            for (entry in filtered) {
                val line = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(6), dp(5), dp(6), dp(5))
                    isClickable = true
                    setOnClickListener { launch(entry) }
                    setOnLongClickListener { showPinDialog(entry); true }
                }
                list.addView(line, LinearLayout.LayoutParams(-1, dp(64)))
                line.addView(ImageView(this).apply {
                    setImageDrawable(entry.icon.constantState?.newDrawable()?.mutate() ?: entry.icon)
                }, LinearLayout.LayoutParams(dp(43), dp(43)))
                line.addView(text(entry.label, 15f, false, white),
                    LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(17) })
                if (entry.key in favorites) line.addView(text("✓ 홈", 11f, false, Color.rgb(168, 216, 254)))
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { populate(s?.toString() ?: "") }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        populate("")
        panel.animate().alpha(1f).translationY(0f).setDuration(260L).setInterpolator(ease).start()
        if (focusSearch) {
            search.requestFocus()
            search.post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            body.isFocusableInTouchMode = true
            body.requestFocus()
        }
    }

    private fun closeDrawer() {
        val current = drawer ?: return
        drawer = null
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(current.windowToken, 0)
        current.animate().alpha(0f).translationY(dp(35).toFloat())
            .setDuration(180L).withEndAction { frame.removeView(current) }.start()
    }

    private fun requestHomeRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        try {
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    AlertDialog.Builder(this).setTitle("기본 홈으로 사용 중")
                        .setMessage("원래 홈으로 돌아가려면 설정 → 앱 → 기본 앱 선택 → 홈 앱에서 One UI 홈을 선택해.")
                        .setPositiveButton("홈 앱 설정 열기") { _, _ -> openHomeSettings() }
                        .setNegativeButton("닫기", null).show()
                } else startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), 203)
            } else openHomeSettings()
        } catch (_: Exception) { openHomeSettings() }
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    @Deprecated("Android back navigation support")
    override fun onBackPressed() {
        if (drawer != null) closeDrawer() else super.onBackPressed()
    }
}
