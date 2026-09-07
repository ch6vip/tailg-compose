package com.tailg.plus.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tailg.plus.R
import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.data.model.NinebotShortcutLayout
import com.tailg.plus.data.store.NinebotShortcutStore
import com.tailg.plus.domain.control.ControlChannelAvailability
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.components.NinebotControlGrid
import com.tailg.plus.ui.components.NinebotShortcutEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class ShortcutLoadState(
    val layout: NinebotShortcutLayout? = null,
    val failed: Boolean = false,
)

/** Owns the editor and its draft for one vehicle, independently of control commands. */
@Composable
internal fun NinebotControlSection(
    vehicleKey: String,
    shortcutStore: NinebotShortcutStore,
    log: LogService,
    powered: Boolean?,
    busy: Boolean,
    activeCommand: CommandCode?,
    findAvailability: ControlChannelAvailability,
    powerAvailability: ControlChannelAvailability,
    seatAvailability: ControlChannelAvailability,
    onFind: () -> Unit,
    onPowerToggle: suspend () -> Unit,
    onArmToggle: () -> Unit,
    onSettings: () -> Unit,
    onSeat: () -> Unit,
    onBattery: () -> Unit,
    onInduction: () -> Unit,
) {
    if (vehicleKey.isBlank()) return
    // Changing vehicles discards the draft and cancels the old collection/save scope.
    key(vehicleKey) {
        var retry by remember { mutableIntStateOf(0) }
        val layouts = remember(shortcutStore, vehicleKey, retry) {
            shortcutStore.observe(vehicleKey)
                .map { ShortcutLoadState(layout = it) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    log.operation("读取九号快捷键失败", detail = error.toString())
                    emit(ShortcutLoadState(failed = true))
                }
        }
        val loadState by layouts.collectAsStateWithLifecycle(initialValue = ShortcutLoadState())
        var editorSlot by rememberSaveable { mutableIntStateOf(-1) }
        var saving by remember { mutableStateOf(false) }
        var saveFailed by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Column {
            NinebotControlGrid(
                powered = powered,
                busy = busy,
                activeCommand = activeCommand,
                findAvailability = findAvailability,
                powerAvailability = powerAvailability,
                seatAvailability = seatAvailability,
                onFind = onFind,
                onPowerToggle = onPowerToggle,
                onArmToggle = onArmToggle,
                onSettings = onSettings,
                onSeat = onSeat,
                onBattery = onBattery,
                onInduction = onInduction,
                shortcutLayout = loadState.layout,
                shortcutsEditable = !busy && !saving,
                onEditShortcuts = { slot ->
                    if (!busy && !saving && loadState.layout != null) {
                        saveFailed = false
                        editorSlot = slot
                    }
                },
            )
            if (loadState.failed) {
                TextButton(onClick = { retry++ }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.nb_shortcuts_load_failed))
                }
            }
        }
        val loadedLayout = loadState.layout
        if (editorSlot >= 0 && loadedLayout != null) {
            NinebotShortcutEditor(
                initialLayout = loadedLayout,
                initialSlot = editorSlot,
                saving = saving,
                saveFailed = saveFailed,
                onDismiss = { if (!saving) editorSlot = -1 },
                onSave = { layout ->
                    if (!saving) {
                        saving = true
                        saveFailed = false
                        scope.launch {
                            try {
                                shortcutStore.save(vehicleKey, layout)
                                editorSlot = -1
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                log.operation("保存九号快捷键失败", detail = error.toString())
                                saveFailed = true
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            )
        }
    }
}
