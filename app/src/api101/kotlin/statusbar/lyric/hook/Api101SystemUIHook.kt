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
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import statusbar.lyric.config.XposedOwnSP
import statusbar.lyric.reflection.ReflectionUtils.callNoArg
import statusbar.lyric.reflection.ReflectionUtils.callWithArgs
import statusbar.lyric.reflection.ReflectionUtils.findMethod
import statusbar.lyric.reflection.ReflectionUtils.findMethodByName
import statusbar.lyric.reflection.ReflectionUtils.getFieldValue
import statusbar.lyric.reflection.ReflectionUtils.getIntFieldValue
import statusbar.lyric.runtime.TargetViewMatcher
import statusbar.lyric.runtime.ViewVisibilityOverrideState
import statusbar.lyric.runtime.TargetViewSpec
import statusbar.lyric.runtime.icon.IconBitmapDecoder
import statusbar.lyric.runtime.input.MediaKeyDispatcher
import statusbar.lyric.runtime.scheduler.ResettableHandlerTask
import statusbar.lyric.runtime.style.RuntimeAppearanceSnapshot
import statusbar.lyric.runtime.style.TypefaceFileCache
import statusbar.lyric.tools.BlurTools.cornerRadius
import statusbar.lyric.tools.BlurTools.setBackgroundBlur
import statusbar.lyric.tools.LyricViewTools
import statusbar.lyric.tools.LyricViewTools.cancelAnimation
import statusbar.lyric.tools.LyricViewTools.hideView
import statusbar.lyric.tools.LyricViewTools.randomAnima
import statusbar.lyric.tools.LyricViewTools.showView
import statusbar.lyric.tools.XiaomiUtils.isHyperOS
import statusbar.lyric.tools.XiaomiUtils.isXiaomi
import statusbar.lyric.view.LyricSwitchView
import statusbar.lyric.view.TitleDialog
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * API 101 SystemUI implementation. It keeps framework interaction in API 101
 * hooks while reusing the module's normal lyric presentation components.
 */
