package statusbar.lyric.runtime

/** legacy SystemUI 集成使用的跨进程内部控制广播。 */
object InternalBroadcasts {
    const val ACTION_UPDATE_CONFIG = "statusbar.lyric.action.UPDATE_CONFIG"
    const val PERMISSION_INTERNAL_CONTROL = "statusbar.lyric.permission.INTERNAL_CONTROL"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
}
