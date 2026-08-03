package statusbar.lyric.config

import statusbar.lyric.config.Config.Companion.CONFIG_NAME
import statusbar.lyric.tools.LegacyPreferences

object XposedOwnSP {
    val config: Config by lazy { Config(LegacyPreferences.get(CONFIG_NAME)) }
}
