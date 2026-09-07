package com.tailg.plus.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tailg.plus.R
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.di.rememberTailgEntryPoint
import com.tailg.plus.ui.components.BottomNavDestination
import com.tailg.plus.ui.components.NinebotIcon
import com.tailg.plus.ui.components.NinebotLucide
import com.tailg.plus.ui.components.material.ExpressiveScaffold
import com.tailg.plus.ui.components.material.ExpressiveSwitch
import com.tailg.plus.ui.components.material.ExpressiveToggleButton
import com.tailg.plus.ui.components.material.TonalCard
import com.tailg.plus.ui.components.material.expressiveTopAppBarColors
import com.tailg.plus.ui.theme.CyberDarkColorScheme
import com.tailg.plus.ui.theme.CyberLightColorScheme
import com.tailg.plus.ui.theme.ColorMode
import com.tailg.plus.ui.theme.NinebotDarkColorScheme
import com.tailg.plus.ui.theme.NinebotLightColorScheme
import com.tailg.plus.ui.theme.UiMode
import com.tailg.plus.ui.theme.amoledBackground
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHostState
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.AppSnackbarHost

/**
 * Theme settings — KernelSU-derived chrome with the Tailg skin system:
 * mini-phone preview (follows the active UI style), text mode tabs
 * (跟随系统/浅色/深色), optional floating navigation and the page-scale slider. UI style
 * (Cyber / 九号) is picked in 设置 → 界面风格.
 */
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    preferencesService: AppPreferencesService? = null,
) {
    val scope = rememberCoroutineScope()
    val prefs = preferencesService ?: rememberTailgEntryPoint().appPreferences()
    val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ColorMode.SYSTEM.value)
    val uiModeValue by prefs.uiMode.collectAsStateWithLifecycle(initialValue = UiMode.CYBER.value)
    val pageScale by prefs.pageScale.collectAsStateWithLifecycle(initialValue = 1.0f)
    val floatingBottomBar by prefs.floatingBottomBar.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(prefs) { AppSnack.runAction(snackbarHostState) { prefs.init() } }

    val currentColorMode = ColorMode.fromValue(themeMode)
    val currentUiMode = UiMode.fromValue(uiModeValue)
    val haptic = LocalHapticFeedback.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(horizontal = 10.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        NinebotIcon(
                            NinebotLucide.arrowLeft,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.settings_theme)) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        val navBars = WindowInsets.navigationBars.asPaddingValues()
        val captionBar = WindowInsets.captionBar.asPaddingValues()

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isDark = currentColorMode.isDark || (currentColorMode.isSystem && isSystemInDarkTheme())
            val isAmoled = currentColorMode.isAmoled
            ThemePreviewCard(
                uiMode = currentUiMode,
                isDark = isDark,
                isAmoled = isAmoled,
                floatingBottomBar = floatingBottomBar,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Text mode tabs (KernelSU Miuix variant's TabRow): 跟随系统/浅色/深色.
                val modeOptions = listOf(
                    ColorMode.SYSTEM to stringResource(R.string.theme_mode_system),
                    ColorMode.LIGHT to stringResource(R.string.theme_mode_light),
                    ColorMode.DARK to stringResource(R.string.theme_mode_dark),
                )
                val selectedTabIndex = when (currentColorMode) {
                    ColorMode.SYSTEM -> 0
                    ColorMode.LIGHT -> 1
                    ColorMode.DARK, ColorMode.DARK_AMOLED -> 2
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    modeOptions.forEachIndexed { index, (mode, label) ->
                        ExpressiveToggleButton(
                            checked = selectedTabIndex == index,
                            onCheckedChange = {
                                if (it) {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    scope.launch { AppSnack.runAction(snackbarHostState) { prefs.setThemeMode(mode.value) } }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modeOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            BasicText(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = LocalContentColor.current,
                                    textAlign = TextAlign.Center,
                                ),
                                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp),
                                maxLines = 2,
                            )
                        }
                    }
                }

                TonalCard(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("theme-floating-bottom-bar-toggle")
                            .toggleable(
                                value = floatingBottomBar,
                                role = Role.Switch,
                                onValueChange = { enabled ->
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    scope.launch { AppSnack.runAction(snackbarHostState) { prefs.setFloatingBottomBar(enabled) } }
                                },
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NinebotIcon(NinebotLucide.panelBottom, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.theme_floating_bottom_bar), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.theme_floating_bottom_bar_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ExpressiveSwitch(
                            checked = floatingBottomBar,
                            onCheckedChange = null,
                            thumbContent = {
                                NinebotIcon(
                                    if (floatingBottomBar) NinebotLucide.check else NinebotLucide.x,
                                    size = 16.dp,
                                    color = if (floatingBottomBar) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            },
                        )
                    }
                }

                TonalCard {
                    var sliderValue by remember(pageScale) { mutableFloatStateOf(pageScale) }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NinebotIcon(
                                NinebotLucide.scaling,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.theme_page_scale),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(id = R.string.theme_page_scale_summary),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${(sliderValue * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                scope.launch {
                                    if (!AppSnack.runAction(snackbarHostState) { prefs.setPageScale(sliderValue) }) {
                                        sliderValue = prefs.pageScale.value
                                    }
                                }
                            },
                            valueRange = 0.8f..1.1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp + navBars.calculateBottomPadding() + captionBar.calculateBottomPadding()))
        }
    }
}

