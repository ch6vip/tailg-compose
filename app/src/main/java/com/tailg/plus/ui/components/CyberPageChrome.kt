package com.tailg.plus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Port of `lib/widgets/cyber_page_chrome.dart` — light-Cyber page chrome
 * (header, section labels, cards, empty state) + button/text-field styles.
 *
 * All colors come from the [CyberHomeColors] token set (ported 1:1).
 * Dart `cyber*Style` consts → [TextStyle] vals; `AppShadows.cyberActionShadow`
 * / `cyberCardShadow` → [Modifier.shadow] with the same-named tokens.
 *
 * Icons: `Lucide.arrow-left` → `Icons.Filled.ArrowBack`.
 */

val cyberPageTitleStyle = TextStyle(
  fontSize = 24.sp,
  fontWeight = FontWeight.W700,
  color = CyberHomeColors.ink,
)

val cyberSectionTitleStyle = TextStyle(
  fontSize = 13.sp,
  fontWeight = FontWeight.W700,
  color = CyberHomeColors.inkMuted,
)

val cyberItemTitleStyle = TextStyle(
  fontSize = 15.sp,
  fontWeight = FontWeight.W700,
  color = CyberHomeColors.ink,
)

val cyberBodyStyle = TextStyle(
  fontSize = 13.sp,
  lineHeight = 13.sp * 1.45f,
  color = CyberHomeColors.inkMuted,
)

val cyberCaptionStyle = TextStyle(
  fontSize = 12.sp,
  lineHeight = 12.sp * 1.4f,
  color = CyberHomeColors.inkFaint,
)

/** CyberPageHeader — back circle + title + trailing actions. */
@Composable
fun CyberPageHeader(
  title: String,
  modifier: Modifier = Modifier,
  showBack: Boolean = true,
  onBack: (() -> Unit)? = null,
  actions: (@Composable RowScope.() -> Unit)? = null,
) {
  Row(
    modifier = modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showBack) {
      AppPressable(
        onClick = onBack,
        shape = CircleShape,
        background = CyberHomeColors.card,
        shadowElevation = 4.dp,
        shadowColor = CyberHomeColors.actionShadow,
        semanticsLabel = stringResource(R.string.common_back),
      ) {
        Box(
          modifier = Modifier.size(AppTouchTargets.min),
          contentAlignment = Alignment.Center,
        ) {
          LucideIcon(icon = Lucide.arrowLeft, size = 20.dp, color = CyberHomeColors.inkSecondary)
        }
      }
      Spacer(Modifier.width(12.dp))
    }
    Text(
      text = title,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = cyberPageTitleStyle,
      modifier = Modifier.weight(1f),
    )
    actions?.invoke(this)
  }
}

/** CyberHeaderAction — 44dp icon action (disabled = faint icon). */
@Composable
fun CyberHeaderAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  onTap: (() -> Unit)? = null,
) {
  AppPressable(
    onClick = onTap,
    enabled = onTap != null,
    shape = CircleShape,
    semanticsLabel = label,
  ) {
    Box(
      modifier = Modifier.size(AppTouchTargets.min),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(
        icon = icon,
        size = 20.dp,
        color = if (onTap == null) CyberHomeColors.inkFaint else CyberHomeColors.inkSecondary,
      )
    }
  }
}

/** CyberSectionLabel — uppercase-feel section title row. */
@Composable
fun CyberSectionLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = text, style = cyberSectionTitleStyle)
  }
}

/** CyberCard — white Cyber card with shadow (Dart `cyberCardShadow`). */
@Composable
fun CyberCard(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(14.dp),
  onClick: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(AppRadii.sheet)
  val base = modifier
    .shadow(
      elevation = 6.dp,
      shape = shape,
      clip = false,
      ambientColor = Color.Transparent,
      spotColor = CyberHomeColors.actionShadow,
    )
    .clip(shape)
    .background(CyberHomeColors.card)
  Box(modifier = if (onClick != null) base.pressableClick(onClick) else base) {
    Box(modifier = Modifier.padding(contentPadding)) {
      content()
    }
  }
}

