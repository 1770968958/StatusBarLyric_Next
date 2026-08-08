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

import android.os.Handler
import android.os.Looper

object MainThreadScheduler {
    private val handler: Handler by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Handler(Looper.getMainLooper())
    }

    fun postDelayedSeconds(delaySeconds: Long = 0, callback: () -> Unit): Boolean {
        val delayMillis = if (delaySeconds <= 0) 0 else delaySeconds * 1_000L
        return handler.postDelayed(callback, delayMillis)
    }
}
