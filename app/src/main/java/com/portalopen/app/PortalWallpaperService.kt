package com.portalopen.app

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * v0.4: only play on an observed CLOSED -> OPEN edge.
 * Preference: real hinge-angle sensor (when available and reporting) > display state fallback.
 * No overlay permission, no privileged DeviceStateManager APIs, no system UI interference.
 */
class PortalWallpaperService : WallpaperService(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private val machine = FoldTransitionMachine()
    private val engines = mutableSetOf<GlassEngine>()
    private var sensorManager: SensorManager? = null
    private var angleSensor: Sensor? = null
    private var lastAngleAtMs: Long = -1L
    private var lastAngleStage = FoldStage.UNKNOWN
    private var lastFallbackDetail: String = ""

    override fun onCreate() {
        super.onCreate()
        FoldDiagnostics.record(this, "배경화면 서비스 시작 (초기 상태는 애니메이션 생략)")
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        angleSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        val sensor = angleSensor
        if (sensor != null) {
            val ok = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI) == true
            FoldDiagnostics.record(this, "힌지 센서 ${if (ok) "등록됨" else "등록 실패, 화면 감지 대체"}")
            if (!ok) angleSensor = null
        } else {
            FoldDiagnostics.record(this, "힌지 센서 미제공: 화면 상태 방식 사용")
        }
    }

    override fun onCreateEngine(): Engine = GlassEngine().also { engines += it }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
        val angle = event.values[0]
        val now = SystemClock.elapsedRealtime()
        lastAngleAtMs = now
        val stage = when {
            angle <= 18f -> FoldStage.CLOSED
            angle >= 155f -> FoldStage.OPEN
            else -> FoldStage.HALF_OPENED
        }
        if (lastAngleStage == stage) return
        lastAngleStage = stage
        observeFold(stage, "힌지 센서 ${"%.0f".format(angle)}°", now)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun observeFold(stage: FoldStage, source: String, now: Long) {
        val previous = machine.currentStableStage
        val opened = machine.observe(stage, now, source)
        if (stage != FoldStage.HALF_OPENED && stage != FoldStage.UNKNOWN && previous != stage) {
            FoldDiagnostics.record(this, "${previous.name} → ${stage.name} ($source)")
        }
        if (opened) {
            FoldDiagnostics.record(this, "펼침 전환 확인 #${machine.totalOpenEvents}, 내부 화면 대기")
            engines.toList().forEach { it.tryPlayPending() }
        }
    }

    private fun onWallpaperScreen(engine: GlassEngine) {
        if (!engine.isReadyForObservation()) return
        val result = FoldScreenClassifier.classify(this, engine.widthPx, engine.heightPx)
        engine.lastClassified = result.stage
        if (engine.isPreview) return // chooser must not affect real fold state
        if (!engine.visible) return
        val now = SystemClock.elapsedRealtime()
        // Trust a recently reporting physical sensor over the display-based inference.
        val sensorFresh = lastAngleAtMs >= 0 && now - lastAngleAtMs <= 2_500L
        if (!sensorFresh && result.stage != FoldStage.UNKNOWN) {
            if (lastFallbackDetail != result.detail) {
                lastFallbackDetail = result.detail
                FoldDiagnostics.record(this, "화면 관측: ${result.stage.name} (${result.source}, ${result.detail})")
            }
            observeFold(result.stage, result.source, now)
        }
        engine.tryPlayPending()
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private inner class GlassEngine : Engine() {
        private val renderer by lazy { PortalRenderer(WallpaperArt.load(this@PortalWallpaperService)) }
        private val drawNext = Runnable { drawFrame() }
        private val durationMs = 1150L
        var widthPx = 0
            private set
        var heightPx = 0
            private set
        var visible = false
            private set
        var lastClassified = FoldStage.UNKNOWN
        private var surfaceReady = false
        private var animating = false
        private var startAt = 0L
        private var progress = 1f // ordinary wallpaper both before and after the transition

        fun isReadyForObservation() = surfaceReady && widthPx > 0 && heightPx > 0

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            widthPx = width
            heightPx = height
            onWallpaperScreen(this)
            drawFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            handler.removeCallbacks(drawNext)
            if (!visible) {
                animating = false
                progress = 1f
                return
            }
            onWallpaperScreen(this)
            drawFrame()
        }

        /** Physical event may precede creation of the inner display surface. */
        fun tryPlayPending() {
            if (!surfaceReady || !visible || isPreview) return
            val screen = FoldScreenClassifier.classify(this@PortalWallpaperService, widthPx, heightPx)
            lastClassified = screen.stage
            if (screen.stage != FoldStage.OPEN) return
            val serial = machine.consumeIfRecent(SystemClock.elapsedRealtime()) ?: return
            progress = 0f
            startAt = SystemClock.uptimeMillis()
            animating = true
            FoldDiagnostics.record(this@PortalWallpaperService, "애니메이션 시작 #$serial (내부 화면, ${screen.source})")
            drawFrame()
        }

        private fun drawFrame() {
            handler.removeCallbacks(drawNext)
            if (!isReadyForObservation()) return
            if (animating) {
                progress = ((SystemClock.uptimeMillis() - startAt).toFloat() / durationMs)
                    .coerceIn(0f, 1f)
                if (progress >= 1f) animating = false
            }
            val canvas = try { surfaceHolder.lockCanvas() } catch (_: Exception) { null }
            if (canvas != null) {
                try { renderer.render(canvas, widthPx, heightPx, progress) }
                finally { try { surfaceHolder.unlockCanvasAndPost(canvas) } catch (_: Exception) {} }
            }
            if (visible && animating) handler.postDelayed(drawNext, 16L)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            animating = false
            handler.removeCallbacks(drawNext)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            engines -= this
            handler.removeCallbacks(drawNext)
            super.onDestroy()
        }
    }
}
