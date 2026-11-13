package com.portalopen.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max

/**
 * Frosted glass slide. Does not (and cannot) sample or blur the Android launcher/icons.
 * It operates solely on a wallpaper bitmap or built-in abstract wallpaper artwork.
 */
class PortalRenderer(private var image: Bitmap? = null) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var blurred: Bitmap? = null
    private var blurredSource: Bitmap? = null

    fun setImage(newImage: Bitmap?) {
        image = newImage
        blurred = null
        blurredSource = null
    }

    fun render(canvas: Canvas, width: Int, height: Int, t: Float) {
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val p = smoother(t.coerceIn(0f, 1f))
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        drawBackdrop(canvas, w, h, false)

        // Two optical panels retreat to either side of the unfolded display.
        val panelWidth = w * .535f
        val shift = w * .57f * p
        glassPanel(canvas, w, h, -shift, panelWidth - shift, true, p)
        glassPanel(canvas, w, h, w - panelWidth + shift, w + shift, false, p)

        // A filmic light wash near the hinge, not a glowing energy beam.
        val remaining = 1f - p
        if (remaining > 0.001f) {
            paint.shader = LinearGradient(w * .47f, 0f, w * .56f, 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb((28 * remaining).toInt(), 255, 244, 231), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
    }

    private fun drawBackdrop(canvas: Canvas, w: Float, h: Float, useBlur: Boolean) {
        val photo = image
        if (photo != null) {
            val current = if (useBlur) getBlurred(photo) else photo
            drawCover(canvas, current, w, h)
            return
        }
        // Built-in quiet color study; selecting a real photo gives the intended result.
        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.rgb(48, 57, 68), Color.rgb(98, 95, 103), Color.rgb(171, 132, 116), Color.rgb(206, 180, 151)),
            floatArrayOf(0f, .35f, .76f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val radius = max(w, h) * .74f
        paint.shader = RadialGradient(w * .73f, h * .66f, radius,
            intArrayOf(Color.argb(112, 249, 218, 185), Color.argb(20, 255, 232, 217), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.shader = LinearGradient(0f, h * .50f, 0f, h,
            intArrayOf(Color.TRANSPARENT, Color.argb(70, 29, 39, 47)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun glassPanel(canvas: Canvas, w: Float, h: Float, left: Float, right: Float, isLeft: Boolean, progress: Float) {
        val clippedLeft = max(0f, left)
        val clippedRight = kotlin.math.min(w, right)
        if (clippedRight <= clippedLeft) return
        canvas.save()
        canvas.clipRect(clippedLeft, 0f, clippedRight, h)

        val photo = image
        if (photo != null) {
            // Blurred version is kept anchored to the wallpaper: only the GLASS slides.
            drawCover(canvas, getBlurred(photo), w, h)
        } else {
            // Fallback opaque translucent film, with diffuse shading.
            paint.color = Color.argb(152, 222, 220, 215)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        paint.color = Color.argb(85, 221, 226, 230)
        canvas.drawRect(clippedLeft, 0f, clippedRight, h, paint)
        paint.shader = LinearGradient(left, 0f, right, 0f,
            if (isLeft) intArrayOf(Color.argb(51, 255, 255, 255), Color.argb(11, 255, 255, 255), Color.argb(45, 254, 251, 245))
            else intArrayOf(Color.argb(55, 254, 251, 245), Color.argb(7, 255, 255, 255), Color.argb(50, 255, 255, 255)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(clippedLeft, 0f, clippedRight, h, paint)
        paint.shader = null

        // Hairline illuminated edges; restrained enough to feel like optical glass.
        paint.strokeWidth = max(1f, w * .0018f)
        paint.color = Color.argb((140 * (1f - .60f * progress)).toInt(), 255, 253, 244)
        val seamX = if (isLeft) right else left
        canvas.drawLine(seamX, 0f, seamX, h, paint)
        paint.color = Color.argb((30 * (1f - progress)).toInt(), 35, 39, 48)
        canvas.drawLine(seamX + (if (isLeft) -w * .006f else w * .006f), 0f,
            seamX + (if (isLeft) -w * .006f else w * .006f), h, paint)
        canvas.restore()
    }

    private fun drawCover(canvas: Canvas, bitmap: Bitmap, w: Float, h: Float) {
        val scale = max(w / bitmap.width, h / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val target = RectF((w - drawW) / 2, (h - drawH) / 2, (w + drawW) / 2, (h + drawH) / 2)
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 255
        canvas.drawBitmap(bitmap, null, target, paint)
    }

    private fun getBlurred(photo: Bitmap): Bitmap {
        if (blurredSource === photo && blurred != null) return blurred!!
        // Aggressive downsample approximates soft frosted glass without deprecated RenderScript.
        // Processed once per image, never inside animation frames.
        val scale = 28f / max(photo.width, photo.height).toFloat()
        blurred = Bitmap.createScaledBitmap(photo,
            max(2, (photo.width * scale).toInt()), max(2, (photo.height * scale).toInt()), true)
        blurredSource = photo
        return blurred!!
    }

    private fun smoother(t: Float): Float = t * t * (3f - 2f * t)
}
