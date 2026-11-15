package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.HeadUnitSettings
import com.example.data.MAX_DOCK_APPS
import com.example.data.PreferencesRepository
import com.example.data.SPEED_LIMIT_AUTO
import com.example.data.SpeedAlertType
import com.example.hardware.AudioEngine
import com.example.hardware.AudioSourceMode
import com.example.hardware.AutoSpeedLimitState
import com.example.hardware.InstalledAppInfo
import com.example.hardware.MediaPlayerState
import com.example.hardware.SpeedGpsState
import com.example.ui.components.LightweightEqualizerBars
import com.example.ui.components.SpeedSettingsModal
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val SHORT_FRENCH_DAYS = arrayOf("Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam")
private val SHORT_FRENCH_MONTHS = arrayOf("jan", "fév", "mar", "avr", "mai", "jun", "jul", "aoû", "sep", "oct", "nov", "déc")

fun formatShortFrenchDate(date: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    val dayName = SHORT_FRENCH_DAYS.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "Lun" }
    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    val monthName = SHORT_FRENCH_MONTHS.getOrElse(cal.get(Calendar.MONTH)) { "jan" }
    val year = cal.get(Calendar.YEAR)
    return "$dayName $dayOfMonth $monthName $year"
}

@Composable
fun CockpitHomeScreen(
    mediaState: MediaPlayerState,
    speedState: SpeedGpsState,
    settings: HeadUnitSettings,
    autoSpeedLimitState: AutoSpeedLimitState = AutoSpeedLimitState(),
    showBreakReminder: Boolean = false,
    dockApps: List<InstalledAppInfo>,
    getCachedIcon: (String) -> ImageBitmap?,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    onSelectSpeedLimit: (Int) -> Unit,
    onSelectAutoDetectionSeconds: (Int) -> Unit,
    onSelectAlertToleranceKmh: (Int) -> Unit,
    onSelectSpeedAlertType: (String) -> Unit,
    onSelectCalibrationOffsetKmh: (Int) -> Unit,
    onSetBreakReminderEnabled: (Boolean) -> Unit,
    onDismissBreakReminder: () -> Unit,
    onTriggerSpeedAlertDucking: () -> Unit,
    onOpenMedia: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onSwitchAudioSource: (AudioSourceMode) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenAppLibrary: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onRemoveDockApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var clockStr by remember { mutableStateOf("") }
    var secondsStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }
    var editDockMode by remember { mutableStateOf(false) }
    var showSpeedSettingsModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
        val secFmt = SimpleDateFormat("ss", Locale.FRANCE)
        while (true) {
            val now = Date()
            clockStr = timeFmt.format(now)
            secondsStr = secFmt.format(now)
            dateStr = formatShortFrenchDate(now)
            delay(1_000L)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactHeight = maxHeight < 430.dp
        val isMediumHeight = maxHeight in 430.dp..560.dp
        val isWide = maxWidth >= 820.dp

        val outerHPad = if (isWide) 18.dp else 12.dp
        val outerVPad = if (isCompactHeight) 8.dp else 12.dp
        val sectionGap = if (isCompactHeight) 8.dp else 12.dp

        val clockFontSize = when {
            isCompactHeight -> 46.sp
            isMediumHeight -> 58.sp
            else -> 72.sp
        }
        val speedFontSize = when {
            isCompactHeight -> 48.sp
            isMediumHeight -> 62.sp
            else -> 76.sp
        }
        val cardPad = if (isCompactHeight) 14.dp else if (isMediumHeight) 18.dp else 22.dp
        val artSize = if (isCompactHeight) 62.dp else if (isMediumHeight) 74.dp else 88.dp
        val dockHeight = if (isCompactHeight) 74.dp else 88.dp
        val dockTileSize = if (isCompactHeight) 58.dp else 68.dp
        val dockIconSize = if (isCompactHeight) 42.dp else 50.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = outerHPad, vertical = outerVPad),
            verticalArrangement = Arrangement.spacedBy(sectionGap),
        ) {
            AnimatedVisibility(visible = showBreakReminder) {
                BreakReminderBanner(onDismiss = onDismissBreakReminder)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (isWide) 16.dp else 12.dp),
            ) {
                ClockAndSpeedDashboardCard(
                    clockStr = clockStr,
                    secondsStr = secondsStr,
                    dateStr = dateStr,
                    speedState = speedState,
                    settings = settings,
                    autoSpeedLimitState = autoSpeedLimitState,
                    clockFontSize = clockFontSize,
                    speedFontSize = speedFontSize,
                    cardPad = cardPad,
                    isCompactHeight = isCompactHeight,
                    onOpenSpeedSettings = { showSpeedSettingsModal = true },
                    onTriggerSpeedAlertDucking = onTriggerSpeedAlertDucking,
                    onRequestLocationPermission = onRequestLocationPermission,
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight(),
                )

                MediaDashboardCard(
                    mediaState = mediaState,
                    cardPad = cardPad,
                    artSize = artSize,
                    isCompactHeight = isCompactHeight,
                    onOpenMedia = onOpenMedia,
                    onTogglePlayPause = onTogglePlayPause,
                    onNextTrack = onNextTrack,
                    onPrevTrack = onPrevTrack,
                    onSwitchAudioSource = onSwitchAudioSource,
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                )
            }

            HeadUnitAppDock(
                dockApps = dockApps,
                editMode = editDockMode,
                dockHeight = dockHeight,
                tileSize = dockTileSize,
                iconSize = dockIconSize,
                getCachedIcon = getCachedIcon,
                loadAppIcon = loadAppIcon,
                onToggleEditMode = { editDockMode = !editDockMode },
                onLaunchApp = onLaunchApp,
                onRemoveDockApp = onRemoveDockApp,
                onOpenAppLibrary = onOpenAppLibrary,
            )
        }
    }

    if (showSpeedSettingsModal) {
        SpeedSettingsModal(
            settings = settings,
            speedState = speedState,
            autoSpeedLimitState = autoSpeedLimitState,
            onSelectSpeedLimit = onSelectSpeedLimit,
            onSelectAutoDetectionSeconds = onSelectAutoDetectionSeconds,
            onSelectAlertToleranceKmh = onSelectAlertToleranceKmh,
            onSelectSpeedAlertType = onSelectSpeedAlertType,
            onSelectCalibrationOffsetKmh = onSelectCalibrationOffsetKmh,
            onSetBreakReminderEnabled = onSetBreakReminderEnabled,
            onDismiss = { showSpeedSettingsModal = false },
        )
    }
}

