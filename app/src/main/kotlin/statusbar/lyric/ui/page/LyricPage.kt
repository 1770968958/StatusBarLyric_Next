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
import androidx.compose.runtime.mutableIntStateOf
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
import statusbar.lyric.tools.ActivityTools.changeConfig
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
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun LyricPage(
    navController: NavController
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val lyricWidth = remember { mutableStateOf(config.lyricWidth.toString()) }
    val fixedLyricWidth = remember { mutableStateOf(config.fixedLyricWidth) }
    val lyricAnimOptions = listOf(
        stringResource(R.string.lyrics_animation_none),
        stringResource(R.string.lyrics_animation_top),
        stringResource(R.string.lyrics_animation_bottom),
        stringResource(R.string.lyrics_animation_start),
        stringResource(R.string.lyrics_animation_end),
        stringResource(R.string.lyrics_animation_fade),
        stringResource(R.string.lyrics_animation_scale_x_y),
        stringResource(R.string.lyrics_animation_scale_x),
        stringResource(R.string.lyrics_animation_scale_y),
        stringResource(R.string.lyrics_animation_horizontalflip),
        stringResource(R.string.lyrics_animation_verticalflip),
        stringResource(R.string.lyrics_animation_random),
    )
    val lyricAnimSelectedOption = remember { mutableIntStateOf(config.lyricAnimation) }
    val lyricInterpolatorOptions = listOf(
        stringResource(R.string.lyrics_interpolator_linear),
        stringResource(R.string.lyrics_interpolator_accelerate),
        stringResource(R.string.lyrics_interpolator_decelerate),
        stringResource(R.string.lyrics_interpolator_accelerate_decelerate),
        stringResource(R.string.lyrics_interpolator_overshoot),
        stringResource(R.string.lyrics_interpolator_bounce),
    )
    val lyricInterpolatorSelectedOption = remember { mutableIntStateOf(config.lyricInterpolator) }
    val showDialog = remember { mutableStateOf(false) }
    val showLyricWidthDialog = remember { mutableStateOf(false) }
    val showLyricSizeDialog = remember { mutableStateOf(false) }
    val showLyricColorDialog = remember { mutableStateOf(false) }
    val showLyricGradientDialog = remember { mutableStateOf(false) }
    val showLyricGradientBgColorDialog = remember { mutableStateOf(false) }
    val showLyricBgRadiusDialog = remember { mutableStateOf(false) }
    val showLyricLetterSpacingDialog = remember { mutableStateOf(false) }
    val showLyricStrokeWidthDialog = remember { mutableStateOf(false) }
    val showLyricSpeedDialog = remember { mutableStateOf(false) }
    val showLyricTopMarginsDialog = remember { mutableStateOf(false) }
    val showLyricBottomMarginsDialog = remember { mutableStateOf(false) }
    val showLyricStartMarginsDialog = remember { mutableStateOf(false) }
    val showLyricEndMarginsDialog = remember { mutableStateOf(false) }
    val showLyricAnimDurationDialog = remember { mutableStateOf(false) }

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
                title = stringResource(R.string.lyric_page),
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
                        text = stringResource(R.string.module_second)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                    ) {
                        SuperArrow(
                            title = stringResource(R.string.lyric_width),
                            onClick = {
                                showLyricWidthDialog.value = true
                            },
                            holdDownState = showLyricWidthDialog.value
                        )
                        AnimatedVisibility(
                            visible = lyricWidth.value != "0",
                        ) {
                            SuperSwitch(
                                title = stringResource(R.string.fixed_lyric_width),
                                summary = stringResource(R.string.fixed_lyric_width_tips),
                                checked = fixedLyricWidth.value,
                                onCheckedChange = {
                                    fixedLyricWidth.value = it
                                    config.fixedLyricWidth = it
                                    changeConfig()
                                }
                            )
                        }
                        SuperArrow(
                            title = stringResource(R.string.lyric_size),
                            onClick = {
                                showLyricSizeDialog.value = true
                            },
                            holdDownState = showLyricSizeDialog.value
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyric_color_and_transparency),
                            onClick = {
                                showLyricColorDialog.value = true
                            },
                            holdDownState = showLyricColorDialog.value
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyrics_are_gradient_and_transparent),
                            titleColor = BasicComponentDefaults.titleColor(
                                color = MiuixTheme.colorScheme.primary
                            ),
                            rightText = stringResource(R.string.tips1),
                            onClick = {
                                showLyricGradientDialog.value = true
                            },
                            holdDownState = showLyricGradientDialog.value
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyrics_gradient_background_color_and_transparency),
                            onClick = {
                                showLyricGradientBgColorDialog.value = true
                            },
                            holdDownState = showLyricGradientBgColorDialog.value
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyric_background_radius),
                            onClick = {
                                showLyricBgRadiusDialog.value = true
                            },
                            holdDownState = showLyricBgRadiusDialog.value
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyric_letter_spacing),
                            onClick = {
                                showLyricLetterSpacingDialog.value = true
                            },
                            holdDownState = showLyricLetterSpacingDialog.value
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyric_stroke_width),
                            onClick = {
                                showLyricStrokeWidthDialog.value = true
                            },
                            holdDownState = showLyricStrokeWidthDialog.value
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyric_speed),
                            onClick = {
                                showLyricSpeedDialog.value = true
                            },
                            holdDownState = showLyricSpeedDialog.value
                        )
                    }
                    SmallTitle(
                        text = stringResource(R.string.module_fourth),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                    ) {
                        SuperArrow(
                            title = stringResource(R.string.lyric_top_margins),
                            onClick = {
                                showLyricTopMarginsDialog.value = true
                            },
                            holdDownState = showLyricTopMarginsDialog.value
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyric_bottom_margins),
                            onClick = {
                                showLyricBottomMarginsDialog.value = true
                            },
                            holdDownState = showLyricBottomMarginsDialog.value
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyric_start_margins),
                            onClick = {
                                showLyricStartMarginsDialog.value = true
                            },
                            holdDownState = showLyricStartMarginsDialog.value
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyric_end_margins),
                            onClick = {
                                showLyricEndMarginsDialog.value = true
                            },
                            holdDownState = showLyricEndMarginsDialog.value
                        )
                    }
                    SmallTitle(
                        text = stringResource(R.string.module_sixth),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                    ) {
                        SuperDropdown(
                            title = stringResource(R.string.lyrics_animation),
                            items = lyricAnimOptions,
                            selectedIndex = lyricAnimSelectedOption.intValue,
                            onSelectedIndexChange = { newOption ->
                                lyricAnimSelectedOption.intValue = newOption
                                config.lyricAnimation = newOption
                                changeConfig()
                            },
                        )
                        SuperDropdown(
                            title = stringResource(R.string.lyrics_animation_interpolator),
                            items = lyricInterpolatorOptions,
                            selectedIndex = lyricInterpolatorSelectedOption.intValue,
                            onSelectedIndexChange = { newOption ->
                                lyricInterpolatorSelectedOption.intValue = newOption
                                config.lyricInterpolator = newOption
                                changeConfig()
                            },
                        )
                        SuperArrow(
                            title = stringResource(R.string.lyrics_animation_duration),
                            onClick = {
                                showLyricAnimDurationDialog.value = true
                            },
                            holdDownState = showLyricAnimDurationDialog.value
                        )
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
    LyricWidthDialog(showLyricWidthDialog, lyricWidth)
    LyricSizeDialog(showLyricSizeDialog)
    LyricColorDialog(showLyricColorDialog)
    LyricGradientDialog(showLyricGradientDialog)
    LyricGradientBgColorDialog(showLyricGradientBgColorDialog)
    LyricBgRadiusDialog(showLyricBgRadiusDialog)
    LyricLetterSpacingDialog(showLyricLetterSpacingDialog)
    LyricStrokeWidthDialog(showLyricStrokeWidthDialog)
    LyricSpeedDialog(showLyricSpeedDialog)
    LyricTopMarginsDialog(showLyricTopMarginsDialog)
    LyricBottomMarginsDialog(showLyricBottomMarginsDialog)
    LyricStartMarginsDialog(showLyricStartMarginsDialog)
    LyricEndMarginsDialog(showLyricEndMarginsDialog)
    LyricAnimDurationDialog(showLyricAnimDurationDialog)
}

