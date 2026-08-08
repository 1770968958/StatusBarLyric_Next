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
import statusbar.lyric.runtime.test.AnchorCandidateDetector
import statusbar.lyric.runtime.test.AnchorCandidateSignature
import statusbar.lyric.runtime.test.AnchorTestProtocol.ACTION_TEST_RECEIVER
import statusbar.lyric.runtime.test.AnchorTestProtocol.EXTRA_DATA
import statusbar.lyric.runtime.test.AnchorTestProtocol.EXTRA_REQUEST_ID
import statusbar.lyric.runtime.test.AnchorTestProtocol.EXTRA_TYPE
import statusbar.lyric.runtime.test.AnchorTestProtocol.NO_REQUEST_ID
import statusbar.lyric.runtime.test.AnchorTestProtocol.TYPE_GET_CLASS
import statusbar.lyric.runtime.test.AnchorTestProtocol.TYPE_SHOW_VIEW
import statusbar.lyric.tools.ActivityTestTools.receiveClass
import java.util.IdentityHashMap
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
    private val candidateDetector = AnchorCandidateDetector()

    @Volatile
    private var collectionEnabled = false

    private val stopCollectionRunnable = Runnable { collectionEnabled = false }

    private var previewView: TextView? = null
    private var previewedTarget: TextView? = null

    fun start(context: Context) {
        candidates.clear()
        candidateDetector.start(XposedOwnSP.config.relaxConditions)
        collectionEnabled = true
        mainHandler.removeCallbacks(stopCollectionRunnable)
        mainHandler.postDelayed(
            stopCollectionRunnable,
            AnchorCandidateDetector.COLLECTION_DURATION_MILLIS
        )
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
        if (!collectionEnabled || candidates.containsKey(view)) return
        val parent = view.parent as? LinearLayout ?: return
        val className = view.javaClass.name
        if (!candidateDetector.isCandidateText(view.text) ||
            !candidateDetector.isCandidateClass(className)
        ) return

        val idName = runCatching {
            if (view.id == View.NO_ID) "" else view.resources.getResourceEntryName(view.id)
        }.getOrDefault("")
        val clockContainerId = view.resources.getIdentifier("clock_container", "id", view.context.packageName)
        if (parent.id == clockContainerId) return

        val signature = AnchorCandidateSignature(
            textViewClassName = className,
            textViewId = view.id,
            parentViewClassName = parent.javaClass.name,
            parentViewId = parent.id,
            textSize = view.textSize,
            idName = idName
        )
        val base = Data(
            signature.textViewClassName,
            signature.textViewId,
            signature.parentViewClassName,
            signature.parentViewId,
            false,
            candidateDetector.nextIndex(signature),
            signature.textSize,
            signature.idName
        )
        candidates[view] = base
        module.log(Log.INFO, TAG, "API101 anchor candidate collected; count=${candidates.size}; data=$base")
    }

    private fun sendCandidates(context: Context, requestId: Long) {
        context.receiveClass(ArrayList(candidates.values), requestId)
        module.log(Log.INFO, TAG, "API101 anchor candidates sent; count=${candidates.size}")
    }

    private fun previewCandidate(data: Data) {
        mainHandler.post {
            val target = candidates.entries.firstOrNull { (_, candidate) -> candidateDetector.matches(candidate, data) }?.key ?: return@post
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
    }
}
