package com.portalopen.app

/**
 * Platform-independent fold-to-open state machine.
 * A first observation is a baseline, NOT an unfold. HALF_OPENED does not reset a closed state.
 * An animation is issued at most once per CLOSED -> OPEN transition.
 */
enum class FoldStage { UNKNOWN, CLOSED, HALF_OPENED, OPEN }

class FoldTransitionMachine(private val pendingLifetimeMs: Long = 4_000L) {
    private var stableStage: FoldStage = FoldStage.UNKNOWN
    private var pendingAtMs: Long = -1L
    private var pendingSerial: Int = 0
    var totalOpenEvents: Int = 0
        private set
    var lastSource: String = "관측 없음"
        private set

    val currentStableStage: FoldStage get() = stableStage
    val hasPendingOpen: Boolean get() = pendingAtMs >= 0

    /** @return true only when a genuine CLOSED -> OPEN edge was observed. */
    fun observe(stage: FoldStage, elapsedMs: Long, source: String): Boolean {
        lastSource = source
        if (stage == FoldStage.UNKNOWN || stage == FoldStage.HALF_OPENED) return false
        if (stage == stableStage) return false
        val was = stableStage
        stableStage = stage
        if (stage == FoldStage.CLOSED) {
            pendingAtMs = -1L
            return false
        }
        if (was == FoldStage.CLOSED && stage == FoldStage.OPEN) {
            totalOpenEvents++
            pendingSerial = totalOpenEvents
            pendingAtMs = elapsedMs
            return true
        }
        // Opening from UNKNOWN is the initial state, not a physical transition.
        pendingAtMs = -1L
        return false
    }

    /** Claim an event for the visible INNER wallpaper. Consumes it exactly once. */
    fun consumeIfRecent(nowMs: Long): Int? {
        if (pendingAtMs < 0L) return null
        val fresh = nowMs >= pendingAtMs && nowMs - pendingAtMs <= pendingLifetimeMs
        pendingAtMs = -1L
        return if (fresh) pendingSerial else null
    }
}
