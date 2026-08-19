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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.model.VehicleLocation
import com.tailg.plus.data.store.ReplicaFeatureStore
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberOutlinedButtonBorder
import com.tailg.plus.ui.components.cyberOutlinedButtonColors
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.cyberTextFieldColors
import com.tailg.plus.ui.components.cyberTextFieldShape
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppSpacing
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.formatDateText
import kotlinx.coroutines.launch

/**
 * Electric-fence draft page of [OfficialReplicaScreen] (Dart ElectricFencePage).
 * Extracted from OfficialReplicaScreen.kt for maintainability.
 */

@Composable
internal fun ElectricFenceTab(
  store: ReplicaFeatureStore,
  vehicleStore: VehicleStore,
  snackbarHostState: SnackbarHostState,
  scope: kotlinx.coroutines.CoroutineScope,
) {
  var enabled by remember { mutableStateOf(false) }
  var latText by remember { mutableStateOf("") }
  var lngText by remember { mutableStateOf("") }
  var radiusText by remember { mutableStateOf("500") }
  var loading by remember { mutableStateOf(true) }
  var lastLocation by remember { mutableStateOf<VehicleLocation?>(null) }
  val context = androidx.compose.ui.platform.LocalContext.current
  val strInvalidCoords = stringResource(R.string.replica_invalid_coords)
  val strNoMapApp = stringResource(R.string.replica_no_map_app)
  val strRadiusHint = stringResource(R.string.replica_radius_hint)
  val strFenceSavedDraft = stringResource(R.string.replica_fence_saved_draft)

  LaunchedEffect(Unit) {
    vehicleStore.init()
    val config = store.loadFenceConfig()
    lastLocation = vehicleStore.defaultVehicle?.lastLocation
    val latitude = config?.latitude ?: lastLocation?.latitude
    val longitude = config?.longitude ?: lastLocation?.longitude
    enabled = config?.enabled ?: false
    latText = latitude?.let { "%.6f".format(it) } ?: ""
    lngText = longitude?.let { "%.6f".format(it) } ?: ""
    radiusText = (config?.radiusMeters ?: 500).toString()
    loading = false
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(bottom = 24.dp),
  ) {
    Text(
      text = stringResource(R.string.replica_local_draft),
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
      modifier = Modifier.padding(horizontal = AppSpacing.screenX),
    )
    Spacer(Modifier.height(8.dp))
    ReplicaNotice(
      icon = Lucide.locationSearching,
      title = stringResource(R.string.replica_unofficial_fence),
      subtitle = stringResource(R.string.replica_local_fence_desc),
    )
    Spacer(Modifier.height(14.dp))
    if (loading) {
      Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CyberHomeColors.primary)
      }
    } else {
      Column(
        modifier = Modifier
          .padding(horizontal = AppSpacing.screenX)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.card)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(16.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(
            checked = enabled,
            onCheckedChange = { enabled = it },
            colors = androidx.compose.material3.SwitchDefaults.colors(
              checkedThumbColor = CyberHomeColors.white,
              checkedTrackColor = CyberHomeColors.primary,
              uncheckedThumbColor = CyberHomeColors.white,
              uncheckedTrackColor = CyberHomeColors.controlStrong,
            ),
          )
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.replica_enable_fence),
              style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
            )
            Text(
              text = stringResource(R.string.replica_save_fence_settings),
              style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted),
            )
          }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = latText,
          onValueChange = { latText = it },
          singleLine = true,
          label = { Text(stringResource(R.string.replica_center_lat)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = lngText,
          onValueChange = { lngText = it },
          singleLine = true,
          label = { Text(stringResource(R.string.replica_center_lng)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = radiusText,
          onValueChange = { radiusText = it.filter { c -> c.isDigit() } },
          singleLine = true,
          label = { Text(stringResource(R.string.replica_radius_m)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = {
              lastLocation?.let {
                latText = "%.6f".format(it.latitude)
                lngText = "%.6f".format(it.longitude)
              }
            },
            enabled = lastLocation != null,
            shape = cyberButtonShape,
            colors = cyberOutlinedButtonColors(),
            border = cyberOutlinedButtonBorder,
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
          ) {
            LucideIcon(icon = Lucide.locate, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.replica_use_last_location))
          }
          OutlinedButton(
            onClick = {
              val lat = latText.trim().toDoubleOrNull()
              val lng = lngText.trim().toDoubleOrNull()
              if (lat == null || lng == null) {
                scope.launch { AppSnack.info(snackbarHostState, strInvalidCoords) }
              } else {
                val geoUri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri)
                try {
                  context.startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                  scope.launch { AppSnack.info(snackbarHostState, strNoMapApp) }
                }
              }
            },
            shape = cyberButtonShape,
            colors = cyberOutlinedButtonColors(),
            border = cyberOutlinedButtonBorder,
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
          ) {
            LucideIcon(icon = Lucide.map, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.replica_open_map))
          }
        }
        Spacer(Modifier.height(10.dp))
        Button(
          onClick = {
            val latitude = latText.trim().toDoubleOrNull()
            val longitude = lngText.trim().toDoubleOrNull()
            val radius = radiusText.trim().toIntOrNull() ?: 500
            if (latitude == null || longitude == null) {
              scope.launch { AppSnack.info(snackbarHostState, strInvalidCoords) }
              return@Button
            }
            if (radius < 100 || radius > 10000) {
              scope.launch { AppSnack.info(snackbarHostState, strRadiusHint) }
              return@Button
            }
            scope.launch {
              store.saveFenceConfig(store.createFenceConfig(enabled = enabled, latitude = latitude, longitude = longitude, radiusMeters = radius))
              AppSnack.info(snackbarHostState, strFenceSavedDraft)
            }
          },
          shape = cyberButtonShape,
          colors = cyberFilledButtonColors(),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        ) {
          LucideIcon(icon = Lucide.save, size = 18.dp, color = CyberHomeColors.white)
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.replica_save_fence), style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700))
        }
      }
    }
  }
}