/**
 * Mini-phone mockup preview rendering the active UI style (Cyber or 九号)
 * under the given dark/AMOLED combination.
 */
@Composable
private fun ThemePreviewCard(
    uiMode: UiMode,
    isDark: Boolean,
    isAmoled: Boolean = false,
    floatingBottomBar: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = screenWidth / screenHeight

    val colorScheme = when (uiMode) {
        UiMode.CYBER -> if (isDark) CyberDarkColorScheme else CyberLightColorScheme
        UiMode.NINEBOT -> if (isDark) NinebotDarkColorScheme else NinebotLightColorScheme
    }.amoledBackground(isAmoled)
    val barHorizontalPadding by animateDpAsState(if (floatingBottomBar) 8.dp else 6.dp, label = "previewBarMargin")
    val barBottomPadding by animateDpAsState(if (floatingBottomBar) 10.dp else 4.dp, label = "previewBarBottom")

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio),
            color = colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, color = colorScheme.outlineVariant)
        ) {
            Column {
                // top bar
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface
                        )
                    }
                }

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val showInfoCard = maxHeight >= 72.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TonalCard(
                            containerColor = colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            content = { }
                        )
                        if (showInfoCard) {
                            TonalCard(
                                containerColor = colorScheme.surfaceBright,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                content = { }
                            )
                        }
                    }
                }

                // The miniature mirrors the selected appearance without navigating away.
                Surface(
                    color = colorScheme.surfaceContainer,
                    modifier = Modifier
                        .padding(horizontal = barHorizontalPadding)
                        .padding(top = 4.dp, bottom = barBottomPadding)
                        .fillMaxWidth()
                        .testTag(if (floatingBottomBar) "theme-preview-floating-bar" else "theme-preview-classic-bar"),
                    shape = CircleShape,
                    shadowElevation = if (floatingBottomBar) 4.dp else 0.dp,
                    border = BorderStroke(0.5.dp, colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .fillMaxWidth()
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomNavDestination.entries.forEachIndexed { index, destination ->
                            val selected = index == BottomNavDestination.CONTROL.ordinal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .background(
                                        if (selected) {
                                            if (floatingBottomBar) colorScheme.primary.copy(alpha = 0.15f)
                                            else colorScheme.secondaryContainer
                                        } else androidx.compose.ui.graphics.Color.Transparent,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                NinebotIcon(
                                    destination.iconRes,
                                    size = 14.dp,
                                    color = if (selected && floatingBottomBar) colorScheme.primary else colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
