package statusbar.lyric.runtime

import android.view.View
import java.util.IdentityHashMap

/**
 * 记录歌词显示期间由模块施加的临时可见性覆盖。
 *
 * 首次覆盖 View 时保存原始可见性；覆盖期间如果 SystemUI 请求新的可见性，则记录最新请求，
 * 在覆盖结束时恢复。模块自身写入会单独标记，避免全局 setVisibility Hook 将其误认为 SystemUI 请求。
 */
class ViewVisibilityOverrideState {
    private data class Entry(
        val originalVisibility: Int,
        var requestedVisibility: Int,
        var overrideVisibility: Int
    )

    private val entries = IdentityHashMap<View, Entry>()
    private val internalWrites = IdentityHashMap<View, Int>()

    @Synchronized
    fun apply(view: View?, visibility: Int) {
        if (view == null) return
        val entry = entries.getOrPut(view) {
            Entry(
                originalVisibility = view.visibility,
                requestedVisibility = view.visibility,
                overrideVisibility = visibility
            )
        }
        entry.overrideVisibility = visibility
        setVisibilityInternal(view, visibility)
    }

    /**
     * 记录状态管理器之外发起的可见性请求。
     *
     * @return 当前隐藏覆盖仍需保持时返回替代可见性；可以放行原请求时返回 null。
     */
    @Synchronized
    fun onVisibilityRequested(
        view: View?,
        requestedVisibility: Int,
        keepHiddenOverride: Boolean
    ): Int? {
        if (view == null || isInternalWrite(view)) return null
        val entry = entries[view] ?: return null
        entry.requestedVisibility = requestedVisibility
        return if (
            keepHiddenOverride &&
            entry.overrideVisibility == View.GONE &&
            requestedVisibility == View.VISIBLE
        ) {
            View.GONE
        } else {
            null
        }
    }

    @Synchronized
    fun restore(view: View?) {
        if (view == null) return
        val entry = entries.remove(view) ?: return
        setVisibilityInternal(view, entry.requestedVisibility)
    }

    @Synchronized
    fun restoreAll() {
        if (entries.isEmpty()) return
        val snapshot = entries.entries.map { it.key to it.value.requestedVisibility }
        entries.clear()
        snapshot.forEach { (view, visibility) ->
            setVisibilityInternal(view, visibility)
        }
    }

    @Synchronized
    fun forget(view: View?) {
        if (view == null) return
        entries.remove(view)
        internalWrites.remove(view)
    }

    @Synchronized
    fun isOverridden(view: View?): Boolean = view != null && entries.containsKey(view)

    private fun isInternalWrite(view: View): Boolean = (internalWrites[view] ?: 0) > 0

    private fun setVisibilityInternal(view: View, visibility: Int) {
        if (view.visibility == visibility) return
        internalWrites[view] = (internalWrites[view] ?: 0) + 1
        try {
            view.visibility = visibility
        } finally {
            val remaining = (internalWrites[view] ?: 1) - 1
            if (remaining <= 0) {
                internalWrites.remove(view)
            } else {
                internalWrites[view] = remaining
            }
        }
    }
}
