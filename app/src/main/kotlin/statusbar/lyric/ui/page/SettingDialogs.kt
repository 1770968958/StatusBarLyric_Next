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

package statusbar.lyric.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import statusbar.lyric.R
import statusbar.lyric.tools.ActivityTools
import statusbar.lyric.tools.ActivityTools.changeConfig
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog

@Composable
fun SettingTextDialog(
    showDialog: MutableState<Boolean>,
    title: String,
    summary: String,
    initialValue: String,
    label: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    onConfirm: (String, (String) -> Unit) -> Boolean
) {
    val value = remember(showDialog.value, initialValue) { mutableStateOf(initialValue) }
    SuperDialog(
        title = title,
        summary = summary,
        show = showDialog,
        onDismissRequest = { showDialog.value = false },
    ) {
        if (label.isEmpty()) {
            TextField(
                modifier = Modifier.padding(bottom = 16.dp),
                value = value.value,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                onValueChange = { value.value = it }
            )
        } else {
            TextField(
                label = label,
                modifier = Modifier.padding(bottom = 16.dp),
                value = value.value,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                onValueChange = { value.value = it }
            )
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.cancel),
                onClick = { showDialog.value = false }
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.ok),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    if (onConfirm(value.value, { value.value = it })) {
                        showDialog.value = false
                    }
                }
            )
        }
    }
}

@Composable
fun IntSettingDialog(
    showDialog: MutableState<Boolean>,
    title: String,
    summary: String,
    initialValue: Int,
    validRange: IntRange,
    fallbackValue: () -> Int,
    keyboardType: KeyboardType = KeyboardType.Number,
    onValueChange: (Int) -> Unit
) {
    SettingTextDialog(
        showDialog = showDialog,
        title = title,
        summary = summary,
        initialValue = initialValue.toString(),
        keyboardType = keyboardType,
    ) { rawValue, updateText ->
        val checkedValue = rawValue.toIntOrNull()?.takeIf { it in validRange } ?: fallbackValue()
        updateText(checkedValue.toString())
        onValueChange(checkedValue)
        changeConfig()
        true
    }
}

@Composable
fun ColorSettingDialog(
    showDialog: MutableState<Boolean>,
    title: String,
    summary: String,
    initialValue: String,
    defaultValue: String = "",
    label: String = "#FFFFFF",
    allowMultipleColors: Boolean = false,
    changeConfigOnConfirm: Boolean = true,
    onValueChange: (String) -> Unit
) {
    SettingTextDialog(
        showDialog = showDialog,
        title = title,
        summary = summary,
        initialValue = initialValue,
        label = label,
        keyboardType = KeyboardType.Ascii,
    ) { rawValue, _ ->
        val success = if (allowMultipleColors) {
            ActivityTools.colorSCheck(rawValue, unit = onValueChange, default = defaultValue)
        } else {
            ActivityTools.colorCheck(rawValue, unit = onValueChange, default = defaultValue)
        }
        if (success && changeConfigOnConfirm) {
            changeConfig()
        }
        success
    }
}
