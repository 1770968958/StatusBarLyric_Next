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

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import statusbar.lyric.config.XposedOwnSP
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The API 101-only SystemUI path. It deliberately keeps the first migration
 * slice to target matching, text mounting and SuperLyric show/hide callbacks.
 */
class Api101SystemUIHook(
    private val module: XposedModule
) {
    // P1 only displays and hides plain text; colors, icons, scrolling, titles and MIUI hooks are deferred.
    private val mainHandler = Handler(Looper.getMainLooper())
    private val targetHookInstalled = AtomicBoolean(false)
    private val receiverRegistered = AtomicBoolean(false)

    private var lyricView: TextView? = null
    private var pendingLyric: String = ""
    private var targetIndex: Int = 0

    private val receiver = object : ISuperLyricReceiver.Stub() {
        override fun onLyric(publisher: String?, data: SuperLyricData?) {
            val lyric = data?.lyric?.text.orEmpty()
            if (lyric.isEmpty()) return

            mainHandler.post {
                pendingLyric = lyric
                lyricView?.let { view ->
                    view.text = lyric
                    view.visibility = View.VISIBLE
                }
                module.log(
                    android.util.Log.INFO,
                    TAG,
                    "API101 lyric received; publisher=${publisher.orEmpty()}; visible=${lyricView != null}"
                )
            }
        }

        override fun onStop(publisher: String?, data: SuperLyricData?) {
            mainHandler.post {
                pendingLyric = ""
                lyricView?.visibility = View.GONE
                module.log(
                    android.util.Log.INFO,
                    TAG,
                    "API101 lyric stopped; publisher=${publisher.orEmpty()}"
                )
            }
        }
    }

    fun onApplicationAttached(context: Context, classLoader: ClassLoader) {
        if (!XposedOwnSP.config.masterSwitch) {
            module.log(android.util.Log.INFO, TAG, "API101 SystemUI hook skipped because masterSwitch is off")
            return
        }

        registerSuperLyric()
        registerTargetViewHook(context, classLoader)
    }

    private fun registerSuperLyric() {
        if (!receiverRegistered.compareAndSet(false, true)) return

        runCatching {
            SuperLyricHelper.registerReceiver(receiver)
            module.log(android.util.Log.INFO, TAG, "API101 SuperLyric receiver registered")
        }.onFailure { throwable ->
            receiverRegistered.set(false)
            module.log(android.util.Log.WARN, TAG, "API101 SuperLyric receiver registration failed", throwable)
        }
    }

    private fun registerTargetViewHook(context: Context, classLoader: ClassLoader) {
        if (!targetHookInstalled.compareAndSet(false, true)) return

        val className = XposedOwnSP.config.textViewClassName
        if (className.isEmpty()) {
            targetHookInstalled.set(false)
            module.log(android.util.Log.WARN, TAG, "API101 target View hook skipped: textViewClassName is empty")
            return
        }

        runCatching {
            val targetClass = classLoader.loadClass(className)
            if (!TextView::class.java.isAssignableFrom(targetClass)) {
                error("configured target is not a TextView: $className")
            }

            val attachMethod = findMethod(targetClass, "onAttachedToWindow")
                ?: error("onAttachedToWindow not found on $className")
            module.hook(attachMethod)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(TargetViewHooker(this))
            module.log(
                android.util.Log.INFO,
                TAG,
                "API101 registered target View hook; context=${context.javaClass.name}; class=$className; loader=$classLoader"
            )
        }.onFailure { throwable ->
            targetHookInstalled.set(false)
            module.log(android.util.Log.WARN, TAG, "API101 target View hook registration failed", throwable)
        }
    }

    private fun onTargetViewAttached(candidate: Any?) {
        val view = candidate as? View ?: return
        if (!isConfiguredTarget(view)) return

        mainHandler.post {
            val parent = view.parent as? ViewGroup
            if (parent == null) {
                module.log(android.util.Log.WARN, TAG, "API101 target View matched without a ViewGroup parent")
                return@post
            }

            val existingParent = lyricView?.parent as? ViewGroup
            if (existingParent != null && existingParent !== parent) {
                existingParent.removeView(lyricView)
            }

            val viewToMount = lyricView ?: TextView(parent.context).also { created ->
                lyricView = created
                created.setSingleLine(true)
                created.maxLines = 1
                created.visibility = View.GONE
            }

            if (viewToMount.parent !== parent) {
                val layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val insertIndex = if (XposedOwnSP.config.viewLocation == 0) 0 else parent.childCount
                parent.addView(viewToMount, insertIndex, layoutParams)
            }

            if (pendingLyric.isNotEmpty()) {
                viewToMount.text = pendingLyric
                viewToMount.visibility = View.VISIBLE
            }
            module.log(
                android.util.Log.INFO,
                TAG,
                "API101 target View matched and lyric TextView mounted; class=${view.javaClass.name}; parent=${parent.javaClass.name}"
            )
        }
    }

    private fun isConfiguredTarget(view: View): Boolean {
        val config = XposedOwnSP.config
        if (view !is TextView || view.javaClass.name != config.textViewClassName) return false
        if (view.id != config.textViewId || view.textSize != config.textSize) return false

        val parent = view.parent as? ViewGroup ?: return false
        if (parent.javaClass.name != config.parentViewClassName || parent.id != config.parentViewId) return false

        if (targetIndex == config.index) return true
        targetIndex += 1
        return false
    }

    private fun findMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }?.let {
                return it
            }
            current = current.superclass
        }
        return null
    }

    private class TargetViewHooker(
        private val owner: Api101SystemUIHook
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            owner.onTargetViewAttached(chain.getThisObject())
            return result
        }
    }

    private companion object {
        const val TAG = "StatusBarLyric/API101"
    }
}
