package statusbar.lyric.runtime

/**
 * SystemUI 主线程内使用的歌词播放状态。
 *
 * Hook 层只负责把事件交给这里，避免 API82/API101 各自维护一组容易漏清理的字段。
 */
class LyricRuntimeState {
    var isPlaying: Boolean = false
        private set

    var publisher: String = ""
        private set

    var lyric: String = ""
        private set

    var delayMillis: Int = 0
        private set

    var track: TrackIdentity? = null
        private set

    var eventIdentity: LyricEventIdentity? = null
        private set

    fun isSameEvent(identity: LyricEventIdentity): Boolean =
        isPlaying && eventIdentity == identity

    fun isTrackChanged(identity: TrackIdentity): Boolean = track != identity

    fun accept(
        publisher: String,
        lyric: String,
        delayMillis: Int,
        track: TrackIdentity,
        eventIdentity: LyricEventIdentity
    ) {
        isPlaying = true
        this.publisher = publisher
        this.lyric = lyric
        this.delayMillis = delayMillis
        this.track = track
        this.eventIdentity = eventIdentity
    }

    fun clearVisibleLyric() {
        lyric = ""
        delayMillis = 0
        eventIdentity = null
    }

    fun stopIfPublisherMatches(publisher: String): Boolean {
        if (this.publisher.isNotEmpty() && this.publisher != publisher) return false
        reset()
        return true
    }

    fun reset() {
        isPlaying = false
        publisher = ""
        lyric = ""
        delayMillis = 0
        track = null
        eventIdentity = null
    }
}
