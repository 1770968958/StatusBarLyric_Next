/*
 * StatusBarLyric
 * Copyright (C) 2021-2022 fkj@fkj233.cn
 * https://github.com/Block-Network/StatusBarLyric
 *
 * This software is free opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package statusbar.lyric.hook

import android.view.View
import android.widget.TextView
import statusbar.lyric.view.LyricSwitchView

/**
 * Keeps the API 101 lyric and matched clock visibility state together so
 * visibility interception never applies to unrelated SystemUI views.
 */
class Api101LyricDisplayState {
    private var matchedClock: TextView? = null
    private var lyricShowing = false
    private var clockHiddenForLyric = false
    private var lastDynamicTint: Int? = null

    @Synchronized
    fun bindClock(clock: TextView, hideTime: Boolean) {
        matchedClock = clock
        if (lyricShowing && hideTime) {
            hideClock()
        }
    }

    @Synchronized
    fun unbindClock(clock: View) {
        if (matchedClock !== clock) return
        matchedClock = null
        clockHiddenForLyric = false
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
    fun shouldKeepClockHidden(
        view: View?,
        requestedVisibility: Int,
        hideTime: Boolean,
        limitVisibilityChange: Boolean
    ): Boolean {
        return limitVisibilityChange &&
            hideTime &&
            lyricShowing &&
            matchedClock === view &&
            requestedVisibility == View.VISIBLE
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
        val clock = matchedClock ?: return
        clockHiddenForLyric = true
        if (clock.visibility != View.GONE) {
            clock.visibility = View.GONE
        }
    }

    private fun restoreClock() {
        if (!clockHiddenForLyric) return
        clockHiddenForLyric = false
        matchedClock?.visibility = View.VISIBLE
    }
}
