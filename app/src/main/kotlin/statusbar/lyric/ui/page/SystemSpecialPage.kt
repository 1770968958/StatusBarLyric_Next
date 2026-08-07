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

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import statusbar.lyric.R
import statusbar.lyric.config.ActivityOwnSP.config
import statusbar.lyric.tools.ActivityTools
import statusbar.lyric.tools.Tools.isNotNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SystemSpecialPage(
    navController: NavController
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val mMiuiHideNetworkSpeed = remember { mutableStateOf(config.mMiuiHideNetworkSpeed) }
    val mMiuiPadOptimize = remember { mutableStateOf(config.mMiuiPadOptimize) }
    val hideCarrier = remember { mutableStateOf(config.hideCarrier) }
    val mHyperOSTexture = remember { mutableStateOf(config.mHyperOSTexture) }
    val mHyperOSTextureRadio = remember { mutableStateOf(config.mHyperOSTextureRadio) }
    val mHyperOSTextureCorner = remember { mutableStateOf(config.mHyperOSTextureCorner) }
    val mHyperOSTextureBgColor = remember { mutableStateOf(config.mHyperOSTextureBgColor) }
    val mAutomateFocusedNotice = remember { mutableStateOf(config.automateFocusedNotice) }
    val showDialog = remember { mutableStateOf(false) }
    val showRadioDialog = remember { mutableStateOf(false) }
    val showCornerDialog = remember { mutableStateOf(false) }
    val showBgColorDialog = remember { mutableStateOf(false) }

    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.background,
        tint = HazeTint(
            MiuixTheme.colorScheme.background.copy(
                if (scrollBehavior.state.collapsedFraction <= 0f) 1f
                else lerp(1f, 0.67f, (scrollBehavior.state.collapsedFraction))
            )
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                color = Color.Transparent,
                modifier = Modifier
                    .hazeEffect(hazeState) {
                        style = hazeStyle
                        blurRadius = 25.dp
                        noiseFactor = 0f
                    }
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Right))
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Right))
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .windowInsetsPadding(WindowInsets.captionBar.only(WindowInsetsSides.Top)),
                title = stringResource(R.string.system_special_page),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 20.dp),
                        onClick = {
                            navController.popBackStack()
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Back,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                defaultWindowInsetsPadding = false
            )
        },
        popupHost = { null }
    ) {
        LazyColumn(
            modifier = Modifier
                .hazeSource(state = hazeState)
                .height(getWindowSize().height.dp)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Right))
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Right)),
            contentPadding = it,
            overscrollEffect = null
        ) {
            item {
                Column(Modifier.padding(top = 6.dp)) {
                    SmallTitle(
                        text = stringResource(R.string.miui_and_hyperos)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                    ) {
                        SuperSwitch(
                            title = stringResource(R.string.miui_hide_network_speed),
                            checked = mMiuiHideNetworkSpeed.value,
                            onCheckedChange = {
                                mMiuiHideNetworkSpeed.value = it
                                config.mMiuiHideNetworkSpeed = it
                            }
                        )
                        SuperSwitch(
                            title = stringResource(R.string.miui_pad_optimize),

                            checked = mMiuiPadOptimize.value,
                            onCheckedChange = {
                                mMiuiPadOptimize.value = it
                                config.mMiuiPadOptimize = it
                            }
                        )
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
                            SuperSwitch(
                                title = stringResource(R.string.hide_carrier),
                                checked = hideCarrier.value,
                                onCheckedChange = {
                                    hideCarrier.value = it
                                    config.hideCarrier = it
                                }
                            )
                        }
                    }
                    SmallTitle(
                        text = stringResource(R.string.hyperos),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                    ) {
                        SuperSwitch(
                            title = stringResource(R.string.automate_focused_notice),
                            checked = mAutomateFocusedNotice.value,
                            onCheckedChange = {
                                mAutomateFocusedNotice.value = it
                                config.automateFocusedNotice = it
                            }
                        )
                        SuperSwitch(
                            title = stringResource(R.string.hyperos_texture),
                            checked = mHyperOSTexture.value,
                            onCheckedChange = {
                                mHyperOSTexture.value = it
                                config.mHyperOSTexture = it
                            }
                        )
                        AnimatedVisibility(mHyperOSTexture.value) {
                            Column {
                                SuperArrow(
                                    title = stringResource(R.string.hyperos_texture_radio),
                                    onClick = {
                                        showRadioDialog.value = true
                                    },
                                    holdDownState = showRadioDialog.value
                                )
                                SuperArrow(
                                    title = stringResource(R.string.hyperos_texture_corner),
                                    onClick = {
                                        showCornerDialog.value = true
                                    },
                                    holdDownState = showCornerDialog.value
                                )
                                SuperArrow(
                                    title = stringResource(R.string.hyperos_texture_color),
                                    onClick = {
                                        showBgColorDialog.value = true
                                    },
                                    holdDownState = showBgColorDialog.value
                                )
                            }
                        }
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 6.dp, bottom = 12.dp)
                ) {
                    BasicComponent(
                        title = stringResource(R.string.reset_system_ui),
                        titleColor = BasicComponentDefaults.titleColor(
                            color = Color.Red
                        ),
                        onClick = {
                            showDialog.value = true
                        }
                    )
                }
                Spacer(
                    Modifier.height(
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        }
    }
    RestartDialog(showDialog)
    RadioDialog(showRadioDialog, mHyperOSTextureRadio)
    CornerDialog(showCornerDialog, mHyperOSTextureCorner)
    BgColorDialog(showBgColorDialog, mHyperOSTextureBgColor)
}

@Composable
fun RadioDialog(showDialog: MutableState<Boolean>, mHyperOSTextureRadio: MutableState<Int>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.hyperos_texture_radio),
        summary = stringResource(R.string.lyric_stroke_width_tips),
        initialValue = mHyperOSTextureRadio.value,
        validRange = 0..400,
        fallbackValue = { 25 },
        onValueChange = {
            config.mHyperOSTextureRadio = it
            mHyperOSTextureRadio.value = it
        }
    )
}

@Composable
fun CornerDialog(showDialog: MutableState<Boolean>, mHyperOSTextureCorner: MutableState<Int>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.hyperos_texture_corner),
        summary = stringResource(R.string.lyric_letter_spacing_tips),
        initialValue = mHyperOSTextureCorner.value,
        validRange = 0..50,
        fallbackValue = { 25 },
        onValueChange = {
            config.mHyperOSTextureCorner = it
            mHyperOSTextureCorner.value = it
        }
    )
}

@Composable
fun BgColorDialog(showDialog: MutableState<Boolean>, mHyperOSTextureBgColor: MutableState<String>) {
    ColorSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.hyperos_texture_color),
        summary = stringResource(R.string.lyric_color_and_transparency_tips),
        initialValue = mHyperOSTextureBgColor.value,
        defaultValue = "#15818181",
        onValueChange = {
            config.mHyperOSTextureBgColor = it
            mHyperOSTextureBgColor.value = it
        }
    )
}
