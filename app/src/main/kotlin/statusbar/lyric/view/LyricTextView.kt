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

import android.content.Context
import android.graphics.Paint
import android.graphics.Shader
import android.view.animation.LinearInterpolator
import android.widget.TextView
import statusbar.lyric.config.XposedOwnSP.config
import kotlin.math.ceil

class LyricTextView(context: Context) : TextView(context) {
    private var isScrolling = false
    private var textLength = 0f
    private var viewportWidth = 0f
    private var scrollSpeed = 4f

    private val startScrollRunnable = Runnable {
        if (!shouldScroll()) {
            isScrolling = false
            translationX = 0f
            return@Runnable
        }
        val distance = textLength - viewportWidth
        val pixelsPerSecond = scrollSpeed.coerceAtLeast(MIN_SCROLL_SPEED) * BASE_FRAMES_PER_SECOND
        val durationMillis = (distance / pixelsPerSecond * 1000f).toLong().coerceAtLeast(1L)
        isScrolling = true
        animate()
            .translationX(-distance)
            .setDuration(durationMillis)
            .setInterpolator(LinearInterpolator())
            .withEndAction { isScrolling = false }
            .start()
    }

    private companion object {
        const val BASE_FRAMES_PER_SECOND = 60f
        const val MIN_SCROLL_SPEED = 0.01f
    }

    init {
        paint.style = Paint.Style.FILL_AND_STROKE
    }

    override fun onDetachedFromWindow() {
        stopScrollNow()
        super.onDetachedFromWindow()
    }

    override fun setText(text: CharSequence, type: BufferType) {
        stopScrollNow()
        translationX = 0f
        super.setText(text, type)
        textLength = paint.measureText(text.toString())
        val params = layoutParams
        if (params != null) {
            val desiredWidth = ceil(textLength + compoundPaddingLeft + compoundPaddingRight).toInt()
            if (params.width != desiredWidth) {
                params.width = desiredWidth
                layoutParams = params
            }
        }
        startScrollIfNeeded()
    }

    override fun setTextColor(color: Int) {
        if (currentTextColor == color && paint.color == color) return
        super.setTextColor(color)
        invalidate()
    }

    fun setLinearGradient(shader: Shader?) {
        if (paint.shader === shader) return
        paint.shader = shader
        invalidate()
    }

    fun setStrokeWidth(width: Float) {
        if (paint.strokeWidth == width) return
        paint.strokeWidth = width
        paint.style = if (width > 0f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
        invalidate()
    }

    fun setViewportWidth(width: Int) {
        val normalizedWidth = width.coerceAtLeast(0).toFloat()
        if (viewportWidth == normalizedWidth) return
        viewportWidth = normalizedWidth
        restartScrollIfNeeded()
    }

    private fun startScrollIfNeeded() {
        if (!shouldScroll()) return
        postDelayed(
            startScrollRunnable,
            config.animationDuration + if (config.dynamicLyricSpeed) 200L else 500L
        )
    }

    private fun restartScrollIfNeeded() {
        stopScrollNow()
        translationX = 0f
        startScrollIfNeeded()
    }

    private fun shouldScroll(): Boolean {
        return viewportWidth > 0f && textLength > viewportWidth
    }

    fun stopScrollNow() {
        isScrolling = false
        removeCallbacks(startScrollRunnable)
        animate().cancel()
    }

    fun setScrollSpeed(speed: Float) {
        if (scrollSpeed == speed) return
        scrollSpeed = speed
    }
}
