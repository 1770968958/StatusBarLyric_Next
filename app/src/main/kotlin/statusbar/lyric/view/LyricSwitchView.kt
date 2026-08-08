/*
 * StatusBarLyric
 * Copyright (C) 2021-2022 fkj@fkj233.cn
 * https://github.com/Block-Network/StatusBarLyric
 *
 * This software is free opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as
 * published by Block-Network contributors.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/Block-Network/StatusBarLyric/blob/main/LICENSE>.
 */

package statusbar.lyric.view

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.widget.TextSwitcher

open class LyricSwitchView(context: Context) : TextSwitcher(context) {
    private var appliedWidth = Int.MIN_VALUE

    init {
        initialize()
    }

    private fun initialize() {
        clipChildren = true
        clipToPadding = true
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
        }
        setFactory {
            LyricTextView(context).apply {
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            }
        }
    }

    fun applyToAllViews(action: (LyricTextView) -> Unit) {
        for (i in 0 until childCount) {
            action(getChildAt(i) as LyricTextView)
        }
    }

    fun setWidth(width: Int) {
        if (appliedWidth == width) return
        appliedWidth = width
        layoutParams?.let { params ->
            if (params.width != width) {
                params.width = width
                layoutParams = params
            }
        }
        applyToAllViews { it.setViewportWidth(width) }
    }

    fun setTextColor(color: Int) {
        applyToAllViews { it.setTextColor(color) }
    }

    fun setLinearGradient(shader: Shader?) {
        applyToAllViews { it.setLinearGradient(shader) }
    }

    override fun setBackground(background: Drawable?) {
        if (this.background === background) return
        super.setBackground(background)
    }

    fun setScrollSpeed(speed: Float) {
        applyToAllViews { it.setScrollSpeed(speed) }
    }

    fun measureText(text: String): Float {
        val textView = currentView as? LyricTextView
            ?: (if (childCount > 0) getChildAt(0) as? LyricTextView else null)
        return textView?.paint?.measureText(text) ?: 0f
    }

    fun stopAllScroll() {
        applyToAllViews { it.stopScrollNow() }
    }

    fun setLetterSpacings(letterSpacing: Float) {
        applyToAllViews { it.letterSpacing = letterSpacing }
    }

    fun setStrokeWidth(width: Float) {
        applyToAllViews { it.setStrokeWidth(width) }
    }

    fun setTypeface(typeface: Typeface) {
        applyToAllViews { it.typeface = typeface }
    }

    fun setTextSize(unit: Int, size: Float) {
        applyToAllViews { it.setTextSize(unit, size) }
    }

    fun setMargins(start: Int, top: Int, end: Int, bottom: Int) {
        applyToAllViews {
            val layoutParams = it.layoutParams as MarginLayoutParams
            if (layoutParams.leftMargin == start &&
                layoutParams.topMargin == top &&
                layoutParams.rightMargin == end &&
                layoutParams.bottomMargin == bottom
            ) {
                return@applyToAllViews
            }
            layoutParams.setMargins(start, top, end, bottom)
            it.layoutParams = layoutParams
        }
    }

    fun setSingleLine(singleLine: Boolean) {
        applyToAllViews { it.isSingleLine = singleLine }
    }

    fun setMaxLines(maxLines: Int) {
        applyToAllViews { it.maxLines = maxLines }
    }
}
