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

package statusbar.lyric.tools

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import statusbar.lyric.MainActivity
import statusbar.lyric.R
import statusbar.lyric.data.Data
import statusbar.lyric.tools.LogTools.log
import kotlin.system.exitProcess

@SuppressLint("StaticFieldLeak")
object ActivityTools {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    lateinit var dataList: ArrayList<Data>

    fun isHook(): Boolean = false

    fun changeConfig(type: String = "normal", path: String = "") {
        handler.postDelayed({
            MainActivity.appContext.sendBroadcast(Intent("updateConfig").apply {
                putExtra("type", type)
                putExtra("path", path)
            })
        }, 200L)
    }

    fun runOnMainDelayed(delayMillis: Long, callback: () -> Unit) {
        handler.postDelayed(callback, delayMillis)
    }

    fun restartAppDelayed(delayMillis: Long = 500L) {
        runOnMainDelayed(delayMillis) {
            restartApp()
        }
    }

    fun showToastOnLooper(message: Any?) {
        try {
            handler.post {
                Toast.makeText(MainActivity.appContext, message.toString(), Toast.LENGTH_LONG).show()
                message.log()
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
    }

    fun colorCheck(value: String, unit: (String) -> Unit, default: String = ""): Boolean {
        val checkedValue = value.trim().ifEmpty { default.trim() }
        if (checkedValue.isEmpty()) {
            unit("")
            return true
        }

        return try {
            checkedValue.toColorInt()
            unit(checkedValue)
            true
        } catch (_: Exception) {
            showToastOnLooper(MainActivity.appContext.getString(R.string.color_error))
            false
        }
    }

    fun colorSCheck(value: String, unit: (String) -> Unit, default: String = ""): Boolean {
        val checkedValue = value.trim().ifEmpty { default.trim() }
        if (checkedValue.isEmpty()) {
            unit("")
            return true
        }

        val colors = checkedValue.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (colors.isEmpty()) {
            showToastOnLooper(MainActivity.appContext.getString(R.string.color_error))
            return false
        }

        return try {
            colors.forEach { it.toColorInt() }
            unit(colors.joinToString(","))
            true
        } catch (_: Exception) {
            showToastOnLooper(MainActivity.appContext.getString(R.string.color_error))
            false
        }
    }

    fun openUrl(url: String) {
        MainActivity.appContext.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    fun restartApp() {
        val intent = MainActivity.appContext.packageManager.getLaunchIntentForPackage(MainActivity.appContext.packageName)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        MainActivity.appContext.startActivity(intent)
        exitProcess(0)
    }
}