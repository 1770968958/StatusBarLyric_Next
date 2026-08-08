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

    override val isReadOnly: Boolean = false

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
            // SharedPreferences implementations without reload are already current.
        }
    }

    override fun put(key: String?, any: Any) {
        when (any) {
            is Int -> mSPEditor?.putInt(key, any)
            is String -> mSPEditor?.putString(key, any)
            is Boolean -> mSPEditor?.putBoolean(key, any)
            is Float -> mSPEditor?.putFloat(key, any)
        }
        mSPEditor?.apply()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> opt(key: String, defValue: T): T {
        if (mSP == null) {
            return defValue
        }
        return when (defValue) {
            is String -> mSP!!.getString(key, defValue.toString()) as T
            is Int -> mSP!!.getInt(key, defValue) as T
            is Long -> mSP!!.getLong(key, defValue) as T
            is Boolean -> mSP!!.getBoolean(key, defValue) as T
            is Double -> mSP!!.getFloat(key, defValue.toFloat()).toDouble() as T
            is Float -> mSP!!.getFloat(key, defValue) as T
            else -> throw IllegalArgumentException(
                "Unsupported preference type for key '$key': ${defValue?.let { it::class.java.name } ?: "null"}"
            )
        }
    }

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
