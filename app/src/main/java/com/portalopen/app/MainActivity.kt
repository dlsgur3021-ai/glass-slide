package com.portalopen.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density + .5f).toInt()
    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(4).toFloat(), 1f)
    }
    private fun button(title: String, color: Int, click: () -> Unit): TextView = text(title, 16f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        minHeight = dp(58)
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(17).toFloat() }
        setOnClickListener { click() }
    }
    private fun add(root: LinearLayout, view: TextView, top: Int) {
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(20, 27, 33)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(23), dp(42), dp(23), dp(35))
        }
        scroll.addView(root)
        setContentView(scroll)
        add(root, text("GLASS SLIDE", 29f, Color.WHITE, true), 0)
        add(root, text("UNFOLD EFFECT  |  v0.6 실험판", 12f, Color.rgb(174, 190, 192)), 3)
        add(root, text("폴드8을 펼치는 순간,\n부드러운 유리막이 양옆으로 열립니다.", 21f, Color.rgb(239, 235, 226), true), 33)
        add(root, text("배경화면이 아닌 화면 위 연출입니다. 앱을 닫아도 작동하도록 알림 영역에서 감지를 유지합니다.", 14f, Color.rgb(187, 200, 200)), 16)
        status = text("", 14f, Color.rgb(232, 216, 181), true)
        add(root, status, 30)
        add(root, button("① 펼침 자동 효과 켜기", Color.rgb(72, 110, 112)) {
            requestAndStart(FoldOverlayService.ACTION_START)
        }, 14)
        add(root, button("② 지금 효과 미리보기", Color.rgb(102, 111, 132)) {
            requestAndStart(FoldOverlayService.ACTION_PREVIEW)
        }, 10)
        add(root, button("효과 끄기", Color.rgb(82, 78, 76)) {
            stopService(Intent(this, FoldOverlayService::class.java))
            Toast.makeText(this, "펼침 감지를 중지했습니다.", Toast.LENGTH_SHORT).show()
            status.postDelayed({ refresh() }, 250)
        }, 10)
        add(root, text("설정 방법", 18f, Color.WHITE, true), 31)
        add(root, text("1. 자동 효과 켜기를 누른 뒤 '다른 앱 위에 표시'를 허용합니다.\n2. 앱으로 돌아와 자동 효과 켜기를 다시 누릅니다.\n3. 커버 화면으로 접었다가 다시 펼쳐 보세요.\n4. 테스트만 할 때는 미리보기 버튼을 사용합니다.", 14f, Color.rgb(213, 215, 214)), 9)
        add(root, text("실행 기록", 18f, Color.WHITE, true), 29)
        add(root, button("기록 새로고침", Color.rgb(56, 68, 76)) { refresh() }, 9)
        log = text("", 12f, Color.rgb(177, 194, 193)).apply { setTextIsSelectable(true) }
        add(root, log, 12)
        add(root, text("※ 기기 또는 삼성 시스템 설정에 따라 접힘 감지가 제한될 수 있습니다. 잠금 화면과 시스템 전환 자체는 바꾸지 않습니다. 사용 중에는 지속적인 실행 알림이 표시됩니다.", 12f, Color.rgb(150, 165, 168)), 28)
        refresh()
    }
    private fun requestAndStart(action: String) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Glass Slide의 '다른 앱 위에 표시'를 허용해주세요.", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
            return
        }
        try {
            startForegroundService(Intent(this, FoldOverlayService::class.java).setAction(action))
            status.postDelayed({ refresh() }, 600)
        } catch (e: Exception) {
            Toast.makeText(this, "실행 실패: " + e.javaClass.simpleName, Toast.LENGTH_LONG).show()
        }
    }
    override fun onResume() { super.onResume(); if (::status.isInitialized) refresh() }
    private fun refresh() {
        val p = getSharedPreferences(FoldOverlayService.PREFS, MODE_PRIVATE)
        val running = p.getBoolean("running", false)
        status.text = if (running) "● 펼침 효과 감지 실행 중" else "○ 자동 효과 꺼짐"
        if (::log.isInitialized) log.text = p.getString("log", "기록 없음") ?: "기록 없음"
    }
}
