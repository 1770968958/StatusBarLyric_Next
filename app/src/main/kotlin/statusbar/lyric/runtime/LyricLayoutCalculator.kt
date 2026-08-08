package statusbar.lyric.runtime

import kotlin.math.max
import kotlin.math.min

data class LyricLayoutResult(
    val widthPx: Int,
    val overflowPx: Int,
    val scrollSpeed: Float
)

object LyricLayoutCalculator {
    fun calculate(
        textWidthPx: Int,
        parentWidthPx: Int,
        startMarginPx: Int,
        endMarginPx: Int,
        widthPercent: Int,
        fixedWidth: Boolean,
        dynamicSpeed: Boolean,
        baseSpeed: Float,
        delayMillis: Int
    ): LyricLayoutResult {
        val availableWidth = max(parentWidthPx - startMarginPx - endMarginPx, 0)
        val normalizedTextWidth = max(textWidthPx, 0)
        val width = when {
            availableWidth == 0 -> 0
            widthPercent <= 0 -> min(normalizedTextWidth, availableWidth)
            else -> {
                val percentWidth = (availableWidth * widthPercent.coerceIn(0, 100) / 100f).toInt()
                if (fixedWidth) percentWidth else min(normalizedTextWidth, percentWidth)
            }
        }.coerceIn(0, availableWidth)

        val overflow = max(normalizedTextWidth - width, 0)
        val speed = calculateScrollSpeed(
            overflowPx = overflow,
            viewportWidthPx = width,
            delayMillis = delayMillis,
            dynamicSpeed = dynamicSpeed,
            baseSpeed = baseSpeed
        )
        return LyricLayoutResult(width, overflow, speed)
    }

    private fun calculateScrollSpeed(
        overflowPx: Int,
        viewportWidthPx: Int,
        delayMillis: Int,
        dynamicSpeed: Boolean,
        baseSpeed: Float
    ): Float {
        if (overflowPx <= 0 || viewportWidthPx <= 0) return baseSpeed
        val overflowRatio = overflowPx.toFloat() / viewportWidthPx
        if (delayMillis > 0) {
            val durationSeconds = delayMillis / 1000f
            return (0.3f + overflowRatio * (5f / durationSeconds)).coerceIn(0.3f, 5f)
        }
        return if (dynamicSpeed) 10f * overflowRatio + 0.7f else baseSpeed
    }
}
