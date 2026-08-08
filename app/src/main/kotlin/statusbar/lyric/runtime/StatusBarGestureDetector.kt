package statusbar.lyric.runtime

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

sealed interface StatusBarGesture {
    data object None : StatusBarGesture
    data object Tap : StatusBarGesture
    data object LongPress : StatusBarGesture
    data object SwipeNext : StatusBarGesture
    data object SwipePrevious : StatusBarGesture
}

class StatusBarGestureDetector(
    private val moveThresholdPx: Float = DEFAULT_MOVE_THRESHOLD_PX,
    private val longPressMillis: Long = DEFAULT_LONG_PRESS_MILLIS
) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var tracking = false

    fun onTouchEvent(
        event: MotionEvent,
        swipeXThresholdPx: Float,
        swipeYRadiusPx: Float
    ): StatusBarGesture {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                tracking = true
                StatusBarGesture.None
            }

            MotionEvent.ACTION_CANCEL -> {
                reset()
                StatusBarGesture.None
            }

            MotionEvent.ACTION_UP -> finish(event, swipeXThresholdPx, swipeYRadiusPx)
            else -> StatusBarGesture.None
        }
    }

    fun reset() {
        tracking = false
    }

    private fun finish(
        event: MotionEvent,
        swipeXThresholdPx: Float,
        swipeYRadiusPx: Float
    ): StatusBarGesture {
        if (!tracking) return StatusBarGesture.None
        tracking = false

        val horizontal = downX - event.rawX
        val vertical = abs(downY - event.rawY)
        val moved = abs(horizontal) > moveThresholdPx || vertical > moveThresholdPx

        if (moved) {
            if (vertical > swipeYRadiusPx || abs(horizontal) <= swipeXThresholdPx) {
                return StatusBarGesture.None
            }
            return if (horizontal > 0f) StatusBarGesture.SwipeNext else StatusBarGesture.SwipePrevious
        }

        val duration = (event.eventTime - downTime).coerceAtLeast(0L)
        return if (duration > longPressMillis) StatusBarGesture.LongPress else StatusBarGesture.Tap
    }

    companion object {
        const val DEFAULT_MOVE_THRESHOLD_PX = 50f
        const val DEFAULT_LONG_PRESS_MILLIS = 500L

        fun containsRawPoint(view: View?, rawX: Float, rawY: Float): Boolean {
            val target = view ?: return false
            val rect = Rect()
            if (!target.getGlobalVisibleRect(rect)) return false
            return rect.contains(rawX.toInt(), rawY.toInt())
        }
    }
}
