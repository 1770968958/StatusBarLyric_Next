package statusbar.lyric.config

/**
 * Storage boundary shared by the activity and runtime-specific hook sources.
 */
interface ConfigStore {
    val isReadOnly: Boolean

    fun reload()

    fun <T> opt(key: String, defValue: T): T

    fun contains(key: String): Boolean

    fun snapshot(): Map<String, Any?>

    fun put(key: String?, any: Any)

    fun clearConfig()
}
