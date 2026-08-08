package statusbar.lyric.runtime

import android.view.View
import java.util.IdentityHashMap

/**
 * Tracks temporary visibility changes made while lyrics are showing.
 *
 * The original visibility is captured when a view is first overridden. If
 * SystemUI requests a different visibility while the override is active, the
 * latest request is remembered and restored when the override ends. Internal
 * writes are marked so the global setVisibility hooks do not mistake module
 * updates for SystemUI requests.
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
     * Records a visibility request made outside this state holder.
     *
     * @return a replacement visibility when the active hidden override should
     * remain enforced, or null when the caller should let the request through.
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
