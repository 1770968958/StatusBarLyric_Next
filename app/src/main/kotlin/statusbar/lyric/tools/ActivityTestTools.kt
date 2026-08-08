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
import statusbar.lyric.runtime.test.AnchorTestProtocol.ACTION_APP_TEST_RECEIVER
import statusbar.lyric.runtime.test.AnchorTestProtocol.ACTION_TEST_RECEIVER
import statusbar.lyric.runtime.test.AnchorTestProtocol.EXTRA_DATA
import statusbar.lyric.runtime.test.AnchorTestProtocol.EXTRA_DATA_LIST
import statusbar.lyric.runtime.test.AnchorTestProtocol.EXTRA_REQUEST_ID
import statusbar.lyric.runtime.test.AnchorTestProtocol.EXTRA_TYPE
import statusbar.lyric.runtime.test.AnchorTestProtocol.SYSTEM_UI_PACKAGE
import statusbar.lyric.runtime.test.AnchorTestProtocol.TYPE_GET_CLASS
import statusbar.lyric.runtime.test.AnchorTestProtocol.TYPE_RECEIVE_CLASS
import statusbar.lyric.runtime.test.AnchorTestProtocol.TYPE_SHOW_VIEW
import statusbar.lyric.tools.LogTools.log

@SuppressLint("StaticFieldLeak")
object ActivityTestTools {
    fun Context.getClass(requestId: Long) {
        this.sendBroadcast(Intent(ACTION_TEST_RECEIVER).apply {
            setPackage(SYSTEM_UI_PACKAGE)
            putExtra(EXTRA_TYPE, TYPE_GET_CLASS)
            putExtra(EXTRA_REQUEST_ID, requestId)
            TYPE_GET_CLASS.log()
        })
    }

    fun Context.receiveClass(dataList: ArrayList<Data>, requestId: Long) {
        sendBroadcast(Intent(ACTION_APP_TEST_RECEIVER).apply {
            setPackage(BuildConfig.APPLICATION_ID)
            putExtra(EXTRA_TYPE, TYPE_RECEIVE_CLASS)
            putExtra(EXTRA_REQUEST_ID, requestId)
            putParcelableArrayListExtra(EXTRA_DATA_LIST, dataList)
        })
    }

    fun Context.showView(data: Data) {
        sendBroadcast(Intent(ACTION_TEST_RECEIVER).apply {
            setPackage(SYSTEM_UI_PACKAGE)
            putExtra(EXTRA_TYPE, TYPE_SHOW_VIEW)
            putExtra(EXTRA_DATA, data)
        })
    }
}
