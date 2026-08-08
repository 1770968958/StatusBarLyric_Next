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

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import statusbar.lyric.BuildConfig
import statusbar.lyric.config.XposedOwnSP
import java.util.concurrent.atomic.AtomicBoolean

/**
 * API 101 entry point. SystemUI work starts from Application.attach and stays in the API 101 source set.
 */
class Api101Module : XposedModule() {
    private val systemUiHookInstalled = AtomicBoolean(false)
    private val systemUiHook = Api101SystemUIHook(this)

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        try {
            XposedOwnSP.attachRemotePreferences(getRemotePreferences(XposedOwnSP.remoteGroup))
            log(
                Log.INFO,
                TAG,
                "API101 module loaded; remote preferences attached; version=${BuildConfig.VERSION_NAME}"
            )
        } catch (throwable: Throwable) {
            log(Log.WARN, TAG, "API101 module loaded; remote preferences unavailable", throwable)
        }
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.getPackageName() != SYSTEM_UI_PACKAGE) return
        if (!systemUiHookInstalled.compareAndSet(false, true)) return

        val attach = Application::class.java.getDeclaredMethod("attach", Context::class.java)
        hook(attach)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .intercept(ApplicationAttachHooker(this))
        log(Log.INFO, TAG, "API101 registered Application.attach hook for $SYSTEM_UI_PACKAGE")
    }

    private class ApplicationAttachHooker(
        private val module: Api101Module
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            val application = chain.getThisObject() as? Application
            val context = chain.getArg(0) as? Context ?: application
            if (context != null) {
                module.systemUiHook.onApplicationAttached(context, context.classLoader)
                module.log(Log.INFO, TAG, "API101 SystemUI Application.attach observed; context=${context.javaClass.name}")
            } else {
                module.log(Log.WARN, TAG, "API101 SystemUI Application.attach observed without a Context")
            }
            return result
        }
    }

    private companion object {
        const val TAG = "StatusBarLyric/API101"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
