package com.portalopen.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** User-selected artwork stays in the app's private storage. No gallery permission needed. */
object WallpaperArt {
    private const val NAME = "glass_slide_wallpaper.jpg"
    private const val MAX_UPLOAD = 30L * 1024 * 1024

    fun load(context: Context): Bitmap? = try {
        val file = File(context.filesDir, NAME)
        if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
    } catch (_: Exception) { null }

    fun saveFromUri(context: Context, uri: android.net.Uri): Bitmap? {
        val tmp = File.createTempFile("glass_art", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(16384)
                    var size = 0L
                    while (true) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        size += n
                        if (size > MAX_UPLOAD) throw IllegalArgumentException("Image is too large")
                        output.write(buf, 0, n)
                    }
                }
            } ?: return null
            val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.absolutePath, probe)
            if (probe.outWidth <= 0 || probe.outHeight <= 0) return null
            var sample = 1
            while (probe.outWidth / sample > 2048 || probe.outHeight / sample > 2048) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(tmp.absolutePath, opts) ?: return null
            File(context.filesDir, NAME).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
            }
            return bitmap
        } finally {
            tmp.delete()
        }
    }
}
