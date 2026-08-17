package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.model.NfcKeyRecord
import com.tailg.plus.data.store.ReplicaFeatureStore
import com.tailg.plus.service.BleNfcService
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
import com.tailg.plus.util.formatDateMinuteText
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch

/**
 * NFC key page of [OfficialReplicaScreen] (Dart NfcKeyPage).
 * Extracted from OfficialReplicaScreen.kt for maintainability.
 */

@Composable
internal fun NfcKeyTab(
  store: ReplicaFeatureStore,
  bleNfc: BleNfcService,
  snackbarHostState: SnackbarHostState,
  scope: kotlinx.coroutines.CoroutineScope,
) {
  var records by remember { mutableStateOf<List<NfcKeyRecord>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var showEditDialog by remember { mutableStateOf<NfcKeyRecord?>(null) }
  var showAddDialog by remember { mutableStateOf(false) }
  val strKeyDeleted = stringResource(R.string.replica_key_deleted)
  val strKeyDeleteFailed = stringResource(R.string.replica_key_delete_failed)
  val strKeyTypeCard = stringResource(R.string.replica_key_type_card)
  val strKeyTypeWatch = stringResource(R.string.replica_key_type_watch)
  val strKeyWritten = stringResource(R.string.replica_key_written)
  val strKeyWriteFailed = stringResource(R.string.replica_key_write_failed)
  val strNotLoggedInLocal = stringResource(R.string.replica_not_logged_in_local)

  LaunchedEffect(Unit) {
    records = store.loadNfcKeys()
    loading = false
  }

  val canBle = bleNfc.canWriteOfficialNfc

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
  ) {
    item {
      Text(
        text = stringResource(R.string.replica_official_local),
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
      )
    }
    item { Spacer(Modifier.height(8.dp)) }
    item {
      ReplicaNotice(
        icon = Lucide.nfc,
        title = if (canBle) stringResource(R.string.replica_nfc_available) else stringResource(R.string.replica_nfc_pending_login),
        subtitle = if (canBle) {
          stringResource(R.string.replica_nfc_logged_in_desc)
        } else {
          stringResource(R.string.replica_nfc_not_logged_in_desc)
        },
      )
    }
    item { Spacer(Modifier.height(14.dp)) }
    if (loading) {
      item {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = CyberHomeColors.primary)
        }
      }
    } else if (records.isEmpty()) {
      item {
        EmptyReplicaCard(icon = Lucide.keyOff, title = stringResource(R.string.replica_no_keys), subtitle = stringResource(R.string.replica_no_keys_hint))
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
          records.forEachIndexed { i, record ->
            NfcKeyTile(
              record = record,
              onEdit = { showEditDialog = record },
              onDelete = {
                scope.launch {
                  if (bleNfc.canWriteOfficialNfc) {
                    val ok = bleNfc.delNfc("01")
                    AppSnack.info(snackbarHostState, if (ok) strKeyDeleted else strKeyDeleteFailed)
                  }
                  records = records.filter { it.id != record.id }
                  store.saveNfcKeys(records)
                }
              },
            )
            if (i != records.lastIndex) {
              HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = CyberHomeColors.line)
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    NfcKeyEditDialog(
      record = null,
      onDismiss = { showAddDialog = false },
      onSave = { name, type ->
        scope.launch {
          val newRecord = store.createNfcKey(name = name, type = type)
          if (bleNfc.canWriteOfficialNfc) {
            val ok = if (type == strKeyTypeCard) {
              bleNfc.addCard("01")
            } else {
              bleNfc.addUserKey(keyType = if (type == strKeyTypeWatch) 2 else 1, type = "1")
            }
            if (ok) {
              AppSnack.success(snackbarHostState, strKeyWritten)
            } else {
              AppSnack.info(snackbarHostState, strKeyWriteFailed)
            }
          } else {
            AppSnack.info(snackbarHostState, strNotLoggedInLocal)
          }
          val next = records + newRecord
          records = next
          store.saveNfcKeys(next)
        }
        showAddDialog = false
      },
    )
  }

  showEditDialog?.let { record ->
    NfcKeyEditDialog(
      record = record,
      onDismiss = { showEditDialog = null },
      onSave = { name, type ->
        val updated = record.copyWith(name = name, type = type)
        val next = records.map { if (it.id == updated.id) updated else it }
        records = next
        scope.launch { store.saveNfcKeys(next) }
        showEditDialog = null
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NfcKeyEditDialog(
  record: NfcKeyRecord?,
  onDismiss: () -> Unit,
  onSave: (String, String) -> Unit,
) {
  val strKeyTypePhone = stringResource(R.string.replica_key_type_phone)
  var name by remember { mutableStateOf(record?.name ?: "") }
  var type by remember { mutableStateOf(record?.type ?: strKeyTypePhone) }
  var expanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
    shape = RoundedCornerShape(AppRadii.tile),
    title = {
      Text(
        text = if (record == null) stringResource(R.string.replica_add_key) else stringResource(R.string.replica_edit_key),
        style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
    },
    text = {
      Column {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          singleLine = true,
          placeholder = { Text(stringResource(R.string.replica_key_name)) },
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(
          expanded = expanded,
          onExpandedChange = { expanded = it },
        ) {
          OutlinedTextField(
            value = type,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.replica_key_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = cyberTextFieldColors(),
            shape = cyberTextFieldShape,
            modifier = Modifier
              .fillMaxWidth()
              .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
          )
          androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
          ) {
            listOf(stringResource(R.string.replica_key_type_phone), stringResource(R.string.replica_key_type_watch), stringResource(R.string.replica_key_type_card)).forEach { item ->
              DropdownMenuItem(
                text = { Text(item) },
                onClick = { type = item; expanded = false },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val trimmed = name.trim()
          if (trimmed.isNotEmpty()) onSave(trimmed, type)
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
internal fun NfcKeyTile(
  record: NfcKeyRecord,
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
    CircleIcon(
      icon = when (record.type) {
        stringResource(R.string.replica_key_type_card) -> Lucide.creditCard
        stringResource(R.string.replica_key_type_watch) -> Lucide.watch
        else -> Lucide.smartphone
      },
    )
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = record.name,
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Text(
        text = "${record.type} · ${formatDateText(java.time.LocalDateTime.ofInstant(record.createdAt, java.time.ZoneId.systemDefault()))}",
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
    }
    AppPressable(
      onClick = { showMenu = true },
      shape = CircleShape,
      semanticsLabel = stringResource(R.string.replica_key_actions),
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
        text = { Text(stringResource(R.string.replica_rename)) },
        onClick = { showMenu = false; onEdit() },
      )
      androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.common_delete)) },
        onClick = { showMenu = false; onDelete() },
      )
    }
  }
}

