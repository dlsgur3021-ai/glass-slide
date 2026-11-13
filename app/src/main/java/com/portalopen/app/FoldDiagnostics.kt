package com.portalopen.app

import android.content.Context
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps only a small local log. No permissions, cloud calls or always-on background service. */
object FoldDiagnostics {
    private const val PREF = "fold_diagnostics_v04"
    private const val HISTORY = "history"

    fun record(context: Context, message: String) {
        val clock = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val old = prefs.getString(HISTORY, "").orEmpty().lines().filter { it.isNotBlank() }
        val updated = (listOf("$clock  $message") + old).take(16).joinToString("\n")
        prefs.edit().putString(HISTORY, updated).apply()
    }

    fun get(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getString(HISTORY, "아직 기록이 없습니다. 커버 화면에서 접었다 펼쳐 보세요.").orEmpty()

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        record(context, "기록 초기화 완료")
    }
}
