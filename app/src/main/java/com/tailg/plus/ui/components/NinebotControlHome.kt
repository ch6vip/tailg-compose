package com.tailg.plus.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.data.cloud.ResolvedVehicleLocation
import com.tailg.plus.domain.control.ControlChannelAvailability
import com.tailg.plus.domain.control.ControlTopBarChannelKind
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * 九号 (NINEBOT) control home — a faithful replica of the 九号出行 车控 page
 * (reference shots: dark M3 95c MAX home + light AE86 home + control-card
 * closeup), replacing the first-pass free interpretation.
 *
 * Structure, top to bottom:
 *  - vehicle name + message bubble; 车控/主题 tabs with signal + BLE chip;
 *  - battery hero: oversized percent + chevron, thin blue charge bar (fixed
 *    210dp like the app), 续航 line with a round battery badge;
 *  - vehicle stage: giant low-alpha "ninebot" watermark behind the car photo
 *    over the blue-gray stage gradient;
 *  - control card: 打开坐垫 / slide-to-power / 更多功能 over 感应解锁 /
 *    电池信息 / 闪灯鸣笛 (row 2 icons are plain line glyphs, no circles);
 *  - bottom cards: mini map with the 车辆定位 chip beside stacked
 *    今日里程 (warm) / 总里程 cards.
 *
 * The replica reads fixed tones lifted from the shots (see [NbReplica]) for
 * the card/circle/thumb/today-card surfaces — the generic [CyberHomeColors]
 * tokens cannot express the exact card-vs-page contrast of the reference —
 * while text colors still come from the palette so light/dark follow the
 * theme mode. Dark and light look like their respective shots.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Replica tones (lifted from the reference screenshots)
// ─────────────────────────────────────────────────────────────────────────────

/** Per-mode surface tones for the 九号 control home. */
private data class NbReplica(
    val stageTop: Color,
    val stageBottom: Color,
    val card: Color,
    val circle: Color,
    val track: Color,
    val thumb: Color,
    val thumbInk: Color,
    val todayCard: Color,
    val totalStrip: Color,
    val watermark: Color,
    val locateChip: Color,
    val locateChipInk: Color,
)

private fun nbReplica(dark: Boolean): NbReplica = if (dark) {
    NbReplica(
        stageTop = Color(0xFF272C37),
        stageBottom = Color(0xFF0D0F14),
        card = Color(0xFF2C2F34),
        circle = Color(0xFF3A3E44),
        track = Color(0xFF3A3E44),
        thumb = Color(0xFFE9EAEC),
        thumbInk = Color(0xFF17191D),
        todayCard = Color(0xFF4B4B46),
        totalStrip = Color(0xFF22252C),
        watermark = Color(0x0FFFFFFF),
        locateChip = Color(0xF01A1C22),
        locateChipInk = Color(0xFFEDEFF4),
    )
} else {
    NbReplica(
        stageTop = Color(0xFFE3E5EF),
        stageBottom = Color(0xFFD5D7E1),
        card = Color(0xFFFFFFFF),
        circle = Color(0xFFEEF0F5),
        track = Color(0xFFECEEF3),
        thumb = Color(0xFF1B2438),
        thumbInk = Color(0xFFFFFFFF),
        todayCard = Color(0xFFF8E4C6),
        totalStrip = Color(0xFFF0F1F5),
        watermark = Color(0x0D1B2438),
        locateChip = Color(0xF5FFFFFF),
        locateChipInk = Color(0xFF1B2438),
    )
}

@Composable
private fun rememberReplica(): NbReplica {
    val dark = CyberHomeColors.pageBg.luminance() < 0.5f
    return remember(dark) { nbReplica(dark) }
}

/** The official charge-bar blue (same in both modes). */
private val NbChargeBlue = Color(0xFF3D7BFF)

/**
 * Page background behind the replica cards — the shots sit on a slightly
 * lifted gray (dark ~#121419, light lavender ~#D9DBE6) rather than the raw
 * palette page bg.
 */
@Composable
fun ninebotPageBackground(): Color =
    if (CyberHomeColors.pageBg.luminance() < 0.5f) Color(0xFF121419) else Color(0xFFD9DBE6)

private val NbControlCardShape = RoundedCornerShape(30.dp)
private val NbStatCardShape = RoundedCornerShape(28.dp)

