package statusbar.lyric.config

import android.content.SharedPreferences
import statusbar.lyric.config.Config.Companion.CONFIG_NAME

/**
 * Minimal API 101 configuration bridge. The module entry attaches Remote Preferences
 * after the modern framework calls onModuleLoaded.
 */
object XposedOwnSP {
    private val configHolder = Config(null)

    val config: Config
        get() = configHolder

    fun attachRemotePreferences(preferences: SharedPreferences) {
        configHolder.attach(preferences)
    }

    const val remoteGroup: String = CONFIG_NAME
}
