package statusbar.lyric.config

import android.content.SharedPreferences

/**
 * Read-only adapter for the Remote Preferences instance supplied by API 101.
 */
class Api101RemotePreferencesStore(
    private val preferences: SharedPreferences
) : ConfigStore {
    override val isReadOnly: Boolean = true

    override fun reload() {
        // Remote Preferences are read directly from the framework-backed instance.
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> opt(key: String, defValue: T): T {
        return when (defValue) {
            is String -> preferences.getString(key, defValue) as T
            is Int -> preferences.getInt(key, defValue) as T
            is Boolean -> preferences.getBoolean(key, defValue) as T
            is Double -> preferences.getFloat(key, defValue.toFloat()) as T
            is Float -> preferences.getFloat(key, defValue) as T
            else -> "" as T
        }
    }

    override fun contains(key: String): Boolean {
        return preferences.contains(key)
    }

    override fun snapshot(): Map<String, Any?> {
        return preferences.all.mapValues { it.value }
    }

    override fun put(key: String?, any: Any) {
        throw UnsupportedOperationException(
            "API 101 Remote Preferences are read-only until a framework-backed write port is available"
        )
    }

    override fun clearConfig() {
        throw UnsupportedOperationException(
            "API 101 Remote Preferences are read-only until a framework-backed write port is available"
        )
    }
}