// ─────────────────────────────────────────────────────────────────────────────
// Header + vehicle stage
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NinebotVehicleHeader(
    vehicleName: String,
    rangeText: String,
    carPhoto: String,
    batteryPercent: Int,
    batteryKnown: Boolean,
    powered: Boolean?,
    channelLabel: String,
    modifier: Modifier = Modifier,
    onTitleTap: () -> Unit = {},
    onBatteryTap: () -> Unit = {},
    onBleChipTap: () -> Unit = {},
    onMessages: () -> Unit = {},
    onChannelTap: () -> Unit = {},
    bluetoothConnected: Boolean = false,
) {
    val r = rememberReplica()
    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.background(Brush.verticalGradient(listOf(r.stageTop, r.stageBottom)))) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                // Row 1 — name + signal / BLE chip / message bubble.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppPressable(
                        onClick = onTitleTap,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        semanticsLabel = stringResource(R.string.vehicle_header_switch),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = vehicleName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.W700,
                                    color = CyberHomeColors.ink,
                                ),
                            )
                            Spacer(Modifier.width(6.dp))
                            NinebotIcon(
                                icon = NinebotLucide.chevronRight,
                                size = 14.dp,
                                color = CyberHomeColors.inkFaint,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    AppPressable(
                        onClick = onChannelTap,
                        shape = CircleShape,
                        semanticsLabel = stringResource(R.string.vehicle_header_channel_format, channelLabel),
                    ) {
                        NinebotIcon(
                            icon = NinebotLucide.signal,
                            size = 20.dp,
                            color = CyberHomeColors.ink.copy(alpha = 0.75f),
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    AppPressable(
                        onClick = onBleChipTap,
                        shape = CircleShape,
                        semanticsLabel = if (bluetoothConnected) {
                            stringResource(R.string.vehicle_header_connected)
                        } else {
                            stringResource(R.string.vehicle_header_connect)
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .border(1.5.dp, CyberHomeColors.ink.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            NinebotIcon(
                                icon = if (bluetoothConnected) NinebotLucide.bluetooth else NinebotLucide.bluetoothSearching,
                                size = 17.dp,
                                color = CyberHomeColors.ink.copy(alpha = 0.85f),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    NinebotIconButton(icon = NinebotLucide.messageSquare, contentDescription = stringResource(R.string.vehicle_header_messages), onTap = onMessages)
                }
                Spacer(Modifier.height(16.dp))
                // Battery hero.
                AppPressable(
                    onClick = onBatteryTap,
                    shape = RoundedCornerShape(12.dp),
                    semanticsLabel = stringResource(R.string.vehicle_header_battery),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            AnimatedValueText(
                                value = if (batteryKnown) batteryPercent.toString() else "--",
                                style = TextStyle(
                                    fontSize = 46.sp,
                                    lineHeight = 46.sp * 0.95f,
                                    fontWeight = FontWeight.W800,
                                    color = CyberHomeColors.ink,
                                ),
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "%",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.W600,
                                    color = CyberHomeColors.ink.copy(alpha = 0.7f),
                                ),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            NinebotIcon(
                                icon = NinebotLucide.chevronRight,
                                size = 20.dp,
                                color = CyberHomeColors.inkMuted,
                                modifier = Modifier.padding(bottom = 9.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val fillFraction by animateFloatAsState(
                            targetValue = if (batteryKnown) batteryPercent / 100f else 0f,
                            animationSpec = tween(AppMotion.dataChange),
                            label = "nbChargeFill",
                        )
                        Box(
                            modifier = Modifier
                                .width(150.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(CyberHomeColors.ink.copy(alpha = 0.16f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fillFraction.coerceIn(0f, 1f))
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(NbChargeBlue),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = stringResource(R.string.ninebot_range_prefix),
                                style = TextStyle(fontSize = 16.sp, color = CyberHomeColors.ink),
                            )
                            Spacer(Modifier.width(5.dp))
                            AnimatedValueText(
                                value = rangeLabelValue(rangeText),
                                style = TextStyle(
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.W800,
                                    color = CyberHomeColors.ink,
                                ),
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = rangeLabelUnit(rangeText),
                                style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.ink.copy(alpha = 0.8f)),
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 2.dp)
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .background(CyberHomeColors.ink.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                NinebotIcon(
                                    icon = NinebotLucide.batteryCharging,
                                    size = 11.dp,
                                    color = CyberHomeColors.ink.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                }
            }
            // Vehicle stage — watermark behind the photo.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ninebot",
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 74.sp,
                        fontWeight = FontWeight.W800,
                        letterSpacing = (-1).sp,
                        color = r.watermark,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 8.dp),
                )
                val loops = MotionPolicy.loopsEnabled()
                val float = rememberInfiniteTransition(label = "nbFloat")
                val floatY by float.animateFloat(
                    initialValue = 0f,
                    targetValue = if (loops) -5f else 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 3000, easing = AppMotion.pulseCurve),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "nbFloatY",
                )
                if (powered == true) {
                    // Subtle alive cue: the photo rides a touch higher when powered.
                    Box(modifier = Modifier.align(Alignment.Center).offset(y = (floatY - 3f).dp)) {
                        VehicleStage(
                            batteryLevel = batteryPercent / 100f,
                            height = 200.dp,
                            imageUrl = carPhoto.ifBlank { null },
                        )
                    }
                } else {
                    Box(modifier = Modifier.align(Alignment.Center).offset(y = floatY.dp)) {
                        VehicleStage(
                            batteryLevel = batteryPercent / 100f,
                            height = 200.dp,
                            imageUrl = carPhoto.ifBlank { null },
                        )
                    }
                }
            }
        }
    }
}

private fun rangeLabelValue(rangeText: String): String {
    val t = rangeText.trim()
    return when {
        t.endsWith("km", ignoreCase = true) -> t.dropLast(2).trim()
        t.endsWith("mi", ignoreCase = true) -> t.dropLast(2).trim()
        else -> t
    }
}

private fun rangeLabelUnit(rangeText: String): String {
    val t = rangeText.trim()
    return when {
        t.endsWith("km", ignoreCase = true) -> "km"
        t.endsWith("mi", ignoreCase = true) -> "mi"
        else -> ""
    }
}

/** Signal / BLE chip / message bubble icons share the header row with the name. */

@Composable
private fun NinebotIconButton(icon: Int, contentDescription: String, onTap: () -> Unit) {
    AppPressable(
        onClick = onTap,
        shape = CircleShape,
        semanticsLabel = contentDescription,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            NinebotIcon(icon = icon, size = 23.dp, color = CyberHomeColors.ink)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Control card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NinebotControlGrid(
    powered: Boolean?,
    armed: Boolean?,
    busy: Boolean,
    activeCommand: CommandCode?,
    findAvailability: ControlChannelAvailability,
    powerAvailability: ControlChannelAvailability,
    armAvailability: ControlChannelAvailability,
    seatAvailability: ControlChannelAvailability,
    modifier: Modifier = Modifier,
    onFind: () -> Unit,
    onPowerToggle: suspend () -> Unit,
    onArmToggle: () -> Unit,
    onSettings: () -> Unit,
    onSeat: () -> Unit,
    onBattery: () -> Unit,
    onInduction: () -> Unit,
) {
    val r = rememberReplica()
    fun active(command: CommandCode) = activeCommand == command
    fun subdued(command: CommandCode) = busy && activeCommand != null && !active(command)
    val armActive = active(CommandCode.lock) || active(CommandCode.unlock)

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NbControlCardShape)
                .background(r.card)
                .padding(horizontal = 16.dp, vertical = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                NinebotCircleTile(
                    icon = NinebotLucide.armchair,
                    label = stringResource(R.string.control_card_seat),
                    circle = r.circle,
                    iconColor = CyberHomeColors.ink,
                    available = seatAvailability.enabled,
                    unavailableReason = seatAvailability.disabledReason,
                    busy = active(CommandCode.openSeat),
                    subdued = subdued(CommandCode.openSeat),
                    modifier = Modifier.weight(1f),
                    onTap = onSeat,
                )
                BoxWithConstraints(modifier = Modifier.weight(1.72f), contentAlignment = Alignment.Center) {
                    SlidePowerButton(
                        isPowered = powered,
                        onSlide = onPowerToggle,
                        trackWidth = minOf(138.dp, maxWidth - 8.dp),
                        enabled = powerAvailability.enabled,
                        busy = busy,
                        unavailableReason = powerAvailability.disabledReason,
                        onUnavailable = onPowerToggle,
                        thumbBackground = r.thumb,
                        thumbContentColor = r.thumbInk,
                        trackBackground = r.track,
                        showTrackChevrons = false,
                    )
                }
                NinebotCircleTile(
                    icon = NinebotLucide.nutFilled,
                    label = stringResource(R.string.control_grid_settings),
                    circle = r.circle,
                    iconColor = CyberHomeColors.ink,
                    available = true,
                    unavailableReason = "",
                    busy = false,
                    subdued = false,
                    modifier = Modifier.weight(1f),
                    onTap = onSettings,
                )
            }
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CyberHomeColors.ink.copy(alpha = 0.08f)),
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                NinebotPlainTile(
                    icon = NinebotLucide.fingerprint,
                    label = stringResource(R.string.ninebot_tile_induction),
                    available = true,
                    busy = false,
                    subdued = false,
                    modifier = Modifier.weight(1f),
                    onTap = onInduction,
                )
                NinebotPlainTile(
                    icon = NinebotLucide.battery,
                    label = stringResource(R.string.ninebot_tile_battery),
                    available = true,
                    busy = false,
                    subdued = false,
                    modifier = Modifier.weight(1f),
                    onTap = onBattery,
                )
                NinebotPlainTile(
                    icon = NinebotLucide.headlight,
                    label = stringResource(R.string.ninebot_tile_horn),
                    available = findAvailability.enabled,
                    unavailableReason = findAvailability.disabledReason,
                    busy = active(CommandCode.find),
                    subdued = subdued(CommandCode.find),
                    modifier = Modifier.weight(1f),
                    onTap = onFind,
                )
            }
        }
    }
}

