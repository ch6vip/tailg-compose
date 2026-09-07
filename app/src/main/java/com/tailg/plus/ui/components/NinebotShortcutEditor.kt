package com.tailg.plus.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.model.NinebotShortcut
import com.tailg.plus.data.model.NinebotShortcutLayout
import com.tailg.plus.ui.theme.CyberHomeColors

internal val NinebotShortcut.labelRes: Int
    @StringRes get() = when (this) {
        NinebotShortcut.INDUCTION -> R.string.ninebot_tile_induction
        NinebotShortcut.BATTERY -> R.string.ninebot_tile_battery
        NinebotShortcut.SEAT -> R.string.control_card_seat
    }

internal val NinebotShortcut.iconRes: Int
    @DrawableRes get() = when (this) {
        NinebotShortcut.INDUCTION -> NinebotLucide.fingerprint
        NinebotShortcut.BATTERY -> NinebotLucide.battery
        NinebotShortcut.SEAT -> NinebotLucide.armchair
    }

@Composable
internal fun ninebotShortcutPosition(slot: Int): String = stringResource(when (slot) {
    0 -> R.string.nb_shortcuts_left
    1 -> R.string.nb_shortcuts_center
    else -> R.string.nb_shortcuts_right
})

private val ShortcutLayoutSaver = Saver<NinebotShortcutLayout, String>(
    save = { it.encode() },
    restore = { NinebotShortcutLayout.decode(it) },
)

/** A draft-only editor: choosing a tile never calls a vehicle command. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NinebotShortcutEditor(
    initialLayout: NinebotShortcutLayout,
    initialSlot: Int,
    saving: Boolean,
    saveFailed: Boolean,
    onSave: (NinebotShortcutLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = ShortcutLayoutSaver) { mutableStateOf(initialLayout) }
    var selectedSlot by rememberSaveable { mutableIntStateOf(initialSlot.coerceIn(0, 2)) }
    val blue = CyberHomeColors.primary
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CyberHomeColors.card,
        contentColor = CyberHomeColors.ink,
    ) {
        Column(
            modifier = Modifier
                .testTag("ninebot-shortcut-editor")
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.nb_shortcuts_title),
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700),
                )
                IconButton(onClick = onDismiss, enabled = !saving) {
                    NinebotIcon(NinebotLucide.x, contentDescription = stringResource(R.string.nb_shortcuts_close))
                }
            }
            Text(
                stringResource(R.string.nb_shortcuts_description),
                style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = CyberHomeColors.inkMuted),
            )
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.nb_shortcuts_preview),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.slots.forEachIndexed { slot, shortcut ->
                    val selected = slot == selectedSlot
                    val label = shortcut?.let { stringResource(it.labelRes) }
                        ?: stringResource(R.string.nb_shortcuts_empty)
                    AppPressable(
                        onClick = { selectedSlot = slot },
                        enabled = !saving,
                        modifier = Modifier.weight(1f).testTag("shortcut-slot-$slot").semantics {
                            this.selected = selected
                            if (saving) disabled()
                        },
                        background = if (selected) blue.copy(alpha = 0.10f) else CyberHomeColors.cardMuted,
                        borderWidth = 1.dp,
                        borderColor = if (selected) blue else Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                        semanticsLabel = stringResource(R.string.nb_shortcuts_slot_description, ninebotShortcutPosition(slot), label),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 126.dp).padding(horizontal = 6.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(ninebotShortcutPosition(slot), style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkMuted))
                            NinebotIcon(shortcut?.iconRes ?: NinebotLucide.plus, size = 27.dp, color = if (selected) blue else CyberHomeColors.ink)
                            Text(label, textAlign = TextAlign.Center, style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.W600))
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            NinebotShortcut.entries.forEach { shortcut ->
                val selected = draft.slots[selectedSlot] == shortcut
                val otherSlot = draft.slots.indexOf(shortcut).takeIf { it >= 0 && it != selectedSlot }
                val hint = when {
                    selected -> stringResource(R.string.nb_shortcuts_selected)
                    otherSlot != null -> stringResource(R.string.nb_shortcuts_swap, ninebotShortcutPosition(otherSlot))
                    else -> stringResource(when (shortcut) {
                        NinebotShortcut.INDUCTION -> R.string.nb_shortcuts_induction_hint
                        NinebotShortcut.BATTERY -> R.string.nb_shortcuts_battery_hint
                        NinebotShortcut.SEAT -> R.string.nb_shortcuts_seat_hint
                    })
                }
                ShortcutChoice(
                    icon = shortcut.iconRes,
                    label = stringResource(shortcut.labelRes),
                    hint = hint,
                    selected = selected,
                    trailingIcon = if (otherSlot != null) NinebotLucide.swapHorizontal else NinebotLucide.plus,
                    enabled = !saving,
                    modifier = Modifier.testTag("shortcut-choice-${shortcut.storageValue}"),
                    onClick = { draft = draft.assign(selectedSlot, if (selected) null else shortcut) },
                )
                Spacer(Modifier.height(6.dp))
            }
            ShortcutChoice(
                icon = NinebotLucide.x,
                label = stringResource(R.string.nb_shortcuts_empty),
                hint = stringResource(R.string.nb_shortcuts_empty_hint),
                selected = draft.slots[selectedSlot] == null,
                enabled = !saving,
                modifier = Modifier.testTag("shortcut-choice-empty"),
                onClick = { draft = draft.assign(selectedSlot, null) },
            )
            Spacer(Modifier.height(18.dp))
            if (saveFailed) {
                Text(
                    stringResource(R.string.nb_shortcuts_save_failed),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.danger),
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { draft = NinebotShortcutLayout.Default },
                    enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("shortcut-reset"),
                ) {
                    NinebotIcon(NinebotLucide.rotateCcw, size = 16.dp, color = blue)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.nb_shortcuts_reset))
                }
                Button(
                    onClick = { onSave(draft) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("shortcut-save"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blue, contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = CyberHomeColors.inkMuted)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (saving) R.string.nb_shortcuts_saving else R.string.nb_shortcuts_save))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.nb_shortcuts_vehicle_hint),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted),
            )
        }
    }
}

@Composable
private fun ShortcutChoice(
    @DrawableRes icon: Int,
    label: String,
    hint: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    trailingIcon: Int = NinebotLucide.plus,
    onClick: () -> Unit,
) {
    val blue = CyberHomeColors.primary
    AppPressable(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().semantics {
            this.selected = selected
            if (!enabled) disabled()
        },
        background = if (selected) blue.copy(alpha = 0.08f) else CyberHomeColors.cardMuted,
        shape = RoundedCornerShape(16.dp),
        semanticsLabel = stringResource(R.string.nb_shortcuts_choose, label),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(CyberHomeColors.card, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) { NinebotIcon(icon, size = 23.dp, color = if (selected) blue else CyberHomeColors.ink) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W600))
                Text(hint, style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = CyberHomeColors.inkMuted))
            }
            NinebotIcon(if (selected) NinebotLucide.check else trailingIcon, size = 19.dp, color = if (selected) blue else CyberHomeColors.inkMuted)
        }
    }
}
