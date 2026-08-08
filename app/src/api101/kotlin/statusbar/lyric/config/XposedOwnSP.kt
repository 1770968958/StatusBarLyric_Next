/*
 * StatusBarLyric
 * Copyright (C) 2021-2022 fkj@fkj233.cn
 * https://github.com/Block-Network/StatusBarLyric
 *
 * This software is free opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as
 * published by Block-Network contributors.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/Block-Network/StatusBarLyric/blob/main/LICENSE>.
 */

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
    private var remotePreferences: SharedPreferences? = null
    private val preferenceListeners = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

    val config: Config
        get() = configHolder

    val isRemotePreferencesAttached: Boolean
        get() = remoteStore != null

    val isRemotePreferencesReadOnly: Boolean
        get() = remoteStore != null

    fun attachRemotePreferences(preferences: SharedPreferences) {
        remotePreferences?.let { previous ->
            preferenceListeners.forEach(previous::unregisterOnSharedPreferenceChangeListener)
        }
        val store = Api101RemotePreferencesStore(preferences)
        remotePreferences = preferences
        remoteStore = store
        configHolder.attachStore(store)
        preferenceListeners.forEach(preferences::registerOnSharedPreferenceChangeListener)
    }

    /**
     * Registers a SystemUI-side listener after the framework provides Remote Preferences.
     */
    fun registerOnPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferenceListeners += listener
        remotePreferences?.registerOnSharedPreferenceChangeListener(listener)
    }

    const val remoteGroup: String = CONFIG_NAME
}
