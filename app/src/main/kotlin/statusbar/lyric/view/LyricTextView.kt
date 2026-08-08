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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.view.Choreographer
import android.widget.TextView
import statusbar.lyric.config.XposedOwnSP.config

class LyricTextView(context: Context) : TextView(context), Choreographer.FrameCallback {
    private var isScrolling = false
    private var textLength = 0f
    private var viewWidth = 0f
    private var scrollSpeed = 4f
    private var currentX = 0f
    private var lastFrameTimeNanos = 0L
    private var drawText = ""

    private val choreographer: Choreographer
        get() = Choreographer.getInstance()

    private val startScrollRunnable = Runnable {
        if (!shouldScroll()) {
            isScrolling = false
            currentX = 0f
            invalidate()
            return@Runnable
        }
        isScrolling = true
        lastFrameTimeNanos = 0L
        choreographer.postFrameCallback(this)
    }

    private companion object {
        const val NANOS_PER_60HZ_FRAME = 16_666_667L
        const val MAX_FRAME_DELTA = 4f
        const val SCROLL_FRAME_DELAY_MILLIS = 33L
    }

    init {
        paint.style = Paint.Style.FILL_AND_STROKE
    }

    override fun onDetachedFromWindow() {
        stopScrollNow()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        if (drawText.isNotEmpty()) {
            restartScrollIfNeeded()
        }
    }

    override fun setText(text: CharSequence, type: BufferType) {
        stopScrollNow()
        currentX = 0f
        lastFrameTimeNanos = 0L
        drawText = text.toString()
        textLength = paint.measureText(drawText)
        super.setText(text, type)
        startScrollIfNeeded()
    }

    override fun setTextColor(color: Int) {
        if (currentTextColor == color && paint.color == color) return
        super.setTextColor(color)
        paint.color = color
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

    override fun onDraw(canvas: Canvas) {
        if (drawText.isEmpty()) return
        val contentHeight = height - paddingTop - paddingBottom
        val baseline = paddingTop +
            (contentHeight - paint.descent() - paint.ascent()) / 2f
        canvas.drawText(drawText, currentX, baseline, paint)
    }

    private fun updateScrollPosition(frameTimeNanos: Long) {
        if (!shouldScroll()) {
            currentX = 0f
            stopScrollNow()
            return
        }

        if (viewWidth - currentX >= textLength) {
            currentX = viewWidth - textLength
            stopScrollNow()
            return
        }

        val frameDelta = if (lastFrameTimeNanos == 0L) {
            1f
        } else {
            ((frameTimeNanos - lastFrameTimeNanos)
                .coerceAtLeast(0L)
                .toFloat() / NANOS_PER_60HZ_FRAME)
                .coerceIn(0f, MAX_FRAME_DELTA)
        }
        lastFrameTimeNanos = frameTimeNanos
        currentX -= scrollSpeed * frameDelta
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isScrolling) return
        updateScrollPosition(frameTimeNanos)
        invalidate()
        if (isScrolling) {
            choreographer.postFrameCallbackDelayed(this, SCROLL_FRAME_DELAY_MILLIS)
        }
    }

    private fun startScrollIfNeeded() {
        if (textLength <= 0f) return
        if (viewWidth > 0f && !shouldScroll()) return
        postDelayed(
            startScrollRunnable,
            config.animationDuration + if (config.dynamicLyricSpeed) 200L else 500L
        )
    }

    private fun restartScrollIfNeeded() {
        stopScrollNow()
        currentX = 0f
        lastFrameTimeNanos = 0L
        startScrollIfNeeded()
    }

    private fun shouldScroll(): Boolean {
        return viewWidth > 0f && textLength > viewWidth
    }

    fun stopScrollNow() {
        isScrolling = false
        lastFrameTimeNanos = 0L
        removeCallbacks(startScrollRunnable)
        choreographer.removeFrameCallback(this)
    }

    fun setScrollSpeed(speed: Float) {
        if (scrollSpeed == speed) return
        scrollSpeed = speed
    }
}
