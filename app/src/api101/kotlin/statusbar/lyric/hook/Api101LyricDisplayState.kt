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

package statusbar.lyric.hook

import android.view.View
import android.widget.TextView
import statusbar.lyric.runtime.ViewVisibilityOverrideState
import statusbar.lyric.view.LyricSwitchView
import java.lang.ref.WeakReference

/**
 * Keeps the API 101 lyric and matched clock visibility state together so
 * visibility interception never applies to unrelated SystemUI views.
 */
class Api101LyricDisplayState(
    private val visibilityOverrides: ViewVisibilityOverrideState
) {
    private var matchedClockRef: WeakReference<TextView>? = null
    private val matchedClock: TextView? get() = matchedClockRef?.get()
    private var lyricShowing = false
    private var lastDynamicTint: Int? = null

    @Synchronized
    fun bindClock(clock: TextView, hideTime: Boolean) {
        matchedClockRef = WeakReference(clock)
        if (lyricShowing && hideTime) {
            hideClock()
        }
    }

    @Synchronized
    fun unbindClock(clock: View) {
        if (matchedClock !== clock) return
        visibilityOverrides.forget(clock)
        matchedClockRef = null
    }

    @Synchronized
    fun updateLyricVisibility(show: Boolean, hideTime: Boolean) {
        lyricShowing = show
        if (show && hideTime) {
            hideClock()
        } else {
            restoreClock()
        }
    }

    @Synchronized
    fun resolveTextColor(fallbackColor: Int, useDynamicColor: Boolean): Int {
        return if (useDynamicColor) lastDynamicTint ?: fallbackColor else fallbackColor
    }

    @Synchronized
    fun updateDynamicTint(lyricView: LyricSwitchView?, tint: Int, useDynamicColor: Boolean) {
        lastDynamicTint = tint
        if (useDynamicColor) {
            lyricView?.setTextColor(tint)
        }
    }

    private fun hideClock() {
        visibilityOverrides.apply(matchedClock, View.GONE)
    }

    private fun restoreClock() {
        visibilityOverrides.restore(matchedClock)
    }
}
