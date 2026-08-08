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

package statusbar.lyric.hook.module

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClassOrNull
import com.github.kyuubiran.ezxhelper.EzXHelper.moduleRes
import com.github.kyuubiran.ezxhelper.HookFactory
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.ObjectHelper.Companion.objectHelper
import com.github.kyuubiran.ezxhelper.finders.ConstructorFinder.`-Static`.constructorFinder
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import statusbar.lyric.R
import statusbar.lyric.config.XposedOwnSP.config
import statusbar.lyric.hook.BaseHook
import statusbar.lyric.hook.module.xiaomi.FocusNotifyController
import statusbar.lyric.hook.module.xiaomi.XiaomiHooks
import statusbar.lyric.tools.BlurTools.cornerRadius
import statusbar.lyric.tools.BlurTools.setBackgroundBlur
import statusbar.lyric.tools.LogTools
import statusbar.lyric.tools.LogTools.log
import statusbar.lyric.tools.LyricViewTools
import statusbar.lyric.tools.LyricViewTools.cancelAnimation
import statusbar.lyric.tools.LyricViewTools.hideView
import statusbar.lyric.tools.LyricViewTools.randomAnima
import statusbar.lyric.tools.LyricViewTools.showView
import statusbar.lyric.tools.Tools.callMethod
import statusbar.lyric.tools.Tools.existField
import statusbar.lyric.tools.Tools.getObjectField
import statusbar.lyric.tools.Tools.getObjectFieldIfExist
import statusbar.lyric.tools.Tools.goMainThread
import statusbar.lyric.tools.Tools.ifNotNull
import statusbar.lyric.tools.Tools.isNot
import statusbar.lyric.tools.Tools.isNotNull
import statusbar.lyric.runtime.InternalBroadcasts
import statusbar.lyric.runtime.LyricLayoutCalculator
import statusbar.lyric.runtime.LyricRuntimeController
import statusbar.lyric.runtime.LyricRuntimeEvent
import statusbar.lyric.runtime.LyricRuntimePolicy
import statusbar.lyric.runtime.LyricRuntimeResult
import statusbar.lyric.runtime.TrackIdentity
import statusbar.lyric.runtime.StatusBarGesture
import statusbar.lyric.runtime.SystemUiVisibilityPolicy
import statusbar.lyric.runtime.StatusBarGestureDetector
import statusbar.lyric.runtime.TargetViewMatcher
import statusbar.lyric.runtime.TargetViewSpec
import statusbar.lyric.runtime.ViewVisibilityOverrideState
import statusbar.lyric.runtime.icon.SharedLyricIconBitmapCache
import statusbar.lyric.runtime.input.MediaKeyDispatcher
import statusbar.lyric.runtime.scheduler.ResettableHandlerTask
import statusbar.lyric.runtime.style.RuntimeAppearanceSnapshot
import statusbar.lyric.runtime.style.LyricAppearanceApplier
import statusbar.lyric.tools.Tools.observableChange
import statusbar.lyric.tools.XiaomiUtils.isHyperOS
import statusbar.lyric.view.LyricSwitchView
import statusbar.lyric.view.TitleDialog
import java.io.File
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

class SystemUILyric : BaseHook() {
    private val context: Context by lazy { AndroidAppHelper.currentApplication() }

    private val runtimeController = LyricRuntimeController(config.masterSwitch)
    private val runtimeState get() = runtimeController.state
    private var lastColor: Int by observableChange(Color.WHITE) { oldValue, newValue ->
        if (oldValue == newValue) return@observableChange
        LogTools.log { "Changing Color: $newValue" }
        goMainThread {
            val appearance = currentAppearanceSnapshot()
            if (appearance.usesDynamicLyricColor) {
                lyricView.setTextColor(newValue)
            }
            if (appearance.iconColor == null) {
                iconView.setColorFilter(newValue, PorterDuff.Mode.SRC_IN)
            }
        }
    }
    private var title: String by observableChange("") { _, newValue ->
        if (!config.titleShowWithSameLyric && runtimeState.lyric == newValue) return@observableChange
        goMainThread {
            titleDialog.apply {
                if (newValue.isEmpty()) {
                    hideTitle()
                } else {
                    showTitle(newValue.trim())
                }
            }
        }
    }
    private var lastBase64Icon: String by observableChange("") { _, newValue ->
        iconDecodeHandler.post {
            val bitmap = SharedLyricIconBitmapCache.instance.getOrDecode(newValue)
            goMainThread {
                if (lastBase64Icon != newValue) {
                    return@goMainThread
                }
                bitmap.isNotNull {
                    iconView.showView()
                    iconView.setImageBitmap(it)
                }.isNot {
                    iconView.hideView()
                }
                "Changing Icon".log()
            }
        }
    }
    private var canLoad: Boolean = true
    private var isScreenLocked: Boolean = false
    private val runtimeEnabled: Boolean get() = runtimeController.enabled
    private var iconSwitch: Boolean = config.iconSwitch

