package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RailClockBadge(
    modifier: Modifier = Modifier,
) {
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
        while (true) {
            timeText = fmt.format(Date())
            delay(10_000L)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonSurfaceElevated)
            .border(1.dp, primary.copy(alpha = 0.48f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("rail_clock_badge"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
fun TopStatusBar(
    btDeviceName: String?,
    externalAppName: String?,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onOpenBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        color = CarbonSurface.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(primary),
                )
                Text(
                    text = "AUTOPULSE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!externalAppName.isNullOrBlank()) {
                    StatusChip(
                        icon = Icons.Rounded.MusicNote,
                        label = externalAppName.take(14),
                        accent = primary,
                        testTag = "status_chip_external_media",
                        onClick = onOpenBluetooth,
                    )
                }

                StatusChip(
                    icon = if (btDeviceName != null) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
                    label = btDeviceName?.take(14) ?: "Bluetooth",
                    accent = if (btDeviceName != null) primary else TextMuted,
                    testTag = "status_chip_bt",
                    onClick = onOpenBluetooth,
                )

                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_mute_button"),
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (isMuted) "Activer le son" else "Couper le son",
                        tint = if (isMuted) AmberWarn else TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CarbonSurfaceElevated)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Barres d'égaliseur GPU-friendly (scaleY uniquement via graphicsLayer, actives uniquement en lecture)
 */
@Composable
fun LightweightEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary

    if (!isPlaying) {
        Row(
            modifier = modifier.height(20.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted.copy(alpha = 0.5f)),
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "eq_bars")
    val b1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "b1",
    )
    val b2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(340), RepeatMode.Reverse),
        label = "b2",
    )
    val b3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "b3",
    )
    val b4 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "b4",
    )

    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(b1, b2, b3, b4).forEach { scale ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .graphicsLayer {
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(primary),
            )
        }
    }
}