class Api101SystemUIHook(
    private val module: XposedModule
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val targetHookInstalled = AtomicBoolean(false)
    private val receiverRegistered = AtomicBoolean(false)
    private val clockVisibilityHookInstalled = AtomicBoolean(false)
    private val darkIconHookInstalled = AtomicBoolean(false)
    private val configObserverRegistered = AtomicBoolean(false)
    private val screenReceiverRegistered = AtomicBoolean(false)
    private val notificationHookInstalled = AtomicBoolean(false)
    private val touchHookInstalled = AtomicBoolean(false)
    private val xiaomiHooksInstalled = AtomicBoolean(false)
    private val focusNotificationHookInstalled = AtomicBoolean(false)
    private val systemUiTest = Api101SystemUITest(module)
    private val visibilityOverrides = ViewVisibilityOverrideState()
    private val lyricDisplayState = Api101LyricDisplayState(visibilityOverrides)
    private val targetViewMatcher = TargetViewMatcher()
    private val typefaceFileCache = TypefaceFileCache()
    private var appearanceSnapshot: RuntimeAppearanceSnapshot? = null
    private var appliedAppearanceKey: AppliedAppearanceKey? = null
    private var mediaKeyDispatcher: MediaKeyDispatcher? = null
    private val iconDecodeGeneration = AtomicLong(0L)
    private val iconDecodeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "StatusBarLyric-Api101-IconDecode")
    }

    private var lyricView: LyricSwitchView? = null
    private var lyricLayout: LinearLayout? = null
    private var iconView: ImageView? = null
    private var titleDialog: TitleDialog? = null
    private var pendingLyric: String = ""
    private var pendingDelay = 0
    private var playingPublisher = ""
    private var lastTitle = ""
    private var lastBase64Icon = ""
    private var isMusicPlaying = false
    private var isScreenLocked = false
    private var lyricShowing = false
    private var notificationIconAreaRef: WeakReference<View>? = null
    private var notificationIconArea: View?
        get() = notificationIconAreaRef?.get()
        set(value) { notificationIconAreaRef = value?.let(::WeakReference) }
    private var systemIconsContainerRef: WeakReference<View>? = null
    private var systemIconsContainer: View?
        get() = systemIconsContainerRef?.get()
        set(value) { systemIconsContainerRef = value?.let(::WeakReference) }
    private var miuiNetworkSpeedViewRef: WeakReference<View>? = null
    private var miuiNetworkSpeedView: View?
        get() = miuiNetworkSpeedViewRef?.get()
        set(value) { miuiNetworkSpeedViewRef = value?.let(::WeakReference) }
    private var miuiPadClockViewRef: WeakReference<View>? = null
    private var miuiPadClockView: View?
        get() = miuiPadClockViewRef?.get()
        set(value) { miuiPadClockViewRef = value?.let(::WeakReference) }
    private var miuiCarrierLabelRef: WeakReference<View>? = null
    private var miuiCarrierLabel: View?
        get() = miuiCarrierLabelRef?.get()
        set(value) { miuiCarrierLabelRef = value?.let(::WeakReference) }
    private var miuiNotificationBigTimeRef: WeakReference<View>? = null
    private var miuiNotificationBigTime: View?
        get() = miuiNotificationBigTimeRef?.get()
        set(value) { miuiNotificationBigTimeRef = value?.let(::WeakReference) }
    private var focusedNotificationController: Any? = null
    private var focusedNotificationShowing = false
    private var touchDownPoint: PointF? = null
    private var pendingTitleToShow = ""
    private var mountedTargetRef: WeakReference<View>? = null
    private var mountedTarget: View?
        get() = mountedTargetRef?.get()
        set(value) { mountedTargetRef = value?.let(::WeakReference) }
    private var mountedParentRef: WeakReference<ViewGroup>? = null
    private var mountedParent: ViewGroup?
        get() = mountedParentRef?.get()
        set(value) { mountedParentRef = value?.let(::WeakReference) }
    private val timeoutRestoreTask = ResettableHandlerTask(mainHandler) {
        if (isMusicPlaying) {
            pendingLyric = ""
            pendingDelay = 0
            hideLyric()
        }
    }
    private val titleDisplayTask = ResettableHandlerTask(mainHandler, action = titleTask@{
        val title = pendingTitleToShow
        if (
            title.isBlank() ||
            !isMusicPlaying ||
            lastTitle != title ||
            !XposedOwnSP.config.titleSwitch
        ) {
            return@titleTask
        }
        val source = mountedTarget as? TextView ?: return@titleTask
        (titleDialog ?: TitleDialog(source.context).also { titleDialog = it }).showTitle(title.trim())
    })
    private val configRefreshRunnable = Runnable {
        runCatching {
            XposedOwnSP.config.update()
            refreshAppearanceSnapshot()
            applyConfiguration()
            if (isMusicPlaying && pendingLyric.isNotEmpty()) {
                showLyric(pendingLyric, pendingDelay)
                refreshTimeoutRestore()
            }
        }.onFailure { throwable ->
            module.log(android.util.Log.WARN, TAG, "API101 config refresh failed", throwable)
        }
    }

    private val receiver = object : ISuperLyricReceiver.Stub() {
        override fun onLyric(publisher: String?, data: SuperLyricData?) {
            val lyricLine = data?.lyric ?: return
            val lyric = lyricLine.text
            if (lyric.isEmpty()) return
            val delay = lyricLine.delay.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val packageName = publisher.orEmpty()
            val title = data.title.orEmpty()
            val icon = resolveIconBase64(data, packageName)

            mainHandler.post {
                runCatching {
                    val sameLyric = isMusicPlaying &&
                        playingPublisher == packageName &&
                        pendingLyric == lyric &&
                        pendingDelay == delay &&
                        lastBase64Icon == icon
                    if (sameLyric) {
                        refreshTimeoutRestore()
                        return@post
                    }
                    isMusicPlaying = true
                    playingPublisher = packageName
                    pendingLyric = lyric
                    pendingDelay = delay
                    updateIcon(icon)
                    if (title != lastTitle) {
                        lastTitle = title
                        showTitle(title, lyric)
                    }
                    showLyric(lyric, delay)
                    refreshTimeoutRestore()
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
                    if (playingPublisher.isNotEmpty() && playingPublisher != publisher.orEmpty()) return@post
                    isMusicPlaying = false
                    playingPublisher = ""
                    pendingLyric = ""
                    pendingDelay = 0
                    iconDecodeGeneration.incrementAndGet()
                    timeoutRestoreTask.cancel()
                    titleDisplayTask.cancel()
                    pendingTitleToShow = ""
                    hideLyric()
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
        mediaKeyDispatcher = MediaKeyDispatcher(context)
        registerConfigObserver()
        if (!XposedOwnSP.config.masterSwitch) {
            module.log(android.util.Log.INFO, TAG, "API101 SystemUI hook skipped because masterSwitch is off")
            return
        }

        if (XposedOwnSP.config.testMode) {
            systemUiTest.start(context)
            return
        }

        registerSuperLyric()
        registerClockVisibilityHook()
        registerDynamicColorHook(classLoader)
        registerNotificationIconHook(classLoader)
        registerTouchHook(classLoader)
        registerXiaomiHooks(classLoader)
        registerFocusNotificationHook(classLoader)
        registerTargetViewHook(context, classLoader)
        registerScreenReceiver(context)
    }

    private fun registerConfigObserver() {
        if (!configObserverRegistered.compareAndSet(false, true)) return
        XposedOwnSP.registerOnPreferenceChangeListener { _, _ ->
            scheduleConfigRefresh()
        }
    }

    private fun scheduleConfigRefresh() {
        mainHandler.removeCallbacks(configRefreshRunnable)
        mainHandler.postDelayed(configRefreshRunnable, CONFIG_REFRESH_DEBOUNCE_MILLIS)
    }

    private fun registerScreenReceiver(context: Context) {
        if (!screenReceiverRegistered.compareAndSet(false, true)) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                isScreenLocked = intent.action == Intent.ACTION_SCREEN_OFF
                if (isScreenLocked && XposedOwnSP.config.hideLyricWhenLockScreen) {
                    hideLyric()
                } else if (isMusicPlaying && pendingLyric.isNotEmpty()) {
                    showLyric(pendingLyric, pendingDelay)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
        }.onFailure { throwable ->
            screenReceiverRegistered.set(false)
            module.log(android.util.Log.WARN, TAG, "API101 screen receiver registration failed", throwable)
        }
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

    private fun registerClockVisibilityHook() {
        if (!clockVisibilityHookInstalled.compareAndSet(false, true)) return

        runCatching {
            val visibilityMethod = View::class.java.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
            module.hook(visibilityMethod)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(ClockVisibilityHooker(this))
            module.log(android.util.Log.INFO, TAG, "API101 registered matched clock visibility hook")
        }.onFailure { throwable ->
            clockVisibilityHookInstalled.set(false)
            module.log(android.util.Log.WARN, TAG, "API101 clock visibility hook registration failed", throwable)
        }
    }

    private fun registerDynamicColorHook(classLoader: ClassLoader) {
        if (!darkIconHookInstalled.compareAndSet(false, true)) return

        runCatching {
            val dispatcherClass = classLoader.loadClass(DARK_ICON_DISPATCHER_CLASS)
            val applyDarkIntensity = findMethod(dispatcherClass, "applyDarkIntensity", 1)
                ?: error("applyDarkIntensity not found on $DARK_ICON_DISPATCHER_CLASS")
            module.hook(applyDarkIntensity)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(DarkIntensityHooker(this))
            module.log(android.util.Log.INFO, TAG, "API101 registered dynamic status bar tint hook")
        }.onFailure { throwable ->
            darkIconHookInstalled.set(false)
            module.log(android.util.Log.INFO, TAG, "API101 dynamic status bar tint hook unavailable", throwable)
        }
    }

    private fun registerNotificationIconHook(classLoader: ClassLoader) {
        if (!notificationHookInstalled.compareAndSet(false, true)) return
        val hooks = listOf(
            NOTIFICATION_ICON_AREA_CONTROLLER_CLASS to "initializeNotificationAreaViews",
            COLLAPSED_STATUS_BAR_FRAGMENT_CLASS to "onViewCreated"
        )
        var installed = false
        hooks.forEach { (className, methodName) ->
            runCatching {
                val method = findMethodByName(classLoader.loadClass(className), methodName)
                    ?: return@runCatching
                module.hook(method)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .intercept(NotificationAreaHooker(this))
                installed = true
            }.onFailure { throwable ->
                module.log(android.util.Log.INFO, TAG, "API101 notification icon hook unavailable: $className", throwable)
            }
        }
        if (!installed) notificationHookInstalled.set(false)
    }

    private fun registerTouchHook(classLoader: ClassLoader) {
        if (!touchHookInstalled.compareAndSet(false, true)) return
        runCatching {
            val method = findMethod(classLoader.loadClass(PHONE_STATUS_BAR_VIEW_CLASS), "onTouchEvent", 1)
                ?: error("onTouchEvent not found on $PHONE_STATUS_BAR_VIEW_CLASS")
            module.hook(method)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(StatusBarTouchHooker(this))
        }.onFailure { throwable ->
            touchHookInstalled.set(false)
            module.log(android.util.Log.INFO, TAG, "API101 status bar touch hook unavailable", throwable)
        }
    }

    private fun captureNotificationIconArea(instance: Any?) {
        notificationIconArea = findObjectField(instance, "mNotificationIconArea") as? View
            ?: findObjectField(instance, "mNotificationIconAreaInner") as? View
    }

    private fun onStatusBarTouch(event: MotionEvent): Boolean {
        if (!isMusicPlaying) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownPoint = PointF(event.rawX, event.rawY)
                return false
            }

            MotionEvent.ACTION_UP -> {
                val start = touchDownPoint ?: return false
                val horizontal = start.x - event.rawX
                val vertical = abs(start.y - event.rawY)
                val moved = abs(horizontal) > TOUCH_MOVE_THRESHOLD || vertical > TOUCH_MOVE_THRESHOLD
                if (moved && XposedOwnSP.config.slideStatusBarCutSongs &&
                    vertical <= XposedOwnSP.config.slideStatusBarCutSongsYRadius
                ) {
                    if (abs(horizontal) > XposedOwnSP.config.slideStatusBarCutSongsXRadius) {
                        if (horizontal > 0f) {
                            mediaKeyDispatcher?.next()
                        } else {
                            mediaKeyDispatcher?.previous()
                        }
                        return true
                    }
                    return false
                }
                if (!moved && event.eventTime - event.downTime > LONG_CLICK_MILLIS &&
                    XposedOwnSP.config.longClickStatusBarStop
                ) {
                    mediaKeyDispatcher?.playPause()
                    return true
                }
                if (!moved && XposedOwnSP.config.clickStatusBarToHideLyric && isTouchInsideLyric(event)) {
                    if (lyricShowing) hideLyric() else showLyric(pendingLyric, pendingDelay)
                    return true
                }
            }
        }
        return false
    }

    private fun isTouchInsideLyric(event: MotionEvent): Boolean {
        val layout = lyricLayout ?: return false
        return event.x >= layout.left && event.x <= layout.right &&
            event.y >= layout.top && event.y <= layout.bottom
    }

    private fun registerXiaomiHooks(classLoader: ClassLoader) {
        if (!isXiaomi || !xiaomiHooksInstalled.compareAndSet(false, true)) return
        registerXiaomiNetworkSpeedHook(classLoader)
        registerXiaomiPadClockHook(classLoader)
        registerXiaomiCarrierHook(classLoader)
        registerXiaomiNotificationClockHook(classLoader)
    }

    private fun registerXiaomiNetworkSpeedHook(classLoader: ClassLoader) {
        runCatching {
            val clazz = classLoader.loadClass(MIUI_NETWORK_SPEED_CLASS)
            findMethod(clazz, "onAttachedToWindow", 0)?.let { method ->
                module.hook(method).setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .intercept(XiaomiViewCaptureHooker(this, XiaomiViewKind.NETWORK_SPEED))
            }
            findMethod(clazz, "setVisibilityByController", 1)?.let { method ->
                module.hook(method).setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .intercept(XiaomiNetworkVisibilityHooker(this))
            }
        }.onFailure { throwable ->
            module.log(android.util.Log.INFO, TAG, "API101 Xiaomi network speed hook unavailable", throwable)
        }
    }

    private fun registerXiaomiPadClockHook(classLoader: ClassLoader) {
        runCatching {
            val clazz = classLoader.loadClass(MIUI_COLLAPSED_STATUS_BAR_FRAGMENT_CLASS)
            val method = findMethodByName(clazz, "initMiuiViewsOnViewCreated")
                ?: findMethodByName(clazz, "onViewCreated")
                ?: return@runCatching
            module.hook(method).setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(XiaomiPadClockHooker(this))
        }.onFailure { throwable ->
            module.log(android.util.Log.INFO, TAG, "API101 Xiaomi pad clock hook unavailable", throwable)
        }
    }

    private fun registerXiaomiCarrierHook(classLoader: ClassLoader) {
        runCatching {
            val clazz = classLoader.loadClass(KEYGUARD_STATUS_BAR_VIEW_CLASS)
            val method = findMethod(clazz, "onFinishInflate", 0) ?: return@runCatching
            module.hook(method).setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(XiaomiViewCaptureHooker(this, XiaomiViewKind.CARRIER))
        }.onFailure { throwable ->
            module.log(android.util.Log.INFO, TAG, "API101 Xiaomi carrier hook unavailable", throwable)
        }
    }

    private fun registerXiaomiNotificationClockHook(classLoader: ClassLoader) {
        runCatching {
            val clazz = classLoader.loadClass(MIUI_NOTIFICATION_CALLBACK_CLASS)
            val method = findMethod(clazz, "onExpansionChanged", 1) ?: return@runCatching
            module.hook(method).setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(XiaomiNotificationClockHooker(this))
        }.onFailure { throwable ->
            module.log(android.util.Log.INFO, TAG, "API101 Xiaomi notification clock hook unavailable", throwable)
        }
    }

    private fun captureXiaomiView(instance: Any?, kind: XiaomiViewKind) {
        when (kind) {
            XiaomiViewKind.NETWORK_SPEED -> miuiNetworkSpeedView = instance as? View
            XiaomiViewKind.CARRIER -> miuiCarrierLabel = findObjectField(instance, "mCarrierLabel") as? View
        }
    }

    private fun captureXiaomiPadClock(instance: Any?) {
        miuiPadClockView = findObjectField(instance, "mPadClockView") as? View
        if (lyricShowing && XposedOwnSP.config.hideTime && XposedOwnSP.config.mMiuiPadOptimize) {
            miuiPadClockHiddenForLyric = miuiPadClockView != null
            miuiPadClockView?.visibility = View.GONE
        }
    }

    private fun captureXiaomiNotificationClock(instance: Any?) {
        val controller = findObjectField(instance, "this$0") ?: return
        val headerController = findObjectField(controller, "headerController") ?: return
        val header = callNoArg(headerController, "get") ?: return
        miuiNotificationBigTime = findObjectField(header, "notificationBigTime") as? View
    }

    private fun shouldHideXiaomiNetworkSpeed(): Boolean {
        return lyricShowing && XposedOwnSP.config.mMiuiHideNetworkSpeed
    }

    private fun registerFocusNotificationHook(classLoader: ClassLoader) {
        if (!isXiaomi || !focusNotificationHookInstalled.compareAndSet(false, true)) return
        runCatching {
            val clazz = classLoader.loadClass(FOCUSED_NOTIFICATION_CONTROLLER_CLASS)
            val method = findMethod(clazz, "shouldShow", 0) ?: return@runCatching
            module.hook(method).setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(FocusNotificationHooker(this))
        }.onFailure { throwable ->
            focusNotificationHookInstalled.set(false)
            module.log(android.util.Log.INFO, TAG, "API101 focused notification hook unavailable", throwable)
        }
    }

    private fun onFocusNotificationEvaluated(controller: Any?, showing: Boolean) {
        focusedNotificationController = controller
        focusedNotificationShowing = showing
        if (!XposedOwnSP.config.automateFocusedNotice || !isMusicPlaying) return
        if (showing) {
            hideLyric()
        } else if (pendingLyric.isNotEmpty()) {
            showLyric(pendingLyric, pendingDelay)
        }
    }

    private fun hideFocusedNotificationIfNeeded() {
        if (!XposedOwnSP.config.automateFocusedNotice || !focusedNotificationShowing) return
        val controller = focusedNotificationController ?: return
        val icon = findObjectField(controller, "mIcon") ?: return
        val content = findObjectField(controller, "mContent") ?: return
        runCatching {
            callWithArgs(controller, "cancelFolme")
            callWithArgs(controller, "hideImmediately", icon)
            callWithArgs(controller, "hideImmediately", content)
            callWithArgs(controller, "setIsFocusedNotifPromptShowing", false)
            focusedNotificationShowing = false
        }.onFailure { throwable ->
            module.log(android.util.Log.INFO, TAG, "API101 focused notification hide unavailable", throwable)
        }
    }

    private fun observeSystemIconsVisibility(view: View?, visibility: Int) {
        if (view == null) return
        if (systemIconsContainer == null) {
            val name = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
            if (name == "system_icons") systemIconsContainer = view
        }
        if (view !== systemIconsContainer || !isMusicPlaying) return
        if (visibility == View.VISIBLE && pendingLyric.isNotEmpty()) {
            showLyric(pendingLyric, pendingDelay)
        } else if (visibility != View.VISIBLE) {
            hideLyric()
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

            val attachMethod = findMethod(targetClass, "onAttachedToWindow", 0)
                ?: error("onAttachedToWindow not found on $className")
            val detachMethod = findMethod(targetClass, "onDetachedFromWindow", 0)
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
        val match = targetViewMatcher.match(source, currentTargetViewSpec()) ?: return

        mainHandler.post {
            runCatching {
                val parent = match.parent
                val existingParent = lyricLayout?.parent as? ViewGroup
                if (existingParent != null && existingParent !== parent) {
                    existingParent.removeView(lyricLayout)
                }

                val viewToMount = lyricLayout ?: createLyricLayout(parent.context, source).also {
                    lyricLayout = it
                }
                applyConfiguration(source)
                lyricDisplayState.bindClock(source, XposedOwnSP.config.hideTime)

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
                source.post {
                    if (mountedTarget === source) {
                        applyConfiguration(source)
                    }
                }
                if (isMusicPlaying && lastBase64Icon.isNotBlank()) {
                    updateIcon(lastBase64Icon, force = true)
                }
                if (isMusicPlaying && pendingLyric.isNotEmpty()) {
                    showLyric(pendingLyric, pendingDelay)
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

    private fun createLyricLayout(context: Context, source: TextView): LinearLayout {
        val icon = ImageView(context).apply { visibility = View.GONE }
        val lyric = object : LyricSwitchView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                applyGradient(this, currentAppearanceSnapshot())
            }
        }.apply {
            visibility = View.VISIBLE
            setSingleLine(true)
            setMaxLines(1)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(icon)
            addView(lyric)
            iconView = icon
            lyricView = lyric
            applyLyricAppearance(lyric, source)
        }
    }

    private fun onTargetViewDetached(candidate: Any?, parentBeforeDetach: ViewGroup?) {
        val view = candidate as? View ?: return
        lyricDisplayState.unbindClock(view)
        val parent = targetViewMatcher.forget(view, parentBeforeDetach)

        if (mountedTarget !== view) return
        mountedTarget = null
        mountedParent = null
        mainHandler.post {
            runCatching {
                lyricLayout?.let { mountedView ->
                    if (mountedView.parent === parent) {
                        parent?.removeView(mountedView)
                    } else {
                        (mountedView.parent as? ViewGroup)?.removeView(mountedView)
                    }
                    mountedView.visibility = View.GONE
                    lyricView?.stopAllScroll()
                }
            }.onFailure { throwable ->
                module.log(android.util.Log.WARN, TAG, "API101 lyric TextView detach cleanup failed", throwable)
            }
        }
    }

    private fun currentTargetViewSpec(): TargetViewSpec {
        val config = XposedOwnSP.config
        return TargetViewSpec(
            textViewClassName = config.textViewClassName,
            textViewId = config.textViewId,
            parentViewClassName = config.parentViewClassName,
            parentViewId = config.parentViewId,
            expectedTextSizePx = config.textSize,
            targetIndex = config.index
        )
    }

    private fun applyLyricAppearance(
        target: LyricSwitchView,
        source: TextView,
        appearance: RuntimeAppearanceSnapshot
    ) {
        target.setSingleLine(true)
        target.setMaxLines(1)

        val lyricSize = if (appearance.lyricSizePx > 0) {
            appearance.lyricSizePx.toFloat()
        } else {
            source.textSize
        }
        if (lyricSize > 0f) {
            target.setTextSize(TypedValue.COMPLEX_UNIT_PX, lyricSize)
        }

        target.setTextColor(
            appearance.lyricColor ?: lyricDisplayState.resolveTextColor(
                source.currentTextColor,
                appearance.usesDynamicLyricColor
            )
        )
        target.setLinearGradient(null)
        target.setLetterSpacings(appearance.lyricLetterSpacingOverride ?: source.letterSpacing)
        target.setStrokeWidth(appearance.lyricStrokeWidth)
        applyBackground(target, appearance.lyricBackgroundColors, appearance.lyricBackgroundRadius)
        applyGradient(target, appearance)
        applyTypeface(target, source.typeface)
    }

    private fun applyBackground(target: LyricSwitchView, colors: List<Int>, radius: Int) {
        target.setBackgroundColor(Color.TRANSPARENT)
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

    private fun applyGradient(target: LyricSwitchView, appearance: RuntimeAppearanceSnapshot) {
        val colors = appearance.lyricGradientColors
        if (colors.size < 2 || target.width <= 0) {
            if (appearance.hasLyricGradient && colors.size == 1) {
                target.setTextColor(colors[0])
            }
            return
        }
        target.setLinearGradient(
            LinearGradient(
                0f,
                0f,
                target.width.toFloat(),
                0f,
                colors.toIntArray(),
                null,
                Shader.TileMode.CLAMP
            )
        )
    }

    private fun applyTypeface(target: LyricSwitchView, fallback: Typeface) {
        val filesDir = mountedParent?.context?.filesDir ?: return target.setTypeface(fallback)
        target.setTypeface(typefaceFileCache.resolve(File(filesDir, "font"), fallback))
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
        val appearance = currentAppearanceSnapshot()
        (params as? ViewGroup.MarginLayoutParams)?.setMargins(
            appearance.lyricStartMargin,
            appearance.lyricTopMargin,
            appearance.lyricEndMargin,
            appearance.lyricBottomMargin
        )
        return params
    }

    private fun updateMountedLayoutMargins(appearance: RuntimeAppearanceSnapshot) {
        val layout = lyricLayout ?: return
        val params = layout.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (
            params.leftMargin == appearance.lyricStartMargin &&
            params.topMargin == appearance.lyricTopMargin &&
            params.rightMargin == appearance.lyricEndMargin &&
            params.bottomMargin == appearance.lyricBottomMargin
        ) {
            return
        }
        params.setMargins(
            appearance.lyricStartMargin,
            appearance.lyricTopMargin,
            appearance.lyricEndMargin,
            appearance.lyricBottomMargin
        )
        layout.layoutParams = params
    }

    private fun applyConfiguration(source: TextView? = mountedTarget as? TextView) {
        val clock = source ?: return
        val lyric = lyricView ?: return
        val appearance = currentAppearanceSnapshot()
        val key = AppliedAppearanceKey(
            appearance = appearance,
            sourceTextSize = clock.textSize,
            sourceTextColor = clock.currentTextColor,
            sourceLetterSpacing = clock.letterSpacing,
            sourceTypefaceIdentity = System.identityHashCode(clock.typeface),
            sourceHeight = clock.height,
            mountedParentIdentity = System.identityHashCode(mountedParent)
        )

        if (appliedAppearanceKey != key) {
            updateMountedLayoutMargins(appearance)
            applyLyricAppearance(lyric, clock, appearance)
            lyric.setScrollSpeed(appearance.lyricSpeed)
            lyric.inAnimation = LyricViewTools.switchViewInAnima(
                if (appearance.lyricAnimation == 11) randomAnima else appearance.lyricAnimation,
                appearance.lyricInterpolator,
                appearance.animationDurationMillis
            )
            lyric.outAnimation = LyricViewTools.switchViewOutAnima(
                appearance.lyricAnimation,
                appearance.animationDurationMillis
            )
            if (isHyperOS && appearance.hyperTextureEnabled) {
                runCatching {
                    lyricLayout?.setBackgroundBlur(
                        appearance.hyperTextureRadius,
                        cornerRadius(appearance.hyperTextureCorner.toFloat()),
                        arrayOf(
                            intArrayOf(106, appearance.hyperTextureBackgroundColor),
                            intArrayOf(3, appearance.hyperTextureBackgroundColor)
                        )
                    )
                }.onFailure { throwable ->
                    module.log(android.util.Log.INFO, TAG, "API101 HyperOS texture unavailable", throwable)
                }
            }

            iconView?.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(
                        appearance.iconStartMargin,
                        appearance.iconTopMargin,
                        0,
                        appearance.iconBottomMargin
                    )
                    val size = if (appearance.iconSizePx == 0) clock.height / 2 else appearance.iconSizePx
                    width = size
                    height = size
                }
                setColorFilter(
                    appearance.iconColor ?: clock.currentTextColor,
                    PorterDuff.Mode.SRC_IN
                )
                setBackgroundColor(appearance.iconBackgroundColor)
            }
            appliedAppearanceKey = key
        }

        iconView?.visibility = if (!appearance.iconEnabled || lastBase64Icon.isEmpty()) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun currentAppearanceSnapshot(): RuntimeAppearanceSnapshot {
        return appearanceSnapshot ?: RuntimeAppearanceSnapshot.from(XposedOwnSP.config).also {
            appearanceSnapshot = it
        }
    }

    private fun refreshAppearanceSnapshot() {
        val updated = RuntimeAppearanceSnapshot.from(XposedOwnSP.config)
        if (appearanceSnapshot != updated) {
            appearanceSnapshot = updated
            appliedAppearanceKey = null
        }
    }

    private fun showLyric(lyric: String, delay: Int) {
        if (lyric.isEmpty()) return
        if (XposedOwnSP.config.hideLyricWhenLockScreen && isScreenLocked) return
        val layout = lyricLayout ?: return
        val lyricDisplay = lyricView ?: return
        val parent = mountedParent ?: return

        hideFocusedNotificationIfNeeded()
        lyricDisplayState.updateLyricVisibility(show = true, hideTime = XposedOwnSP.config.hideTime)
        layout.cancelAnimation()
        layout.visibility = View.VISIBLE
        lyricShowing = true
        syncSystemUiVisibilityOverrides()

        val measuredTextWidth = lyricDisplay.measureText(lyric).toInt()
        val width = getLyricWidth(measuredTextWidth, parent)
        lyricDisplay.setWidth(width)
        val overflow = measuredTextWidth - width
        if (overflow > 0 && width > 0) {
            val speed = when {
                delay > 0 -> {
                    (0.3f + (overflow.toFloat() / width) * (5f / (delay / 1000f))).coerceIn(0.3f, 5f)
                }

                currentAppearanceSnapshot().dynamicLyricSpeed -> 10f * overflow / width + 0.7f
                else -> currentAppearanceSnapshot().lyricSpeed
            }
            lyricDisplay.setScrollSpeed(speed)
        }
        lyricDisplay.stopAllScroll()
        lyricDisplay.setText(lyric)
    }

    private fun hideLyric() {
        titleDisplayTask.cancel()
        lyricDisplayState.updateLyricVisibility(show = false, hideTime = false)
        lyricShowing = false
        lyricLayout?.hideView(false)
        lyricView?.apply {
            stopAllScroll()
            setText("")
        }
        titleDialog?.hideTitle()
        visibilityOverrides.restoreAll()
    }

    private fun syncSystemUiVisibilityOverrides() {
        val config = XposedOwnSP.config
        if (config.hideNotificationIcon) {
            visibilityOverrides.apply(notificationIconArea, View.GONE)
        } else {
            visibilityOverrides.restore(notificationIconArea)
        }

        if (config.hideTime) {
            if (config.mMiuiPadOptimize) {
                visibilityOverrides.apply(miuiPadClockView, View.GONE)
            } else {
                visibilityOverrides.restore(miuiPadClockView)
            }
            visibilityOverrides.apply(miuiNotificationBigTime, View.GONE)
        } else {
            visibilityOverrides.restore(miuiPadClockView)
            visibilityOverrides.restore(miuiNotificationBigTime)
        }

        if (config.mMiuiHideNetworkSpeed) {
            visibilityOverrides.apply(miuiNetworkSpeedView, View.GONE)
        } else {
            visibilityOverrides.restore(miuiNetworkSpeedView)
        }

        if (config.hideCarrier) {
            visibilityOverrides.apply(miuiCarrierLabel, View.GONE)
        } else {
            visibilityOverrides.restore(miuiCarrierLabel)
        }
    }

    private fun getLyricWidth(textWidth: Int, parent: ViewGroup): Int {
        val appearance = currentAppearanceSnapshot()
        val availableWidth = max(
            parent.width - appearance.lyricStartMargin - appearance.lyricEndMargin,
            0
        )
        if (appearance.lyricWidthPercent == 0) return min(textWidth, availableWidth)
        val display = parent.resources.displayMetrics
        val scaleBase = max(display.widthPixels, display.heightPixels)
        val scaledWidth = (appearance.lyricWidthPercent / 100f * scaleBase).toInt()
        return if (appearance.fixedLyricWidth) scaledWidth else min(textWidth, scaledWidth)
    }

    private fun refreshTimeoutRestore() {
        if (!XposedOwnSP.config.timeoutRestore) {
            timeoutRestoreTask.cancel()
            return
        }
        timeoutRestoreTask.schedule(XposedOwnSP.config.timeoutRestoreSeconds * 1000L)
    }

    private fun showTitle(title: String, lyric: String) {
        if (!XposedOwnSP.config.titleSwitch || title.isBlank() ||
            (!XposedOwnSP.config.titleShowWithSameLyric && title == lyric)
        ) {
            pendingTitleToShow = ""
            titleDisplayTask.cancel()
            return
        }
        pendingTitleToShow = title
        titleDisplayTask.schedule(TITLE_DELAY_MILLIS)
    }

    private fun resolveIconBase64(data: SuperLyricData, publisher: String): String {
        if (!XposedOwnSP.config.iconSwitch) return ""
        return XposedOwnSP.config.changeAllIcons.ifEmpty {
            data.base64Icon.orEmpty().ifEmpty { XposedOwnSP.config.getDefaultIcon(publisher) }
        }
    }

    private fun updateIcon(base64Icon: String, force: Boolean = false) {
        if (!force && base64Icon == lastBase64Icon) return
        lastBase64Icon = base64Icon
        val generation = iconDecodeGeneration.incrementAndGet()
        val icon = iconView
        if (!XposedOwnSP.config.iconSwitch || base64Icon.isBlank()) {
            icon?.visibility = View.GONE
            return
        }
        if (icon == null || mountedTarget == null) return

        iconDecodeExecutor.execute {
            val bitmap = IconBitmapDecoder.decode(base64Icon)
            mainHandler.post {
                if (
                    generation != iconDecodeGeneration.get() ||
                    lastBase64Icon != base64Icon ||
                    !isMusicPlaying
                ) {
                    bitmap?.recycle()
                    return@post
                }

                val currentIcon = iconView
                if (currentIcon == null || mountedTarget == null) {
                    bitmap?.recycle()
                    return@post
                }
                if (bitmap == null) {
                    currentIcon.visibility = View.GONE
                } else {
                    currentIcon.setImageBitmap(bitmap)
                    currentIcon.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun onClockVisibilityRequested(view: View?, requestedVisibility: Int): Boolean {
        observeSystemIconsVisibility(view, requestedVisibility)
        return visibilityOverrides.onVisibilityRequested(
            view = view,
            requestedVisibility = requestedVisibility,
            keepHiddenOverride = XposedOwnSP.config.limitVisibilityChange && lyricShowing
        ) == View.GONE
    }

    private fun onDarkIntensityApplied(dispatcher: Any?) {
        val tint = findIntField(dispatcher, "mIconTint") ?: return
        mainHandler.post {
            runCatching {
                val appearance = currentAppearanceSnapshot()
                lyricDisplayState.updateDynamicTint(
                    lyricView = lyricView,
                    tint = tint,
                    useDynamicColor = appearance.usesDynamicLyricColor
                )
                if (appearance.iconColor == null) {
                    iconView?.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
                }
            }.onFailure { throwable ->
                module.log(android.util.Log.WARN, TAG, "API101 dynamic lyric color update failed", throwable)
            }
        }
    }

    private fun findObjectField(instance: Any?, name: String): Any? {
        return instance?.let { getFieldValue(it, name) }
    }

    private fun findIntField(instance: Any?, name: String): Int? {
        return instance?.let { getIntFieldValue(it, name) }
    }

    private data class AppliedAppearanceKey(
        val appearance: RuntimeAppearanceSnapshot,
        val sourceTextSize: Float,
        val sourceTextColor: Int,
        val sourceLetterSpacing: Float,
        val sourceTypefaceIdentity: Int,
        val sourceHeight: Int,
        val mountedParentIdentity: Int
    )

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

    private class ClockVisibilityHooker(
        private val owner: Api101SystemUIHook
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val requestedVisibility = chain.getArg(0) as? Int ?: return chain.proceed()
            return if (owner.onClockVisibilityRequested(chain.getThisObject() as? View, requestedVisibility)) {
                chain.proceed(arrayOf(View.GONE))
            } else {
                chain.proceed()
            }
        }
    }

    private class DarkIntensityHooker(
        private val owner: Api101SystemUIHook
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            owner.onDarkIntensityApplied(chain.getThisObject())
            return result
        }
    }

    private class NotificationAreaHooker(
        private val owner: Api101SystemUIHook
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            owner.captureNotificationIconArea(chain.getThisObject())
            return result
        }
    }

    private class StatusBarTouchHooker(
        private val owner: Api101SystemUIHook
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val event = chain.getArg(0) as? MotionEvent ?: return chain.proceed()
            return if (owner.onStatusBarTouch(event)) true else chain.proceed()
        }
    }

    private class XiaomiViewCaptureHooker(
        private val owner: Api101SystemUIHook,
        private val kind: XiaomiViewKind
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            owner.captureXiaomiView(chain.getThisObject(), kind)
            return result
        }
    }

    private class XiaomiPadClockHooker(
        private val owner: Api101SystemUIHook
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            owner.captureXiaomiPadClock(chain.getThisObject())
            return result
        }
    }

    private class XiaomiNotificationClockHooker(
        private val owner: Api101SystemUIHook
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            owner.captureXiaomiNotificationClock(chain.getThisObject())
            return result
        }
    }

    private class XiaomiNetworkVisibilityHooker(
        private val owner: Api101SystemUIHook
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            return if (owner.shouldHideXiaomiNetworkSpeed()) {
                chain.proceed(arrayOf(false))
            } else {
                chain.proceed()
            }
        }
    }

    private class FocusNotificationHooker(
        private val owner: Api101SystemUIHook
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            owner.onFocusNotificationEvaluated(chain.getThisObject(), result as? Boolean ?: false)
            return result
        }
    }

    private enum class XiaomiViewKind {
        NETWORK_SPEED,
        CARRIER
    }

    private companion object {
        const val TAG = "StatusBarLyric/API101"
        const val DARK_ICON_DISPATCHER_CLASS = "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl"
        const val PHONE_STATUS_BAR_VIEW_CLASS = "com.android.systemui.statusbar.phone.PhoneStatusBarView"
        const val NOTIFICATION_ICON_AREA_CONTROLLER_CLASS = "com.android.systemui.statusbar.phone.NotificationIconAreaController"
        const val COLLAPSED_STATUS_BAR_FRAGMENT_CLASS = "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment"
        const val MIUI_NETWORK_SPEED_CLASS = "com.android.systemui.statusbar.views.NetworkSpeedView"
        const val MIUI_COLLAPSED_STATUS_BAR_FRAGMENT_CLASS = "com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment"
        const val KEYGUARD_STATUS_BAR_VIEW_CLASS = "com.android.systemui.statusbar.phone.KeyguardStatusBarView"
        const val MIUI_NOTIFICATION_CALLBACK_CLASS = "com.android.systemui.controlcenter.shade.NotificationHeaderExpandController\$notificationCallback\$1"
        const val FOCUSED_NOTIFICATION_CONTROLLER_CLASS = "com.android.systemui.statusbar.phone.FocusedNotifPromptController"
        const val TITLE_DELAY_MILLIS = 800L
        const val CONFIG_REFRESH_DEBOUNCE_MILLIS = 32L
        const val LONG_CLICK_MILLIS = 500L
        const val TOUCH_MOVE_THRESHOLD = 50f
    }
}
