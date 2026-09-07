package com.tailg.plus.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.NinebotIcon
import com.tailg.plus.ui.components.NinebotLucide
import com.tailg.plus.ui.navigation.Routes

/** Scroll and window insets belong to the tab host, including bottom-bar clearance. */
@Composable
fun VectorServiceContent(
  vehicleRouteId: String,
  onNavigate: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
    VectorPageMasthead(stringResource(R.string.vector_service_kicker))
    BasicText(
      text = stringResource(R.string.vector_service_heading),
      modifier = Modifier.padding(top = 20.dp).semantics { heading() },
      style = MaterialTheme.typography.displayLarge.copy(
        color = colors.onSurface,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.8).sp,
      ),
      autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 44.sp),
      maxLines = 2,
    )
    Text(
      text = stringResource(R.string.vector_service_intro),
      modifier = Modifier.padding(top = 14.dp, bottom = 28.dp),
      color = colors.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
    )

    VectorPageSection("01", stringResource(R.string.service_location_section))
    Spacer(Modifier.height(12.dp))
    VectorLocationPanel { onNavigate(Routes.location(vehicleRouteId)) }
    Spacer(Modifier.height(10.dp))
    VectorPageActionRow(
      index = "02",
      icon = NinebotLucide.route,
      title = stringResource(R.string.service_travel),
      subtitle = stringResource(R.string.vector_service_travel_note),
      onClick = { onNavigate(Routes.location(vehicleRouteId, "travel")) },
    )
    VectorPageActionRow(
      index = "03",
      icon = NinebotLucide.radar,
      title = stringResource(R.string.service_fence),
      subtitle = stringResource(R.string.vector_service_fence_note),
      onClick = { onNavigate(Routes.location(vehicleRouteId, "fence")) },
    )

    Spacer(Modifier.height(28.dp))
    VectorPageSection("02", stringResource(R.string.service_vehicle_energy))
    Spacer(Modifier.height(12.dp))
    VectorPageActionRow(
      index = "04",
      icon = NinebotLucide.slidersHorizontal,
      title = stringResource(R.string.service_vehicle_settings),
      subtitle = stringResource(R.string.vector_service_settings_note),
      onClick = { onNavigate(Routes.vehicleSettings(vehicleRouteId)) },
    )
    Spacer(Modifier.height(16.dp))
    VectorEnergyPanels(
      onBattery = { onNavigate(Routes.batteryDetails(vehicleRouteId)) },
      onStatistics = { onNavigate(Routes.rideStats(vehicleRouteId)) },
    )

    Spacer(Modifier.height(30.dp))
    VectorPageSection("03", stringResource(R.string.vector_service_support))
    Spacer(Modifier.height(12.dp))
    VectorPageActionRow(
      index = "07",
      icon = NinebotLucide.activity,
      title = stringResource(R.string.service_fault_diag),
      subtitle = stringResource(R.string.service_fault_diag_desc),
      onClick = { onNavigate(Routes.diagnostic(vehicleRouteId)) },
    )
    VectorPageActionRow(
      index = "08",
      icon = NinebotLucide.cloudDownload,
      title = stringResource(R.string.service_official_account),
      subtitle = stringResource(R.string.service_official_account_desc),
      onClick = { onNavigate(Routes.OFFICIAL_CLOUD) },
    )
    VectorPageSignature()
  }
}

/**
 * The caller owns account state and route guards. Every action delegates to the
 * existing screen so editing, vehicle switching and sign-out keep their behavior.
 */
