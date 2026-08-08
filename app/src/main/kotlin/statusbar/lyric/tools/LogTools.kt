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

import android.util.Log

object LogTools {
    private const val MAX_LENGTH = 4000
    private const val TAG = "StatusBarLyric"
    @Volatile
    private var outprint = false

    fun Any?.log(): Any? {
        if (!outprint) return this
        write(this)
        return this
    }

    inline fun log(message: () -> Any?) {
        if (!isEnabled()) return
        write(message())
    }

    @PublishedApi
    internal fun isEnabled(): Boolean = outprint

    @PublishedApi
    internal fun write(value: Any?) {
        val content = if (value is Throwable) Log.getStackTraceString(value) else value.toString()
        if (content.length <= MAX_LENGTH) {
            Log.d(TAG, content)
            return
        }

        var start = 0
        while (start < content.length) {
            val end = minOf(start + MAX_LENGTH, content.length)
            Log.d(TAG, content.substring(start, end))
            start = end
        }
    }

    fun init(out: Boolean) {
        outprint = out
    }
}
