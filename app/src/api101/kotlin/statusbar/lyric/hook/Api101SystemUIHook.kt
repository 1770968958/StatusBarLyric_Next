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
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
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
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * The API 101-only SystemUI path. It deliberately keeps the first migration
 * slice to target matching, text mounting and SuperLyric show/hide callbacks.
 */
class Api101SystemUIHook(
    private val module: XposedModule
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val targetHookInstalled = AtomicBoolean(false)
    private val receiverRegistered = AtomicBoolean(false)
    private val unsupportedConfigLogged = AtomicBoolean(false)

    private var lyricView: TextView? = null
    private var pendingLyric: String = ""
    private var mountedTarget: View? = null
    private var mountedParent: ViewGroup? = null
    private val parentMatchStates = IdentityHashMap<ViewGroup, ParentMatchState>()
    private val targetParents = IdentityHashMap<View, ViewGroup>()

    private val receiver = object : ISuperLyricReceiver.Stub() {
        override fun onLyric(publisher: String?, data: SuperLyricData?) {
            val lyric = data?.lyric?.text.orEmpty()
            if (lyric.isEmpty()) return

            mainHandler.post {
                runCatching {
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
                }.onFailure { throwable ->
                    module.log(android.util.Log.WARN, TAG, "API101 lyric update failed", throwable)
                }
            }
        }

        override fun onStop(publisher: String?, data: SuperLyricData?) {
            mainHandler.post {
                runCatching {
                    pendingLyric = ""
                    lyricView?.visibility = View.GONE
                    module.log(
                        android.util.Log.INFO,
                        TAG,
                        "API101 lyric stopped; publisher=${publisher.orEmpty()}"
                    )
                }.onFailure { throwable ->
                    module.log(android.util.Log.WARN, TAG, "API101 lyric stop update failed", throwable)
                }
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
            val detachMethod = findMethod(targetClass, "onDetachedFromWindow")
                ?: error("onDetachedFromWindow not found on $className")
            module.hook(attachMethod)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(TargetViewHooker(this))
            module.hook(detachMethod)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(TargetViewDetachedHooker(this))
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
        val source = view as? TextView ?: return
        if (view === lyricView) return
        val match = findConfiguredTarget(source) ?: return

        mainHandler.post {
            runCatching {
                val parent = match.parent
                val existingParent = lyricView?.parent as? ViewGroup
                if (existingParent != null && existingParent !== parent) {
                    existingParent.removeView(lyricView)
                }

                val viewToMount = lyricView ?: TextView(parent.context).also { created ->
                    lyricView = created
                    created.visibility = View.GONE
                }
                applyLyricAppearance(viewToMount, source)

                if (viewToMount.parent !== parent) {
                    val insertIndex = if (XposedOwnSP.config.viewLocation == 0) 0 else parent.childCount
                    parent.addView(
                        viewToMount,
                        insertIndex.coerceIn(0, parent.childCount),
                        createLayoutParams(view)
                    )
                }

                mountedTarget = view
                mountedParent = parent
                if (pendingLyric.isNotEmpty()) {
                    viewToMount.text = pendingLyric
                    viewToMount.visibility = View.VISIBLE
                }
                module.log(
                    android.util.Log.INFO,
                    TAG,
                    "API101 target View matched and lyric TextView mounted; class=${view.javaClass.name}; parent=${parent.javaClass.name}; index=${match.index}"
                )
            }.onFailure { throwable ->
                module.log(android.util.Log.WARN, TAG, "API101 lyric TextView mount failed", throwable)
            }
        }
    }

    private fun onTargetViewDetached(candidate: Any?, parentBeforeDetach: ViewGroup?) {
        val view = candidate as? View ?: return
        val parent = synchronized(parentMatchStates) {
            val knownParent = targetParents.remove(view) ?: parentBeforeDetach
            if (knownParent != null) {
                parentMatchStates[knownParent]?.let { state ->
                    state.matchedIndices.remove(view)
                    if (state.matchedIndices.isEmpty()) {
                        parentMatchStates.remove(knownParent)
                    }
                }
            }
            knownParent
        }

        if (mountedTarget !== view) return
        mountedTarget = null
        mountedParent = null
        mainHandler.post {
            runCatching {
                lyricView?.let { mountedView ->
                    if (mountedView.parent === parent) {
                        parent?.removeView(mountedView)
                    } else {
                        (mountedView.parent as? ViewGroup)?.removeView(mountedView)
                    }
                    mountedView.visibility = View.GONE
                }
            }.onFailure { throwable ->
                module.log(android.util.Log.WARN, TAG, "API101 lyric TextView detach cleanup failed", throwable)
            }
        }
    }

    private fun findConfiguredTarget(view: View): TargetMatch? {
        val config = XposedOwnSP.config
        if (view !is TextView || view.javaClass.name != config.textViewClassName) return null
        if (view.id != config.textViewId) return null

        // A zero/default recorded text size means "do not constrain by size".
        val expectedTextSize = config.textSize
        if (expectedTextSize > 0f && abs(view.textSize - expectedTextSize) > TEXT_SIZE_EPSILON) {
            return null
        }

        val parent = view.parent as? ViewGroup ?: return null
        if (parent.javaClass.name != config.parentViewClassName || parent.id != config.parentViewId) return null

        val index = synchronized(parentMatchStates) {
            val state = parentMatchStates.getOrPut(parent) { ParentMatchState() }
            state.matchedIndices[view] ?: state.nextIndex.also {
                state.nextIndex += 1
                state.matchedIndices[view] = it
                targetParents[view] = parent
            }
        }
        return if (index == config.index) TargetMatch(parent, index) else null
    }

    private fun applyLyricAppearance(target: TextView, source: TextView) {
        val config = XposedOwnSP.config
        target.setSingleLine(true)
        target.maxLines = 1
        target.gravity = Gravity.CENTER_VERTICAL
        target.typeface = source.typeface
        target.includeFontPadding = source.includeFontPadding

        val lyricSize = if (config.lyricSize > 0) config.lyricSize.toFloat() else source.textSize
        if (lyricSize > 0f) {
            target.setTextSize(TypedValue.COMPLEX_UNIT_PX, lyricSize)
        }

        val lyricColor = parseColor(config.lyricColor)
        target.setTextColor(lyricColor ?: source.currentTextColor)
        target.letterSpacing = if (config.lyricLetterSpacing == 0) {
            source.letterSpacing
        } else {
            config.lyricLetterSpacing / 100f
        }
        applyBackground(target, config.lyricBackgroundColor, config.lyricBackgroundRadius)
        logUnsupportedConfigBoundary()
    }

    private fun applyBackground(target: TextView, value: String, radius: Int) {
        target.setBackgroundColor(Color.TRANSPARENT)
        val colors = parseColorList(value)
        if (colors.isEmpty()) return

        target.background = if (colors.size == 1) {
            GradientDrawable().apply {
                setColor(colors[0])
                if (radius > 0) cornerRadius = radius.toFloat()
            }
        } else {
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors.toIntArray()).apply {
                if (radius > 0) cornerRadius = radius.toFloat()
            }
        }
    }

    private fun createLayoutParams(source: View): ViewGroup.LayoutParams {
        val sourceParams = source.layoutParams
        val params = runCatching {
            sourceParams?.javaClass
                ?.getConstructor(ViewGroup.LayoutParams::class.java)
                ?.newInstance(sourceParams) as? ViewGroup.LayoutParams
        }.getOrNull() ?: ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        (params as? ViewGroup.MarginLayoutParams)?.setMargins(
            XposedOwnSP.config.lyricStartMargins,
            XposedOwnSP.config.lyricTopMargins,
            XposedOwnSP.config.lyricEndMargins,
            XposedOwnSP.config.lyricBottomMargins
        )
        return params
    }

    private fun parseColor(value: String): Int? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return null
        return runCatching { Color.parseColor(normalized) }.getOrNull()
    }

    private fun parseColorList(value: String): List<Int> {
        val tokens = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return runCatching { tokens.map { Color.parseColor(it) } }.getOrElse { throwable ->
            module.log(android.util.Log.WARN, TAG, "API101 background color ignored: $value", throwable)
            emptyList()
        }
    }

    private fun logUnsupportedConfigBoundary() {
        val config = XposedOwnSP.config
        val hasDeferredBehavior = config.iconSwitch ||
            config.titleSwitch ||
            config.lyricGradientColor.isNotEmpty() ||
            config.lyricStrokeWidth != 0 ||
            config.lyricAnimation != 0 ||
            config.dynamicLyricSpeed ||
            config.lyricWidth != 0 ||
            config.mHyperOSTexture
        if (hasDeferredBehavior && unsupportedConfigLogged.compareAndSet(false, true)) {
            module.log(
                android.util.Log.INFO,
                TAG,
                "API101 TODO: icon/title/scroll/gradient/stroke/animation/width/MIUI behavior remains deferred"
            )
        }
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

    private class TargetViewDetachedHooker(
        private val owner: Api101SystemUIHook
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val view = chain.getThisObject() as? View
            val parent = view?.parent as? ViewGroup
            val result = chain.proceed()
            owner.onTargetViewDetached(view, parent)
            return result
        }
    }

    private data class TargetMatch(
        val parent: ViewGroup,
        val index: Int
    )

    private class ParentMatchState(
        var nextIndex: Int = 0,
        val matchedIndices: IdentityHashMap<View, Int> = IdentityHashMap()
    )

    private companion object {
        const val TAG = "StatusBarLyric/API101"
        const val TEXT_SIZE_EPSILON = 0.5f
    }
}
