package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferencesRepository
import com.example.data.TripStats
import com.example.hardware.SpeedGpsState
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun TripScreen(
    tripStats: TripStats,
    speedState: SpeedGpsState,
    onResetTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactHeight = maxHeight < 440.dp
        val isMediumHeight = maxHeight in 440.dp..560.dp
        val outerPad = if (isCompactHeight) 12.dp else 18.dp
        val gridGap = if (isCompactHeight) 10.dp else 14.dp
        val valueFontSize = when {
            isCompactHeight -> 34.sp
            isMediumHeight -> 42.sp
            else -> 50.sp
        }
        val cardPad = if (isCompactHeight) 14.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPad),
            verticalArrangement = Arrangement.spacedBy(gridGap),
        ) {
            // En-tête Ordinateur de bord
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(primary),
                    )
                    Text(
                        text = "ORDINATEUR DE BORD • TRAJET",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val (gpsIcon, gpsLabel, gpsColor) = when {
                    !speedState.hasPermission -> Triple(Icons.Rounded.GpsOff, "GPS non autorisé", AmberWarn)
                    !speedState.isGpsEnabled -> Triple(Icons.Rounded.GpsOff, "GPS désactivé", AmberWarn)
                    !speedState.hasSignalFix -> Triple(Icons.Rounded.GpsNotFixed, "Recherche GPS...", TextSecondary)
                    else -> Triple(Icons.Rounded.GpsFixed, "${speedState.speedKmh} km/h en direct", EmeraldOk)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CarbonSurface)
                        .border(1.dp, gpsColor.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(gpsIcon, contentDescription = null, tint = gpsColor, modifier = Modifier.size(15.dp))
                    Text(
                        text = gpsLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = gpsColor,
                        maxLines = 1,
                    )
                }
            }

            // Grille 2x2 des statistiques de trajet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(gridGap),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gridGap),
                ) {
                    TripMetricCard(
                        title = "DISTANCE PARCOURUE",
                        value = String.format(Locale.US, "%.1f km", tripStats.distanceKm),
                        subtitle = "Filtrage micro-variations actif",
                        icon = Icons.Rounded.Route,
                        valueFontSize = valueFontSize,
                        cardPad = cardPad,
                        highlight = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .testTag("trip_distance_card"),
                    )
                    TripMetricCard(
                        title = "TEMPS DE CONDUITE",
                        value = PreferencesRepository.formatDrivingDuration(tripStats.drivingSeconds),
                        subtitle = "Sauvegardé automatiquement",
                        icon = Icons.Rounded.Timer,
                        valueFontSize = valueFontSize,
                        cardPad = cardPad,
                        highlight = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .testTag("trip_duration_card"),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gridGap),
                ) {
                    TripMetricCard(
                        title = "VITESSE MOYENNE",
                        value = "${tripStats.averageSpeedKmh} km/h",
                        subtitle = "Sur le trajet en cours",
                        icon = Icons.Rounded.Speed,
                        valueFontSize = valueFontSize,
                        cardPad = cardPad,
                        highlight = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .testTag("trip_avg_speed_card"),
                    )
                    TripMetricCard(
                        title = "VITESSE MAXIMALE",
                        value = "${tripStats.maxSpeedKmh} km/h",
                        subtitle = "Pic enregistré (GPS)",
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                        valueFontSize = valueFontSize,
                        cardPad = cardPad,
                        highlight = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .testTag("trip_max_speed_card"),
                    )
                }
            }

            // Grand bouton Reset / Nouveau trajet
            Button(
                onClick = onResetTrip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompactHeight) 50.dp else 58.dp)
                    .testTag("trip_reset_btn"),
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Reset / Nouveau trajet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TripMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    valueFontSize: TextUnit,
    cardPad: androidx.compose.ui.unit.Dp,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(CarbonSurfaceElevated, CarbonSurface)),
            )
            .border(
                width = 1.dp,
                color = if (highlight) primary.copy(alpha = 0.45f) else CarbonBorder,
                shape = RoundedCornerShape(24.dp),
            )
            .padding(cardPad),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) primary else TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = valueFontSize,
                lineHeight = valueFontSize * 1.05f,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
            ),
            color = if (highlight) primary else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
