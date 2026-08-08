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

package statusbar.lyric.runtime

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import statusbar.lyric.config.ActivityOwnSP
import statusbar.lyric.config.Config
import java.util.concurrent.atomic.AtomicBoolean

/**
 * API 101 module apps are not self-hooked. A bound framework service proves that
 * the module is active and provides the writable side of Remote Preferences.
 */
object ModuleRuntimeBridge {
    private const val TAG = "StatusBarLyric/API101"

    private val initialized = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeService: XposedService? = null

    @Volatile
    private var remotePreferences: SharedPreferences? = null

    @Volatile
    private var activationCallback: ((Boolean) -> Unit)? = null

    private val localPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { local, key ->
        val remote = remotePreferences ?: return@OnSharedPreferenceChangeListener
        runCatching {
            val editor = remote.edit()
            if (key == null) {
                editor.clear()
            } else if (!local.contains(key)) {
                editor.remove(key)
            } else {
                putValue(editor, key, local.all[key])
            }
            editor.apply()
        }.onFailure { throwable ->
            Log.w(TAG, "API101 Remote Preferences mirror failed", throwable)
        }
    }

    fun initialize(onActivationChanged: (Boolean) -> Unit) {
        activationCallback = onActivationChanged
        if (!initialized.compareAndSet(false, true)) {
            notifyActivation(activeService != null)
            return
        }

        ActivityOwnSP.ownSP.registerOnSharedPreferenceChangeListener(localPreferencesListener)
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                runCatching {
                    val remote = service.getRemotePreferences(Config.CONFIG_NAME)
                    remotePreferences = remote
                    activeService = service
                    replaceRemoteValues(ActivityOwnSP.ownSP, remote)
                    notifyActivation(true)
                    Log.i(TAG, "API101 Xposed service connected; Remote Preferences are writable")
                }.onFailure { throwable ->
                    if (activeService === service) {
                        activeService = null
                        remotePreferences = null
                    }
                    notifyActivation(false)
                    Log.w(TAG, "API101 Xposed service is unavailable", throwable)
                }
            }

            override fun onServiceDied(service: XposedService) {
                if (activeService === service) {
                    activeService = null
                    remotePreferences = null
                    notifyActivation(false)
                    Log.w(TAG, "API101 Xposed service disconnected")
                }
            }
        })
    }

    /**
     * The module application owns configuration. Replacing the remote snapshot on
     * every service bind prevents an older Remote Preferences file from pinning a
     * stale anchor or appearance after the user changed it in the application.
     */
    private fun replaceRemoteValues(local: SharedPreferences, remote: SharedPreferences) {
        val editor = remote.edit()
        editor.clear()
        local.all.forEach { (key, value) ->
            putValue(editor, key, value)
        }
        editor.apply()
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            null -> editor.remove(key)
            else -> Log.w(TAG, "Ignoring unsupported preference type for $key: ${value.javaClass.name}")
        }
    }

    private fun notifyActivation(active: Boolean) {
        mainHandler.post { activationCallback?.invoke(active) }
    }
}