@Composable
private fun BreakReminderBanner(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AmberWarn.copy(alpha = 0.16f))
            .border(1.dp, AmberWarn.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("cockpit_break_reminder_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Coffee,
            contentDescription = "Pause conduite",
            tint = AmberWarn,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "2h de conduite continue — Pensez à faire une pause",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("cockpit_break_reminder_dismiss_btn"),
        ) {
            Text("Compris", color = AmberWarn, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ClockAndSpeedDashboardCard(
    clockStr: String,
    secondsStr: String,
    dateStr: String,
    speedState: SpeedGpsState,
    settings: HeadUnitSettings,
    autoSpeedLimitState: AutoSpeedLimitState,
    clockFontSize: TextUnit,
    speedFontSize: TextUnit,
    cardPad: Dp,
    isCompactHeight: Boolean,
    onOpenSpeedSettings: () -> Unit,
    onTriggerSpeedAlertDucking: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    val isAutoMode = settings.speedLimitKmh == SPEED_LIMIT_AUTO
    val activeLimitKmh = if (isAutoMode) {
        autoSpeedLimitState.detectedLimitKmh ?: 0
    } else {
        settings.speedLimitKmh
    }
    val alertType = SpeedAlertType.fromId(settings.speedAlertType)
    val isOverSpeedLimit = PreferencesRepository.isSpeedOverLimit(
        speedKmh = speedState.speedKmh,
        activeLimitKmh = activeLimitKmh,
        toleranceKmh = settings.speedAlertToleranceKmh,
    )
    var blinkPhase by remember { mutableStateOf(false) }

    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { toneGenerator?.release() } }
    }

    LaunchedEffect(isOverSpeedLimit, alertType) {
        if (!isOverSpeedLimit) {
            blinkPhase = false
            return@LaunchedEffect
        }
        var lastAudioAlertAtMs = 0L
        while (true) {
            val now = System.currentTimeMillis()
            if (now - lastAudioAlertAtMs >= 8_000L) {
                when (alertType) {
                    SpeedAlertType.BEEP -> {
                        runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 130) }
                    }
                    SpeedAlertType.DUCKING -> {
                        onTriggerSpeedAlertDucking()
                    }
                    SpeedAlertType.VISUAL -> Unit
                }
                lastAudioAlertAtMs = now
            }
            blinkPhase = !blinkPhase
            delay(420L)
        }
    }

    val speedDisplayColor = if (isOverSpeedLimit) {
        if (blinkPhase) CrimsonAlert else AmberWarn
    } else {
        primary
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(listOf(CarbonSurfaceElevated, CarbonSurface))
            )
            .border(
                width = if (isOverSpeedLimit) 1.5.dp else 1.dp,
                color = if (isOverSpeedLimit) speedDisplayColor else CarbonBorder,
                shape = RoundedCornerShape(26.dp),
            )
            .padding(cardPad),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(primary),
                )
                Text(
                    text = if (isOverSpeedLimit) {
                        "ALERTE LIMITE $activeLimitKmh (+${settings.speedAlertToleranceKmh})"
                    } else {
                        "TABLEAU DE BORD"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isOverSpeedLimit) speedDisplayColor else accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            GpsStatusIndicator(
                speedState = speedState,
                onRequestLocationPermission = onRequestLocationPermission,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Bloc Horloge
            Column(
                modifier = Modifier.weight(1.02f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = clockStr,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = clockFontSize,
                            lineHeight = clockFontSize * 1.04f,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = secondsStr,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                        ),
                        color = primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(if (isCompactHeight) 82.dp else 108.dp)
                    .background(CarbonBorder),
            )

            // Zone Compteur GPS (cliquable pour ouvrir le menu dédié "Compteur & Alertes Vitesse")
            Column(
                modifier = Modifier
                    .weight(0.98f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenSpeedSettings)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("cockpit_speedometer"),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${speedState.speedKmh}",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = speedFontSize,
                            lineHeight = speedFontSize * 1.02f,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = speedDisplayColor,
                        maxLines = 1,
                    )
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = if (isOverSpeedLimit) speedDisplayColor else accent,
                        modifier = Modifier.padding(bottom = 8.dp),
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(6.dp))

                val badgeText = when {
                    isAutoMode && activeLimitKmh > 0 -> "Auto • $activeLimitKmh km/h"
                    isAutoMode -> "Auto • attente"
                    activeLimitKmh > 0 -> "Limite • $activeLimitKmh km/h"
                    else -> "Limite • Off"
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                isOverSpeedLimit -> CrimsonAlert.copy(alpha = 0.22f)
                                activeLimitKmh > 0 -> container.copy(alpha = 0.72f)
                                else -> CarbonSurface
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                isOverSpeedLimit -> speedDisplayColor
                                activeLimitKmh > 0 -> primary.copy(alpha = 0.65f)
                                else -> CarbonBorder
                            },
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("cockpit_active_limit_badge"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = when {
                            isOverSpeedLimit -> speedDisplayColor
                            activeLimitKmh > 0 -> accent
                            else -> TextSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Réglages compteur",
                        tint = if (isOverSpeedLimit) speedDisplayColor else TextSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                }

                // Phase 2 : petite barre de progression fine + mini chrono uniquement pendant détection active
                if (isAutoMode && autoSpeedLimitState.isCountdownVisible) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_speed_limit_progress_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { autoSpeedLimitState.progressFraction },
                            color = primary,
                            trackColor = CarbonBorder,
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50)),
                        )
                        Text(
                            text = "${autoSpeedLimitState.countdownSeconds}s/${autoSpeedLimitState.countdownTotalSeconds}s",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            ),
                            color = accent,
                            maxLines = 1,
                            modifier = Modifier.testTag("auto_speed_limit_chrono"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GpsStatusIndicator(
    speedState: SpeedGpsState,
    onRequestLocationPermission: () -> Unit,
) {
    val (icon, text, color) = when {
        !speedState.hasPermission -> Triple(Icons.Rounded.GpsOff, "GPS non autorisé", AmberWarn)
        !speedState.isGpsEnabled -> Triple(Icons.Rounded.GpsOff, "GPS désactivé", AmberWarn)
        !speedState.hasSignalFix -> Triple(Icons.Rounded.GpsNotFixed, "Signal GPS...", TextSecondary)
        else -> Triple(Icons.Rounded.GpsFixed, "GPS actif", EmeraldOk)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CarbonSurface)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .then(
                if (!speedState.hasPermission) Modifier.clickable(onClick = onRequestLocationPermission)
                else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("cockpit_gps_status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(13.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaDashboardCard(
    mediaState: MediaPlayerState,
    cardPad: Dp,
    artSize: Dp,
    isCompactHeight: Boolean,
    onOpenMedia: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onSwitchAudioSource: (AudioSourceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val isExternal = mediaState.sourceMode == AudioSourceMode.EXTERNAL
    val ext = mediaState.externalMedia

    val playing = if (isExternal) {
        if (ext.hasActiveSession) ext.isPlaying else mediaState.btAudioActive
    } else {
        mediaState.isPlaying
    }

    val playBtnScale by animateFloatAsState(
        targetValue = if (playing) 1.06f else 1.0f,
        animationSpec = spring(stiffness = 300f),
        label = "play_scale",
    )

    val titleText = if (isExternal) {
        when {
            ext.hasActiveSession -> ext.title
            mediaState.connectedBtDevice != null -> mediaState.connectedBtDevice.name
            else -> "Audio Externe / BT"
        }
    } else {
        mediaState.currentTrack?.title ?: "Aucune musique en cours"
    }

    val subtitleText = if (isExternal) {
        when {
            ext.hasActiveSession -> "${ext.artist} • ${ext.appName}"
            mediaState.connectedBtDevice != null -> "Bluetooth A2DP • AVRCP"
            !mediaState.hasNotificationListenerAccess -> "Activez l'accès média dans Réglages"
            else -> "Lancez Spotify, YouTube Music ou connectez un BT"
        }
    } else {
        val track = mediaState.currentTrack
        if (track != null) {
            if (track.year.isNotBlank()) "${track.artist} • ${track.year}" else track.artist
        } else {
            "Touchez ici pour ouvrir votre bibliothèque"
        }
    }

    val activeArt = if (isExternal) ext.albumArt else mediaState.currentAlbumArt
    val activeArtBitmap = remember(
        mediaState.currentTrack?.id,
        mediaState.currentTrack?.coverPath,
        activeArt,
        isExternal,
        ext.title,
    ) {
        activeArt?.asImageBitmap()
    }
    val displayPos = if (isExternal) ext.positionMs else mediaState.positionMs
    val displayDur = if (isExternal) ext.durationMs else mediaState.durationMs

    val controlBtnSize = if (isCompactHeight) 46.dp else 54.dp
    val playControlSize = if (isCompactHeight) 54.dp else 64.dp
    val swipeThresholdPx = with(LocalDensity.current) { 60.dp.toPx() }
    var artDragX by remember(mediaState.currentTrack?.id, ext.title) { mutableFloatStateOf(0f) }
    val animatedArtOffset by animateFloatAsState(
        targetValue = artDragX * 0.45f,
        animationSpec = spring(stiffness = 450f),
        label = "cockpit_art_drag_offset",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(CarbonSurfaceElevated)
            .border(
                1.dp,
                if (playing) primary.copy(alpha = 0.58f) else CarbonBorder,
                RoundedCornerShape(26.dp),
            )
            .clickable(onClick = onOpenMedia)
            .padding(cardPad)
            .testTag("cockpit_media_card"),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourcePill(
                icon = Icons.Rounded.FolderOpen,
                label = "Stockage Local",
                selected = !isExternal,
                onClick = { onSwitchAudioSource(AudioSourceMode.LOCAL) },
                modifier = Modifier.weight(1f),
                testTag = "cockpit_source_local",
            )
            SourcePill(
                icon = Icons.Rounded.Bluetooth,
                label = if (ext.hasActiveSession) ext.appName.take(14) else "Externe / BT",
                selected = isExternal,
                onClick = { onSwitchAudioSource(AudioSourceMode.EXTERNAL) },
                modifier = Modifier.weight(1f),
                testTag = "cockpit_source_bt",
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(artSize)
                    .clip(RoundedCornerShape(18.dp))
                    .pointerInput(mediaState.currentTrack?.id, isExternal) {
                        detectHorizontalDragGestures(
                            onDragStart = { artDragX = 0f },
                            onDragCancel = { artDragX = 0f },
                            onDragEnd = {
                                if (artDragX > swipeThresholdPx) {
                                    onPrevTrack()
                                } else if (artDragX < -swipeThresholdPx) {
                                    onNextTrack()
                                }
                                artDragX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                artDragX = (artDragX + dragAmount).coerceIn(-swipeThresholdPx * 1.6f, swipeThresholdPx * 1.6f)
                            },
                        )
                    }
                    .testTag("cockpit_album_art"),
                contentAlignment = Alignment.Center,
            ) {
                val artModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = animatedArtOffset }
                    .clip(RoundedCornerShape(18.dp))
                if (activeArtBitmap != null) {
                    Image(
                        bitmap = activeArtBitmap,
                        contentDescription = "Pochette d'album",
                        contentScale = ContentScale.Crop,
                        modifier = artModifier,
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_album_red),
                        contentDescription = "Pochette audio",
                        contentScale = ContentScale.Crop,
                        modifier = artModifier,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    LightweightEqualizerBars(isPlaying = playing)
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (displayDur > 0L) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val progress = (displayPos.toFloat() / displayDur.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    color = primary,
                    trackColor = CarbonBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = AudioEngine.formatDuration(displayPos),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = TextSecondary,
                    )
                    Text(
                        text = AudioEngine.formatDuration(displayDur),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = TextSecondary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevTrack,
                modifier = Modifier
                    .size(controlBtnSize)
                    .clip(CircleShape)
                    .background(CarbonSurface)
                    .testTag("cockpit_media_prev"),
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Précédent", tint = TextPrimary, modifier = Modifier.size(28.dp))
            }

            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(playControlSize)
                    .graphicsLayer {
                        scaleX = playBtnScale
                        scaleY = playBtnScale
                    }
                    .clip(CircleShape)
                    .background(primary)
                    .testTag("cockpit_media_play_pause"),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Lecture / Pause",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }

            IconButton(
                onClick = onNextTrack,
                modifier = Modifier
                    .size(controlBtnSize)
                    .clip(CircleShape)
                    .background(CarbonSurface)
                    .testTag("cockpit_media_next"),
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant", tint = TextPrimary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun HeadUnitAppDock(
    dockApps: List<InstalledAppInfo>,
    editMode: Boolean,
    dockHeight: Dp,
    tileSize: Dp,
    iconSize: Dp,
    getCachedIcon: (String) -> ImageBitmap?,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    onToggleEditMode: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onRemoveDockApp: (String) -> Unit,
    onOpenAppLibrary: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    val emptySlotsCount = if (dockApps.size < MAX_DOCK_APPS) {
        maxOf(6 - dockApps.size, 1)
    } else {
        0
    }

    Surface(
        color = CarbonSurfaceElevated.copy(alpha = 0.96f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(dockHeight)
            .border(1.dp, CarbonBorder, RoundedCornerShape(22.dp))
            .testTag("cockpit_app_dock"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(tileSize)
                    .clip(RoundedCornerShape(18.dp))
                    .background(container)
                    .border(1.dp, primary.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenAppLibrary)
                    .testTag("dock_open_library_btn"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = "Bibliothèque d'applications",
                    tint = accent,
                    modifier = Modifier.size(32.dp),
                )
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(
                    items = dockApps,
                    key = { _, app -> app.packageName },
                    contentType = { _, _ -> "dock_app_icon" },
                ) { slotIndex, app ->
                    val iconBmp by produceState<ImageBitmap?>(
                        initialValue = getCachedIcon(app.packageName),
                        key1 = app.packageName,
                    ) {
                        if (value == null) {
                            value = loadAppIcon(app.packageName)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(tileSize)
                            .clip(RoundedCornerShape(18.dp))
                            .background(CarbonSurface)
                            .border(
                                1.5.dp,
                                if (editMode) AmberWarn.copy(alpha = 0.75f) else CarbonBorder,
                                RoundedCornerShape(18.dp),
                            )
                            .clickable {
                                if (editMode) onRemoveDockApp(app.packageName) else onLaunchApp(app.packageName)
                            }
                            .testTag("dock_slot_$slotIndex"),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = iconBmp
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(iconSize)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = app.label,
                                tint = accent,
                                modifier = Modifier.size(34.dp),
                            )
                        }

                        if (editMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonAlert),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Retirer du dock",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }

                items(
                    count = emptySlotsCount,
                    key = { idx -> "empty_slot_$idx" },
                    contentType = { "dock_empty_slot" },
                ) { emptyIndex ->
                    val slotIndex = dockApps.size + emptyIndex
                    Box(
                        modifier = Modifier
                            .size(tileSize)
                            .clip(RoundedCornerShape(18.dp))
                            .background(CarbonSurface.copy(alpha = 0.45f))
                            .border(1.dp, CarbonBorder.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                            .clickable(onClick = onOpenAppLibrary)
                            .testTag("dock_empty_slot_$slotIndex"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Ajouter une application",
                            tint = TextMuted,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .testTag("dock_autopulse_brand"),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primary),
                )
                Text(
                    text = "AUTOPULSE",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            IconButton(
                onClick = onToggleEditMode,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (editMode) AmberWarn.copy(alpha = 0.2f) else CarbonSurface)
                    .testTag("dock_edit_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Modifier le dock",
                    tint = if (editMode) AmberWarn else TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun SourcePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) container else CarbonSurface)
            .border(1.dp, if (selected) primary else CarbonBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent else TextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
