package statusbar.lyric.config

import android.content.SharedPreferences
import statusbar.lyric.config.Config.Companion.CONFIG_NAME

/**
 * Minimal API 101 configuration bridge. The module entry attaches Remote Preferences
 * after the modern framework calls onModuleLoaded.
 */
object XposedOwnSP {
    private val configHolder = Config(null)
    private var remoteStore: Api101RemotePreferencesStore? = null

    val config: Config
        get() = configHolder

    val isRemotePreferencesAttached: Boolean
        get() = remoteStore != null

    val isRemotePreferencesReadOnly: Boolean
        get() = remoteStore?.isReadOnly == true

    fun attachRemotePreferences(preferences: SharedPreferences) {
        val store = Api101RemotePreferencesStore(preferences)
        remoteStore = store
        configHolder.attachStore(store)
    }

    const val remoteGroup: String = CONFIG_NAME
}
