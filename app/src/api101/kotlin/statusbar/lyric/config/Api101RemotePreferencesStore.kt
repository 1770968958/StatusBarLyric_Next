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
            is Long -> preferences.getLong(key, defValue) as T
            is Boolean -> preferences.getBoolean(key, defValue) as T
            is Double -> preferences.getFloat(key, defValue.toFloat()).toDouble() as T
            is Float -> preferences.getFloat(key, defValue) as T
            else -> throw IllegalArgumentException(
                "Unsupported preference type for key '$key': ${defValue?.let { it::class.java.name } ?: "null"}"
            )
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
