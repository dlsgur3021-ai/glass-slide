package com.portalopen.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class FoldOverlayService : Service(), SensorEventListener {
    companion object {
        const val PREFS = "glass_slide_overlay_v06"
        const val ACTION_START = "glassslide.START"
        const val ACTION_PREVIEW = "glassslide.PREVIEW"
        private const val CHANNEL = "glass_slide_status"
    }
    private val handler = Handler(Looper.getMainLooper())
    private val displayManager by lazy { getSystemService(DISPLAY_SERVICE) as DisplayManager }
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val windows by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var hinge: Sensor? = null
    private var lastSensorAt = -1L
    private var sensorStage = FoldStage.UNKNOWN
    private var stable = FoldStage.UNKNOWN
    private var overlay: GlassVeil? = null
    private var animator: ValueAnimator? = null
    private val poll = object : Runnable {
        override fun run() {
            sampleState()
            handler.postDelayed(this, 450)
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = sampleState()
        override fun onDisplayRemoved(displayId: Int) = sampleState()
        override fun onDisplayChanged(displayId: Int) = sampleState()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Glass Slide 실행 상태",
            NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Glass Slide 펼침 효과 실행 중")
            .setContentText("접었다 펼칠 때의 화면 전환을 감지합니다")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1016, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1016, notification)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("running", true).apply()
        note("서비스 실행, 접힘 감지 시작")
        hinge = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        hinge?.let { if (!sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)) hinge = null }
        note(if (hinge != null) "힌지 센서 연결됨" else "힌지 센서 없음: 화면 비율 방식")
        displayManager.registerDisplayListener(displayListener, handler)
        handler.post(poll)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PREVIEW) handler.postDelayed({ showEffect("수동 미리보기") }, 300)
        return START_STICKY
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
        lastSensorAt = SystemClock.elapsedRealtime()
        val angle = event.values[0]
        sensorStage = when {
            angle <= 18f -> FoldStage.CLOSED
            angle >= 155f -> FoldStage.OPEN
            else -> FoldStage.HALF_OPENED
        }
        observe(sensorStage, "힌지 " + angle.toInt() + "도")
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun sampleState() {
        val now = SystemClock.elapsedRealtime()
        val stage = if (lastSensorAt > 0 && now - lastSensorAt <= 1800 &&
            sensorStage != FoldStage.UNKNOWN) sensorStage else screenStage()
        observe(stage, "화면/센서")
    }
    private fun screenStage(): FoldStage {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            val point = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            val stage = FoldScreenAspect.classify(point.x, point.y)
            if (stage != FoldStage.UNKNOWN) return stage
        }
        val config = resources.configuration
        return FoldScreenAspect.classify(config.screenWidthDp, config.screenHeightDp)
    }
    private fun observe(stage: FoldStage, source: String) {
        if (stage == FoldStage.UNKNOWN || stage == FoldStage.HALF_OPENED || stage == stable) return
        val old = stable
        stable = stage
        if (stage == FoldStage.CLOSED) note("접힘 확인 (" + source + ")")
        if (stage == FoldStage.OPEN && old == FoldStage.CLOSED) {
            note("펼침 확인: 효과 실행 예약")
            handler.postDelayed({ showEffect("펼침 자동 실행") }, 220)
        }
    }
    private fun showEffect(reason: String) {
        if (!Settings.canDrawOverlays(this)) { note("다른 앱 위에 표시 권한 없음"); return }
        if (overlay != null) return
        val view = GlassVeil(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Glass Slide unfolding effect"
        }
        try {
            windows.addView(view, params)
            overlay = view
            note(reason + ": 화면 효과 시작")
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1080L
                addUpdateListener { a -> view.progress = a.animatedValue as Float; view.invalidate() }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { removeOverlay() }
                    override fun onAnimationCancel(animation: Animator) { removeOverlay() }
                })
            }
            animator = anim
            anim.start()
        } catch (e: Exception) {
            note("효과 표시 실패: " + e.javaClass.simpleName)
            removeOverlay()
        }
    }
    private fun removeOverlay() {
        val view = overlay ?: return
        overlay = null
        animator = null
        try { windows.removeViewImmediate(view) } catch (_: Exception) { }
    }
    private fun note(message: String) {
        val p = getSharedPreferences(PREFS, MODE_PRIVATE)
        val old = p.getString("log", "").orEmpty().lines().filter { it.isNotBlank() }
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date())
        p.edit().putString("log", (listOf(stamp + "  " + message) + old).take(12).joinToString("\n")).apply()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        displayManager.unregisterDisplayListener(displayListener)
        sensorManager.unregisterListener(this)
        animator?.cancel()
        removeOverlay()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("running", false).apply()
        super.onDestroy()
    }
    private class GlassVeil(context: Context) : View(context) {
        var progress = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w < 2 || h < 2) return
            val t = 1f - (1f - progress) * (1f - progress) * (1f - progress)
            val middle = w / 2f
            val travel = t * (middle + w * .08f)
            val fade = (1f - progress * .8f).coerceIn(0f, 1f)
            val a = (fade * 255).toInt()
            paint.alpha = a
            paint.shader = LinearGradient(0f, 0f, middle, h,
                intArrayOf(Color.argb(236, 249, 237, 214), Color.argb(207, 210, 225, 233),
                    Color.argb(221, 233, 216, 201)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(-travel, 0f, middle - travel, h, paint)
            paint.shader = LinearGradient(middle, 0f, w, h,
                intArrayOf(Color.argb(218, 207, 224, 231), Color.argb(229, 250, 241, 226),
                    Color.argb(213, 218, 207, 202)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(middle + travel, 0f, w + travel, h, paint)
            paint.shader = null
            paint.color = Color.argb((170 * fade).toInt(), 255, 252, 245)
            val edgeW = w * .009f
            canvas.drawRect(middle - travel - edgeW, 0f, middle - travel, h, paint)
            canvas.drawRect(middle + travel, 0f, middle + travel + edgeW, h, paint)
        }
    }
}