@Composable
fun VectorProfileContent(
  nickname: String,
  phoneLine: String,
  memberLabel: String,
  signedIn: Boolean,
  vehicleName: String,
  vehicleOnline: Boolean,
  vehicleStatusLabel: String,
  batteryLabel: String,
  unreadCount: Int,
  onAvatarTap: () -> Unit,
  onEditTap: () -> Unit,
  onVehicleTap: () -> Unit,
  onMessages: () -> Unit,
  onAbout: () -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val editLabel = stringResource(if (signedIn) R.string.profile_edit_nickname else R.string.profile_login_now)
  Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
    VectorPageMasthead(stringResource(R.string.vector_profile_kicker))
    Spacer(Modifier.height(28.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      VectorPagePressable(
        onClick = onAvatarTap,
        semanticsLabel = editLabel,
        background = colors.secondaryContainer,
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(22.dp),
      ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
          NinebotIcon(NinebotLucide.userRound, size = 32.dp, color = colors.onSecondaryContainer)
        }
      }
      Column(
        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          stringResource(R.string.vector_profile_identity),
          color = colors.onSurfaceVariant,
          style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        )
        Text(
          memberLabel,
          color = colors.onSurface,
          style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
      }
      VectorPagePressable(
        onClick = onEditTap,
        semanticsLabel = editLabel,
        background = colors.surfaceContainer,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
      ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
          NinebotIcon(NinebotLucide.pencil, size = 20.dp, color = colors.onSurface)
        }
      }
    }
    Text(
      nickname,
      modifier = Modifier.padding(top = 24.dp).semantics { heading() },
      color = colors.onSurface,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.displayLarge.copy(
        fontSize = 38.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.3).sp,
      ),
    )
    Text(
      phoneLine,
      modifier = Modifier.padding(top = 10.dp, bottom = 26.dp),
      color = colors.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
    )
    HorizontalDivider(color = colors.outlineVariant)

    Spacer(Modifier.height(24.dp))
    VectorPageSection("01", stringResource(R.string.vector_profile_vehicle))
    Spacer(Modifier.height(14.dp))
    VectorProfileVehiclePanel(
      vehicleName = vehicleName,
      vehicleOnline = vehicleOnline,
      statusLabel = vehicleStatusLabel,
      batteryLabel = batteryLabel,
      onClick = onVehicleTap,
    )

    Spacer(Modifier.height(30.dp))
    VectorPageSection("02", stringResource(R.string.profile_section_account))
    Spacer(Modifier.height(12.dp))
    val messageCount = unreadCount.takeIf { signedIn && it > 0 }
    VectorPageActionRow(
      icon = NinebotLucide.messageSquare,
      title = stringResource(R.string.profile_message_center),
      subtitle = if (messageCount != null) stringResource(R.string.vector_profile_unread, messageCount)
      else stringResource(R.string.vector_profile_messages_note),
      badge = messageCount,
      onClick = onMessages,
    )
    VectorPageActionRow(
      icon = NinebotLucide.info,
      title = stringResource(R.string.profile_about_us),
      subtitle = stringResource(R.string.vector_profile_about_note),
      onClick = onAbout,
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 22.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(colors.surfaceContainer)
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        stringResource(R.string.profile_phone),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
      )
      Text(
        if (signedIn) phoneLine else stringResource(R.string.profile_unbound),
        color = colors.onSurface,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
      )
    }
    if (signedIn) {
      Spacer(Modifier.height(14.dp))
      val logoutLabel = stringResource(R.string.common_logout)
      VectorPagePressable(
        onClick = onLogout,
        semanticsLabel = logoutLabel,
        background = colors.surface,
        borderColor = colors.outlineVariant,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
      ) {
        Row(
          Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            logoutLabel,
            modifier = Modifier.weight(1f),
            color = colors.error,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
          )
          NinebotIcon(NinebotLucide.arrowLeft, size = 20.dp, color = colors.error)
        }
      }
    }
    VectorPageSignature()
  }
}

@Composable
private fun VectorLocationPanel(onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val title = stringResource(R.string.service_location)
  val note = stringResource(R.string.vector_service_location_note)
  VectorPagePressable(
    onClick = onClick,
    semanticsLabel = "$title, $note",
    background = colors.secondaryContainer,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 4.dp),
  ) {
    Box(Modifier.fillMaxWidth()) {
      val lineColor = colors.onSecondaryContainer.copy(alpha = 0.11f)
      Canvas(Modifier.matchParentSize()) {
        // Abstract route ribbons, not a map or a representation of vehicle data.
        repeat(5) { line ->
          val shift = line * size.height * 0.13f
          val path = Path().apply {
            moveTo(size.width * 0.53f, -size.height * 0.15f + shift)
            cubicTo(
              size.width * 0.25f, size.height * 0.45f + shift,
              size.width * 1.28f, size.height * 0.08f + shift,
              size.width * 1.04f, size.height * 0.80f + shift,
            )
          }
          drawPath(path, lineColor, style = Stroke(width = 1.dp.toPx()))
        }
      }
      Column(Modifier.fillMaxWidth().padding(22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            "01",
            modifier = Modifier.weight(1f),
            color = colors.onSecondaryContainer,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
          )
          NinebotIcon(NinebotLucide.mapPin, size = 30.dp, color = colors.onSecondaryContainer)
        }
        Spacer(Modifier.height(38.dp))
        Text(
          title,
          color = colors.onSecondaryContainer,
          style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
        )
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          Text(
            note,
            modifier = Modifier.weight(1f),
            color = colors.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
          )
          Spacer(Modifier.width(16.dp))
          NinebotIcon(NinebotLucide.arrowUpRight, size = 28.dp, color = colors.onSecondaryContainer)
        }
      }
    }
  }
}

