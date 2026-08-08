package statusbar.lyric.runtime.style

import android.graphics.Color
import statusbar.lyric.config.Config

/** SystemUI Runtime 使用的已解析不可变外观配置快照。 */
data class RuntimeAppearanceSnapshot(
    val lyricSizePx: Int,
    val lyricStartMargin: Int,
    val lyricTopMargin: Int,
    val lyricEndMargin: Int,
    val lyricBottomMargin: Int,
    val lyricWidthPercent: Int,
    val fixedLyricWidth: Boolean,
    val dynamicLyricSpeed: Boolean,
    val lyricColor: Int?,
    val hasExplicitLyricColor: Boolean,
    val lyricGradientColors: List<Int>,
    val hasLyricGradient: Boolean,
    val lyricBackgroundColors: List<Int>,
    val lyricBackgroundRadius: Int,
    val lyricLetterSpacingOverride: Float?,
    val lyricStrokeWidth: Float,
    val lyricSpeed: Float,
    val lyricAnimation: Int,
    val lyricInterpolator: Int,
    val animationDurationMillis: Int,
    val iconEnabled: Boolean,
    val iconSizePx: Int,
    val iconStartMargin: Int,
    val iconTopMargin: Int,
    val iconBottomMargin: Int,
    val iconColor: Int?,
    val iconBackgroundColor: Int,
    val hyperTextureEnabled: Boolean,
    val hyperTextureRadius: Int,
    val hyperTextureCorner: Int,
    val hyperTextureBackgroundColor: Int
) {
    val usesDynamicLyricColor: Boolean
        get() = !hasExplicitLyricColor && !hasLyricGradient

    companion object {
        fun from(config: Config): RuntimeAppearanceSnapshot {
            val lyricColorValue = config.lyricColor
            val lyricGradientValue = config.lyricGradientColor
            return RuntimeAppearanceSnapshot(
                lyricSizePx = config.lyricSize,
                lyricStartMargin = config.lyricStartMargins,
                lyricTopMargin = config.lyricTopMargins,
                lyricEndMargin = config.lyricEndMargins,
                lyricBottomMargin = config.lyricBottomMargins,
                lyricWidthPercent = config.lyricWidth,
                fixedLyricWidth = config.fixedLyricWidth,
                dynamicLyricSpeed = config.dynamicLyricSpeed,
                lyricColor = parseColorOrNull(lyricColorValue),
                hasExplicitLyricColor = lyricColorValue.isNotBlank(),
                lyricGradientColors = parseColorList(lyricGradientValue),
                hasLyricGradient = lyricGradientValue.isNotBlank(),
                lyricBackgroundColors = parseColorList(config.lyricBackgroundColor),
                lyricBackgroundRadius = config.lyricBackgroundRadius,
                lyricLetterSpacingOverride = config.lyricLetterSpacing
                    .takeIf { it != 0 }
                    ?.div(100f),
                lyricStrokeWidth = config.lyricStrokeWidth / 100f,
                lyricSpeed = config.lyricSpeed.toFloat(),
                lyricAnimation = config.lyricAnimation,
                lyricInterpolator = config.lyricInterpolator,
                animationDurationMillis = config.animationDuration,
                iconEnabled = config.iconSwitch,
                iconSizePx = config.iconSize,
                iconStartMargin = config.iconStartMargins,
                iconTopMargin = config.iconTopMargins,
                iconBottomMargin = config.iconBottomMargins,
                iconColor = parseColorOrNull(config.iconColor),
                iconBackgroundColor = parseColorOrNull(config.iconBgColor) ?: Color.TRANSPARENT,
                hyperTextureEnabled = config.mHyperOSTexture,
                hyperTextureRadius = config.mHyperOSTextureRadio,
                hyperTextureCorner = config.mHyperOSTextureCorner,
                hyperTextureBackgroundColor = parseColorOrNull(config.mHyperOSTextureBgColor)
                    ?: Color.TRANSPARENT
            )
        }

        private fun parseColorOrNull(value: String): Int? {
            val normalized = value.trim()
            if (normalized.isEmpty()) return null
            return runCatching { Color.parseColor(normalized) }.getOrNull()
        }

        private fun parseColorList(value: String): List<Int> {
            val tokens = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return emptyList()
            return runCatching { tokens.map(Color::parseColor) }.getOrDefault(emptyList())
        }
    }
}