    val isMusicPlaying: Boolean
        get() = runtimeState.isPlaying

    @Volatile
    var isHiding: Boolean = false
    private var isRandomAnima: Boolean = false
    private var autoHideController: Any? = null
    private val isReady: Boolean get() = this@SystemUILyric::clockView.isInitialized

    private var fullscreenModeType: Int = -1
    private val iconDecodeThread: HandlerThread by lazy {
        HandlerThread("StatusBarLyric-IconDecode").apply { start() }
    }
    private val iconDecodeHandler: Handler by lazy { Handler(iconDecodeThread.looper) }
    private val statusBarGestureDetector = StatusBarGestureDetector()


    private lateinit var clockView: TextView
    private lateinit var targetView: ViewGroup


    private val lyricView: LyricSwitchView by lazy {
        object : LyricSwitchView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                val appearance = currentAppearanceSnapshot()
                if (appearance.hasLyricGradient) {
                    val colors = appearance.lyricGradientColors
                    if (colors.isEmpty()) {
                        setTextColor(Color.WHITE)
                    } else if (colors.size < 2) {
                        setTextColor(colors[0])
                    } else {
                        val textShader = LinearGradient(
                            0f, 0f, width.toFloat(),
                            0f, colors.toIntArray(), null, Shader.TileMode.CLAMP
                        )
                        setLinearGradient(textShader)
                    }
                }
            }
        }.apply {
            if (!isReady) return@apply
            setTypeface(clockView.typeface)
            setSingleLine(true)
            setMaxLines(1)
        }
    }
    private val iconView: ImageView by lazy {
        ImageView(context).apply {
            visibility = View.GONE
        }
    }
    private val lyricLayout: LinearLayout by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(iconView)
            addView(lyricView)
            visibility = View.GONE
        }
    }
    private val titleDialog by lazy {
        TitleDialog(context)
    }

    //////////////////////////////钩子逻辑//////////////////////////////////////
    private var defaultDisplay: Any? = null
    private var centralSurfacesImpl: Any? = null
    private var notificationIconAreaRef: WeakReference<View>? = null
    private var notificationIconArea: View?
        get() = notificationIconAreaRef?.get()
        set(value) { notificationIconAreaRef = value?.let(::WeakReference) }
    private var statusBatteryContainerRef: WeakReference<View>? = null
    private var statusBatteryContainer: View?
        get() = statusBatteryContainerRef?.get()
        set(value) { statusBatteryContainerRef = value?.let(::WeakReference) }
    private val targetViewMatcher = TargetViewMatcher()
    private val visibilityOverrides = ViewVisibilityOverrideState()
    private val lyricAppearanceApplier = LyricAppearanceApplier()
    private var appearanceSnapshot: RuntimeAppearanceSnapshot? = null
    private val mediaKeyDispatcher by lazy { MediaKeyDispatcher(context) }
    private val observedTargetViews = Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    private val targetAttachStateListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            (view as? TextView)?.let(::onTargetViewAttached)
        }

        override fun onViewDetachedFromWindow(view: View) {
            onTargetViewDetached(view)
        }
    }

    @SuppressLint("DiscouragedApi", "NewApi")
    override fun init() {
        "Initializing Hook".log()
        if (config.limitVisibilityChange) {
            moduleRes.getString(R.string.limit_visibility_change).log()
        }
        Application::class.java.methodFinder().filterByName("attach").single().createHook {
            after { hook ->
                registerSuperLyric(hook.args[0] as Context)
            }
        }

        loadClassOrNull(config.textViewClassName).isNotNull { targetClass ->
            targetClass.declaredConstructors.forEach { constructor ->
                constructor.createHook {
                    after { hookParam ->
                        val view = hookParam.thisObject as? TextView ?: return@after
                        observeTargetView(view)
                    }
                }
            }

            View::class.java.methodFinder().filterByName("setVisibility").single()
                .createHook {
                    before { param ->
                        val view = param.thisObject as View
                        val requestedVisibility = param.args[0] as? Int ?: return@before
                        visibilityOverrides.onVisibilityRequested(
                            view = view,
                            requestedVisibility = requestedVisibility,
                            keepHiddenOverride = config.limitVisibilityChange && isMusicPlaying && !isHiding
                        )?.let { forcedVisibility ->
                            param.args[0] = forcedVisibility
                        }

                        if (statusBatteryContainer.isNotNull()) {
                            if (statusBatteryContainer != view) return@before
                            if (!isMusicPlaying) return@before

                            val visibility = param.args[0] == View.VISIBLE
                            if (visibility) {
                                updateLyricState()
                            } else {
                                updateLyricState(showLyric = false)
                            }
                        } else {
                            val idName =
                                runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
                            if (idName.isNotNull() && idName == "system_icons") {
                                statusBatteryContainer = view
                            }
                        }
                    }
                }
        }.isNot {
            moduleRes.getString(R.string.load_class_empty).log()
            return
        }

        // 状态栏图标颜色更改
        loadClassOrNull("com.android.systemui.statusbar.phone.DarkIconDispatcherImpl").isNotNull {
            it.methodFinder().filterByName("applyDarkIntensity").filterNonAbstract().single().createHook {
                after { hookParam ->
                    if (!isMusicPlaying) return@after

                    val mIconTint =
                        hookParam.thisObject.objectHelper().getObjectOrNullAs<Int>("mIconTint")
                    lastColor = mIconTint ?: Color.BLACK
                }
            }
        }

        if (config.hideNotificationIcon) {
            moduleRes.getString(R.string.hide_notification_icon).log()
            fun HookFactory.hideNoticeIcon(mode: Int) {
                after { hookParam ->
                    val clazz = hookParam.thisObject::class.java
                    val name =
                        if (mode == 0) "NotificationIconAreaController" else "CollapsedStatusBarFragment"
                    val method =
                        if (mode == 0) "mNotificationIconArea" else "mNotificationIconAreaInner"
                    if (clazz.simpleName == name) {
                        hookParam.thisObject.objectHelper {
                            notificationIconArea = this.getObjectOrNullAs<View>(method)!!
                        }
                    } else {
                        notificationIconArea =
                            clazz.superclass.getField(method).get(hookParam.thisObject) as View
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                loadClassOrNull("com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment").isNotNull {
                    it.methodFinder().filterByName("onViewCreated").single().createHook {
                        hideNoticeIcon(1)
                    }
                }
            } else {
                loadClassOrNull("com.android.systemui.statusbar.phone.NotificationIconAreaController").isNotNull {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.constructorFinder().single().createHook {
                            hideNoticeIcon(0)
                        }
                    } else {
                        it.methodFinder().filterByName("initializeNotificationAreaViews").single()
                            .createHook {
                                hideNoticeIcon(0)
                            }
                    }
                }
            }
        }

        // 触摸监听
        loadClassOrNull("com.android.systemui.statusbar.phone.PhoneStatusBarView").isNotNull {
            it.methodFinder().filterByName("onTouchEvent").single().createHook {
                before { hookParam ->
                    val motionEvent = hookParam.args[0] as MotionEvent
                    if (!isMusicPlaying) {
                        statusBarGestureDetector.reset()
                        return@before
                    }

                    when (
                        statusBarGestureDetector.onTouchEvent(
                            event = motionEvent,
                            swipeXThresholdPx = config.slideStatusBarCutSongsXRadius.toFloat(),
                            swipeYRadiusPx = config.slideStatusBarCutSongsYRadius.toFloat()
                        )
                    ) {
                        StatusBarGesture.SwipeNext -> {
                            if (!config.slideStatusBarCutSongs || isHiding) return@before
                            moduleRes.getString(R.string.slide_status_bar_cut_songs).log()
                            mediaKeyDispatcher.next()
                            hookParam.result = true
                        }

                        StatusBarGesture.SwipePrevious -> {
                            if (!config.slideStatusBarCutSongs || isHiding) return@before
                            moduleRes.getString(R.string.slide_status_bar_cut_songs).log()
                            mediaKeyDispatcher.previous()
                            hookParam.result = true
                        }

                        StatusBarGesture.LongPress -> {
                            if (!config.longClickStatusBarStop || isHiding) return@before
                            moduleRes.getString(R.string.long_click_status_bar_stop).log()
                            mediaKeyDispatcher.playPause()
                            hookParam.result = true
                        }

                        StatusBarGesture.Tap -> {
                            if (!config.clickStatusBarToHideLyric && !FocusNotifyController.isOS2FocusNotifyShowing) {
                                return@before
                            }
                            if (FocusNotifyController.isOS1FocusNotifyShowing) return@before

                            moduleRes.getString(R.string.click_status_bar_to_hide_lyric).log()
                            if (isHiding) {
                                if (FocusNotifyController.canControlFocusNotify() &&
                                    FocusNotifyController.shouldOpenFocusNotify(motionEvent)
                                ) {
                                    "Should open focus notify".log()
                                    return@before
                                }
                                FocusNotifyController.isInteraction = false
                                hookParam.result = true
                                updateLyricState()
                                autoHideStatusBarInFullScreenModeIfNeed()
                            } else if (
                                StatusBarGestureDetector.containsRawPoint(
                                    lyricLayout,
                                    motionEvent.rawX,
                                    motionEvent.rawY
                                )
                            ) {
                                FocusNotifyController.isInteraction = true
                                hookParam.result = true
                                updateLyricState(showLyric = false)
                                autoHideStatusBarInFullScreenModeIfNeed()
                            }
                            LogTools.log { "Change to hide LyricView: $isHiding" }
                        }

                        StatusBarGesture.None -> Unit
                    }
                }
            }
        }

        // 屏幕状态
        loadClassOrNull("com.android.systemui.statusbar.phone.CentralSurfacesImpl").isNotNull {
            it.constructorFinder().singleOrNull().ifNotNull { constructor ->
                constructor.createHook {
                    after { hook ->
                        centralSurfacesImpl = hook.thisObject
                        autoHideController = hook.thisObject.getObjectField("mAutoHideController")
                        val mStatusBarModeRepository = hook.thisObject.getObjectFieldIfExist("mStatusBarModeRepository")
                        defaultDisplay = mStatusBarModeRepository?.getObjectFieldIfExist("defaultDisplay")
                    }
                }
            }
        }

        loadClassOrNull("com.android.systemui.SystemUIApplication").isNotNull { clazz ->
            clazz.methodFinder().filterByName("onConfigurationChanged").single().createHook {
                after { hookParam ->
                    "onConfigurationChanged".log()
                    val newConfig = hookParam.args[0] as Configuration

                    if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                        newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
                    ) {
                        if (!isReady) return@after
                        updateLyricState()
                    }
                }
            }
        }

        XiaomiHooks.init(this)
    }

    private fun lyricInit() {
        goMainThread(1) {
            "LyricView init".log()
            runCatching { (lyricLayout.parent as ViewGroup).removeView(lyricLayout) }
            if (config.viewLocation == 0) {
                targetView.addView(lyricLayout, 0)
            } else {
                targetView.addView(lyricLayout)
            }
            val appearance = currentAppearanceSnapshot()
            if (isHyperOS && appearance.hyperTextureEnabled) {
                val blurRadio = appearance.hyperTextureRadius
                val cornerRadius = cornerRadius(appearance.hyperTextureCorner.toFloat())
                val blendModes = arrayOf(
                    intArrayOf(106, appearance.hyperTextureBackgroundColor),
                    intArrayOf(3, appearance.hyperTextureBackgroundColor)
                )
                lyricLayout.setBackgroundBlur(blurRadio, cornerRadius, blendModes)
            }
        }

        updateConfig(1)
    }

    private var statusBarShowing: Boolean = true

    // 适合考虑状态的更新
    fun updateLyricState(showLyric: Boolean = true, showFocus: Boolean = true, delay: Int = 0) {
        if (
            isInFullScreenMode() &&
            (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
        ) {
            if (statusBarShowing && showLyric && canShowLyric()) {
                showLyric(runtimeState.lyric, delay)
                FocusNotifyController.hideFocusNotifyIfNeed()
                "StatusBar state is showing".log()
            } else {
                hideLyric()
                if (showFocus)
                    FocusNotifyController.showFocusNotifyIfNeed()
                if (!statusBarShowing) "StatusBar state is hiding".log()
            }
        } else {
            if (showLyric && canShowLyric()) {
                showLyric(runtimeState.lyric, delay)
                FocusNotifyController.hideFocusNotifyIfNeed()
            } else {
                hideLyric()
                if (showFocus)
                    FocusNotifyController.showFocusNotifyIfNeed()
            }
        }
    }

    private fun observeTargetView(view: TextView) {
        val added = synchronized(observedTargetViews) { observedTargetViews.add(view) }
        if (!added) return
        view.addOnAttachStateChangeListener(targetAttachStateListener)
        if (view.isAttachedToWindow) {
            onTargetViewAttached(view)
        }
    }

    private fun onTargetViewAttached(view: TextView) {
        if (!canLoad) return
        val match = targetViewMatcher.match(view, currentTargetViewSpec()) ?: return
        val parent = match.parent as? LinearLayout ?: return
        clockView = view
        targetView = parent.apply { gravity = Gravity.CENTER }
        canLoad = false
        lyricInit()
    }

    private fun onTargetViewDetached(view: View) {
        targetViewMatcher.forget(view, view.parent as? ViewGroup)
        visibilityOverrides.forget(view)
        if (!isReady || clockView !== view) return
        "Running onDetachedFromWindow".log()
        canLoad = true
        updateLyricState(showLyric = false, showFocus = false)
    }

    private fun currentTargetViewSpec() = TargetViewSpec(
        textViewClassName = config.textViewClassName,
        textViewId = config.textViewId,
        parentViewClassName = config.parentViewClassName,
        parentViewId = config.parentViewId,
        expectedTextSizePx = config.textSize,
        targetIndex = config.index
    )

    fun applyVisibilityOverride(view: View?, visibility: Int) {
        visibilityOverrides.apply(view, visibility)
    }

    private fun canShowLyric(): Boolean {
        return runtimeEnabled && isMusicPlaying &&
            !FocusNotifyController.isOS1FocusNotifyShowing && !FocusNotifyController.isInteraction
    }

    private fun isInFullScreenMode(): Boolean {
        if (fullscreenModeType == -1) {
            fullscreenModeType = when {
                centralSurfacesImpl.existField("mIsFullscreen") -> 1
                defaultDisplay.existField("isInFullscreenMode") -> 2
                else -> 0
            }
        }

        return runCatching {
            when (fullscreenModeType) {
                1 -> {
                    statusBarShowing = centralSurfacesImpl?.getObjectField("mTransientShown") as Boolean
                    centralSurfacesImpl?.getObjectField("mIsFullscreen") as Boolean
                }

                2 -> {
                    val isTransientShown = defaultDisplay?.getObjectField("isTransientShown")
                    statusBarShowing =
                        isTransientShown?.getObjectField("$\$delegate_0")?.callMethod("getValue") as Boolean

                    val isInFullscreenMode = defaultDisplay?.getObjectField("isInFullscreenMode")
                    isInFullscreenMode?.getObjectField("$\$delegate_0")
                        ?.callMethod("getValue") as Boolean
                }

                else -> false
            }
        }.getOrElse {
            fullscreenModeType = -1
            false
        }
    }

    private fun autoHideStatusBarInFullScreenModeIfNeed() {
        if (autoHideController == null) return
        if (!isInFullScreenMode()) return

        autoHideController!!.callMethod("touchAutoHide")
    }

    private var updateConfig: UpdateConfig = UpdateConfig()
    private var screenLockReceiver: ScreenLockReceiver = ScreenLockReceiver()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingTitlePublisher = ""
    private var pendingTitleData: SuperLyricData? = null
    private val timeoutRestoreTask = ResettableHandlerTask(handler) {
        if (!config.timeoutRestore) return@ResettableHandlerTask
        if (!runtimeController.onTimeout()) return@ResettableHandlerTask
        updateLyricState(showLyric = false)
        "Timeout restore".log()
    }
    private val titleDisplayTask = ResettableHandlerTask(handler) {
        val publisher = pendingTitlePublisher
        val data = pendingTitleData
        pendingTitlePublisher = ""
        pendingTitleData = null
        if (data != null) showTitleIfCurrent(publisher, data)
    }
    private fun showTitleIfCurrent(publisher: String, data: SuperLyricData) {
        if (!isMusicPlaying) return
        if (runtimeState.publisher != publisher) return

        this@SystemUILyric.title = data.title.orEmpty()
    }

    private fun scheduleTitleOnce(publisher: String, data: SuperLyricData) {
        pendingTitlePublisher = publisher
        pendingTitleData = data
        titleDisplayTask.schedule(800L)
    }

    private fun refreshTimeoutRestore() {
        if (!config.timeoutRestore) {
            timeoutRestoreTask.cancel()
            return
        }
        timeoutRestoreTask.schedule(config.timeoutRestoreSeconds * 1000L)
    }

    private fun resolveIconBase64(data: SuperLyricData, publisher: String): String =
        LyricIconResolver.resolve(
            enabled = iconSwitch,
            overrideIcon = config.changeAllIcons,
            eventIcon = data.base64Icon,
            defaultIcon = { config.getDefaultIcon(publisher) }
        )

    private fun handleSuperLyricStop(packageName: String) {
        if (!isReady || !runtimeController.onStop(packageName)) return

        pendingTitlePublisher = ""
        pendingTitleData = null
        titleDisplayTask.cancel()
        timeoutRestoreTask.cancel()
        updateLyricState(showLyric = false)
    }

    private fun handleSuperLyric(packageName: String, data: SuperLyricData) {
        if (!runtimeEnabled || !isReady) return

        val lyricLine = data.lyric ?: return
        val lyric = lyricLine.text
        if (lyric.isEmpty()) return

        val delay = lyricLine.delay.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val trackIdentity = TrackIdentity(
            title = data.title.orEmpty(),
            artist = data.artist.orEmpty(),
            album = data.album.orEmpty()
        )
        val incomingIcon = resolveIconBase64(data, packageName)
        when (
            val result = runtimeController.onLyric(
                event = LyricRuntimeEvent(
                    publisher = packageName,
                    lyric = lyric,
                    delayMillis = delay,
                    track = trackIdentity,
                    iconSource = incomingIcon
                ),
                policy = LyricRuntimePolicy(
                    includeTrackInIdentity = config.titleSwitch,
                    includeIconInIdentity = iconSwitch
                )
            )
        ) {
            LyricRuntimeResult.Disabled -> return
            LyricRuntimeResult.Duplicate -> {
                refreshTimeoutRestore()
                return
            }
            is LyricRuntimeResult.Accepted -> {
                if (config.titleSwitch && result.trackChanged) {
                    scheduleTitleOnce(packageName, data)
                    LogTools.log {
                        "Title: ${trackIdentity.title}, Artist: ${trackIdentity.artist}, Album: ${trackIdentity.album}"
                    }
                }
                changeIcon(incomingIcon)
            }
        }
        updateLyricState(delay = delay)
        refreshTimeoutRestore()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerSuperLyric(context: Context) {
        runCatching {
            SuperLyricHelper.registerReceiver(object : ISuperLyricReceiver.Stub() {
                override fun onStop(publisher: String?, data: SuperLyricData?) {
                    val packageName = publisher.orEmpty()
                    handler.post { handleSuperLyricStop(packageName) }
                }

                override fun onLyric(publisher: String?, data: SuperLyricData?) {
                    val lyricData = data ?: return
                    val packageName = publisher.orEmpty()
                    handler.post { handleSuperLyric(packageName, lyricData) }
                }
            })
        }.onFailure {
            ("Register SuperLyric failed: " + it.message).log()
        }

        val updateConfigFilter = IntentFilter(InternalBroadcasts.ACTION_UPDATE_CONFIG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                updateConfig,
                updateConfigFilter,
                InternalBroadcasts.PERMISSION_INTERNAL_CONTROL,
                null,
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                updateConfig,
                updateConfigFilter,
                InternalBroadcasts.PERMISSION_INTERNAL_CONTROL,
                null
            )
        }

        val screenLockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                screenLockReceiver,
                screenLockFilter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(screenLockReceiver, screenLockFilter)
        }

        "Register SuperLyric".log()
    }

    // 适用于直接显示歌词，不需要考虑其他类似焦点通知的状态
    private fun showLyric(lyric: String, delay: Int = 0) {
        if (!runtimeEnabled || !isReady || !isMusicPlaying || lyric.isEmpty() || isScreenLocked) return

        "Showing LyricView".log()
        goMainThread {
            isHiding = false
            lastColor = clockView.currentTextColor
            lyricLayout.cancelAnimation()
            lyricLayout.showView()
            syncSystemUiVisibilityOverrides()

            lyricView.apply {
                val appearance = currentAppearanceSnapshot()
                val layoutResult = LyricLayoutCalculator.calculate(
                    textWidthPx = measureText(lyric).toInt(),
                    parentWidthPx = targetView.width,
                    startMarginPx = appearance.lyricStartMargin,
                    endMarginPx = appearance.lyricEndMargin,
                    widthPercent = appearance.lyricWidthPercent,
                    fixedWidth = appearance.fixedLyricWidth,
                    dynamicSpeed = appearance.dynamicLyricSpeed,
                    baseSpeed = appearance.lyricSpeed,
                    delayMillis = delay
                )
                width = layoutResult.widthPx
                setScrollSpeed(layoutResult.scrollSpeed)
                LogTools.log {
                    "Lyric width: ${layoutResult.widthPx}, overflow: ${layoutResult.overflowPx}, speed: ${layoutResult.scrollSpeed}"
                }
                if (isRandomAnima) {
                    val animation = randomAnima
                    val interpolator = appearance.lyricInterpolator
                    val duration = appearance.animationDurationMillis
                    inAnimation =
                        LyricViewTools.switchViewInAnima(animation, interpolator, duration)
                    outAnimation = LyricViewTools.switchViewOutAnima(animation, duration)
                }
                stopAllScroll()
                setText(lyric)
            }
        }
    }

    private fun syncSystemUiVisibilityOverrides() {
        val policy = currentVisibilityPolicy()
        syncVisibility(clockView, policy.hideClock)
        syncVisibility(notificationIconArea, policy.hideNotificationIcons)
        syncVisibility(XiaomiHooks.getPadClockView(), policy.hidePadClock)
        syncVisibility(XiaomiHooks.getMiuiNetworkSpeedView(), policy.hideNetworkSpeed)
        syncVisibility(XiaomiHooks.getCarrierLabel(), policy.hideCarrier)
        if (!policy.hideNotificationBigTime) {
            visibilityOverrides.restore(XiaomiHooks.getNotificationBigTime())
        }
    }

    private fun currentVisibilityPolicy(): SystemUiVisibilityPolicy =
        SystemUiVisibilityPolicy.create(
            hideTime = config.hideTime,
            hideNotificationIcons = config.hideNotificationIcon,
            optimizePadClock = config.mMiuiPadOptimize,
            hideNetworkSpeed = config.mMiuiHideNetworkSpeed,
            hideCarrier = config.hideCarrier
        )

    private fun syncVisibility(view: View?, hidden: Boolean) {
        if (hidden) visibilityOverrides.apply(view, View.GONE)
        else visibilityOverrides.restore(view)
    }

    // 更改图标
    private fun changeIcon(base64Icon: String) {
        if (!iconSwitch) return
        if (!isMusicPlaying) return

        lastBase64Icon = base64Icon
    }

    // 适用于不考虑状态的隐藏
    private fun hideLyric() {
        if (!isReady) return
        if (isHiding) return
        isHiding = true

        "Hiding LyricView".log()
        goMainThread {
            lyricLayout.hideView(false)
            lyricView.stopAllScroll()
            lyricView.setText("")
            if (config.titleSwitch) titleDialog.hideTitle()
            visibilityOverrides.restoreAll()
        }
    }

    private fun updateConfig(delay: Long = 0L) {
        "Updating Config".log()
        config.update()
        val enabledChanged = runtimeController.setEnabled(config.masterSwitch)
        if (!runtimeEnabled) {
            disableRuntime(resetState = !enabledChanged)
            return
        }
        refreshAppearanceSnapshot()
        goMainThread(delay) {
            val appearance = currentAppearanceSnapshot()
            lyricAppearanceApplier.applyMargins(lyricView, appearance)
            lyricAppearanceApplier.applyText(
                target = lyricView,
                appearance = appearance,
                sourceTextSizePx = clockView.textSize,
                sourceTextColor = clockView.currentTextColor,
                sourceLetterSpacing = clockView.letterSpacing,
                fallbackTypeface = clockView.typeface,
                fontFile = File(context.filesDir, "font"),
                dynamicTextColor = clockView.currentTextColor
            )
            lyricView.apply {
                if (!appearance.dynamicLyricSpeed) setScrollSpeed(appearance.lyricSpeed)
                val animation = appearance.lyricAnimation
                isRandomAnima = animation == 11
                if (!isRandomAnima) {
                    val appearance = currentAppearanceSnapshot()
                    val interpolator = appearance.lyricInterpolator
                    val duration = appearance.animationDurationMillis
                    inAnimation =
                        LyricViewTools.switchViewInAnima(animation, interpolator, duration)
                    outAnimation = LyricViewTools.switchViewOutAnima(animation, duration)
                }
            }
            if (!appearance.iconEnabled) {
                iconView.hideView()
                iconSwitch = false
            } else {
                iconView.showView()
                iconSwitch = true
                lyricAppearanceApplier.applyIcon(
                    target = iconView,
                    appearance = appearance,
                    sourceHeightPx = clockView.height,
                    sourceTextColor = clockView.currentTextColor
                )
            }
            if (isMusicPlaying && !isHiding) {
                syncSystemUiVisibilityOverrides()
            }
            if (isMusicPlaying && runtimeState.lyric.isNotEmpty()) {
                refreshTimeoutRestore()
            } else {
                timeoutRestoreTask.cancel()
            }
        }
    }


    private fun disableRuntime(resetState: Boolean = true) {
        if (resetState) runtimeController.reset()
        pendingTitlePublisher = ""
        pendingTitleData = null
        timeoutRestoreTask.cancel()
        titleDisplayTask.cancel()
        goMainThread {
            if (isReady) {
                hideLyric()
            } else {
                visibilityOverrides.restoreAll()
            }
        }
    }

    private fun currentAppearanceSnapshot(): RuntimeAppearanceSnapshot {
        return appearanceSnapshot ?: RuntimeAppearanceSnapshot.from(config).also {
            appearanceSnapshot = it
        }
    }

    private fun refreshAppearanceSnapshot() {
        appearanceSnapshot = RuntimeAppearanceSnapshot.from(config)
    }

    inner class UpdateConfig : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra("type")) {
                "normal" -> {
                    if (!isReady) return
                    updateConfig()
                }

                "change_font" -> {}
                "reset_font" -> {}
            }
        }
    }

    inner class ScreenLockReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            isScreenLocked = intent.action == Intent.ACTION_SCREEN_OFF
            LogTools.log { "isScreenLocked: $isScreenLocked" }
            if (!config.hideLyricWhenLockScreen) return
            if (isScreenLocked) {
                updateLyricState(showLyric = false)
            } else if (isMusicPlaying && runtimeState.lyric.isNotEmpty()) {
                updateLyricState()
            }
        }
    }
}
