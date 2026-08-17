package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.model.ShareMemberRecord
import com.tailg.plus.data.store.ReplicaFeatureStore
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberOutlinedButtonBorder
import com.tailg.plus.ui.components.cyberOutlinedButtonColors
import com.tailg.plus.ui.components.cyberTextFieldColors
import com.tailg.plus.ui.components.cyberTextFieldShape
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.formatDateText
import kotlinx.coroutines.launch

/**
 * Share-bike page of [OfficialReplicaScreen] (Dart ShareBikePage).
 * Extracted from OfficialReplicaScreen.kt for maintainability.
 */

@Composable
internal fun ShareBikeTab(
  store: ReplicaFeatureStore,
  snackbarHostState: SnackbarHostState,
  scope: kotlinx.coroutines.CoroutineScope,
) {
  var members by remember { mutableStateOf<List<ShareMemberRecord>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var showAddDialog by remember { mutableStateOf(false) }
  var editMember by remember { mutableStateOf<ShareMemberRecord?>(null) }

  LaunchedEffect(Unit) {
    members = store.loadShareMembers()
    loading = false
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
  ) {
    item {
      Text(
        text = stringResource(R.string.replica_family_share),
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
      )
    }
    item { Spacer(Modifier.height(8.dp)) }
    item {
      ReplicaNotice(
        icon = Lucide.share,
        title = stringResource(R.string.replica_local_demo),
        subtitle = stringResource(R.string.replica_family_share_desc),
      )
    }
    item { Spacer(Modifier.height(14.dp)) }
    if (loading) {
      item {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = CyberHomeColors.primary)
        }
      }
    } else if (members.isEmpty()) {
      item {
        EmptyReplicaCard(icon = Lucide.groupOff, title = stringResource(R.string.replica_no_members), subtitle = stringResource(R.string.replica_no_members_hint))
      }
    } else {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.tile))
            .background(CyberHomeColors.card)
            .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
        ) {
          members.forEachIndexed { i, member ->
            ShareMemberTile(
              member = member,
              onEdit = { editMember = member },
              onDelete = {
                scope.launch {
                  members = members.filter { it.id != member.id }
                  store.saveShareMembers(members)
                }
              },
            )
            if (i != members.lastIndex) {
              HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = CyberHomeColors.line)
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    ShareMemberEditDialog(
      member = null,
      onDismiss = { showAddDialog = false },
      onSave = { name, phone ->
        scope.launch {
          val newMember = store.createShareMember(name = name, phone = phone)
          val next = members + newMember
          members = next
          store.saveShareMembers(next)
        }
        showAddDialog = false
      },
    )
  }

  editMember?.let { member ->
    ShareMemberEditDialog(
      member = member,
      onDismiss = { editMember = null },
      onSave = { name, phone ->
        val updated = member.copyWith(name = name, phone = phone)
        val next = members.map { if (it.id == updated.id) updated else it }
        members = next
        scope.launch { store.saveShareMembers(next) }
        editMember = null
      },
    )
  }
}

@Composable
internal fun ShareMemberEditDialog(
  member: ShareMemberRecord?,
  onDismiss: () -> Unit,
  onSave: (String, String) -> Unit,
) {
  var name by remember { mutableStateOf(member?.name ?: "") }
  var phone by remember { mutableStateOf(member?.phone ?: "") }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
    shape = RoundedCornerShape(AppRadii.tile),
    title = {
      Text(
        text = if (member == null) stringResource(R.string.replica_add_member) else stringResource(R.string.replica_edit_member),
        style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
    },
    text = {
      Column {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          singleLine = true,
          label = { Text(stringResource(R.string.replica_member_name)) },
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          singleLine = true,
          label = { Text(stringResource(R.string.replica_member_phone)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val trimmed = name.trim()
          if (trimmed.isNotEmpty()) onSave(trimmed, phone.trim())
        },
        shape = cyberButtonShape,
        colors = cyberFilledButtonColors(),
      ) {
        Text(stringResource(R.string.common_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.common_cancel), color = CyberHomeColors.inkMuted)
      }
    },
  )
}

@Composable
internal fun ShareMemberTile(
  member: ShareMemberRecord,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(68.dp)
      .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CircleIcon(icon = Lucide.mine)
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = member.name,
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Text(
        text = if (member.phone.isEmpty()) {
          stringResource(R.string.replica_member_pending_format, formatDateText(java.time.LocalDateTime.ofInstant(member.createdAt, java.time.ZoneId.systemDefault())))
        } else {
          stringResource(R.string.replica_member_pending_phone_format, member.phone)
        },
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
    }
    AppPressable(
      onClick = { showMenu = true },
      shape = CircleShape,
      semanticsLabel = stringResource(R.string.replica_member_actions),
    ) {
      Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
        LucideIcon(icon = Lucide.more, color = CyberHomeColors.inkMuted)
      }
    }
    androidx.compose.material3.DropdownMenu(
      expanded = showMenu,
      onDismissRequest = { showMenu = false },
    ) {
      androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.common_edit)) },
        onClick = { showMenu = false; onEdit() },
      )
      androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.replica_remove)) },
        onClick = { showMenu = false; onDelete() },
      )
    }
  }
}

