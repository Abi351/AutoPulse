package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.HeadUnitSettings
import com.example.data.SPEED_LIMIT_AUTO
import com.example.data.SPEED_LIMIT_OFF
import com.example.data.SpeedAlertType
import com.example.hardware.AutoSpeedLimitState
import com.example.hardware.SpeedGpsState
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private val SPEED_TIER_OPTIONS = listOf(
    SPEED_LIMIT_AUTO,
    SPEED_LIMIT_OFF,
    30,
    50,
    70,
    80,
    90,
    110,
    130,
)

private val AUTO_DETECTION_DURATIONS_SEC = listOf(5, 8, 12)
private val ALERT_TOLERANCE_OPTIONS_KMH = listOf(0, 3, 5, 10)
private val CALIBRATION_OFFSETS_KMH = listOf(0, 1, 2, 3, 4, 5)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedSettingsModal(
    settings: HeadUnitSettings,
    speedState: SpeedGpsState,
    autoSpeedLimitState: AutoSpeedLimitState,
    onSelectSpeedLimit: (Int) -> Unit,
    onSelectAutoDetectionSeconds: (Int) -> Unit,
    onSelectAlertToleranceKmh: (Int) -> Unit,
    onSelectSpeedAlertType: (String) -> Unit,
    onSelectCalibrationOffsetKmh: (Int) -> Unit,
    onSetBreakReminderEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    val currentAlertType = SpeedAlertType.fromId(settings.speedAlertType)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = CarbonSurfaceElevated,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(0.86f)
                .heightIn(max = 580.dp)
                .border(1.dp, CarbonBorder, RoundedCornerShape(26.dp))
                .testTag("speed_settings_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(container),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Compteur & Alertes Vitesse",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val liveSummary = buildString {
                                append("Affiché : ${speedState.speedKmh} km/h")
                                if (settings.speedCalibrationOffsetKmh > 0) {
                                    append(" (GPS brut : ${speedState.rawSpeedKmh} +${settings.speedCalibrationOffsetKmh})")
                                }
                                if (settings.speedLimitKmh == SPEED_LIMIT_AUTO) {
                                    val det = autoSpeedLimitState.detectedLimitKmh
                                    append(if (det != null) " • Auto : $det km/h" else " • Auto en attente")
                                }
                            }
                            Text(
                                text = liveSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CarbonSurface)
                            .border(1.dp, CarbonBorder, CircleShape)
                            .testTag("speed_settings_close_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fermer Compteur & Alertes Vitesse",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 1. Paliers de vitesse & Mode Auto
                    SpeedSettingSection(
                        title = "Limitation active & Mode Auto",
                        subtitle = "Choisissez Auto pour la détection par vitesse stabilisée ou un palier manuel.",
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SPEED_TIER_OPTIONS.forEach { limit ->
                                val selected = settings.speedLimitKmh == limit
                                val label = when (limit) {
                                    SPEED_LIMIT_AUTO -> "Auto"
                                    SPEED_LIMIT_OFF -> "Off"
                                    else -> "$limit km/h"
                                }
                                val tag = if (limit == SPEED_LIMIT_AUTO) "auto" else "$limit"
                                SpeedOptionChip(
                                    label = label,
                                    selected = selected,
                                    onClick = { onSelectSpeedLimit(limit) },
                                    testTag = "speed_modal_limit_$tag",
                                )
                            }
                        }
                    }

                    // 2. Durée de détection du mode Auto
                    SpeedSettingSection(
                        title = "Durée de détection du mode Auto",
                        subtitle = "Temps de vitesse stable (±2 km/h) avant le verrouillage du palier.",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AUTO_DETECTION_DURATIONS_SEC.forEach { sec ->
                                SpeedOptionChip(
                                    label = "${sec}s",
                                    selected = settings.autoSpeedDetectionSeconds == sec,
                                    onClick = { onSelectAutoDetectionSeconds(sec) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "speed_modal_duration_${sec}s",
                                )
                            }
                        }
                    }

                    // 3. Tolérance d'alerte
                    SpeedSettingSection(
                        title = "Tolérance avant alerte",
                        subtitle = "L'alerte se déclenche à partir de (limite + tolérance).",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ALERT_TOLERANCE_OPTIONS_KMH.forEach { tol ->
                                SpeedOptionChip(
                                    label = "+$tol km/h",
                                    selected = settings.speedAlertToleranceKmh == tol,
                                    onClick = { onSelectAlertToleranceKmh(tol) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "speed_modal_tolerance_$tol",
                                )
                            }
                        }
                    }

                    // 4. Type d'alerte
                    SpeedSettingSection(
                        title = "Type d'alerte de dépassement",
                        subtitle = "Comportement déclenché dès que la vitesse dépasse le seuil.",
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SpeedAlertType.entries.forEach { type ->
                                SpeedOptionChip(
                                    label = type.label,
                                    selected = currentAlertType == type,
                                    onClick = { onSelectSpeedAlertType(type.id) },
                                    testTag = "speed_modal_alert_${type.id.lowercase()}",
                                )
                            }
                        }
                    }

                    // 5. Étalonnage GPS (+0 à +5 km/h)
                    SpeedSettingSection(
                        title = "Étalonnage compteur (+0 à +5 km/h)",
                        subtitle = "Décalage appliqué uniquement en roulant (> 2 km/h) pour s'aligner au compteur voiture.",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CALIBRATION_OFFSETS_KMH.forEach { offset ->
                                SpeedOptionChip(
                                    label = "+$offset",
                                    selected = settings.speedCalibrationOffsetKmh == offset,
                                    onClick = { onSelectCalibrationOffsetKmh(offset) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "speed_modal_calib_$offset",
                                )
                            }
                        }
                    }

                    // 6. Rappel de pause conduite
                    SpeedSettingSection(
                        title = "Rappel de pause conduite",
                        subtitle = "Affiche un rappel discret après 2 heures de conduite continue.",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SpeedOptionChip(
                                label = "Off",
                                selected = !settings.breakReminderEnabled,
                                onClick = { onSetBreakReminderEnabled(false) },
                                modifier = Modifier.weight(1f),
                                testTag = "speed_modal_break_off",
                            )
                            SpeedOptionChip(
                                label = "Toutes les 2h",
                                selected = settings.breakReminderEnabled,
                                onClick = { onSetBreakReminderEnabled(true) },
                                modifier = Modifier.weight(1f),
                                testTag = "speed_modal_break_2h",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedSettingSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CarbonSurface)
            .border(1.dp, CarbonBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

@Composable
private fun SpeedOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) container else CarbonSurfaceElevated)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) primary else CarbonBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) accent else TextPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
