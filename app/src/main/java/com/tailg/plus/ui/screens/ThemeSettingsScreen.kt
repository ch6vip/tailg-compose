package com.tailg.plus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.tailg.plus.R
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.di.rememberTailgEntryPoint
import com.tailg.plus.ui.components.material.ExpressiveScaffold
import com.tailg.plus.ui.components.material.ExpressiveToggleButton
import com.tailg.plus.ui.components.material.SegmentedColumn
import com.tailg.plus.ui.components.material.SegmentedDropdownItem
import com.tailg.plus.ui.components.material.SegmentedSwitchItem
import com.tailg.plus.ui.components.material.TonalCard
import com.tailg.plus.ui.components.material.TopBarBackButton
import com.tailg.plus.ui.components.material.expressiveTopAppBarColors
import com.tailg.plus.ui.theme.ColorMode
import com.tailg.plus.ui.theme.CyberDarkColorScheme
import com.tailg.plus.ui.theme.CyberLightColorScheme
import com.tailg.plus.ui.theme.UiMode
import com.tailg.plus.ui.theme.amoledBackground
import com.tailg.plus.ui.theme.keyColorOptions
import com.tailg.plus.ui.theme.rememberTailgColorScheme
import kotlinx.coroutines.launch

/**
 * Faithful port of KernelSU's theme customiser (ColorPaletteScreen, following
 * the Miuix variant's structure): mini-phone preview card, text mode tabs
 * (跟随系统/浅色/深色), an "启用 Monet 颜色" switch that gates the dynamic
 * colour engine (off = static Cyber brand colours), pie-slice key-colour
 * swatches and segmented dropdowns for palette style / colour spec. Every
 * change persists through [AppPreferencesService] and re-themes the whole app
 * live.
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
    val keyColor by prefs.keyColor.collectAsStateWithLifecycle(initialValue = 0)
    val colorStyleName by prefs.colorStyle.collectAsStateWithLifecycle(initialValue = PaletteStyle.TonalSpot.name)
    val colorSpecName by prefs.colorSpec.collectAsStateWithLifecycle(initialValue = ColorSpec.SpecVersion.SPEC_2025.name)
    val pageScale by prefs.pageScale.collectAsStateWithLifecycle(initialValue = 1.0f)
    LaunchedEffect(Unit) { prefs.init() }

    val currentColorMode = ColorMode.fromValue(themeMode)
    val monetOn = UiMode.fromValue(uiModeValue) == UiMode.MONET
    val colorStyle = try {
        PaletteStyle.valueOf(colorStyleName)
    } catch (_: Exception) {
        PaletteStyle.TonalSpot
    }
    val colorSpec = try {
        ColorSpec.SpecVersion.valueOf(colorSpecName)
    } catch (_: Exception) {
        ColorSpec.SpecVersion.SPEC_2025
    }
    val haptic = LocalHapticFeedback.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = {
                    TopBarBackButton(onClick = onBack)
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
            val isDark = currentColorMode.isDark || currentColorMode.isSystem && isSystemInDarkTheme()
            val isAmoled = currentColorMode.isAmoled
            ThemePreviewCard(
                keyColor = keyColor,
                isDark = isDark,
                isAmoled = isAmoled,
                monet = monetOn,
                paletteStyle = colorStyle,
                colorSpec = colorSpec,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Key-colour swatches only drive the Monet engine — hidden while
            // Monet is off, same as KernelSU Miuix's colour card.
            AnimatedVisibility(visible = monetOn) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                item {
                    ColorButtonMaterial(
                        color = Color.Unspecified,
                        isSelected = keyColor == 0,
                        isDark = isDark,
                        isAmoled = isAmoled,
                        paletteStyle = colorStyle,
                        colorSpec = colorSpec,
                        onClick = {
                            scope.launch { prefs.setKeyColor(0) }
                        }
                    )
                }

                items(keyColorOptions) { color ->
                    ColorButtonMaterial(
                        color = Color(color),
                        isSelected = keyColor == color,
                        isDark = isDark,
                        isAmoled = isAmoled,
                        paletteStyle = colorStyle,
                        colorSpec = colorSpec,
                        onClick = { seed ->
                            scope.launch { prefs.setKeyColor(seed) }
                        }
                    )
                }
                }
            }

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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    modeOptions.forEachIndexed { index, (mode, label) ->
                        ExpressiveToggleButton(
                            checked = selectedTabIndex == index,
                            onCheckedChange = {
                                if (it) {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    scope.launch { prefs.setThemeMode(mode.value) }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modeOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Monet switch + colour controls — the dynamic engine is gated
                // here, exactly like KernelSU Miuix's "启用 Monet 颜色" card.
                SegmentedColumn(
                    modifier = Modifier.padding(top = 4.dp),
                    content = {
                        item("monet") {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.Wallpaper,
                                title = stringResource(R.string.theme_enable_monet),
                                summary = stringResource(R.string.theme_enable_monet_summary),
                                checked = monetOn,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        prefs.setUiMode(if (on) UiMode.MONET.value else UiMode.CYBER.value)
                                    }
                                }
                            )
                        }
                        item("style", visible = monetOn) {
                            val styles = PaletteStyle.entries
                            SegmentedDropdownItem(
                                icon = Icons.Rounded.Style,
                                title = stringResource(R.string.theme_color_style),
                                items = styles.map { it.name },
                                selectedIndex = styles.indexOf(colorStyle),
                                onItemSelected = { index ->
                                    scope.launch { prefs.setColorStyle(styles[index].name) }
                                }
                            )
                        }
                        item("spec", visible = monetOn) {
                            val specs = ColorSpec.SpecVersion.entries
                            SegmentedDropdownItem(
                                icon = Icons.Rounded.DesignServices,
                                title = stringResource(R.string.theme_color_spec),
                                items = specs.map { it.name },
                                selectedIndex = specs.indexOf(colorSpec).coerceAtLeast(0),
                                onItemSelected = { index ->
                                    scope.launch { prefs.setColorSpec(specs[index].name) }
                                }
                            )
                        }
                    }
                )

                TonalCard(modifier = Modifier.padding(top = 4.dp)) {
                    var sliderValue by remember(pageScale) { mutableFloatStateOf(pageScale) }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.AspectRatio,
                                contentDescription = stringResource(id = R.string.theme_page_scale),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                scope.launch { prefs.setPageScale(sliderValue) }
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
 * Mini-phone mockup preview (KernelSU's `ThemePreviewCard`, bottom-bar
 * variant — Tailg has no navigation rail).
 */
