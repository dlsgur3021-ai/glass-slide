package com.portalopen.app

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View

/** User-controlled preview, independent of the actual home-screen transition. */
class PortalPreviewView(context: Context) : View(context) {
    private val renderer = PortalRenderer(WallpaperArt.load(context))
    private val durationMs = 1150L
    private var startAt = 0L
    private var playing = false
    private var manualProgress = 1f

    fun setImage(bitmap: android.graphics.Bitmap?) {
        renderer.setImage(bitmap)
        invalidate()
    }

    fun play() {
        startAt = SystemClock.uptimeMillis()
        playing = true
        invalidate()
    }

    fun setManualProgress(value: Float) {
        playing = false
        manualProgress = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = if (playing) {
            ((SystemClock.uptimeMillis() - startAt).toFloat() / durationMs).coerceIn(0f, 1f)
        } else manualProgress
        renderer.render(canvas, width, height, p)
        if (playing && p < 1f) {
            postInvalidateDelayed(16L)
        } else if (playing) {
            playing = false
            manualProgress = 1f
        }
    }
}
