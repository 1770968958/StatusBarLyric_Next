package statusbar.lyric.runtime.style

import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import statusbar.lyric.view.LyricSwitchView
import java.io.File

class LyricAppearanceApplier(
    private val typefaceFileCache: TypefaceFileCache = TypefaceFileCache()
) {
    fun applyText(
        target: LyricSwitchView,
        appearance: RuntimeAppearanceSnapshot,
        sourceTextSizePx: Float,
        sourceTextColor: Int,
        sourceLetterSpacing: Float,
        fallbackTypeface: Typeface,
        fontFile: File,
        dynamicTextColor: Int = sourceTextColor
    ) {
        target.setSingleLine(true)
        target.setMaxLines(1)
        val textSize = appearance.lyricSizePx.takeIf { it > 0 }?.toFloat() ?: sourceTextSizePx
        if (textSize > 0f) {
            target.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        }

        target.setLinearGradient(null)
        target.setTextColor(appearance.lyricColor ?: dynamicTextColor)
        target.setLetterSpacings(appearance.lyricLetterSpacingOverride ?: sourceLetterSpacing)
        target.setStrokeWidth(appearance.lyricStrokeWidth)
        applyBackground(target, appearance.lyricBackgroundColors, appearance.lyricBackgroundRadius)
        applyGradient(target, appearance)
        target.setTypeface(typefaceFileCache.resolve(fontFile, fallbackTypeface))
    }

    fun applyMargins(target: View, appearance: RuntimeAppearanceSnapshot) {
        val params = target.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (
            params.leftMargin == appearance.lyricStartMargin &&
            params.topMargin == appearance.lyricTopMargin &&
            params.rightMargin == appearance.lyricEndMargin &&
            params.bottomMargin == appearance.lyricBottomMargin
        ) {
            return
        }
        params.setMargins(
            appearance.lyricStartMargin,
            appearance.lyricTopMargin,
            appearance.lyricEndMargin,
            appearance.lyricBottomMargin
        )
        target.layoutParams = params
    }

    fun applyIcon(
        target: ImageView,
        appearance: RuntimeAppearanceSnapshot,
        sourceHeightPx: Int,
        sourceTextColor: Int
    ) {
        val size = appearance.iconSizePx.takeIf { it > 0 } ?: sourceHeightPx / 2
        val current = target.layoutParams as? LinearLayout.LayoutParams
        val params = current ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        val layoutChanged = params.width != size ||
            params.height != size ||
            params.leftMargin != appearance.iconStartMargin ||
            params.topMargin != appearance.iconTopMargin ||
            params.rightMargin != 0 ||
            params.bottomMargin != appearance.iconBottomMargin
        if (layoutChanged) {
            params.width = size
            params.height = size
            params.setMargins(
                appearance.iconStartMargin,
                appearance.iconTopMargin,
                0,
                appearance.iconBottomMargin
            )
            target.layoutParams = params
        }
        target.setColorFilter(appearance.iconColor ?: sourceTextColor, PorterDuff.Mode.SRC_IN)
        target.setBackgroundColor(appearance.iconBackgroundColor)
    }

    private fun applyBackground(target: LyricSwitchView, colors: List<Int>, radius: Int) {
        target.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (colors.isEmpty()) return
        target.background = if (colors.size == 1) {
            GradientDrawable().apply {
                setColor(colors[0])
                if (radius > 0) cornerRadius = radius.toFloat()
            }
        } else {
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors.toIntArray()).apply {
                if (radius > 0) cornerRadius = radius.toFloat()
            }
        }
    }

    fun applyGradient(target: LyricSwitchView, appearance: RuntimeAppearanceSnapshot) {
        val colors = appearance.lyricGradientColors
        if (colors.size < 2 || target.width <= 0) {
            if (appearance.hasLyricGradient && colors.size == 1) {
                target.setTextColor(colors[0])
            }
            return
        }
        target.setLinearGradient(
            LinearGradient(
                0f,
                0f,
                target.width.toFloat(),
                0f,
                colors.toIntArray(),
                null,
                Shader.TileMode.CLAMP
            )
        )
    }
}
