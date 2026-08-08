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
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.github.kyuubiran.ezxhelper.EzXHelper.moduleRes
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import de.robv.android.xposed.XC_MethodHook
import statusbar.lyric.R
import statusbar.lyric.config.XposedOwnSP.config
import statusbar.lyric.data.Data
import statusbar.lyric.hook.BaseHook
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
import statusbar.lyric.tools.LogTools.log
import statusbar.lyric.tools.LyricViewTools.hideView
import statusbar.lyric.tools.LyricViewTools.showView
import statusbar.lyric.tools.Tools.goMainThread

class SystemUITest : BaseHook() {
    private lateinit var hook: XC_MethodHook.Unhook
    private val candidateDetector = AnchorCandidateDetector()
    lateinit var context: Context
    lateinit var lastView: TextView
    var lastViewId = 0
    val testTextView by lazy {
        TextView(context).apply {
            text = moduleRes.getString(R.string.app_name)
            isSingleLine = true
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
        }
    }
    private val dataHashMap by lazy { HashMap<TextView, Data>() }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun init() {
        var isLoad = false
        Application::class.java.methodFinder().filterByName("attach").first().createHook {
            after {
                if (isLoad) return@after
                isLoad = true
                context = it.args[0] as Context
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        TestReceiver(),
                        IntentFilter(ACTION_TEST_RECEIVER),
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    context.registerReceiver(TestReceiver(), IntentFilter(ACTION_TEST_RECEIVER))
                }
                moduleRes.getString(R.string.start_hooking_text_view).log()
                hook()

            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun hook() {
        candidateDetector.start(config.relaxConditions)
        hook = TextView::class.java.methodFinder().filterByName("onDraw").first().createHook {
            after { hookParam ->
                if (!candidateDetector.isCollectionActive()) {
                    hook.unhook()
                    return@after
                }

                val view = hookParam.thisObject as? TextView ?: return@after
                if (dataHashMap.containsKey(view)) return@after
                val parentView = view.parent as? LinearLayout ?: return@after
                val className = view.javaClass.name
                if (!candidateDetector.isCandidateText(view.text) ||
                    !candidateDetector.isCandidateClass(className)
                ) return@after

                val clockContainerId = context.resources.getIdentifier(
                    "clock_container",
                    "id",
                    context.packageName
                )
                if (parentView.id == clockContainerId) return@after

                val idName = runCatching {
                    if (view.id == android.view.View.NO_ID) ""
                    else context.resources.getResourceEntryName(view.id)
                }.getOrDefault("")
                val signature = AnchorCandidateSignature(
                    textViewClassName = className,
                    textViewId = view.id,
                    parentViewClassName = parentView.javaClass.name,
                    parentViewId = parentView.id,
                    textSize = view.textSize,
                    idName = idName
                )
                val newData = Data(
                    signature.textViewClassName,
                    signature.textViewId,
                    signature.parentViewClassName,
                    signature.parentViewId,
                    false,
                    candidateDetector.nextIndex(signature),
                    signature.textSize,
                    signature.idName
                )
                dataHashMap[view] = newData
                moduleRes.getString(R.string.first_filter)
                    .format(newData, dataHashMap.size).log()
            }
        }
    }

    inner class TestReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra(EXTRA_TYPE)) {
                TYPE_GET_CLASS -> {
                    val requestId = intent.getLongExtra(EXTRA_REQUEST_ID, NO_REQUEST_ID)
                    if (dataHashMap.isEmpty()) {
                        moduleRes.getString(R.string.no_text_view).log()
                        context.receiveClass(arrayListOf(), requestId)
                        return
                    } else {
                        moduleRes.getString(R.string.send_text_view_class).format(dataHashMap).log()
                        context.receiveClass(ArrayList(dataHashMap.values), requestId)
                    }
                }

                TYPE_SHOW_VIEW -> {
                    val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_DATA, Data::class.java)
                    } else {
                        @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
                    }!!
                    goMainThread {
                        dataHashMap.forEach { (textview, da) ->
                            if (candidateDetector.matches(da, data)) {
                                if (lastViewId != textview.id) {
                                    if (this@SystemUITest::lastView.isInitialized) {
                                        (lastView.parent as LinearLayout).removeView(testTextView)
                                        lastView.showView()
                                    }
                                    textview.hideView()
                                    val parentLinearLayout = textview.parent as LinearLayout
                                    parentLinearLayout.addView(testTextView, 0)
                                    lastViewId = textview.id
                                    lastView = textview
                                }
                                return@forEach
                            }
                        }
                    }
                }
            }
        }
    }
}
