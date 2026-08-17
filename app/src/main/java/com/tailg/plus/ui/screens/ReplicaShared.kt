package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppSpacing
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Shared UI pieces of [OfficialReplicaScreen]: page header, notice banner,
 * empty-state card, metric block and circular icon.
 */

@Composable
internal fun ReplicaPageHeader(
  title: String,
  actionIcon: androidx.compose.ui.graphics.vector.ImageVector?,
  actionLabel: String?,
  onAction: (() -> Unit)?,
  onBack: () -> Unit,
) {
  Row(
    modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppPressable(
      onClick = onBack,
      shape = CircleShape,
      background = CyberHomeColors.card,
      shadowElevation = 4.dp,
      shadowColor = CyberHomeColors.actionShadow,
      semanticsLabel = stringResource(R.string.common_back),
    ) {
      Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
        LucideIcon(icon = Lucide.arrowLeft, size = 20.dp, color = CyberHomeColors.inkSecondary)
      }
    }
    Spacer(Modifier.width(12.dp))
    Text(
      text = title,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      modifier = Modifier.weight(1f),
    )
    if (actionIcon != null && actionLabel != null && onAction != null) {
      AppPressable(
        onClick = onAction,
        shape = CircleShape,
        semanticsLabel = actionLabel,
      ) {
        Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
          LucideIcon(icon = actionIcon, size = 20.dp, color = CyberHomeColors.inkSecondary)
        }
      }
    }
  }
}
@Composable
internal fun ReplicaNotice(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.primarySoft)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
    verticalAlignment = Alignment.Top,
  ) {
    CircleIcon(icon = icon)
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = subtitle,
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
      )
    }
  }
}

@Composable
internal fun EmptyReplicaCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = AppSpacing.screenX)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LucideIcon(icon = icon, size = AppIconSizes.xl, color = CyberHomeColors.inkFaint)
    Spacer(Modifier.height(10.dp))
    Text(
      text = title,
      style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = subtitle,
      textAlign = TextAlign.Center,
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
    )
  }
}
@Composable
internal fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
  }
}
@Composable
internal fun CircleIcon(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  color: androidx.compose.ui.graphics.Color = CyberHomeColors.primary,
) {
  Box(
    modifier = Modifier
      .size(42.dp)
      .clip(CircleShape)
      .background(color.copy(alpha = 0.1f)),
    contentAlignment = Alignment.Center,
  ) {
    LucideIcon(icon = icon, color = color, size = AppIconSizes.md)
  }
}
