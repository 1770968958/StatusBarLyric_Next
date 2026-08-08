package statusbar.lyric.runtime

data class SystemUiVisibilityPolicy(
    val hideClock: Boolean,
    val hideNotificationIcons: Boolean,
    val hidePadClock: Boolean,
    val hideNotificationBigTime: Boolean,
    val hideNetworkSpeed: Boolean,
    val hideCarrier: Boolean
) {
    companion object {
        fun create(
            hideTime: Boolean,
            hideNotificationIcons: Boolean,
            optimizePadClock: Boolean,
            hideNetworkSpeed: Boolean,
            hideCarrier: Boolean
        ): SystemUiVisibilityPolicy = SystemUiVisibilityPolicy(
            hideClock = hideTime,
            hideNotificationIcons = hideNotificationIcons,
            hidePadClock = hideTime && optimizePadClock,
            hideNotificationBigTime = hideTime,
            hideNetworkSpeed = hideNetworkSpeed,
            hideCarrier = hideCarrier
        )
    }
}
