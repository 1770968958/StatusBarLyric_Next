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
 */

package statusbar.lyric.tools

import java.lang.reflect.Method

/**
 * Cached access to Android's hidden SystemProperties.get(String) API.
 * Capability discovery happens once; missing/blocked APIs degrade to an empty string.
 */
object SystemPropertiesReader {
    private val getMethod: Method? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java)
                .apply { isAccessible = true }
        }.getOrNull()
    }

    fun get(key: String): String {
        val method = getMethod ?: return ""
        return try {
            method.invoke(null, key) as? String ?: ""
        } catch (illegalArgumentException: IllegalArgumentException) {
            throw illegalArgumentException
        } catch (_: Throwable) {
            ""
        }
    }
}
