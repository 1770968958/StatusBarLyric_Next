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
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import statusbar.lyric.BuildConfig
import statusbar.lyric.MainActivity
import statusbar.lyric.reflection.ReflectionUtils
import statusbar.lyric.tools.ActivityTools.isHook
import statusbar.lyric.tools.LogTools.log
import java.io.DataOutputStream
import java.util.Locale
import java.util.Objects
import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty

@SuppressLint("StaticFieldLeak")
object Tools {
    val buildTime: String =
        SimpleDateFormat("yyyy/M/d H:m:s", Locale.CHINA).format(BuildConfig.BUILD_TIME)

    val isPad by lazy { getSystemProperties("ro.build.characteristics") == "tablet" }

    val getPhoneName by lazy {
        val xiaomiMarketName = getSystemProperties("ro.product.marketname")
        val vivoMarketName = getSystemProperties("ro.vivo.market.name")
        when {
            Build.BRAND.uppercaseFirstChar() == "Vivo" -> vivoMarketName.uppercaseFirstChar()
            xiaomiMarketName.isNotEmpty() -> xiaomiMarketName.uppercaseFirstChar()
            else -> "${Build.BRAND.uppercaseFirstChar()} ${Build.MODEL}"
        }
    }

    fun String.uppercaseFirstChar(): String {
        val formattedBrand = this.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        return formattedBrand
    }

    fun dp2px(context: Context, dpValue: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue,
            context.resources.displayMetrics
        ).toInt()

    internal fun isPresent(name: String): Boolean {
        return try {
            Objects.requireNonNull(Thread.currentThread().contextClassLoader).loadClass(name)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    @SuppressLint("PrivateApi")
    fun getSystemProperties(key: String): String = SystemPropertiesReader.get(key)

    fun <T> observableChange(
        initialValue: T, onChange: (oldValue: T, newValue: T) -> Unit
    ): ReadWriteProperty<Any?, T> {
        return Delegates.observable(initialValue) { _, oldVal, newVal ->
            if (oldVal != newVal) {
                onChange(oldVal, newVal)
            }
        }
    }

    fun goMainThread(delayed: Long = 0, callback: () -> Unit): Boolean =
        MainThreadScheduler.postDelayedSeconds(delayed, callback)

    fun Context.isLandscape() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun String.dispose(): String {
        if (' ' !in this && '\n' !in this) return this
        return buildString(length) {
            this@dispose.forEach { char ->
                if (char != ' ' && char != '\n') append(char)
            }
        }
    }

    fun getSP(context: Context, key: String): SharedPreferences {
        @Suppress("DEPRECATION", "WorldReadableFiles")
        return context.createDeviceProtectedStorageContext()
            .getSharedPreferences(
                key, if (isHook()) Context.MODE_WORLD_READABLE else Context.MODE_PRIVATE
            )
    }

    fun shell(command: String, isSu: Boolean) {
        try {
            if (isSu) {
                try {
                    val p = Runtime.getRuntime().exec("su")
                    val outputStream = p.outputStream
                    DataOutputStream(outputStream).apply {
                        writeBytes(command)
                        flush()
                        close()
                    }
                    outputStream.close()
                } catch (_: Exception) {
                    // Su shell 命令执行失败
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(MainActivity.appContext, "Root permissions required!!", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Runtime.getRuntime().exec(command)
            }
        } catch (_: Throwable) {
            // Shell 命令执行失败
        }
    }

    inline fun <T> T?.isNotNull(callback: (T) -> Unit): Boolean {
        if (this != null) {
            callback(this)
            return true
        }
        return false
    }

    inline fun Boolean.isNot(callback: () -> Unit) {
        if (!this) {
            callback()
        }
    }

    inline fun Any?.isNull(callback: () -> Unit): Boolean {
        if (this == null) {
            callback()
            return true
        }
        return false
    }

    inline fun <T> T?.ifNotNull(callback: (T) -> Any?): Any? {
        if (this != null) {
            return callback(this)
        }
        return null
    }

    fun Any?.isNull() = this == null

    fun Any?.isNotNull() = this != null

    fun Any.getObjectField(fieldName: String): Any? {
        val field = ReflectionUtils.findField(javaClass, fieldName)
            ?: throw NoSuchFieldException("$fieldName on ${javaClass.name}")
        field.isAccessible = true
        return field.get(this)
    }

    fun Any.getSuperObjectField(fieldName: String): Any? {
        val field = ReflectionUtils.findField(javaClass, fieldName) ?: return null
        return runCatching { field.get(this) }.getOrNull()
    }

    fun Any?.existField(fieldName: String): Boolean {
        if (this == null) return false
        return ReflectionUtils.hasField(javaClass, fieldName)
    }

    fun Any?.existMethod(methodName: String): Boolean {
        return this?.let { ReflectionUtils.hasMethod(it.javaClass, methodName) } == true
    }

    fun Any.getObjectFieldIfExist(fieldName: String): Any? {
        return try {
            getObjectField(fieldName)
        } catch (_: Throwable) {
            null
        }
    }

    fun Any.setObjectField(fieldName: String, value: Any?) {
        val field = ReflectionUtils.findField(javaClass, fieldName)
            ?: throw NoSuchFieldException("$fieldName on ${javaClass.name}")
        field.isAccessible = true
        field.set(this, value)
    }

    fun Any.callMethod(methodName: String, vararg args: Any): Any? {
        val method = ReflectionUtils.findCompatibleMethod(javaClass, methodName, args)
            ?: throw NoSuchMethodException("$methodName on ${javaClass.name}")
        method.isAccessible = true
        return method.invoke(this, *args)
    }
}
