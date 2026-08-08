package statusbar.lyric.runtime

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.IdentityHashMap
import kotlin.math.abs

/** Immutable description of the TextView anchor recorded by SystemUI test mode. */
data class TargetViewSpec(
    val textViewClassName: String,
    val textViewId: Int,
    val parentViewClassName: String,
    val parentViewId: Int,
    val expectedTextSizePx: Float,
    val targetIndex: Int
) {
    val isComplete: Boolean
        get() = textViewClassName.isNotEmpty() &&
            textViewId != 0 &&
            parentViewClassName.isNotEmpty() &&
            parentViewId != 0 &&
            targetIndex >= 0
}

data class TargetViewMatch(
    val parent: ViewGroup,
    val index: Int
)

/**
 * Matches the configured SystemUI TextView without relying on one process-global index.
 *
 * Candidate indices are tracked independently for each concrete parent ViewGroup. When a
 * candidate detaches its slot is removed and later indices are compacted, so a replacement
 * View can reuse the same logical index after a SystemUI reinflate.
 */
class TargetViewMatcher(
    private val textSizeEpsilonPx: Float = DEFAULT_TEXT_SIZE_EPSILON_PX
) {
    private val parentStates = IdentityHashMap<ViewGroup, ParentMatchState>()
    private val targetParents = IdentityHashMap<View, ViewGroup>()
    private var activeSpec: TargetViewSpec? = null

    @Synchronized
    fun match(view: View, spec: TargetViewSpec): TargetViewMatch? {
        if (!spec.isComplete || view !is TextView) return null
        ensureSpec(spec)

        if (view.javaClass.name != spec.textViewClassName || view.id != spec.textViewId) {
            return null
        }
        if (spec.expectedTextSizePx > 0f &&
            abs(view.textSize - spec.expectedTextSizePx) > textSizeEpsilonPx
        ) {
            return null
        }

        val parent = view.parent as? ViewGroup ?: return null
        if (parent.javaClass.name != spec.parentViewClassName || parent.id != spec.parentViewId) {
            return null
        }

        val previousParent = targetParents[view]
        if (previousParent != null && previousParent !== parent) {
            removeCandidate(view, previousParent)
        }

        val state = parentStates.getOrPut(parent) { ParentMatchState() }
        val index = state.matchedIndices[view] ?: state.nextIndex.also { assignedIndex ->
            state.matchedIndices[view] = assignedIndex
            state.nextIndex += 1
            targetParents[view] = parent
        }

        return if (index == spec.targetIndex) {
            TargetViewMatch(parent, index)
        } else {
            null
        }
    }

    @Synchronized
    fun forget(view: View, fallbackParent: ViewGroup? = null): ViewGroup? {
        val parent = targetParents.remove(view) ?: fallbackParent
        if (parent != null) {
            removeCandidate(view, parent, removeParentReference = false)
        }
        return parent
    }

    @Synchronized
    fun reset() {
        parentStates.clear()
        targetParents.clear()
        activeSpec = null
    }

    private fun ensureSpec(spec: TargetViewSpec) {
        if (activeSpec == spec) return
        parentStates.clear()
        targetParents.clear()
        activeSpec = spec
    }

    private fun removeCandidate(
        view: View,
        parent: ViewGroup,
        removeParentReference: Boolean = true
    ) {
        if (removeParentReference) {
            targetParents.remove(view)
        }
        val state = parentStates[parent] ?: return
        val removedIndex = state.matchedIndices.remove(view) ?: return

        state.matchedIndices.entries.forEach { entry ->
            if (entry.value > removedIndex) {
                entry.setValue(entry.value - 1)
            }
        }
        state.nextIndex = state.matchedIndices.size
        if (state.matchedIndices.isEmpty()) {
            parentStates.remove(parent)
        }
    }

    private class ParentMatchState(
        var nextIndex: Int = 0,
        val matchedIndices: IdentityHashMap<View, Int> = IdentityHashMap()
    )

    private companion object {
        const val DEFAULT_TEXT_SIZE_EPSILON_PX = 0.5f
    }
}