@Composable
fun LyricWidthDialog(showDialog: MutableState<Boolean>, lyricWidth: MutableState<String>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_width),
        summary = stringResource(R.string.lyric_width_tips),
        initialValue = lyricWidth.value.toIntOrNull() ?: config.lyricWidth,
        validRange = 0..100,
        fallbackValue = { 0 },
        onValueChange = {
            config.lyricWidth = it
            lyricWidth.value = it.toString()
        }
    )
}

@Composable
fun LyricSizeDialog(showDialog: MutableState<Boolean>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_size),
        summary = stringResource(R.string.lyric_size_tips),
        initialValue = config.lyricSize,
        validRange = 0..100,
        fallbackValue = { 0 },
        onValueChange = { config.lyricSize = it }
    )
}

@Composable
fun LyricColorDialog(showDialog: MutableState<Boolean>) {
    ColorSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_color_and_transparency),
        summary = stringResource(R.string.lyric_color_and_transparency_tips),
        initialValue = config.lyricColor,
        onValueChange = { config.lyricColor = it }
    )
}

@Composable
fun LyricGradientDialog(showDialog: MutableState<Boolean>) {
    ColorSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyrics_are_gradient_and_transparent),
        summary = stringResource(R.string.lyrics_are_gradient_and_transparent_tips),
        initialValue = config.lyricGradientColor,
        label = "#ff0099,#d508a8,#aa10b8",
        allowMultipleColors = true,
        onValueChange = { config.lyricGradientColor = it }
    )
}

