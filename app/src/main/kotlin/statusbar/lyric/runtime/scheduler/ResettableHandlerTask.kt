package statusbar.lyric.runtime.scheduler

import android.os.Handler

/** 可复用的单一 Handler 任务，支持重置等待时间或取消调度。 */
class ResettableHandlerTask(
    private val handler: Handler,
    action: () -> Unit
) {
    private val runnable = Runnable(action)

    fun schedule(delayMillis: Long) {
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, delayMillis.coerceAtLeast(0L))
    }

    fun cancel() {
        handler.removeCallbacks(runnable)
    }
}
