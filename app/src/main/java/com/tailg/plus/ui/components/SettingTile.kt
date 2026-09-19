package com.tailg.plus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Shared settings row in the 九号 visual language: large-radius white cards,
 * circular line-icon wells, ink titles. Core tiles use a larger well so
 * vehicle / BMS still read as primary.
 *
 * Note: 设置页核心/普通分层与 SettingTile — 见 .agents/notes/implemented/feature/2026-09-20-settings-hierarchy.md
 */
enum class SettingTileEmphasis {
  Core,
  Standard,
}

data class SettingItemModel(
  @DrawableRes val icon: Int,
  val title: String,
  val subtitle: String? = null,
  val trailing: @Composable (() -> Unit)? = null,
  val onClick: (() -> Unit)? = null,
  val showChevron: Boolean = true,
  val emphasis: SettingTileEmphasis = SettingTileEmphasis.Standard,
)

fun settingItemModel(
  @DrawableRes icon: Int,
  title: String,
  subtitle: String? = null,
  trailing: @Composable (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  showChevron: Boolean = true,
  emphasis: SettingTileEmphasis = SettingTileEmphasis.Standard,
): SettingItemModel = SettingItemModel(
  icon = icon,
  title = title,
  subtitle = subtitle,
  trailing = trailing,
  onClick = onClick,
  showChevron = showChevron,
  emphasis = emphasis,
)

internal val NinebotSettingsCardShape = RoundedCornerShape(28.dp)

@Composable
internal fun ninebotSettingsDark(): Boolean =
  MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
internal fun ninebotSettingsCardColor(): Color =
  if (ninebotSettingsDark()) Color(0xFF2C2F34) else Color.White

@Composable
internal fun ninebotSettingsCircleColor(): Color =
  if (ninebotSettingsDark()) Color(0xFF3A3E44) else Color(0xFFEEF0F5)

@Composable
fun SettingsGroup(
  vararg items: SettingItemModel,
  modifier: Modifier = Modifier,
) {
  SettingsGroup(modifier = modifier) {
    items.forEachIndexed { index, item ->
      if (index > 0) {
        HorizontalDivider(
          thickness = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f),
          modifier = Modifier.padding(start = 72.dp),
        )
      }
      SettingTile(item)
    }
  }
}

@Composable
fun SettingsGroup(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(NinebotSettingsCardShape)
      .background(ninebotSettingsCardColor()),
    content = content,
  )
}

@Composable
fun SettingTile(
  item: SettingItemModel,
  modifier: Modifier = Modifier,
) {
  SettingTile(
    icon = item.icon,
    title = item.title,
    modifier = modifier,
    subtitle = item.subtitle,
    emphasis = item.emphasis,
    trailing = item.trailing,
    showChevron = item.showChevron,
    onClick = item.onClick,
  )
}

@Composable
fun SettingTile(
  @DrawableRes icon: Int,
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  emphasis: SettingTileEmphasis = SettingTileEmphasis.Standard,
  trailing: @Composable (() -> Unit)? = null,
  showChevron: Boolean = true,
  onClick: (() -> Unit)? = null,
) {
  val core = emphasis == SettingTileEmphasis.Core
  val well = if (core) 44.dp else 40.dp
  AppPressable(
    onClick = onClick,
    enabled = onClick != null,
    pressedScale = 0.985f,
    semanticsButton = onClick != null,
  ) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .heightIn(min = AppTouchTargets.min)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(well)
          .clip(CircleShape)
          .background(ninebotSettingsCircleColor()),
        contentAlignment = Alignment.Center,
      ) {
        NinebotIcon(
          icon = icon,
          size = if (core) 22.dp else 20.dp,
          color = CyberHomeColors.ink,
        )
      }
      Spacer(Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          fontSize = 16.sp,
          fontWeight = FontWeight.W700,
          color = CyberHomeColors.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
          Spacer(Modifier.height(2.dp))
          Text(
            text = subtitle,
            fontSize = 12.sp,
            color = CyberHomeColors.inkFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (trailing != null) {
        Spacer(Modifier.width(8.dp))
        trailing()
      } else if (showChevron && onClick != null) {
        NinebotIcon(
          icon = NinebotLucide.chevronRight,
          size = 16.dp,
          color = CyberHomeColors.inkFaint,
        )
      }
    }
  }
}

@Composable
fun SettingFeatureTile(
  @DrawableRes icon: Int,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AppPressable(
    onClick = onClick,
    modifier = modifier,
    pressedScale = 0.985f,
    shape = NinebotSettingsCardShape,
    semanticsButton = true,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 108.dp)
        .clip(NinebotSettingsCardShape)
        .background(ninebotSettingsCardColor())
        .padding(PaddingValues(16.dp)),
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(ninebotSettingsCircleColor()),
        contentAlignment = Alignment.Center,
      ) {
        NinebotIcon(icon = icon, size = 22.dp, color = CyberHomeColors.ink)
      }
      Spacer(Modifier.height(14.dp))
      Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.W700,
        color = CyberHomeColors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = subtitle,
        fontSize = 12.sp,
        color = CyberHomeColors.inkFaint,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun SettingsSectionLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    fontSize = 13.sp,
    fontWeight = FontWeight.W700,
    color = CyberHomeColors.inkMuted,
    modifier = modifier.padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
  )
}