@Composable
fun LyricGradientBgColorDialog(showDialog: MutableState<Boolean>) {
    ColorSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyrics_gradient_background_color_and_transparency),
        summary = stringResource(R.string.lyrics_gradient_background_color_and_transparency_tips),
        initialValue = config.lyricBackgroundColor,
        label = "#00000000",
        allowMultipleColors = true,
        onValueChange = { config.lyricBackgroundColor = it }
    )
}

@Composable
fun LyricBgRadiusDialog(showDialog: MutableState<Boolean>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_background_radius),
        summary = stringResource(R.string.lyric_background_radius_tips),
        initialValue = config.lyricBackgroundRadius,
        validRange = 0..100,
        fallbackValue = { 0 },
        onValueChange = { config.lyricBackgroundRadius = it }
    )
}

@Composable
fun LyricLetterSpacingDialog(showDialog: MutableState<Boolean>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_letter_spacing),
        summary = stringResource(R.string.lyric_letter_spacing_tips),
        initialValue = config.lyricLetterSpacing,
        validRange = 0..50,
        fallbackValue = { 0 },
        onValueChange = { config.lyricLetterSpacing = it }
    )
}

@Composable
fun LyricStrokeWidthDialog(showDialog: MutableState<Boolean>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_stroke_width),
        summary = stringResource(R.string.lyric_stroke_width_tips),
        initialValue = config.lyricStrokeWidth,
        validRange = 0..400,
        fallbackValue = { 0 },
        onValueChange = { config.lyricStrokeWidth = it }
    )
}

@Composable
fun LyricSpeedDialog(showDialog: MutableState<Boolean>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_speed),
        summary = stringResource(R.string.lyric_speed_tips),
        initialValue = config.lyricSpeed,
        validRange = 0..20,
        fallbackValue = { 1 },
        keyboardType = KeyboardType.Number,
        onValueChange = { config.lyricSpeed = it }
    )
}

@Composable
fun LyricTopMarginsDialog(showDialog: MutableState<Boolean>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_top_margins),
        summary = stringResource(R.string.lyric_top_margins_tips),
        initialValue = config.lyricTopMargins,
        validRange = 0..100,
        fallbackValue = { 0 },
        onValueChange = { config.lyricTopMargins = it }
    )
}

@Composable
fun LyricBottomMarginsDialog(showDialog: MutableState<Boolean>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_bottom_margins),
        summary = stringResource(R.string.lyric_bottom_margins_tips),
        initialValue = config.lyricBottomMargins,
        validRange = 0..100,
        fallbackValue = { 0 },
        onValueChange = { config.lyricBottomMargins = it }
    )
}

@Composable
fun LyricStartMarginsDialog(showDialog: MutableState<Boolean>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_start_margins),
        summary = stringResource(R.string.lyric_start_margins_tips),
        initialValue = config.lyricStartMargins,
        validRange = -2000..2000,
        fallbackValue = { if (config.mHyperOSTexture) 20 else 8 },
        onValueChange = { config.lyricStartMargins = it }
    )
}

@Composable
fun LyricEndMarginsDialog(showDialog: MutableState<Boolean>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyric_end_margins),
        summary = stringResource(R.string.lyric_end_margins_tips),
        initialValue = config.lyricEndMargins,
        validRange = -2000..2000,
        fallbackValue = { if (config.mHyperOSTexture) 20 else 10 },
        onValueChange = { config.lyricEndMargins = it }
    )
}

@Composable
fun LyricAnimDurationDialog(showDialog: MutableState<Boolean>) {
    IntSettingDialog(
        showDialog = showDialog,
        title = stringResource(R.string.lyrics_animation_duration),
        summary = stringResource(R.string.lyric_animation_duration_tips),
        initialValue = config.animationDuration,
        validRange = 0..1000,
        fallbackValue = { 300 },
        onValueChange = { config.animationDuration = it }
    )
}
