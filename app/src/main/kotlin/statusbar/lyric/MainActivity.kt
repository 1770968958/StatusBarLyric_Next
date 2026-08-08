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

package statusbar.lyric

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import statusbar.lyric.config.ActivityOwnSP
import statusbar.lyric.config.ActivityOwnSP.config
import statusbar.lyric.config.ActivityOwnSP.updateConfigVer
import statusbar.lyric.data.Data
import statusbar.lyric.runtime.ModuleRuntimeBridge
import statusbar.lyric.tools.ActivityTools
import statusbar.lyric.tools.ActivityTools.dataList
import statusbar.lyric.tools.BackupTools
import statusbar.lyric.tools.ConfigTools
import statusbar.lyric.tools.LogTools
import statusbar.lyric.tools.LogTools.log
import statusbar.lyric.tools.Tools.isNotNull

class MainActivity : ComponentActivity() {
    private val appTestReceiver by lazy { AppTestReceiver() }
    lateinit var createDocumentLauncher: ActivityResultLauncher<Intent>
    lateinit var openDocumentLauncher: ActivityResultLauncher<Intent>
    private var stopObservingActivation: (() -> Unit)? = null

    companion object {
        lateinit var appContext: Context private set

        var isLoad by mutableStateOf(false)

        var testReceiver = false
        private var pendingAnchorRequestId = NO_ANCHOR_REQUEST
        private var lastAnchorResponseId = NO_ANCHOR_REQUEST

        fun beginAnchorRequest(): Long {
            val requestId = SystemClock.elapsedRealtimeNanos()
            pendingAnchorRequestId = requestId
            lastAnchorResponseId = NO_ANCHOR_REQUEST
            testReceiver = false
            dataList = arrayListOf()
            return requestId
        }

        fun isAnchorRequestSuccessful(requestId: Long): Boolean {
            return lastAnchorResponseId == requestId && testReceiver
        }

        fun isAnchorResponseReceived(requestId: Long): Boolean {
            return lastAnchorResponseId == requestId
        }

        fun abandonAnchorRequest(requestId: Long) {
            if (pendingAnchorRequestId == requestId) {
                pendingAnchorRequestId = NO_ANCHOR_REQUEST
            }
        }

        private fun acceptAnchorResponse(requestId: Long): Boolean {
            if (requestId != pendingAnchorRequestId) return false
            pendingAnchorRequestId = NO_ANCHOR_REQUEST
            lastAnchorResponseId = requestId
            return true
        }

        private const val NO_ANCHOR_REQUEST = Long.MIN_VALUE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appContext = this
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false // Xiaomi moment, this code must be here
        }

        createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data.isNotNull()) {
                BackupTools.handleCreateDocument(this, result.data!!.data)
            }
        }

        openDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data.isNotNull()) {
                BackupTools.handleReadDocument(this, result.data!!.data)
                ActivityTools.restartAppDelayed()
            }
        }

        ModuleRuntimeBridge.initialize()
        init()

        setContent {
            App()
        }
    }

    override fun onStart() {
        super.onStart()
        stopObservingActivation = ModuleRuntimeBridge.observeActivation { isLoad = it }
    }

    override fun onStop() {
        stopObservingActivation?.invoke()
        stopObservingActivation = null
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(appTestReceiver)
        super.onDestroy()
    }

    private fun init() {
        ConfigTools(ActivityOwnSP.ownSP)
        updateConfigVer()
        requestPermission()
        registerReceiver()
        if (!BuildConfig.DEBUG) {
            LogTools.init(true)
        }
        LogTools.init(config.outLog)
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!(appContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()) {
                this.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    inner class AppTestReceiver : BroadcastReceiver() {
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra("Type")) {
                "ReceiveClass" -> {
                    val requestId = intent.getLongExtra("RequestId", NO_ANCHOR_REQUEST)
                    if (!acceptAnchorResponse(requestId)) {
                        "Ignored stale anchor response: $requestId".log()
                        return
                    }
                    dataList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra("DataList", Data::class.java)
                    } else {
                        intent.getParcelableArrayListExtra("DataList")
                    } ?: arrayListOf()
                    if (dataList.isEmpty()) {
                        "DataList is empty".log()
                        Toast.makeText(
                            context,
                            context.getString(R.string.not_found_hook),
                            Toast.LENGTH_SHORT
                        ).show()
                        testReceiver = false
                    } else {
                        "DataList size: ${dataList.size}".log()
                        testReceiver = true
                    }
                }
            }
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter("AppTestReceiver")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(appTestReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(appTestReceiver, filter)
        }
    }
}
