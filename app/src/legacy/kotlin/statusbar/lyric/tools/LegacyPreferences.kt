package statusbar.lyric.tools

import de.robv.android.xposed.XSharedPreferences
import statusbar.lyric.BuildConfig
import statusbar.lyric.tools.LogTools.log

object LegacyPreferences {
    fun get(key: String): XSharedPreferences? {
        return try {
            XSharedPreferences(BuildConfig.APPLICATION_ID, key).takeIf { it.file.canRead() }
        } catch (e: Throwable) {
            e.log()
            null
        }
    }
}
