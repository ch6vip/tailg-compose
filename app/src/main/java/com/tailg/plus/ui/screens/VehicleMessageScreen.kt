package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.di.rememberTailgEntryPoint
import com.tailg.plus.data.model.OfficialCloudMessage
import com.tailg.plus.data.model.OfficialCloudMessageCategory
import com.tailg.plus.data.store.MessageReadStore
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSkeleton
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.util.formatMonthDayMinuteText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/pages/vehicle_message_page.dart` — message center with three
 * tabs (全部 / 系统消息 / 设备消息), mark-read, clear-all, refresh, and a
 * detail bottom sheet.
 *
 * Navigation: [onBack] pops; the signed-out gate routes to the official cloud
 * login page via [Routes.OFFICIAL_CLOUD].
 */
@Composable
fun VehicleMessageScreen(
  vehicleId: String,
  onBack: () -> Unit,
  cloudService: OfficialCloudService,
  modifier: Modifier = Modifier,
  onNavigate: (String) -> Unit = {},
) {
  val cloudService = cloudService
  val context = androidx.compose.ui.platform.LocalContext.current
  val messageReadStore = remember { MessageReadStore(context) }
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val log = rememberTailgEntryPoint().logService()
  val cloudState by cloudService.stateFlow.collectAsState()

  var activeTab by remember { mutableIntStateOf(0) }
  var loading by remember { mutableStateOf(false) }
  var clearing by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var initialized by remember { mutableStateOf(false) }
  var readIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var hiddenIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var detailMessage by remember { mutableStateOf<VehicleMessage?>(null) }

  val strNoDetail = stringResource(R.string.msg_no_detail)
  val strTypeFault = stringResource(R.string.msg_type_fault)
  val strTypeAlarm = stringResource(R.string.msg_type_alarm)
  val strTypeError = stringResource(R.string.msg_type_error)
  val strTypeFailed = stringResource(R.string.msg_type_failed)
  val strClearedFormat = stringResource(R.string.msg_cleared_format)
  val strTypeLocation = stringResource(R.string.msg_type_location)
  val strTypeGps = stringResource(R.string.msg_type_gps)
  val strTypePower = stringResource(R.string.msg_type_power)
  val strTypeBattery = stringResource(R.string.msg_type_battery)
  val strRefreshFailed = stringResource(R.string.msg_refresh_failed)
  val signedIn = cloudState.signedIn

  // Bootstrap: load read state + refresh messages.
  LaunchedEffect(Unit) {
    messageReadStore.ensureLoaded()
    readIds = messageReadStore.readIds
    hiddenIds = messageReadStore.hiddenIds
    refreshMessages(
      cloudService = cloudService,
      messageReadStore = messageReadStore,
      force = true,
      onLoading = { loading = it },
      onError = { error = it },
      onInitialized = { initialized = it },
      onReadIds = { readIds = it },
      onHiddenIds = { hiddenIds = it },
      log = log,
      strRefreshFailed = strRefreshFailed,
    )
  }

  // Sync from cloud state changes.
  LaunchedEffect(cloudState) {
    messageReadStore.syncFromCloudMessages(
      vehicleMessages = cloudState.vehicleMessages,
      systemMessages = cloudState.systemMessages,
    )
  }

  val visibleMessages = remember(cloudState, hiddenIds) {
    if (!signedIn) emptyList()
    else buildList {
      cloudState.vehicleMessages.forEach { add(mapCloudMessage(it, strNoDetail, strTypeFault, strTypeAlarm, strTypeError, strTypeFailed, strTypeLocation, strTypeGps, strTypePower, strTypeBattery)) }
      cloudState.systemMessages.forEach { add(mapCloudMessage(it, strNoDetail, strTypeFault, strTypeAlarm, strTypeError, strTypeFailed, strTypeLocation, strTypeGps, strTypePower, strTypeBattery)) }
    }.sortedByDescending { it.time }
      .filter { it.id !in hiddenIds }
  }

  val tabMessages = remember(visibleMessages, activeTab) {
    when (activeTab) {
      1 -> visibleMessages.filter { it.category == VehicleMessageCategory.SYSTEM }
      2 -> visibleMessages.filter { it.category == VehicleMessageCategory.DEVICE }
      else -> visibleMessages
    }
  }
  val allMessages = visibleMessages
  val unreadCount = allMessages.count { it.id !in readIds }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      MessageHeader(
        unreadCount = unreadCount,
        canMarkRead = signedIn && unreadCount > 0,
        canClear = signedIn && allMessages.isNotEmpty() && !clearing,
        clearing = clearing,
        refreshing = loading,
        onBack = onBack,
        onMarkRead = {
          scope.launch {
            val tabMsgs = tabMessages
            val newRead = readIds + tabMsgs.map { it.id }
            readIds = newRead
            messageReadStore.replaceState(readIds = newRead, hiddenIds = hiddenIds)
          }
        },
        onClear = {
          if (allMessages.isEmpty() || clearing) return@MessageHeader
          clearing = true
          scope.launch {
            try {
              cloudService.deleteMessages()
              val newHidden = hiddenIds + allMessages.map { it.id }
              val newRead = readIds + allMessages.map { it.id }
              hiddenIds = newHidden
              readIds = newRead
              messageReadStore.replaceState(readIds = newRead, hiddenIds = newHidden)
              AppSnack.success(snackbarHostState, strClearedFormat.format(allMessages.size))
            } catch (e: Exception) {
              AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
            } finally {
              clearing = false
            }
          }
        },
        onRefresh = {
          scope.launch {
            refreshMessages(
              cloudService = cloudService,
              messageReadStore = messageReadStore,
              force = true,
              onLoading = { loading = it },
              onError = { error = it },
              onInitialized = { initialized = it },
              onReadIds = { readIds = it },
              onHiddenIds = { hiddenIds = it },
              log = log,
      strRefreshFailed = strRefreshFailed,
            )
          }
        },
      )
      MessageTabs(
        activeTab = activeTab,
        onSelect = { activeTab = it },
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f),
      ) {
        when {
          !signedIn -> MessageState(
            icon = Lucide.lock,
            title = stringResource(R.string.msg_login_required),
            subtitle = stringResource(R.string.msg_login_hint),
            actionLabel = stringResource(R.string.msg_login_action),
            onAction = { onNavigate(com.tailg.plus.ui.navigation.Routes.LOGIN) },
          )
          loading && !initialized -> MessageListSkeleton()
          error != null && allMessages.isEmpty() -> MessageState(
            icon = Lucide.wifiOff,
            title = stringResource(R.string.msg_load_failed),
            subtitle = error,
            actionLabel = stringResource(R.string.msg_retry),
            onAction = if (loading) null else {
              {
                scope.launch {
                  refreshMessages(
                    cloudService = cloudService,
                    messageReadStore = messageReadStore,
                    force = true,
                    onLoading = { loading = it },
                    onError = { error = it },
                    onInitialized = { initialized = it },
                    onReadIds = { readIds = it },
                    onHiddenIds = { hiddenIds = it },
                    log = log,
      strRefreshFailed = strRefreshFailed,
                  )
                }
              }
            },
          )
          tabMessages.isEmpty() -> MessageState(
            icon = Lucide.message,
            title = stringResource(R.string.msg_empty),
            subtitle = stringResource(R.string.msg_empty_hint),
          )
          else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(tabMessages, key = { it.id }) { message ->
              val read = message.id in readIds
              MessageCard(
                message = message,
                read = read,
                onOpen = {
                  if (message.id !in readIds) {
                    val newRead = readIds + message.id
                    readIds = newRead
                    scope.launch {
                      messageReadStore.replaceState(readIds = newRead, hiddenIds = hiddenIds)
                    }
                  }
                  detailMessage = message
                },
              )
            }
          }
        }
      }
    }
  }

  // Detail sheet.
  detailMessage?.let { message ->
    MessageDetailSheet(
      message = message,
      onDismiss = { detailMessage = null },
    )
  }
}

// ── Data model ─────────────────────────────────────────────────────────────

data class VehicleMessage(
  val id: String,
  val title: String,
  val subtitle: String,
  val time: Instant,
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val category: VehicleMessageCategory,
  val severity: VehicleMessageSeverity,
)

enum class VehicleMessageCategory {
  SYSTEM,
  DEVICE,
}


@Composable
private fun categoryLabel(category: VehicleMessageCategory): String = when (category) {
  VehicleMessageCategory.SYSTEM -> stringResource(R.string.msg_system)
  VehicleMessageCategory.DEVICE -> stringResource(R.string.msg_device)
}

enum class VehicleMessageSeverity(val color: Color) {
  INFO(CyberHomeColors.primary),
  WARNING(CyberHomeColors.warning),
  ERROR(CyberHomeColors.danger),
}

private fun mapCloudMessage(message: OfficialCloudMessage, strNoDetail: String, strTypeFault: String, strTypeAlarm: String, strTypeError: String, strTypeFailed: String, strTypeLocation: String, strTypeGps: String, strTypePower: String, strTypeBattery: String): VehicleMessage {
  val isSystem = message.category == OfficialCloudMessageCategory.SYSTEM
  val lower = "${message.title} ${message.content}".lowercase()
  val severity = severityFor(lower, strTypeFault, strTypeAlarm, strTypeError, strTypeFailed)
  return VehicleMessage(
    id = message.id,
    title = message.title,
    subtitle = if (message.content.isEmpty()) strNoDetail else message.content,
    time = message.time,
    icon = if (isSystem) Lucide.megaphone else iconFor(lower, severity, strTypeLocation, strTypeGps, strTypePower, strTypeBattery),
    category = if (isSystem) VehicleMessageCategory.SYSTEM else VehicleMessageCategory.DEVICE,
    severity = severity,
  )
}

private fun severityFor(lower: String, strTypeFault: String, strTypeAlarm: String, strTypeError: String, strTypeFailed: String): VehicleMessageSeverity {
  val hasFault = lower.contains(strTypeFault) || lower.contains("error") || lower.contains(strTypeAlarm)
  val hasWarn = lower.contains(strTypeAlarm) || lower.contains(strTypeError) || lower.contains(strTypeFailed) ||
    lower.contains("warning") || lower.contains(strTypeFault)
  return when {
    hasFault -> VehicleMessageSeverity.ERROR
    hasWarn -> VehicleMessageSeverity.WARNING
    else -> VehicleMessageSeverity.INFO
  }
}

private fun iconFor(lower: String, severity: VehicleMessageSeverity, strTypeLocation: String, strTypeGps: String, strTypePower: String, strTypeBattery: String): ImageVector {
  return when {
    lower.contains(strTypeLocation) || lower.contains(strTypeGps) -> Lucide.mapPin
    lower.contains(strTypePower) || lower.contains(strTypeBattery) -> Lucide.batteryWarning
    severity == VehicleMessageSeverity.ERROR -> Lucide.alert
    else -> Lucide.vehicle
  }
}

// ── Refresh helper ─────────────────────────────────────────────────────────

private suspend fun refreshMessages(
  cloudService: com.tailg.plus.data.cloud.OfficialCloudService,
  messageReadStore: MessageReadStore,
  force: Boolean,
  onLoading: (Boolean) -> Unit,
  onError: (String?) -> Unit,
  onInitialized: (Boolean) -> Unit,
  onReadIds: (Set<String>) -> Unit,
  onHiddenIds: (Set<String>) -> Unit,
  log: LogService,
  strRefreshFailed: String,
) {
  if (!cloudService.currentState.signedIn) {
    onInitialized(true)
    onLoading(false)
    onError(null)
    messageReadStore.setUnreadCount(0)
    return
  }
  onLoading(true)
  onError(null)
  try {
    cloudService.refreshMessages(force = force)
    onLoading(false)
    onInitialized(true)
    onError(cloudService.currentState.messagesError)
    messageReadStore.syncFromCloudMessages(
      vehicleMessages = cloudService.currentState.vehicleMessages,
      systemMessages = cloudService.currentState.systemMessages,
    )
  } catch (e: Exception) {
    onLoading(false)
    onInitialized(true)
    onError(OfficialCloudRedactor.errorMessage(e))
    log.operation(strRefreshFailed, detail = OfficialCloudRedactor.errorMessage(e), level = LogLevel.WARNING)
  }
}

// ── Header ────────────────────────────────────────────────────────────────

@Composable
private fun MessageHeader(
  unreadCount: Int,
  canMarkRead: Boolean,
  canClear: Boolean,
  clearing: Boolean,
  refreshing: Boolean,
  onBack: () -> Unit,
  onMarkRead: () -> Unit,
  onClear: () -> Unit,
  onRefresh: () -> Unit,
) {
  Row(
    modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    HeaderButton(
      icon = Lucide.arrowLeft,
      label = stringResource(R.string.common_back),
      onTap = onBack,
      filled = true,
    )
    Spacer(Modifier.width(12.dp))
    Text(
      text = stringResource(R.string.msg_title),
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
    HeaderButton(
      icon = Lucide.check,
      label = stringResource(R.string.msg_mark_all_read),
      enabled = canMarkRead,
      badge = unreadCount,
      onTap = onMarkRead,
    )
    HeaderButton(
      icon = Lucide.trash,
      label = stringResource(R.string.msg_clear_all),
      enabled = canClear,
      loading = clearing,
      onTap = onClear,
    )
    HeaderButton(
      icon = Lucide.refresh,
      label = stringResource(R.string.common_refresh),
      enabled = !refreshing,
      loading = refreshing,
      onTap = onRefresh,
    )
  }
}

@Composable
private fun HeaderButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onTap: () -> Unit,
  enabled: Boolean = true,
  filled: Boolean = false,
  loading: Boolean = false,
  badge: Int = 0,
) {
  AppPressable(
    onClick = if (enabled) onTap else null,
    enabled = enabled,
    semanticsLabel = label,
  ) {
    Box(
      modifier = Modifier.size(AppTouchTargets.min),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .size(if (filled) AppTouchTargets.min else 36.dp)
          .clip(CircleShape)
          .background(if (filled) CyberHomeColors.card else Color.Transparent),
        contentAlignment = Alignment.Center,
      ) {
        if (loading) {
          CircularProgressIndicator(
            modifier = Modifier.size(17.dp),
            strokeWidth = 1.8.dp,
            color = CyberHomeColors.primary,
          )
        } else {
          Box {
            LucideIcon(
              icon = icon,
              size = 20.dp,
              color = if (enabled) CyberHomeColors.inkSecondary else CyberHomeColors.inkFaint,
            )
            if (badge > 0) {
              Box(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .height(16.dp)
                  .clip(RoundedCornerShape(AppRadii.pill))
                  .background(CyberHomeColors.danger)
                  .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = if (badge > 9) "9+" else "$badge",
                  style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.white),
                )
              }
            }
          }
        }
      }
    }
  }
}

// ── Tabs ──────────────────────────────────────────────────────────────────

@Composable
private fun MessageTabs(
  activeTab: Int,
  onSelect: (Int) -> Unit,
) {
  val tabs = listOf(stringResource(R.string.msg_filter_all), stringResource(R.string.msg_system), stringResource(R.string.msg_device))
  Row(
    modifier = Modifier
      .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 10.dp)
      .fillMaxWidth()
      .height(AppTouchTargets.min)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.control),
  ) {
    tabs.forEachIndexed { i, label ->
      val active = activeTab == i
      AppPressable(
        onClick = { onSelect(i) },
        haptic = false,
        semanticsLabel = label,
        modifier = Modifier.weight(1f),
      ) {
        Box(
          modifier = Modifier
            .padding(3.dp)
            .fillMaxSize()
            .clip(RoundedCornerShape(AppRadii.xs))
            .background(if (active) CyberHomeColors.card else Color.Transparent),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
              fontSize = 13.sp,
              fontWeight = if (active) FontWeight.W700 else FontWeight.W600,
              color = if (active) CyberHomeColors.ink else CyberHomeColors.inkMuted,
            ),
          )
        }
      }
    }
  }
}

// ── Message card ──────────────────────────────────────────────────────────

@Composable
private fun MessageCard(
  message: VehicleMessage,
  read: Boolean,
  onOpen: () -> Unit,
) {
  val cardColor = if (read) CyberHomeColors.card else message.severity.color.copy(alpha = 0.05f)
  val borderColor = if (read) CyberHomeColors.line else message.severity.color.copy(alpha = 0.16f)
  AppPressable(
    onClick = onOpen,
    semanticsLabel = "${message.title}，${message.subtitle}，${categoryLabel(message.category)}，${if (read) stringResource(R.string.msg_read) else stringResource(R.string.msg_unread)}",
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(AppRadii.tile))
        .background(cardColor)
        .border(1.dp, borderColor, RoundedCornerShape(AppRadii.tile))
        .padding(14.dp),
    ) {
      Row(verticalAlignment = Alignment.Top) {
        MessageIcon(message = message, read = read)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.Top) {
            Text(
              text = message.title,
              modifier = Modifier.weight(1f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = formatMonthDayMinuteText(
                LocalDateTime.ofInstant(message.time, ZoneId.systemDefault()),
              ),
              style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
            )
          }
          Spacer(Modifier.height(5.dp))
          Text(
            text = message.subtitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted, lineHeight = 12.sp * 1.4f),
          )
          Spacer(Modifier.height(10.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(text = categoryLabel(message.category))
            Spacer(Modifier.width(8.dp))
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (read) CyberHomeColors.inkFaint else message.severity.color),
            )
            Spacer(Modifier.width(5.dp))
            Text(
              text = if (read) stringResource(R.string.msg_read) else stringResource(R.string.msg_unread),
              style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
            )
            Spacer(Modifier.weight(1f))
            LucideIcon(icon = Lucide.chevronRight, size = AppIconSizes.sm, color = CyberHomeColors.inkFaint)
          }
        }
      }
    }
  }
}

@Composable
private fun MessageIcon(message: VehicleMessage, read: Boolean) {
  val color = if (read) CyberHomeColors.inkFaint else message.severity.color
  Box(
    modifier = Modifier
      .size(42.dp)
      .clip(CircleShape)
      .background(color.copy(alpha = 0.12f)),
    contentAlignment = Alignment.Center,
  ) {
    LucideIcon(icon = message.icon, color = color, size = AppIconSizes.md)
  }
}

@Composable
private fun Tag(text: String) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(CyberHomeColors.control)
      .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Text(
      text = text,
      style = TextStyle(fontSize = 10.sp, color = CyberHomeColors.inkMuted, fontWeight = FontWeight.W600),
    )
  }
}

// ── Skeleton ──────────────────────────────────────────────────────────────

@Composable
private fun MessageListSkeleton() {
  // Dart `_MessageListSkeleton`: avatar + title + body AppSkeleton lines.
  Column(
    modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    repeat(4) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(112.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.card)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(14.dp),
      ) {
        Row(verticalAlignment = Alignment.Top) {
          AppSkeleton(
            width = 42.dp,
            height = 42.dp,
            borderRadius = CircleShape,
            baseColor = CyberHomeColors.control,
            highlightColor = CyberHomeColors.cardMuted,
          )
          Spacer(Modifier.width(12.dp))
          Column(horizontalAlignment = Alignment.Start) {
            AppSkeleton(
              width = 146.dp,
              height = 16.dp,
              baseColor = CyberHomeColors.control,
              highlightColor = CyberHomeColors.cardMuted,
            )
            Spacer(Modifier.height(10.dp))
            AppSkeleton(
              width = 280.dp,
              height = 12.dp,
              baseColor = CyberHomeColors.control,
              highlightColor = CyberHomeColors.cardMuted,
            )
            Spacer(Modifier.height(8.dp))
            AppSkeleton(
              width = 110.dp,
              height = 10.dp,
              baseColor = CyberHomeColors.control,
              highlightColor = CyberHomeColors.cardMuted,
            )
          }
        }
      }
    }
  }
}

// ── State ─────────────────────────────────────────────────────────────────

@Composable
private fun MessageState(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String?,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Box(
    modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.card),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = icon, size = 28.dp, color = CyberHomeColors.inkMuted)
      }
      Spacer(Modifier.height(16.dp))
      Text(
        text = title,
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      if (subtitle != null) {
        Spacer(Modifier.height(7.dp))
        Text(
          text = subtitle,
          textAlign = TextAlign.Center,
          style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted, lineHeight = 13.sp * 1.45f),
        )
      }
      if (actionLabel != null && onAction != null) {
        Spacer(Modifier.height(18.dp))
        Button(
          onClick = onAction,
          modifier = Modifier.width(148.dp).height(46.dp),
          colors = cyberFilledButtonColors(),
          shape = cyberButtonShape,
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}

// ── Detail sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageDetailSheet(
  message: VehicleMessage,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 20.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        MessageIcon(message = message, read = false)
        Spacer(Modifier.width(12.dp))
        Text(
          text = message.title,
          modifier = Modifier.weight(1f),
          style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        )
      }
      Spacer(Modifier.height(16.dp))
      Text(
        text = message.subtitle,
        style = TextStyle(fontSize = 14.sp, color = CyberHomeColors.inkMuted, lineHeight = 14.sp * 1.55f),
      )
      Spacer(Modifier.height(18.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Tag(text = categoryLabel(message.category))
        Spacer(Modifier.width(8.dp))
        Tag(text = formatMonthDayMinuteText(
          LocalDateTime.ofInstant(message.time, ZoneId.systemDefault()),
        ))
      }
      Spacer(Modifier.height(18.dp))
      Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = cyberFilledButtonColors(),
        shape = cyberButtonShape,
      ) {
        Text(stringResource(R.string.msg_got_it))
      }
    }
  }
}