@Composable
private fun VectorEnergyPanels(onBattery: () -> Unit, onStatistics: () -> Unit) {
  val fontScale = LocalDensity.current.fontScale
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    // The editorial split becomes a stack for large text or compact phones.
    // Labels keep their full text, and touch areas expand with the content.
    if (maxWidth < 310.dp || fontScale > 1.2f) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VectorEnergyPanel(
          index = "05",
          icon = NinebotLucide.batteryCharging,
          title = stringResource(R.string.service_battery),
          note = stringResource(R.string.vector_service_battery_note),
          prominent = true,
          onClick = onBattery,
        )
        VectorEnergyPanel(
          index = "06",
          icon = NinebotLucide.route,
          title = stringResource(R.string.service_ride_stats),
          note = stringResource(R.string.vector_service_stats_note),
          prominent = false,
          onClick = onStatistics,
        )
      }
    } else {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        VectorEnergyPanel(
          index = "05",
          icon = NinebotLucide.batteryCharging,
          title = stringResource(R.string.service_battery),
          note = stringResource(R.string.vector_service_battery_note),
          prominent = true,
          onClick = onBattery,
          modifier = Modifier.weight(1.08f),
        )
        VectorEnergyPanel(
          index = "06",
          icon = NinebotLucide.route,
          title = stringResource(R.string.service_ride_stats),
          note = stringResource(R.string.vector_service_stats_note),
          prominent = false,
          onClick = onStatistics,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun VectorEnergyPanel(
  index: String,
  @DrawableRes icon: Int,
  title: String,
  note: String,
  prominent: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val background = if (prominent) colors.inverseSurface else colors.surfaceContainer
  val foreground = if (prominent) colors.inverseOnSurface else colors.onSurface
  VectorPagePressable(
    onClick = onClick,
    semanticsLabel = "$title, $note",
    background = background,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(18.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NinebotIcon(icon, size = 28.dp, color = foreground)
        Spacer(Modifier.weight(1f))
        Text(
          index,
          color = foreground,
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
        )
      }
      Spacer(Modifier.height(if (prominent) 40.dp else 20.dp))
      Text(
        title,
        color = foreground,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
      )
      Text(
        note,
        modifier = Modifier.padding(top = 6.dp),
        color = foreground.copy(alpha = 0.78f),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
      )
      Spacer(Modifier.height(18.dp))
      NinebotIcon(NinebotLucide.arrowUpRight, modifier = Modifier.align(Alignment.End), size = 22.dp, color = foreground)
    }
  }
}

@Composable
private fun VectorProfileVehiclePanel(
  vehicleName: String,
  vehicleOnline: Boolean,
  statusLabel: String,
  batteryLabel: String,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val label = stringResource(R.string.profile_switch_vehicle)
  val batteryTitle = stringResource(R.string.vector_profile_battery)
  val foreground = colors.inverseOnSurface
  VectorPagePressable(
    onClick = onClick,
    semanticsLabel = "$label, $vehicleName, $statusLabel, $batteryTitle $batteryLabel",
    background = colors.inverseSurface,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.fillMaxWidth().padding(22.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NinebotIcon(NinebotLucide.bike, size = 30.dp, color = foreground)
        Spacer(Modifier.weight(1f))
        NinebotIcon(NinebotLucide.swapHorizontal, size = 24.dp, color = foreground)
      }
      Text(
        vehicleName,
        modifier = Modifier.padding(top = 22.dp, bottom = 24.dp),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        color = foreground,
        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
      )
      HorizontalDivider(color = foreground.copy(alpha = 0.2f))
      Row(
        Modifier.fillMaxWidth().padding(top = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          NinebotIcon(
            NinebotLucide.signal,
            size = 20.dp,
            color = foreground.copy(alpha = if (vehicleOnline) 1f else 0.55f),
          )
          Text(
            statusLabel,
            color = foreground,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
          )
        }
        Column(Modifier.weight(1.15f), horizontalAlignment = Alignment.End) {
          BasicText(
            batteryLabel,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displaySmall.copy(
              color = foreground,
              fontSize = 32.sp,
              lineHeight = 38.sp,
              fontWeight = FontWeight.Medium,
              textAlign = TextAlign.End,
            ),
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 32.sp),
            maxLines = 1,
          )
          Text(
            batteryTitle,
            color = foreground.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
          )
        }
      }
    }
  }
}

