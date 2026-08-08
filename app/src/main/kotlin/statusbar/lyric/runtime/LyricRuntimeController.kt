package statusbar.lyric.runtime

data class LyricRuntimeEvent(
    val publisher: String,
    val lyric: String,
    val delayMillis: Int,
    val track: TrackIdentity,
    val iconSource: String
)

data class LyricRuntimePolicy(
    val includeTrackInIdentity: Boolean,
    val includeIconInIdentity: Boolean
)

sealed class LyricRuntimeResult {
    data object Disabled : LyricRuntimeResult()
    data object Duplicate : LyricRuntimeResult()
    data class Accepted(
        val event: LyricRuntimeEvent,
        val trackChanged: Boolean,
        val iconChanged: Boolean
    ) : LyricRuntimeResult()
}

class LyricRuntimeController(
    enabled: Boolean
) {
    val state = LyricRuntimeState()

    var enabled: Boolean = enabled
        private set

    fun setEnabled(enabled: Boolean): Boolean {
        if (this.enabled == enabled) return false
        this.enabled = enabled
        state.reset()
        return true
    }

    fun onLyric(event: LyricRuntimeEvent, policy: LyricRuntimePolicy): LyricRuntimeResult {
        if (!enabled || event.lyric.isEmpty()) return LyricRuntimeResult.Disabled
        val identity = LyricEventIdentity.create(
            publisher = event.publisher,
            lyric = event.lyric,
            delayMillis = event.delayMillis,
            track = event.track,
            iconSource = event.iconSource,
            includeTrack = policy.includeTrackInIdentity,
            includeIcon = policy.includeIconInIdentity
        )
        if (state.isSameEvent(identity)) return LyricRuntimeResult.Duplicate

        val trackChanged = state.isTrackChanged(event.track)
        val iconChanged = state.iconSource != event.iconSource
        state.accept(
            publisher = event.publisher,
            lyric = event.lyric,
            delayMillis = event.delayMillis,
            track = event.track,
            eventIdentity = identity,
            iconSource = event.iconSource
        )
        return LyricRuntimeResult.Accepted(event, trackChanged, iconChanged)
    }

    fun onStop(publisher: String): Boolean {
        if (!enabled) return false
        return state.stopIfPublisherMatches(publisher)
    }

    fun onTimeout(): Boolean {
        if (!enabled || !state.isPlaying) return false
        state.clearVisibleLyric()
        return true
    }

    fun reset() {
        state.reset()
    }
}
