package com.portalopen.app

import kotlin.math.max
import kotlin.math.min

/**
 * Tuned using Fold8's 10:16 cover (~0.633) and 4:3 inside (~0.755).
 * Applies in either rotation; intentionally refuses the gray zone.
 */
object FoldScreenAspect {
    fun classify(width: Int, height: Int): FoldStage {
        if (width <= 0 || height <= 0) return FoldStage.UNKNOWN
        val ratio = min(width, height).toFloat() / max(width, height).toFloat()
        return when {
            ratio >= 0.715f -> FoldStage.OPEN
            ratio <= 0.670f -> FoldStage.CLOSED
            else -> FoldStage.UNKNOWN
        }
    }
}
