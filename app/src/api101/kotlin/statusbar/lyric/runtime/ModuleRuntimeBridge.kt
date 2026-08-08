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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * API101 模块应用不再通过自 Hook 判断激活状态。框架服务绑定成功即表示模块已激活，
 * 同时提供 Remote Preferences 的可写端。
 */
object ModuleRuntimeBridge {
    private const val TAG = "StatusBarLyric/API101"
    private const val PREFERENCE_MIRROR_DEBOUNCE_MILLIS = 75L

    private val initialized = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingPreferenceLock = Any()
    private val pendingPreferenceKeys = LinkedHashSet<String>()

    @Volatile
    private var fullPreferenceSyncPending = false

    @Volatile
    private var activeService: XposedService? = null

    @Volatile
    private var remotePreferences: SharedPreferences? = null

    @Volatile
    private var activationCallback: ((Boolean) -> Unit)? = null

    private val preferenceTypes = ConcurrentHashMap<String, PreferenceValueType>()

    private val preferenceSyncRunnable = Runnable { flushPendingPreferenceChanges() }

    private val localPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (remotePreferences == null) return@OnSharedPreferenceChangeListener
        scheduleRemotePreferenceSync(key)
    }

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return

        ActivityOwnSP.ownSP.registerOnSharedPreferenceChangeListener(localPreferencesListener)
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                runCatching {
                    val remote = service.getRemotePreferences(Config.CONFIG_NAME)
                    remotePreferences = remote
                    activeService = service
                    cancelPendingPreferenceSync()
                    synchronizeRemoteValues(ActivityOwnSP.ownSP, remote)
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
                    cancelPendingPreferenceSync()
                    notifyActivation(false)
                    Log.w(TAG, "API101 Xposed service disconnected")
                }
            }
        })
    }

    fun observeActivation(onActivationChanged: (Boolean) -> Unit): () -> Unit {
        activationCallback = onActivationChanged
        notifyActivation(activeService != null)
        return {
            if (activationCallback === onActivationChanged) {
                activationCallback = null
            }
        }
    }

    private fun scheduleRemotePreferenceSync(key: String?) {
        synchronized(pendingPreferenceLock) {
            if (key == null) {
                fullPreferenceSyncPending = true
                pendingPreferenceKeys.clear()
            } else if (!fullPreferenceSyncPending) {
                pendingPreferenceKeys += key
            }
        }
        mainHandler.removeCallbacks(preferenceSyncRunnable)
        mainHandler.postDelayed(preferenceSyncRunnable, PREFERENCE_MIRROR_DEBOUNCE_MILLIS)
    }

    private fun flushPendingPreferenceChanges() {
        val remote = remotePreferences ?: run {
            clearPendingPreferenceState()
            return
        }
        val local = ActivityOwnSP.ownSP
        val (fullSync, keys) = synchronized(pendingPreferenceLock) {
            val result = fullPreferenceSyncPending to pendingPreferenceKeys.toList()
            fullPreferenceSyncPending = false
            pendingPreferenceKeys.clear()
            result
        }

        runCatching {
            if (fullSync) {
                synchronizeRemoteValues(local, remote)
            } else if (keys.isNotEmpty()) {
                synchronizeChangedKeys(local, remote, keys)
            }
        }.onFailure { throwable ->
            Log.w(TAG, "API101 Remote Preferences mirror failed", throwable)
        }
    }

    private fun synchronizeChangedKeys(
        local: SharedPreferences,
        remote: SharedPreferences,
        keys: Collection<String>
    ) {
        val editor = remote.edit()
        var changed = false

        keys.forEach { key ->
            if (!local.contains(key)) {
                preferenceTypes.remove(key)
                if (remote.contains(key)) {
                    editor.remove(key)
                    changed = true
                }
                return@forEach
            }

            val type = preferenceTypes[key] ?: detectPreferenceType(local, key).also {
                preferenceTypes[key] = it
            }
            val localValue = readValue(local, key, type)
            val remoteAlreadyMatches = remote.contains(key) && runCatching {
                preferenceValuesEqual(localValue, readValue(remote, key, type))
            }.getOrDefault(false)
            if (!remoteAlreadyMatches) {
                putValue(editor, key, localValue)
                changed = true
            }
        }

        if (changed) editor.apply()
    }

    private fun cancelPendingPreferenceSync() {
        mainHandler.removeCallbacks(preferenceSyncRunnable)
        clearPendingPreferenceState()
    }

    private fun clearPendingPreferenceState() {
        synchronized(pendingPreferenceLock) {
            fullPreferenceSyncPending = false
            pendingPreferenceKeys.clear()
        }
    }

    /**
     * 模块应用持有配置源。通过增量同步保持 Remote Preferences 与本地配置一致，
     * 避免清空并重复写入未发生变化的键。
     */
    private fun synchronizeRemoteValues(local: SharedPreferences, remote: SharedPreferences) {
        val localSnapshot = local.all
        val remoteSnapshot = remote.all
        val editor = remote.edit()
        var changed = false

        preferenceTypes.clear()
        localSnapshot.forEach { (key, value) ->
            preferenceTypeOf(value)?.let { preferenceTypes[key] = it }
        }

        (remoteSnapshot.keys - localSnapshot.keys).forEach { key ->
            editor.remove(key)
            changed = true
        }

        localSnapshot.forEach { (key, localValue) ->
            if (!preferenceValuesEqual(localValue, remoteSnapshot[key])) {
                putValue(editor, key, localValue)
                changed = true
            }
        }

        if (changed) editor.apply()
    }

    private fun detectPreferenceType(preferences: SharedPreferences, key: String): PreferenceValueType {
        PreferenceValueType.values().forEach { type ->
            if (runCatching { readValue(preferences, key, type) }.isSuccess) {
                return type
            }
        }
        throw IllegalStateException("Unsupported preference type for $key")
    }

    private fun readValue(
        preferences: SharedPreferences,
        key: String,
        type: PreferenceValueType
    ): Any? = when (type) {
        PreferenceValueType.BOOLEAN -> preferences.getBoolean(key, false)
        PreferenceValueType.FLOAT -> preferences.getFloat(key, 0f)
        PreferenceValueType.INT -> preferences.getInt(key, 0)
        PreferenceValueType.LONG -> preferences.getLong(key, 0L)
        PreferenceValueType.STRING -> preferences.getString(key, null)
        PreferenceValueType.STRING_SET -> preferences.getStringSet(key, emptySet())?.toSet()
    }

    private fun preferenceTypeOf(value: Any?): PreferenceValueType? = when (value) {
        is Boolean -> PreferenceValueType.BOOLEAN
        is Float -> PreferenceValueType.FLOAT
        is Int -> PreferenceValueType.INT
        is Long -> PreferenceValueType.LONG
        is String -> PreferenceValueType.STRING
        is Set<*> -> PreferenceValueType.STRING_SET
        else -> null
    }

    private fun preferenceValuesEqual(first: Any?, second: Any?): Boolean {
        if (first is Set<*> && second is Set<*>) return first.toSet() == second.toSet()
        return first == second
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

    private enum class PreferenceValueType {
        BOOLEAN,
        FLOAT,
        INT,
        LONG,
        STRING,
        STRING_SET
    }

    private fun notifyActivation(active: Boolean) {
        val callback = activationCallback ?: return
        mainHandler.post {
            if (activationCallback === callback) {
                callback(active)
            }
        }
    }
}
