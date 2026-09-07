package com.tailg.plus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.ui.theme.VectorFontFamily

/** The system page shares the visual language of the vehicle instrument. */
@Composable
internal fun VectorSettingsHeader(onTheme: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 20.dp).testTag("vectorSettingsHeader")) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("VECTOR", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp), color = colors.onSurface)
            Text("04 / SYSTEM", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            color = colors.onSurface,
        )
        Text(
            stringResource(R.string.vector_settings_caption),
            Modifier.padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        AppPressable(
            onClick = onTheme,
            modifier = Modifier.fillMaxWidth().testTag("vectorAppearanceCard"),
            shape = CutCornerShape(topEnd = 28.dp, bottomStart = 12.dp),
            background = colors.secondaryContainer,
            semanticsLabel = stringResource(R.string.settings_theme),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 190.dp)) {
                VectorConstructionLines(colors, Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        stringResource(R.string.vector_appearance_kicker),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSecondaryContainer,
                    )
                    BasicText(
                        "VECTOR",
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.displayMedium.copy(color = colors.onSecondaryContainer),
                        autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 54.sp),
                        maxLines = 1,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.vector_appearance_action),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSecondaryContainer,
                        )
                        NinebotIcon(NinebotLucide.arrowUpRight, size = 26.dp, color = colors.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
internal fun VectorSettingsSection(index: String, title: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, top = 26.dp, end = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(index, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelMedium, color = colors.primary)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
    }
}

/** A miniature of the graphic treatment, without invented vehicle readings. */
@Composable
internal fun VectorThemeMiniature(colors: ColorScheme, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .clip(CutCornerShape(topEnd = 14.dp))
                .background(colors.secondaryContainer),
        ) {
            VectorConstructionLines(colors, Modifier.matchParentSize())
            BasicText(
                "V.",
                Modifier.align(Alignment.BottomStart).padding(8.dp),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = VectorFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSecondaryContainer,
                ),
                autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 70.sp),
                maxLines = 1,
            )
            NinebotIcon(
                NinebotLucide.arrowUpRight,
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                color = colors.onSecondaryContainer,
                size = 18.dp,
            )
        }
        Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (icon in listOf(NinebotLucide.zap, NinebotLucide.lock, NinebotLucide.mapPin)) {
                Box(
                    Modifier.weight(1f).height(36.dp)
                        .clip(MaterialTheme.shapes.small).background(colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    NinebotIcon(icon, size = 16.dp, color = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun VectorConstructionLines(colors: ColorScheme, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val ink = colors.onSecondaryContainer.copy(alpha = 0.12f)
        val origin = Offset(size.width * 0.92f, size.height * 0.12f)
        for (step in 1..4) {
            drawCircle(ink, size.width * (0.16f + step * 0.13f), origin, style = Stroke(1.dp.toPx()))
        }
        drawLine(ink, Offset(size.width * 0.55f, 0f), Offset(size.width, size.height * 0.8f), 1.dp.toPx())
        drawLine(ink, Offset(size.width * 0.75f, 0f), Offset(size.width * 0.3f, size.height), 1.dp.toPx())
    }
}