/** Row-1 key: 62dp disc + label, matching the shot's circled glyphs. */
@Composable
private fun NinebotCircleTile(
    icon: Int,
    label: String,
    circle: Color,
    iconColor: Color,
    available: Boolean,
    unavailableReason: String,
    busy: Boolean,
    subdued: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    Column(
        modifier = modifier.alpha(if (subdued || !available) 0.5f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppPressable(
            onClick = { if (!busy) onTap() },
            enabled = true,
            shape = CircleShape,
            background = circle,
            semanticsLabel = if (available) {
                label
            } else if (unavailableReason.isEmpty()) {
                stringResource(R.string.control_grid_unavailable_format, label)
            } else {
                stringResource(R.string.control_grid_unavailable_reason_format, label, unavailableReason)
            },
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.4.dp,
                        color = iconColor,
                    )
                } else {
                    NinebotIcon(icon = icon, size = 25.dp, color = iconColor)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (busy) stringResource(R.string.control_grid_in_progress_format, label) else label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 16.sp,
                color = if (available) CyberHomeColors.ink else CyberHomeColors.inkFaint,
            ),
        )
        Box(modifier = Modifier.heightIn(min = 14.dp))
    }
}

/** Row-2 key: plain line glyph (no circle), like the shot's lower row. */
@Composable
private fun NinebotPlainTile(
    icon: Int,
    label: String,
    available: Boolean,
    busy: Boolean,
    subdued: Boolean,
    modifier: Modifier = Modifier,
    unavailableReason: String = "",
    onTap: () -> Unit,
) {
    Column(
        modifier = modifier.alpha(if (subdued || !available) 0.4f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppPressable(
            onClick = { if (!busy) onTap() },
            enabled = true,
            shape = RoundedCornerShape(16.dp),
            semanticsLabel = if (available) {
                label
            } else if (unavailableReason.isEmpty()) {
                stringResource(R.string.control_grid_unavailable_format, label)
            } else {
                stringResource(R.string.control_grid_unavailable_reason_format, label, unavailableReason)
            },
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.4.dp,
                        color = CyberHomeColors.ink,
                    )
                } else {
                    NinebotIcon(icon = icon, size = 30.dp, color = CyberHomeColors.ink)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (busy) stringResource(R.string.control_grid_in_progress_format, label) else label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 16.sp,
                color = if (available) CyberHomeColors.ink else CyberHomeColors.inkFaint,
            ),
        )
        Box(modifier = Modifier.heightIn(min = 12.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Map + ride stats
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NinebotStatsRow(
    location: ResolvedVehicleLocation?,
    address: String,
    todayKm: String,
    totalKm: String,
    modifier: Modifier = Modifier,
    onMapTap: () -> Unit,
    onRideStatsTap: () -> Unit,
) {
    val r = rememberReplica()
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val colW = (maxWidth - 12.dp) / 2
        val shellPad = 8.dp
        val stripH = 56.dp
        val innerW = colW - shellPad * 2
        val peachH = innerW / 1.7f
        // White shell cards with inset content (official nesting), bottoms aligned.
        Row {
            // Map shell: map + inset address strip.
            Column(
                modifier = Modifier
                    .width(colW)
                    .clip(NbStatCardShape)
                    .background(r.card)
                    .padding(shellPad),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(peachH)
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    MiniMap(
                        location = location,
                        address = address,
                        onMapTap = onMapTap,
                        showFooter = false,
                    )
                    // Vehicle pin — blue round marker at the tile centre.
                    if (location?.hasCoordinate == true) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(NbChargeBlue)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            NinebotIcon(
                                icon = NinebotLucide.mapPin,
                                size = 16.dp,
                                color = Color.White,
                            )
                        }
                    }
                    // 车辆定位 floating chip (official top-left square).
                    AppPressable(
                        onClick = onMapTap,
                        shape = RoundedCornerShape(14.dp),
                        semanticsLabel = stringResource(R.string.service_location),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(r.locateChip),
                            contentAlignment = Alignment.Center,
                        ) {
                            NinebotIcon(
                                icon = NinebotLucide.radar,
                                size = 19.dp,
                                color = r.locateChipInk,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stripH)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CyberHomeColors.control)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = address
                            .trim()
                            .takeUnless { it.isEmpty() || Regex("^-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?$").containsMatchIn(it) }
                            ?: stringResource(R.string.service_location),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W500,
                            color = CyberHomeColors.ink.copy(alpha = 0.9f),
                        ),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Ride shell: peach 今日里程 inset card + inset 总里程 strip.
            Column(
                modifier = Modifier
                    .width(colW)
                    .clip(NbStatCardShape)
                    .background(r.card)
                    .padding(shellPad),
            ) {
                AppPressable(
                    onClick = onRideStatsTap,
                    shape = RoundedCornerShape(20.dp),
                    semanticsLabel = stringResource(R.string.map_stats_view_ride),
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(peachH)
                            .clip(RoundedCornerShape(20.dp))
                            .background(r.todayCard)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.map_stats_today_distance),
                                style = TextStyle(fontSize = 15.sp, color = CyberHomeColors.ink),
                            )
                            Spacer(Modifier.weight(1f))
                            NinebotIcon(icon = NinebotLucide.swapHorizontal, size = 17.dp, color = CyberHomeColors.ink.copy(alpha = 0.85f))
                        }
                        Spacer(Modifier.weight(1f))
                        RideValue(valueWithUnit = todayKm, numberSize = 48.sp, unitSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                AppPressable(
                    onClick = onRideStatsTap,
                    shape = RoundedCornerShape(16.dp),
                    semanticsLabel = stringResource(R.string.map_stats_view_ride),
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(stripH)
                            .clip(RoundedCornerShape(16.dp))
                            .background(r.totalStrip)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.map_stats_total_distance),
                            style = TextStyle(fontSize = 15.sp, color = CyberHomeColors.ink),
                        )
                        Spacer(Modifier.weight(1f))
                        RideValue(valueWithUnit = totalKm, numberSize = 26.sp, unitSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** "2.7 km" → big number + small unit, bottom-aligned (same split as Cyber). */
@Composable
private fun RideValue(valueWithUnit: String, numberSize: androidx.compose.ui.unit.TextUnit, unitSize: androidx.compose.ui.unit.TextUnit) {
    val (number, unit) = remember(valueWithUnit) {
        val idx = valueWithUnit.lastIndexOf(' ')
        if (idx > 0) valueWithUnit.substring(0, idx) to valueWithUnit.substring(idx + 1)
        else valueWithUnit to ""
    }
    Row(verticalAlignment = Alignment.Bottom) {
        AnimatedValueText(
            value = number,
            maxLines = 1,
            style = TextStyle(
                fontSize = numberSize,
                fontWeight = FontWeight.W800,
                color = CyberHomeColors.ink,
            ),
        )
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = unit,
                style = TextStyle(fontSize = unitSize, color = CyberHomeColors.ink.copy(alpha = 0.85f)),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/** Channel status dot — same mapping as the Cyber header (kept local: it is private there). */
@Composable
internal fun ninebotChannelDotColor(kind: ControlTopBarChannelKind): Color = when (kind) {
    ControlTopBarChannelKind.BLE_DIRECT,
    ControlTopBarChannelKind.MQTT_REMOTE,
    ControlTopBarChannelKind.CLOUD_STANDBY,
    -> CyberHomeColors.primary
    ControlTopBarChannelKind.BLE_CONNECTING,
    ControlTopBarChannelKind.MQTT_CONNECTING,
    ControlTopBarChannelKind.MQTT_RETRY,
    -> CyberHomeColors.warning
    ControlTopBarChannelKind.UNAVAILABLE -> CyberHomeColors.danger
}