/** CyberEmptyState — icon glyph + title + optional subtitle. */
@Composable
fun CyberEmptyState(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
) {
  Column(
    modifier = modifier.padding(horizontal = 40.dp, vertical = 36.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(CircleShape)
        .background(CyberHomeColors.control),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = icon, size = 24.dp, color = CyberHomeColors.inkMuted)
    }
    Spacer(Modifier.height(14.dp))
    Text(text = title, textAlign = TextAlign.Center, style = cyberItemTitleStyle)
    if (subtitle != null) {
      Spacer(Modifier.height(6.dp))
      Text(text = subtitle, textAlign = TextAlign.Center, style = cyberCaptionStyle)
    }
  }
}

/**
 * Cyber outlined text-field colors (Dart `cyberInputDecoration`) —
 * white-ish container, strong line on focus.
 */
@Composable
fun cyberTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
  focusedTextColor = CyberHomeColors.ink,
  unfocusedTextColor = CyberHomeColors.ink,
  disabledTextColor = CyberHomeColors.inkFaint,
  cursorColor = CyberHomeColors.primary,
  focusedBorderColor = CyberHomeColors.primary,
  unfocusedBorderColor = CyberHomeColors.lineStrong,
  disabledBorderColor = CyberHomeColors.line,
  focusedContainerColor = CyberHomeColors.card,
  unfocusedContainerColor = CyberHomeColors.card,
  disabledContainerColor = CyberHomeColors.cardMuted,
  // Dart `_inputDecoration`: hintStyle = inkFaint, errorText = danger.
  // Placeholder raised to inkMuted for readable contrast on white fields.
  focusedPlaceholderColor = CyberHomeColors.inkMuted,
  unfocusedPlaceholderColor = CyberHomeColors.inkMuted,
  disabledPlaceholderColor = CyberHomeColors.inkMuted,
  errorPlaceholderColor = CyberHomeColors.inkMuted,
  focusedSupportingTextColor = CyberHomeColors.inkMuted,
  unfocusedSupportingTextColor = CyberHomeColors.inkMuted,
  disabledSupportingTextColor = CyberHomeColors.inkFaint,
  errorSupportingTextColor = CyberHomeColors.danger,
  focusedLabelColor = CyberHomeColors.inkMuted,
  unfocusedLabelColor = CyberHomeColors.inkMuted,
  disabledLabelColor = CyberHomeColors.inkFaint,
  errorLabelColor = CyberHomeColors.danger,
)

/** Cyber outlined text-field shape (Dart `AppRadii.tile`). */
val cyberTextFieldShape = RoundedCornerShape(AppRadii.tile)

/**
 * Cyber filled-button colors (Dart `cyberFilledButtonStyle`) — primary fill,
 * 48dp min height is applied by the screen (`ButtonDefaults` has no 48dp token).
 */
@Composable
fun cyberFilledButtonColors(): androidx.compose.material3.ButtonColors =
  androidx.compose.material3.ButtonDefaults.buttonColors(
    containerColor = CyberHomeColors.primary,
    contentColor = CyberHomeColors.white,
    disabledContainerColor = CyberHomeColors.controlStrong,
    disabledContentColor = CyberHomeColors.inkFaint,
  )

/** Cyber outlined-button colors (Dart `cyberOutlinedButtonStyle`). */
@Composable
fun cyberOutlinedButtonColors(): androidx.compose.material3.ButtonColors =
  androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
    contentColor = CyberHomeColors.inkSecondary,
    disabledContentColor = CyberHomeColors.inkFaint,
  )

/** Cyber outlined-button border (Dart `BorderSide(lineStrong)`). */
val cyberOutlinedButtonBorder = BorderStroke(1.dp, CyberHomeColors.lineStrong)

/** Cyber button shape (Dart `AppRadii.tile`). */
val cyberButtonShape = RoundedCornerShape(AppRadii.tile)

private fun Modifier.pressableClick(onClick: () -> Unit): Modifier =
  this.clickable(
    role = androidx.compose.ui.semantics.Role.Button,
    onClick = onClick,
  )
