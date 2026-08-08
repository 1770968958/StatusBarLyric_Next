package statusbar.lyric.runtime

object LyricIconResolver {
    fun resolve(
        enabled: Boolean,
        overrideIcon: String,
        eventIcon: String?,
        defaultIcon: () -> String
    ): String {
        if (!enabled) return ""
        if (overrideIcon.isNotEmpty()) return overrideIcon
        val suppliedIcon = eventIcon.orEmpty()
        if (suppliedIcon.isNotEmpty()) return suppliedIcon
        return defaultIcon()
    }
}
