package statusbar.lyric.runtime

data class TrackIdentity(
    val title: String,
    val artist: String,
    val album: String
)

data class LyricEventIdentity(
    val publisher: String,
    val lyric: String,
    val delayMillis: Int,
    val track: TrackIdentity?,
    val iconSource: String?
) {
    companion object {
        fun create(
            publisher: String,
            lyric: String,
            delayMillis: Int,
            track: TrackIdentity,
            iconSource: String,
            includeTrack: Boolean,
            includeIcon: Boolean
        ): LyricEventIdentity = LyricEventIdentity(
            publisher = publisher,
            lyric = lyric,
            delayMillis = delayMillis,
            track = track.takeIf { includeTrack },
            iconSource = iconSource.takeIf { includeIcon }
        )
    }
}
