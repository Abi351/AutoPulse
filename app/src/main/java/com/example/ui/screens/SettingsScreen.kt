package com.example.ui.screens

import android.app.TimePickerDialog
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HeadUnitSettings
import com.example.data.MAX_DOCK_APPS
import com.example.data.PreferencesRepository
import com.example.hardware.EqPreset
import com.example.hardware.InstalledAppInfo
import com.example.hardware.MediaPlayerState
import com.example.hardware.SpeedGpsState
import com.example.ui.theme.AccentThemeColor
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CoverSyncUiState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SettingsScreen(
    settings: HeadUnitSettings,
    mediaStateFlow: StateFlow<MediaPlayerState>,
    speedStateFlow: StateFlow<SpeedGpsState>,
    installedApps: List<InstalledAppInfo>,
    isDefaultLauncher: Boolean,
    hasOverlayPermission: Boolean,
    isNightFilterActiveNow: Boolean,
    coverSyncStateFlow: StateFlow<CoverSyncUiState>,
    onAccentColorSelected: (String) -> Unit,
    onNightDimmerChange: (Float) -> Unit,
    onAutoNightModeChange: (Boolean) -> Unit,
    onNightScheduleChange: (Int, Int) -> Unit,
    onSetEqEnabled: (Boolean) -> Unit,
    onApplyEqPreset: (Int) -> Unit,
    onSetEqBand: (Short, Short) -> Unit,
    onOpenSystemDsp: () -> Unit,
    onSdvcChange: (Boolean) -> Unit,
    onFloatingPillChange: (Boolean) -> Unit,
    onExportConfig: (Uri) -> Unit,
    onImportConfig: (Uri) -> Unit,
    onFetchOnlineCovers: (Boolean) -> Unit,
    onAutoResumeMusicChange: (Boolean) -> Unit,
    onAutoLaunchPackageChange: (String) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenOverlayPermissionSettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenAppLibrary: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAutoLaunchPicker by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) onExportConfig(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportConfig(uri)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoColumns = maxWidth >= 740.dp
        val outerPad = if (maxHeight < 450.dp) 14.dp else 20.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(outerPad),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "settings_header") {
                Text(
                    text = "Paramètres Autoradio",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                )
            }

            if (isTwoColumns) {
                item(key = "row_theme_metadata") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            RainbowThemeSection(
                                selectedColorId = settings.accentColorId,
                                onSelectColor = onAccentColorSelected,
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OnlineMetadataSection(
                                coverSyncStateFlow = coverSyncStateFlow,
                                onFetchOnlineCovers = onFetchOnlineCovers,
                            )
                        }
                    }
                }

                item(key = "row_eq_backup") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AudioEqualizerSection(
                                mediaStateFlow = mediaStateFlow,
                                sdvcEnabled = settings.sdvcEnabled,
                                onSetEqEnabled = onSetEqEnabled,
                                onApplyEqPreset = onApplyEqPreset,
                                onSetEqBand = onSetEqBand,
                                onOpenSystemDsp = onOpenSystemDsp,
                                onSdvcChange = onSdvcChange,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            BackupRestoreSection(
                                onExportClick = { exportLauncher.launch("autopulse_config.json") },
                                onImportClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            )
                            UniversalMediaSection(
                                mediaStateFlow = mediaStateFlow,
                                onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                            )
                        }
                    }
                }

                item(key = "row_night_acc") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            NightModeSection(
                                settings = settings,
                                hasOverlayPermission = hasOverlayPermission,
                                isNightFilterActiveNow = isNightFilterActiveNow,
                                onNightDimmerChange = onNightDimmerChange,
                                onAutoNightModeChange = onAutoNightModeChange,
                                onNightScheduleChange = onNightScheduleChange,
                                onFloatingPillChange = onFloatingPillChange,
                                onOpenOverlayPermissionSettings = onOpenOverlayPermissionSettings,
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AccBootWakeSection(
                                settings = settings,
                                installedApps = installedApps,
                                onAutoResumeMusicChange = onAutoResumeMusicChange,
                                onOpenAutoLaunchPicker = { showAutoLaunchPicker = true },
                            )
                        }
                    }
                }

                item(key = "row_launcher_hardware") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            LauncherSection(
                                isDefaultLauncher = isDefaultLauncher,
                                onOpenLauncherSettings = onOpenLauncherSettings,
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            DockAndHardwareSection(
                                settings = settings,
                                mediaStateFlow = mediaStateFlow,
                                speedStateFlow = speedStateFlow,
                                onOpenAppLibrary = onOpenAppLibrary,
                                onRequestPermissions = onRequestPermissions,
                            )
                        }
                    }
                }
            } else {
                item(key = "theme") {
                    RainbowThemeSection(
                        selectedColorId = settings.accentColorId,
                        onSelectColor = onAccentColorSelected,
                    )
                }
                item(key = "eq") {
                    AudioEqualizerSection(
                        mediaStateFlow = mediaStateFlow,
                        sdvcEnabled = settings.sdvcEnabled,
                        onSetEqEnabled = onSetEqEnabled,
                        onApplyEqPreset = onApplyEqPreset,
                        onSetEqBand = onSetEqBand,
                        onOpenSystemDsp = onOpenSystemDsp,
                        onSdvcChange = onSdvcChange,
                    )
                }
                item(key = "metadata") {
                    OnlineMetadataSection(
                        coverSyncStateFlow = coverSyncStateFlow,
                        onFetchOnlineCovers = onFetchOnlineCovers,
                    )
                }
                item(key = "backup") {
                    BackupRestoreSection(
                        onExportClick = { exportLauncher.launch("autopulse_config.json") },
                        onImportClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                }
                item(key = "night") {
                    NightModeSection(
                        settings = settings,
                        hasOverlayPermission = hasOverlayPermission,
                        isNightFilterActiveNow = isNightFilterActiveNow,
                        onNightDimmerChange = onNightDimmerChange,
                        onAutoNightModeChange = onAutoNightModeChange,
                        onNightScheduleChange = onNightScheduleChange,
                        onFloatingPillChange = onFloatingPillChange,
                        onOpenOverlayPermissionSettings = onOpenOverlayPermissionSettings,
                    )
                }
                item(key = "universal_media") {
                    UniversalMediaSection(
                        mediaStateFlow = mediaStateFlow,
                        onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                    )
                }
                item(key = "acc") {
                    AccBootWakeSection(
                        settings = settings,
                        installedApps = installedApps,
                        onAutoResumeMusicChange = onAutoResumeMusicChange,
                        onOpenAutoLaunchPicker = { showAutoLaunchPicker = true },
                    )
                }
                item(key = "launcher") {
                    LauncherSection(
                        isDefaultLauncher = isDefaultLauncher,
                        onOpenLauncherSettings = onOpenLauncherSettings,
                    )
                }
                item(key = "hardware") {
                    DockAndHardwareSection(
                        settings = settings,
                        mediaStateFlow = mediaStateFlow,
                        speedStateFlow = speedStateFlow,
                        onOpenAppLibrary = onOpenAppLibrary,
                        onRequestPermissions = onRequestPermissions,
                    )
                }
            }
        }
    }

    if (showAutoLaunchPicker) {
        AutoLaunchAppPickerDialog(
            installedApps = installedApps,
            selectedPackageName = settings.autoLaunchPackageName,
            onSelectPackage = { pkg ->
                onAutoLaunchPackageChange(pkg)
                showAutoLaunchPicker = false
            },
            onDismiss = { showAutoLaunchPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioEqualizerSection(
    mediaStateFlow: StateFlow<MediaPlayerState>,
    sdvcEnabled: Boolean,
    onSetEqEnabled: (Boolean) -> Unit,
    onApplyEqPreset: (Int) -> Unit,
    onSetEqBand: (Short, Short) -> Unit,
    onOpenSystemDsp: () -> Unit,
    onSdvcChange: (Boolean) -> Unit,
) {
    val mediaState by mediaStateFlow.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    SettingsSectionCard(title = "Égaliseur Audio & DSP (5 bandes)") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Column {
                    Text(
                        text = "Activer l'égaliseur",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        color = TextPrimary,
                    )
                    Text(
                        text = if (mediaState.eqEnabled) "Profil actif : ${mediaState.eqPresetName}" else "Égaliseur désactivé",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = if (mediaState.eqEnabled) EmeraldOk else TextSecondary,
                    )
                }
            }
            Switch(
                checked = mediaState.eqEnabled,
                onCheckedChange = onSetEqEnabled,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = primary),
                modifier = Modifier.testTag("settings_eq_switch"),
            )
        }

        Spacer(Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqPreset.entries.forEach { preset ->
                val selected = mediaState.eqPresetIndex == preset.index
                FilterChip(
                    selected = selected,
                    onClick = { onApplyEqPreset(preset.index) },
                    label = {
                        Text(
                            text = preset.label,
                            fontSize = 14.sp,
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier.testTag("settings_eq_preset_${preset.name.lowercase()}"),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        mediaState.eqBands.forEach { band ->
            val freqLabel = if (band.centerFreqHz >= 1000) {
                val khz = band.centerFreqHz / 1000f
                if (khz % 1f == 0f) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
            } else {
                "${band.centerFreqHz} Hz"
            }
            val minRange = minOf(band.minMb.toFloat(), -1500f)
            val maxRange = maxOf(band.maxMb.toFloat(), 1500f)
            val dbValue = (band.levelMb / 100).coerceIn(-15, 15)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = freqLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.sp,
                    ),
                    color = accent,
                    modifier = Modifier.width(64.dp),
                )
                Slider(
                    value = band.levelMb.toFloat().coerceIn(minRange, maxRange),
                    onValueChange = { onSetEqBand(band.index, it.toInt().toShort()) },
                    enabled = mediaState.eqEnabled,
                    valueRange = minRange..maxRange,
                    colors = SliderDefaults.colors(
                        thumbColor = primary,
                        activeTrackColor = primary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_eq_band_${band.index}"),
                )
                Text(
                    text = "${if (dbValue > 0) "+" else ""}${dbValue} dB",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextPrimary,
                    modifier = Modifier.width(56.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onOpenSystemDsp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("settings_open_dsp_btn"),
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Ouvrir DSP Autoradio",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Speed, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Column {
                    Text(
                        text = "Volume automatique selon la vitesse",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = TextPrimary,
                    )
                    Text(
                        text = "+2 crans > 90 km/h • retour < 55 km/h (délai 5 s)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = TextSecondary,
                    )
                }
            }
            Switch(
                checked = sdvcEnabled,
                onCheckedChange = onSdvcChange,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = primary),
                modifier = Modifier.testTag("settings_sdvc_switch"),
            )
        }
    }
}

@Composable
private fun BackupRestoreSection(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    SettingsSectionCard(title = "Sauvegarde & Restauration de la configuration") {
        Text(
            text = "Exportez ou restaurez au format JSON vos applications du dock, thème couleur, égaliseur, mode nuit et options ACC.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onExportClick,
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("settings_export_config_btn"),
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Exporter", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("settings_import_config_btn"),
            ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Importer", color = accent, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OnlineMetadataSection(
    coverSyncStateFlow: StateFlow<CoverSyncUiState>,
    onFetchOnlineCovers: (Boolean) -> Unit,
) {
    val coverSyncState by coverSyncStateFlow.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    var forceRedownload by remember { mutableStateOf(false) }

    SettingsSectionCard(title = "Pochettes & Infos Musique en ligne") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Album, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Text(
                text = "Récupère automatiquement pochettes (RGB_565 256px), artiste, album, genre et année pour une utilisation hors ligne.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = TextSecondary,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Forcer le re-téléchargement de toutes les pochettes",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = forceRedownload,
                onCheckedChange = { forceRedownload = it },
                enabled = !coverSyncState.isRunning,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = primary),
                modifier = Modifier.testTag("settings_force_covers_switch"),
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { onFetchOnlineCovers(forceRedownload) },
            enabled = !coverSyncState.isRunning,
            colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("settings_fetch_covers_btn"),
        ) {
            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (coverSyncState.isRunning) {
                    "Téléchargement en cours..."
                } else {
                    "Recharger les pochettes et infos en ligne"
                },
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (coverSyncState.isRunning && coverSyncState.totalCount > 0) {
            Spacer(Modifier.height(10.dp))
            val progress = (coverSyncState.processedCount.toFloat() / coverSyncState.totalCount.toFloat())
                .coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                color = primary,
                trackColor = CarbonBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${coverSyncState.processedCount} / ${coverSyncState.totalCount} morceaux analysés",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = accent,
            )
        }

        coverSyncState.statusMessage?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = if (coverSyncState.isOfflineError) AmberWarn else EmeraldOk,
                modifier = Modifier.testTag("settings_covers_status"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RainbowThemeSection(
    selectedColorId: String,
    onSelectColor: (String) -> Unit,
) {
    val active = AccentThemeColor.fromId(selectedColorId)

    SettingsSectionCard(title = "Thème Couleur Arc-en-ciel (7 couleurs)") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Palette, contentDescription = null, tint = active.primary, modifier = Modifier.size(22.dp))
            Text(
                text = "Couleur active : ${active.label}",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentThemeColor.entries.forEach { option ->
                val isSelected = option.id == active.id
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(option.primary)
                        .border(
                            width = if (isSelected) 3.dp else 1.5.dp,
                            color = if (isSelected) Color.White else CarbonBorder,
                            shape = CircleShape,
                        )
                        .clickable { onSelectColor(option.id) }
                        .testTag("accent_color_${option.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = option.label,
                            tint = option.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NightModeSection(
    settings: HeadUnitSettings,
    hasOverlayPermission: Boolean,
    isNightFilterActiveNow: Boolean,
    onNightDimmerChange: (Float) -> Unit,
    onAutoNightModeChange: (Boolean) -> Unit,
    onNightScheduleChange: (Int, Int) -> Unit,
    onFloatingPillChange: (Boolean) -> Unit,
    onOpenOverlayPermissionSettings: () -> Unit,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    SettingsSectionCard(title = "Luminosité & Mode Nuit Automatique") {
        Text(
            text = "Atténuation nocturne écran : ${(settings.nightDimmerAlpha * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            color = TextPrimary,
        )
        Slider(
            value = settings.nightDimmerAlpha.coerceIn(0f, 0.85f),
            onValueChange = onNightDimmerChange,
            valueRange = 0f..0.85f,
            colors = SliderDefaults.colors(thumbColor = primary, activeTrackColor = primary),
            modifier = Modifier.testTag("settings_dimmer_slider"),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.NightlightRound, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Column {
                    Text(
                        text = "Mode nuit automatique",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = TextPrimary,
                    )
                    Text(
                        text = if (settings.autoNightModeEnabled) {
                            if (isNightFilterActiveNow) "Actif actuellement (horaire nuit)" else "Inactif actuellement (journée)"
                        } else {
                            "Assombrissement automatique selon l'heure"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = if (settings.autoNightModeEnabled && isNightFilterActiveNow) EmeraldOk else TextSecondary,
                    )
                }
            }
            Switch(
                checked = settings.autoNightModeEnabled,
                onCheckedChange = onAutoNightModeChange,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = primary),
                modifier = Modifier.testTag("settings_auto_night_switch"),
            )
        }

        if (settings.autoNightModeEnabled) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val h = settings.nightStartMinutes / 60
                        val m = settings.nightStartMinutes % 60
                        TimePickerDialog(
                            context,
                            { _, pickedH, pickedM ->
                                onNightScheduleChange(pickedH * 60 + pickedM, settings.nightEndMinutes)
                            },
                            h,
                            m,
                            true,
                        ).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("settings_night_start_btn"),
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Début ${PreferencesRepository.formatMinutesAsTime(settings.nightStartMinutes)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily, fontSize = 14.sp),
                        color = TextPrimary,
                    )
                }

                OutlinedButton(
                    onClick = {
                        val h = settings.nightEndMinutes / 60
                        val m = settings.nightEndMinutes % 60
                        TimePickerDialog(
                            context,
                            { _, pickedH, pickedM ->
                                onNightScheduleChange(settings.nightStartMinutes, pickedH * 60 + pickedM)
                            },
                            h,
                            m,
                            true,
                        ).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("settings_night_end_btn"),
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Fin ${PreferencesRepository.formatMinutesAsTime(settings.nightEndMinutes)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily, fontSize = 14.sp),
                        color = TextPrimary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Column {
                    Text(
                        text = "Pastille flottante rapide (Android Auto)",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = TextPrimary,
                    )
                    Text(
                        text = "Appui simple : filtre nuit • Appui long : retour AutoPulse",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = TextSecondary,
                    )
                }
            }
            Switch(
                checked = settings.floatingPillEnabled,
                onCheckedChange = onFloatingPillChange,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = primary),
                modifier = Modifier.testTag("settings_floating_pill_switch"),
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (hasOverlayPermission) {
                "Superposition globale : Active (par-dessus Android Auto, Waze & Maps)"
            } else {
                "Superposition globale : Autorisation requise pour filtre nuit & pastille"
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = if (hasOverlayPermission) EmeraldOk else AmberWarn,
            modifier = Modifier.testTag("settings_overlay_status"),
        )
        if (!hasOverlayPermission) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpenOverlayPermissionSettings,
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("settings_overlay_permission_btn"),
            ) {
                Text("Autoriser la superposition (Android Auto)", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun UniversalMediaSection(
    mediaStateFlow: StateFlow<MediaPlayerState>,
    onOpenNotificationListenerSettings: () -> Unit,
) {
    val mediaState by mediaStateFlow.collectAsStateWithLifecycle()
    val hasNotificationAccess = mediaState.hasNotificationListenerAccess
    val activeExternalApp = mediaState.externalMedia.takeIf { it.hasActiveSession }?.appName
    val primary = MaterialTheme.colorScheme.primary

    SettingsSectionCard(title = "Contrôle Média Universel & Radio FM") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = if (hasNotificationAccess) EmeraldOk else AmberWarn,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = if (hasNotificationAccess) {
                    "Statut : Accès actif" + (activeExternalApp?.let { " ($it)" } ?: "")
                } else {
                    "Statut : Accès aux notifications requis"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = if (hasNotificationAccess) EmeraldOk else AmberWarn,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Détecte automatiquement Spotify, YouTube Music, Deezer, Bluetooth et la Radio FM autoradio (fréquence + RDS).",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenNotificationListenerSettings,
            colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier
                .height(48.dp)
                .testTag("settings_notification_access_btn"),
        ) {
            Text(
                text = if (hasNotificationAccess) "Gérer l'accès aux notifications" else "Activer l'accès aux notifications",
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun AccBootWakeSection(
    settings: HeadUnitSettings,
    installedApps: List<InstalledAppInfo>,
    onAutoResumeMusicChange: (Boolean) -> Unit,
    onOpenAutoLaunchPicker: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    val chosenAppLabel = remember(settings.autoLaunchPackageName, installedApps) {
        if (settings.autoLaunchPackageName.isBlank()) {
            "Aucune"
        } else {
            installedApps.firstOrNull { it.packageName == settings.autoLaunchPackageName }?.label
                ?: settings.autoLaunchPackageName
        }
    }

    SettingsSectionCard(title = "Démarrage automatique (ACC)") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Column {
                    Text(
                        text = "Reprendre la musique automatiquement",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = TextPrimary,
                    )
                    Text(
                        text = "Relance la lecture (Play) 3 secondes après démarrage ou réveil de l'écran",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = TextSecondary,
                    )
                }
            }
            Switch(
                checked = settings.autoResumeMusicEnabled,
                onCheckedChange = onAutoResumeMusicChange,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = primary),
                modifier = Modifier.testTag("settings_acc_resume_switch"),
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Application favorite au démarrage (3s)",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenAutoLaunchPicker,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("settings_acc_app_picker_btn"),
        ) {
            Icon(Icons.Rounded.Apps, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "App au démarrage : $chosenAppLabel",
                color = TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LauncherSection(
    isDefaultLauncher: Boolean,
    onOpenLauncherSettings: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary

    SettingsSectionCard(title = "Launcher d'accueil Android (Touche Home)") {
        Text(
            text = if (isDefaultLauncher) "Statut : AutoPulse est votre Launcher par défaut" else "Statut : Non défini comme écran d'accueil",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            color = if (isDefaultLauncher) EmeraldOk else AmberWarn,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Définissez AutoPulse comme application d'accueil par défaut pour revenir automatiquement sur ce tableau de bord avec le bouton Home.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenLauncherSettings,
            colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier
                .height(50.dp)
                .testTag("settings_set_launcher_btn"),
        ) {
            Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isDefaultLauncher) "Changer d'accueil Android" else "Définir comme Launcher Home",
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun DockAndHardwareSection(
    settings: HeadUnitSettings,
    mediaStateFlow: StateFlow<MediaPlayerState>,
    speedStateFlow: StateFlow<SpeedGpsState>,
    onOpenAppLibrary: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val mediaState by mediaStateFlow.collectAsStateWithLifecycle()
    val speedState by speedStateFlow.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.secondary

    SettingsSectionCard(title = "Dock, GPS & Bluetooth") {
        Text(
            text = "Dock d'accueil (${settings.dockPackageNames.size}/$MAX_DOCK_APPS applications)",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            color = TextPrimary,
        )
        Text(
            text = "Sélectionnez jusqu'à $MAX_DOCK_APPS applications installées pour le dock icônes de l'écran d'accueil.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onOpenAppLibrary,
            modifier = Modifier
                .height(48.dp)
                .testTag("settings_open_apps_btn"),
        ) {
            Text("Choisir les apps du dock", color = accent, fontSize = 14.sp)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permissions GPS & Bluetooth",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    color = TextPrimary,
                )
                Text(
                    text = "GPS : ${if (speedState.hasPermission) "Autorisé" else "Requis"} • BT : ${if (mediaState.hasBluetoothPermission) "Autorisé" else "Requis"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = if (speedState.hasPermission && mediaState.hasBluetoothPermission) EmeraldOk else AmberWarn,
                )
            }
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("settings_permissions_btn"),
            ) {
                Text("Vérifier", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "AutoPulse Launcher • Commandes au volant SWC actives • Allégé 4 Go RAM • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = accent,
        )
    }
}

@Composable
private fun AutoLaunchAppPickerDialog(
    installedApps: List<InstalledAppInfo>,
    selectedPackageName: String,
    onSelectPackage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Application au démarrage (ACC)",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedPackageName.isBlank()) container else CarbonSurface)
                        .border(
                            1.dp,
                            if (selectedPackageName.isBlank()) primary else CarbonBorder,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { onSelectPackage("") }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("acc_picker_none"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Aucune (désactiver)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = TextPrimary,
                    )
                    if (selectedPackageName.isBlank()) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = primary)
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(installedApps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName == selectedPackageName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) container else CarbonSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) primary else CarbonBorder,
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable { onSelectPackage(app.packageName) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer", fontSize = 15.sp) }
        },
    )
}

@Composable
private fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.secondary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CarbonSurfaceElevated)
            .border(1.dp, CarbonBorder, RoundedCornerShape(22.dp))
            .padding(20.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = accent,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}