@Composable
private fun ThemePreviewCard(
    keyColor: Int,
    isDark: Boolean,
    isAmoled: Boolean = false,
    monet: Boolean = true,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = screenWidth / screenHeight

    val colorScheme = if (monet) {
        rememberTailgColorScheme(
            seedColor = if (keyColor == 0) Color.Unspecified else Color(keyColor),
            isDark = isDark,
            isAmoled = isAmoled,
            paletteStyle = paletteStyle,
            colorSpec = colorSpec,
        )
    } else {
        // Monet off → static Cyber brand colours, mirroring TailgTheme.
        (if (isDark) CyberDarkColorScheme else CyberLightColorScheme).amoledBackground(isAmoled)
    }

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

                // bottom bar
                Surface(
                    color = colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Home, null, tint = colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pie-slice key-colour swatch (KernelSU's `ColorButtonMaterial`): a half
 * primaryContainer / half tertiaryContainer disc previewing the generated
 * scheme, with an animated selection ring. [onClick] receives the seed ARGB
 * (0 for the dynamic/wallpaper swatch when [color] is unspecified).
 */
@Composable
private fun ColorButtonMaterial(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    isAmoled: Boolean = false,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    onClick: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val colorScheme = rememberTailgColorScheme(
        seedColor = color,
        isDark = isDark,
        isAmoled = isAmoled,
        paletteStyle = paletteStyle,
        colorSpec = colorSpec,
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick(if (color == Color.Unspecified) 0 else color.toArgb())
        },
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainer,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(48.dp)) {
                drawArc(
                    color = colorScheme.primaryContainer,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true
                )
                drawArc(
                    color = colorScheme.tertiaryContainer,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true
                )
            }

            val scale by animateFloatAsState(targetValue = if (isSelected) 1.1f else 1.0f)
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(2.dp, colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(16.dp)
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}
