/*
 * StatusBarLyric
 * Copyright (C) 2021-2022 fkj@fkj233.cn
 * https://github.com/Block-Network/StatusBarLyric
 *
 * This software is free opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.  If not, see
 * <https://www.gnu.org/licenses/>.
 */

package statusbar.lyric.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import statusbar.lyric.R
import statusbar.lyric.config.XposedOwnSP
import statusbar.lyric.data.Data
import statusbar.lyric.tools.ActivityTestTools.receiveClass
import java.text.SimpleDateFormat
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * API 101 replacement for the Legacy anchor discovery hook.
 */
class Api101SystemUITest(
    private val module: XposedModule
) {
    private val receiverRegistered = AtomicBoolean(false)
    private val drawHookInstalled = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val candidates = IdentityHashMap<TextView, Data>()
    private val timeFormats = arrayOf(
        SimpleDateFormat("H:mm", Locale.getDefault()),
        SimpleDateFormat("h:mm", Locale.getDefault())
    )

    private var previewView: TextView? = null
    private var previewedTarget: TextView? = null

    fun start(context: Context) {
        registerReceiver(context)
        installDrawHook()
    }

    private fun registerReceiver(context: Context) {
        if (!receiverRegistered.compareAndSet(false, true)) return

        runCatching {
            val filter = IntentFilter(ACTION_TEST_RECEIVER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(testReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(testReceiver, filter)
            }
            module.log(Log.INFO, TAG, "API101 anchor test receiver registered")
        }.onFailure { throwable ->
            receiverRegistered.set(false)
            module.log(Log.WARN, TAG, "API101 anchor test receiver registration failed", throwable)
        }
    }

    private fun installDrawHook() {
        if (!drawHookInstalled.compareAndSet(false, true)) return

        runCatching {
            val onDraw = TextView::class.java.getDeclaredMethod("onDraw", Canvas::class.java)
            module.hook(onDraw)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(DrawHooker(this))
            module.log(Log.INFO, TAG, "API101 anchor TextView.onDraw hook registered")
        }.onFailure { throwable ->
            drawHookInstalled.set(false)
            module.log(Log.WARN, TAG, "API101 anchor TextView.onDraw hook registration failed", throwable)
        }
    }

    private fun onTextViewDraw(view: TextView) {
        if (candidates.containsKey(view)) return
        val parent = view.parent as? LinearLayout ?: return
        val text = view.text?.toString().orEmpty()
        if (!isCandidateText(text) || !isCandidateClass(view.javaClass.name)) return

        val idName = runCatching {
            if (view.id == View.NO_ID) "" else view.resources.getResourceEntryName(view.id)
        }.getOrDefault("")
        val clockContainerId = view.resources.getIdentifier("clock_container", "id", view.context.packageName)
        if (parent.id == clockContainerId) return

        val base = Data(
            view.javaClass.name,
            view.id,
            parent.javaClass.name,
            parent.id,
            false,
            0,
            view.textSize,
            idName
        )
        base.index = candidates.values.count { candidate ->
            candidate.textViewClassName == base.textViewClassName &&
                candidate.textViewId == base.textViewId &&
                candidate.parentViewClassName == base.parentViewClassName &&
                candidate.parentViewId == base.parentViewId &&
                candidate.textSize == base.textSize &&
                candidate.idName == base.idName
        }
        candidates[view] = base
        module.log(Log.INFO, TAG, "API101 anchor candidate collected; count=${candidates.size}; data=$base")
    }

    private fun isCandidateText(text: String): Boolean {
        if (timeFormats.any { it.format(System.currentTimeMillis()).toRegex().containsMatchIn(text) }) {
            return true
        }
        return XposedOwnSP.config.relaxConditions && listOf("周", "月", "日").any(text::contains)
    }

    private fun isCandidateClass(className: String): Boolean {
        if (XposedOwnSP.config.relaxConditions) return true
        if (className == TextView::class.java.name) return false
        return listOf("controlcenter", "image", "keyguard").none { filter ->
            className.contains(filter, ignoreCase = true)
        }
    }

    private fun sendCandidates(context: Context, requestId: Long) {
        context.receiveClass(ArrayList(candidates.values), requestId)
        module.log(Log.INFO, TAG, "API101 anchor candidates sent; count=${candidates.size}")
    }

    private fun previewCandidate(data: Data) {
        mainHandler.post {
            val target = candidates.entries.firstOrNull { (_, candidate) -> candidate.matches(data) }?.key ?: return@post
            val parent = target.parent as? LinearLayout ?: return@post
            previewedTarget?.let { previous ->
                (previewView?.parent as? LinearLayout)?.removeView(previewView)
                previous.visibility = View.VISIBLE
            }

            target.visibility = View.GONE
            val marker = TextView(parent.context).apply {
                text = parent.context.getString(R.string.app_name)
                isSingleLine = true
                gravity = Gravity.CENTER
                setBackgroundColor(Color.WHITE)
                setTextColor(Color.BLACK)
            }
            parent.addView(marker, 0)
            previewView = marker
            previewedTarget = target
            module.log(Log.INFO, TAG, "API101 anchor preview shown; data=$data")
        }
    }

    private val testReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra(EXTRA_TYPE)) {
                TYPE_GET_CLASS -> sendCandidates(context, intent.getLongExtra(EXTRA_REQUEST_ID, NO_REQUEST_ID))
                TYPE_SHOW_VIEW -> readData(intent)?.let(::previewCandidate)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun readData(intent: Intent): Data? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, Data::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_DATA)
        }
    }

    private fun Data.matches(other: Data): Boolean {
        return textViewClassName == other.textViewClassName &&
            textViewId == other.textViewId &&
            parentViewClassName == other.parentViewClassName &&
            parentViewId == other.parentViewId &&
            textSize == other.textSize &&
            index == other.index
    }

    private class DrawHooker(
        private val owner: Api101SystemUITest
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            (chain.getThisObject() as? TextView)?.let(owner::onTextViewDraw)
            return result
        }
    }

    private companion object {
        const val TAG = "StatusBarLyric/API101"
        const val ACTION_TEST_RECEIVER = "TestReceiver"
        const val EXTRA_TYPE = "Type"
        const val EXTRA_DATA = "Data"
        const val EXTRA_REQUEST_ID = "RequestId"
        const val TYPE_GET_CLASS = "GetClass"
        const val TYPE_SHOW_VIEW = "ShowView"
        const val NO_REQUEST_ID = Long.MIN_VALUE

    }
}
