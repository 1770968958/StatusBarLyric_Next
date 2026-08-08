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

package statusbar.lyric.tools

import de.robv.android.xposed.XSharedPreferences
import statusbar.lyric.BuildConfig
import statusbar.lyric.tools.LogTools.log

object LegacyPreferences {
    fun get(key: String): XSharedPreferences? {
        val current = getReadable(key)
        if (current != null || key == LEGACY_CONFIG_NAME) return current
        return getReadable(LEGACY_CONFIG_NAME)
    }

    private fun getReadable(key: String): XSharedPreferences? {
        return try {
            XSharedPreferences(BuildConfig.APPLICATION_ID, key).takeIf { it.file.canRead() }
        } catch (e: Throwable) {
            e.log()
            null
        }
    }

    private const val LEGACY_CONFIG_NAME = "Lyric_Config"
}
