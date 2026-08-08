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

package statusbar.lyric.runtime.test

import statusbar.lyric.data.Data
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Shared anchor-discovery rules for the legacy and API101 SystemUI test hooks.
 * Hook installation stays flavor-specific; this class owns the hot-path filtering,
 * clock-token cache and stable duplicate-candidate indexing.
 */
class AnchorCandidateDetector(
    private val collectionDurationNanos: Long = COLLECTION_DURATION_MILLIS * NANOS_PER_MILLI
) {
    private val signatureCounts = HashMap<AnchorCandidateSignature, Int>()
    private val timeFormats = arrayOf(
        SimpleDateFormat("H:mm", Locale.getDefault()),
        SimpleDateFormat("h:mm", Locale.getDefault())
    )

    @Volatile
    private var relaxConditions = false

    @Volatile
    private var deadlineNanos = Long.MIN_VALUE

    private var cachedMinuteBucket = Long.MIN_VALUE
    private var cachedClockTokens: Array<String> = emptyArray()

    fun start(relaxConditions: Boolean, nowNanos: Long = System.nanoTime()) {
        this.relaxConditions = relaxConditions
        deadlineNanos = nowNanos + collectionDurationNanos
        signatureCounts.clear()
        synchronized(timeFormats) {
            cachedMinuteBucket = Long.MIN_VALUE
            cachedClockTokens = emptyArray()
        }
    }

    fun isCollectionActive(nowNanos: Long = System.nanoTime()): Boolean {
        return nowNanos < deadlineNanos
    }

    fun isCandidateText(text: CharSequence?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val normalized = normalizeText(text?.toString().orEmpty())
        if (currentClockTokens(nowMillis).any(normalized::contains)) return true
        if (!relaxConditions) return false
        return RELAXED_DATE_MARKERS.any { marker -> normalized.indexOf(marker) >= 0 }
    }

    fun isCandidateClass(className: String): Boolean {
        if (relaxConditions) return true
        if (className == BASE_TEXT_VIEW_CLASS_NAME) return false
        return FILTERED_CLASS_NAME_PARTS.none { filter ->
            className.contains(filter, ignoreCase = true)
        }
    }

    fun nextIndex(signature: AnchorCandidateSignature): Int {
        val next = signatureCounts[signature] ?: 0
        signatureCounts[signature] = next + 1
        return next
    }

    fun matches(candidate: Data, requested: Data): Boolean {
        return candidate.textViewClassName == requested.textViewClassName &&
            candidate.textViewId == requested.textViewId &&
            candidate.parentViewClassName == requested.parentViewClassName &&
            candidate.parentViewId == requested.parentViewId &&
            candidate.textSize == requested.textSize &&
            candidate.index == requested.index
    }

    @Synchronized
    private fun currentClockTokens(nowMillis: Long): Array<String> {
        val minuteBucket = nowMillis / MILLIS_PER_MINUTE
        if (minuteBucket == cachedMinuteBucket) return cachedClockTokens

        val tokens = synchronized(timeFormats) {
            timeFormats.map { formatter -> formatter.format(nowMillis) }.distinct().toTypedArray()
        }
        cachedMinuteBucket = minuteBucket
        cachedClockTokens = tokens
        return tokens
    }

    private fun normalizeText(text: String): String {
        if (' ' !in text && '\n' !in text) return text
        return buildString(text.length) {
            text.forEach { char ->
                if (char != ' ' && char != '\n') append(char)
            }
        }
    }

    companion object {
        const val COLLECTION_DURATION_MILLIS = 60_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val BASE_TEXT_VIEW_CLASS_NAME = "android.widget.TextView"
        private val RELAXED_DATE_MARKERS = charArrayOf('周', '月', '日')
        private val FILTERED_CLASS_NAME_PARTS = arrayOf("controlcenter", "image", "keyguard")
    }
}

data class AnchorCandidateSignature(
    val textViewClassName: String,
    val textViewId: Int,
    val parentViewClassName: String,
    val parentViewId: Int,
    val textSize: Float,
    val idName: String
)
