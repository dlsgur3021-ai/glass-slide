package com.portalopen.app

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Fallback for the wallpaper process when a real hinge-angle sensor is unavailable.
 * Prefers logical display metrics (not scrollable wallpaper surface dimensions).
 * A normalized min/max aspect ratio prevents rotation being mistaken for a fold.
 * This is still a device-dependent heuristic, not a hardware fold event.
 */
object FoldScreenClassifier {
    data class Result(val stage: FoldStage, val source: String, val detail: String)

    fun classify(context: Context, surfaceW: Int, surfaceH: Int): Result {
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = manager?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            val size = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            FoldScreenAspect.classify(size.x, size.y).takeIf { it != FoldStage.UNKNOWN }?.let { stage ->
                return Result(stage, "논리 디스플레이", "display=${size.x}×${size.y}; surface=${surfaceW}×${surfaceH}")
            }
        }
        val config = context.resources.configuration
        FoldScreenAspect.classify(config.screenWidthDp, config.screenHeightDp).takeIf { it != FoldStage.UNKNOWN }?.let { stage ->
            return Result(stage, "화면 dp", "dp=${config.screenWidthDp}×${config.screenHeightDp}; surface=${surfaceW}×${surfaceH}")
        }
        // The wallpaper surface may be wider due to launcher parallax; lowest-confidence fallback.
        FoldScreenAspect.classify(surfaceW, surfaceH).takeIf { it != FoldStage.UNKNOWN }?.let { stage ->
            return Result(stage, "배경화면 표면(추정)", "surface=${surfaceW}×${surfaceH}")
        }
        return Result(FoldStage.UNKNOWN, "화면 판별 불가", "surface=${surfaceW}×${surfaceH}")
    }

}