@Composable
private fun VectorPageActionRow(
  @DrawableRes icon: Int,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  index: String? = null,
  badge: Int? = null,
) {
  val colors = MaterialTheme.colorScheme
  VectorPagePressable(
    onClick = onClick,
    semanticsLabel = "$title, $subtitle",
    background = colors.surface,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(vertical = 18.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.width(36.dp),
          horizontalAlignment = Alignment.Start,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          NinebotIcon(icon, size = 22.dp, color = colors.onSurface)
          if (index != null) {
            Text(
              index,
              color = colors.onSurfaceVariant,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            )
          }
        }
        Column(Modifier.weight(1f)) {
          Text(
            title,
            modifier = Modifier.fillMaxWidth(),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
          )
          Text(
            subtitle,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
          )
        }
        Spacer(Modifier.width(12.dp))
        if (badge != null) {
          Box(
            Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(colors.secondaryContainer)
              .padding(horizontal = 9.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              if (badge > 99) "99+" else badge.toString(),
              color = colors.onSecondaryContainer,
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        } else {
          NinebotIcon(NinebotLucide.arrowUpRight, size = 21.dp, color = colors.onSurface)
        }
      }
      HorizontalDivider(color = colors.outlineVariant)
    }
  }
}

@Composable
private fun VectorPageMasthead(kicker: String) {
  val colors = MaterialTheme.colorScheme
  Row(
    Modifier.fillMaxWidth().padding(top = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      kicker,
      modifier = Modifier.weight(1f),
      color = colors.onSurfaceVariant,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
    )
    Text(
      "VECTOR",
      color = colors.onSurface,
      style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
    )
  }
}

@Composable
private fun VectorPageSection(index: String, title: String) {
  val colors = MaterialTheme.colorScheme
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      index,
      color = colors.onSurfaceVariant,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
    )
    Spacer(Modifier.width(12.dp))
    Text(
      title,
      modifier = Modifier.weight(1f).semantics { heading() },
      color = colors.onSurface,
      style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    )
    Spacer(Modifier.width(12.dp))
    HorizontalDivider(Modifier.width(32.dp), color = colors.outlineVariant)
  }
}

@Composable
private fun VectorPageSignature() {
  Text(
    stringResource(R.string.vector_pages_signature),
    modifier = Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 18.dp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp),
  )
}

/** Spring feedback lives on the panel; its unscaled hit target remains stable. */
@Composable
private fun VectorPagePressable(
  onClick: () -> Unit,
  semanticsLabel: String,
  background: Color,
  modifier: Modifier = Modifier,
  borderColor: Color? = null,
  shape: Shape = RoundedCornerShape(22.dp),
  content: @Composable () -> Unit,
) {
  AppPressable(
    onClick = onClick,
    modifier = modifier,
    pressedScale = 1f,
    semanticsLabel = semanticsLabel,
    builder = { pressed ->
      val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 520f),
        label = "vectorPagePress",
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .graphicsLayer { scaleX = scale; scaleY = scale }
          .clip(shape)
          .background(background)
          .then(if (borderColor == null) Modifier else Modifier.border(1.dp, borderColor, shape)),
      ) {
        content()
      }
    },
  )
}
