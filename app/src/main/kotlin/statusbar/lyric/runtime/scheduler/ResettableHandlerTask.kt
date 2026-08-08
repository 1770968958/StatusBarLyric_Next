package statusbar.lyric.runtime.scheduler

import android.os.Handler

/** A single reusable Handler task whose pending schedule can be reset or cancelled. */
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
