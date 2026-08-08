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

import android.annotation.SuppressLint
import android.content.SharedPreferences
import statusbar.lyric.config.ConfigStore

class ConfigTools : ConfigStore {
    private var mSP: SharedPreferences? = null
    private var mSPEditor: SharedPreferences.Editor? = null

    @SuppressLint("CommitPrefEdits")
    constructor(sharedPreferences: SharedPreferences?) {
        attach(sharedPreferences)
    }

    @SuppressLint("CommitPrefEdits")
    fun attach(sharedPreferences: SharedPreferences?) {
        mSP = sharedPreferences
        mSPEditor = sharedPreferences?.edit()
    }

    override fun reload() {
        val reload = mSP?.javaClass?.methods?.firstOrNull {
            it.name == "reload" && it.parameterTypes.isEmpty()
        } ?: return
        try {
            reload.isAccessible = true
            reload.invoke(mSP)
        } catch (_: Throwable) {
            // 不支持 reload 的 SharedPreferences 实现本身就是实时读取，无需额外处理。
        }
    }

    override fun put(key: String, value: Any) {
        when (value) {
            is Int -> mSPEditor?.putInt(key, value)
            is Long -> mSPEditor?.putLong(key, value)
            is String -> mSPEditor?.putString(key, value)
            is Boolean -> mSPEditor?.putBoolean(key, value)
            is Float -> mSPEditor?.putFloat(key, value)
            else -> throw IllegalArgumentException("不支持的配置类型: ${value::class.java.name}")
        }
        mSPEditor?.apply()
    }

    override fun getString(key: String, defaultValue: String): String =
        mSP?.getString(key, defaultValue) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int =
        mSP?.getInt(key, defaultValue) ?: defaultValue

    override fun getLong(key: String, defaultValue: Long): Long =
        mSP?.getLong(key, defaultValue) ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        mSP?.getBoolean(key, defaultValue) ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float =
        mSP?.getFloat(key, defaultValue) ?: defaultValue

    override fun contains(key: String): Boolean {
        return mSP?.contains(key) == true
    }

    override fun snapshot(): Map<String, Any?> {
        return mSP?.all?.mapValues { it.value } ?: emptyMap()
    }

    override fun clearConfig() {
        mSPEditor?.clear()?.apply()
    }
}
