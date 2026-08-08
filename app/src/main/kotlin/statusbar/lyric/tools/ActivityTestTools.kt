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
import android.content.Intent
import statusbar.lyric.BuildConfig
import statusbar.lyric.data.Data
import statusbar.lyric.tools.LogTools.log

@SuppressLint("StaticFieldLeak")
object ActivityTestTools {
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val EXTRA_REQUEST_ID = "RequestId"

    fun Context.getClass(requestId: Long) {
        this.sendBroadcast(Intent("TestReceiver").apply {
            setPackage(SYSTEM_UI_PACKAGE)
            putExtra("Type", "GetClass")
            putExtra(EXTRA_REQUEST_ID, requestId)
            "GetClass".log()
        })
    }

    fun Context.receiveClass(dataList: ArrayList<Data>, requestId: Long) {
        sendBroadcast(Intent("AppTestReceiver").apply {
            setPackage(BuildConfig.APPLICATION_ID)
            putExtra("Type", "ReceiveClass")
            putExtra(EXTRA_REQUEST_ID, requestId)
            putParcelableArrayListExtra("DataList", dataList)
        })
    }

    fun Context.showView(data: Data) {
        sendBroadcast(Intent("TestReceiver").apply {
            setPackage(SYSTEM_UI_PACKAGE)
            putExtra("Type", "ShowView")
            putExtra("Data", data)
        })
    }
}
