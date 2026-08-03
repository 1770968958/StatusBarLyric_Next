package statusbar.lyric.config

/**
 * Pending write boundary for a future local-to-remote configuration bridge.
 *
 * The local COMPOSE_CONFIG preferences remain authoritative. An implementation
 * must copy only missing remote keys, preserve both stores, and mirror a local
 * change only after the local write succeeds.
 */
interface Api101ConfigSyncPort {
    fun copyMissingValues(localValues: Map<String, Any?>): Api101ConfigSyncResult

    fun mirrorValue(key: String, value: Any): Api101ConfigSyncResult
}

enum class Api101ConfigSyncResult {
    WRITTEN,
    REMOTE_NOT_AVAILABLE,
    REMOTE_READ_ONLY
}

object Api101ConfigSyncPolicy {
    const val LOCAL_CONFIG_NAME = Config.CONFIG_NAME
    const val REMOTE_GROUP = Config.CONFIG_NAME
    const val COPY_ONLY_MISSING_KEYS = true
    const val PRESERVE_LOCAL_VALUES = true
    const val PRESERVE_REMOTE_VALUES = true
}
