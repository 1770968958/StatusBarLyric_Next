package statusbar.lyric.runtime

/** Cross-process control broadcasts used by the legacy SystemUI integration. */
object InternalBroadcasts {
    const val ACTION_UPDATE_CONFIG = "statusbar.lyric.action.UPDATE_CONFIG"
    const val PERMISSION_INTERNAL_CONTROL = "statusbar.lyric.permission.INTERNAL_CONTROL"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
}
