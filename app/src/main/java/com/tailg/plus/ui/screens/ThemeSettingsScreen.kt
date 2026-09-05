package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.PaletteStyle
import com.tailg.plus.R
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.di.rememberTailgEntryPoint
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.CyberCard
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.CyberSectionLabel
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.cyberCaptionStyle
import com.tailg.plus.ui.components.cyberItemTitleStyle
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.ColorMode
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.ui.theme.LocalCyberPalette
import com.tailg.plus.ui.theme.keyColorOptions
import kotlinx.coroutines.launch

/**
 * Material You theme customiser — port of KernelSU's `ColorPaletteScreen`
 * adapted to the Cyber semantic palette. Lets the user pick theme mode
 * (system / light / dark / AMOLED), a key (seed) colour and a [PaletteStyle];
 * every change is persisted through [AppPreferencesService] and re-themes the
 * whole app instantly via [LocalCyberPalette].
 */
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    preferencesService: AppPreferencesService? = null,
) {
    val scope = rememberCoroutineScope()
    val prefs = preferencesService ?: rememberTailgEntryPoint().appPreferences()
    val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ColorMode.SYSTEM.value)
    val keyColor by prefs.keyColor.collectAsStateWithLifecycle(initialValue = 0)
    val colorStyleName by prefs.colorStyle.collectAsStateWithLifecycle(initialValue = PaletteStyle.TonalSpot.name)
    LaunchedEffect(Unit) { prefs.init() }

    val colorMode = ColorMode.fromValue(themeMode)
    val paletteStyle = try {
        PaletteStyle.valueOf(colorStyleName)
    } catch (_: Exception) {
        PaletteStyle.TonalSpot
    }

    Scaffold(containerColor = CyberHomeColors.pageBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CyberPageHeader(title = stringResource(R.string.settings_theme), onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                ThemePreviewCard()
                CyberSectionLabel(stringResource(R.string.settings_ui_mode))
                ThemeModeSelector(
                    selected = colorMode,
                    onSelect = { mode -> scope.launch { prefs.setThemeMode(mode.value) } },
                )
                CyberSectionLabel(stringResource(R.string.theme_key_color))
                KeyColorPicker(
                    selected = keyColor,
                    onSelect = { color -> scope.launch { prefs.setKeyColor(color) } },
                )
                CyberSectionLabel(stringResource(R.string.theme_color_style))
                PaletteStylePicker(
                    selected = paletteStyle,
                    onSelect = { style -> scope.launch { prefs.setColorStyle(style.name) } },
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewCard() {
    val p = LocalCyberPalette.current
    CyberCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Aa 123",
                style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.W800, color = p.ink),
            )
            Spacer(Modifier.height(6.dp))
            Text(text = stringResource(R.string.settings_theme), style = cyberCaptionStyle)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(AppRadii.tile))
                        .background(p.primary),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(AppRadii.tile))
                        .background(p.primarySoft),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(AppRadii.tile))
                        .background(p.cardMuted)
                        .border(1.dp, p.line, RoundedCornerShape(AppRadii.tile)),
                )
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selected: ColorMode,
    onSelect: (ColorMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ColorMode.entries.forEach { mode ->
            val isSelected = mode == selected
            AppPressable(
                onClick = { onSelect(mode) },
                shape = RoundedCornerShape(AppRadii.pill),
                background = if (isSelected) CyberHomeColors.primary else CyberHomeColors.control,
                semanticsLabel = colorModeLabel(mode),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = colorModeLabel(mode),
                        maxLines = 1,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.W700 else FontWeight.W600,
                            color = if (isSelected) CyberHomeColors.white else CyberHomeColors.inkMuted,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun colorModeLabel(mode: ColorMode): String = when (mode) {
    ColorMode.SYSTEM -> stringResource(R.string.theme_mode_system)
    ColorMode.LIGHT -> stringResource(R.string.theme_mode_light)
    ColorMode.DARK -> stringResource(R.string.theme_mode_dark)
    ColorMode.DARK_AMOLED -> stringResource(R.string.theme_mode_amoled)
}

@Composable
private fun KeyColorPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            KeyColorSwatch(color = 0, isSelected = selected == 0, onClick = { onSelect(0) })
        }
        items(keyColorOptions) { color ->
            KeyColorSwatch(color = color, isSelected = selected == color, onClick = { onSelect(color) })
        }
    }
}

@Composable
private fun KeyColorSwatch(
    color: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    AppPressable(
        onClick = onClick,
        shape = CircleShape,
        semanticsLabel = if (color == 0) stringResource(R.string.theme_key_color_system) else null,
        modifier = Modifier.size(46.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberHomeColors.primary),
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(
                        if (color == 0) {
                            Modifier.background(
                                Brush.linearGradient(
                                    listOf(CyberHomeColors.primary, CyberHomeColors.rideAccent),
                                ),
                            )
                        } else {
                            Modifier.background(Color(color))
                        },
                    )
                    .border(1.dp, CyberHomeColors.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (color == 0) {
                    LucideIcon(icon = Lucide.spark, size = 16.dp, color = CyberHomeColors.white)
                }
            }
        }
    }
}

@Composable
private fun PaletteStylePicker(
    selected: PaletteStyle,
    onSelect: (PaletteStyle) -> Unit,
) {
    CyberCard(
        modifier = Modifier.padding(horizontal = 20.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            PaletteStyle.values().forEachIndexed { index, style ->
                if (index > 0) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = CyberHomeColors.line,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(style) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = style.name,
                        modifier = Modifier.weight(1f),
                        style = cyberItemTitleStyle,
                    )
                    if (style == selected) {
                        LucideIcon(icon = Lucide.check, size = 18.dp, color = CyberHomeColors.primary)
                    }
                }
            }
        }
    }
}
