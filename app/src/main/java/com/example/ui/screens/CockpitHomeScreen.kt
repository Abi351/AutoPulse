package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Radio
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.hardware.UniversalMediaManager
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
import kotlinx.coroutines.flow.StateFlow
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
    mediaStateFlow: StateFlow<MediaPlayerState>,
    speedStateFlow: StateFlow<SpeedGpsState>,
    settings: HeadUnitSettings,
    autoSpeedLimitStateFlow: StateFlow<AutoSpeedLimitState>,
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
    var editDockMode by remember { mutableStateOf(false) }
    var showSpeedSettingsModal by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactHeight = maxHeight < 450.dp
        val isMediumHeight = maxHeight in 450.dp..570.dp
        val isWide = maxWidth >= 820.dp

        val outerHPad = if (isWide) 18.dp else 14.dp
        val outerVPad = if (isCompactHeight) 8.dp else 12.dp
        val sectionGap = if (isCompactHeight) 8.dp else 12.dp

        val clockFontSize = when {
            isCompactHeight -> 56.sp
            isMediumHeight -> 58.sp
            else -> 62.sp
        }
        val speedFontSize = when {
            isCompactHeight -> 68.sp
            isMediumHeight -> 72.sp
            else -> 76.sp
        }
        val cardPad = if (isCompactHeight) 14.dp else if (isMediumHeight) 18.dp else 22.dp
        val dockHeight = if (isCompactHeight) 76.dp else 80.dp
        val dockTileSize = if (isCompactHeight) 62.dp else 66.dp
        val dockIconSize = if (isCompactHeight) 52.dp else 56.dp

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
                    speedStateFlow = speedStateFlow,
                    settings = settings,
                    autoSpeedLimitStateFlow = autoSpeedLimitStateFlow,
                    clockFontSize = clockFontSize,
                    speedFontSize = speedFontSize,
                    cardPad = cardPad,
                    isCompactHeight = isCompactHeight,
                    onOpenSpeedSettings = { showSpeedSettingsModal = true },
                    onTriggerSpeedAlertDucking = onTriggerSpeedAlertDucking,
                    onRequestLocationPermission = onRequestLocationPermission,
                    modifier = Modifier
                        .weight(0.96f)
                        .fillMaxHeight(),
                )

                MediaDashboardCard(
                    mediaStateFlow = mediaStateFlow,
                    cardPad = cardPad,
                    isCompactHeight = isCompactHeight,
                    onOpenMedia = onOpenMedia,
                    onTogglePlayPause = onTogglePlayPause,
                    onNextTrack = onNextTrack,
                    onPrevTrack = onPrevTrack,
                    onSwitchAudioSource = onSwitchAudioSource,
                    modifier = Modifier
                        .weight(1.04f)
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
        val speedState by speedStateFlow.collectAsStateWithLifecycle()
        val autoSpeedLimitState by autoSpeedLimitStateFlow.collectAsStateWithLifecycle()
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("cockpit_break_reminder_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Coffee,
            contentDescription = "Pause conduite",
            tint = AmberWarn,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = "2h de conduite continue — Pensez à faire une pause",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("cockpit_break_reminder_dismiss_btn"),
        ) {
            Text("Compris", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/**
 * Composant horloge isolé : son tic chaque seconde ne recomposera jamais la carte média, le dock ou le compteur GPS.
 */
@Composable
private fun IsolatedClockDisplay(
    clockFontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    var clockStr by remember { mutableStateOf("") }
    var secondsStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }

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

    val primary = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val maxSafeClockSp = ((maxWidth.value - 42f).coerceAtLeast(62f) / 3.25f)
        val effectiveClockSize = minOf(clockFontSize.value, maxSafeClockSp)
            .coerceIn(24f, 62f)
            .sp

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = clockStr,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = effectiveClockSize,
                        lineHeight = effectiveClockSize * 1.04f,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = secondsStr,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 26.sp,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = primary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .widthIn(min = 34.dp)
                        .padding(bottom = 7.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = dateStr,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ClockAndSpeedDashboardCard(
    speedStateFlow: StateFlow<SpeedGpsState>,
    settings: HeadUnitSettings,
    autoSpeedLimitStateFlow: StateFlow<AutoSpeedLimitState>,
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

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(listOf(CarbonSurfaceElevated, CarbonSurface))
            )
            .border(
                width = 1.dp,
                color = CarbonBorder,
                shape = RoundedCornerShape(26.dp),
            )
            .padding(cardPad),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        IsolatedDashboardHeader(
            speedStateFlow = speedStateFlow,
            settings = settings,
            autoSpeedLimitStateFlow = autoSpeedLimitStateFlow,
            onRequestLocationPermission = onRequestLocationPermission,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IsolatedClockDisplay(
                clockFontSize = clockFontSize,
                modifier = Modifier.weight(1.06f),
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(if (isCompactHeight) 96.dp else 124.dp)
                    .background(CarbonBorder),
            )

            IsolatedSpeedometerZone(
                speedStateFlow = speedStateFlow,
                settings = settings,
                autoSpeedLimitStateFlow = autoSpeedLimitStateFlow,
                speedFontSize = speedFontSize,
                onOpenSpeedSettings = onOpenSpeedSettings,
                onTriggerSpeedAlertDucking = onTriggerSpeedAlertDucking,
                modifier = Modifier
                    .weight(0.94f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun IsolatedDashboardHeader(
    speedStateFlow: StateFlow<SpeedGpsState>,
    settings: HeadUnitSettings,
    autoSpeedLimitStateFlow: StateFlow<AutoSpeedLimitState>,
    onRequestLocationPermission: () -> Unit,
) {
    val speedState by speedStateFlow.collectAsStateWithLifecycle()
    val autoSpeedLimitState by autoSpeedLimitStateFlow.collectAsStateWithLifecycle()

    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    val isAutoMode = settings.speedLimitKmh == SPEED_LIMIT_AUTO
    val activeLimitKmh = if (isAutoMode) {
        autoSpeedLimitState.detectedLimitKmh ?: 0
    } else {
        settings.speedLimitKmh
    }
    val isOverSpeedLimit = PreferencesRepository.isSpeedOverLimit(
        speedKmh = speedState.speedKmh,
        activeLimitKmh = activeLimitKmh,
        toleranceKmh = settings.speedAlertToleranceKmh,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isOverSpeedLimit) CrimsonAlert else primary),
            )
            Text(
                text = if (isOverSpeedLimit) {
                    "ALERTE LIMITE $activeLimitKmh (+${settings.speedAlertToleranceKmh})"
                } else {
                    "TABLEAU DE BORD"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = if (isOverSpeedLimit) CrimsonAlert else accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        GpsStatusIndicator(
            speedState = speedState,
            onRequestLocationPermission = onRequestLocationPermission,
        )
    }
}

@Composable
private fun IsolatedSpeedometerZone(
    speedStateFlow: StateFlow<SpeedGpsState>,
    settings: HeadUnitSettings,
    autoSpeedLimitStateFlow: StateFlow<AutoSpeedLimitState>,
    speedFontSize: TextUnit,
    onOpenSpeedSettings: () -> Unit,
    onTriggerSpeedAlertDucking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedState by speedStateFlow.collectAsStateWithLifecycle()
    val autoSpeedLimitState by autoSpeedLimitStateFlow.collectAsStateWithLifecycle()

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
                    fontSize = 20.sp,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isOverSpeedLimit) speedDisplayColor else accent,
                modifier = Modifier.padding(bottom = 10.dp),
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(10.dp))

        val badgeText = when {
            isAutoMode && activeLimitKmh > 0 -> "Auto • $activeLimitKmh km/h"
            isAutoMode -> "Auto • attente"
            activeLimitKmh > 0 -> "Limite • $activeLimitKmh km/h"
            else -> "Limite • Off"
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
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
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("cockpit_active_limit_badge"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
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
                modifier = Modifier.size(16.dp),
            )
        }

        if (isAutoMode && autoSpeedLimitState.isCountdownVisible) {
            Spacer(Modifier.height(10.dp))
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
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                )
                Text(
                    text = "${autoSpeedLimitState.countdownSeconds}s/${autoSpeedLimitState.countdownTotalSeconds}s",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    ),
                    color = accent,
                    maxLines = 1,
                    modifier = Modifier.testTag("auto_speed_limit_chrono"),
                )
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
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("cockpit_gps_status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(15.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaDashboardCard(
    mediaStateFlow: StateFlow<MediaPlayerState>,
    cardPad: Dp,
    isCompactHeight: Boolean,
    onOpenMedia: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onSwitchAudioSource: (AudioSourceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mediaState by mediaStateFlow.collectAsStateWithLifecycle()

    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer
    val isExternal = mediaState.sourceMode == AudioSourceMode.EXTERNAL
    val ext = mediaState.externalMedia
    val isFmActive = isExternal && ext.isFmRadio

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

    val cleanBtName = UniversalMediaManager.sanitizeMediaText(mediaState.connectedBtDevice?.name)
    val cleanExtTitle = UniversalMediaManager.sanitizeMediaText(ext.title)
    val cleanExtArtist = UniversalMediaManager.sanitizeMediaText(ext.artist)
    val cleanExtApp = UniversalMediaManager.sanitizeMediaText(ext.appName)

    val titleText = if (isExternal) {
        when {
            isFmActive -> {
                ext.fmStationName.ifBlank {
                    if (ext.fmFrequencyMhz.isNotBlank()) "${ext.fmFrequencyMhz} MHz" else "Radio FM"
                }
            }
            ext.hasActiveSession && cleanExtTitle.isNotBlank() -> cleanExtTitle
            cleanBtName.isNotBlank() -> cleanBtName
            else -> "Audio Externe • BT • FM"
        }
    } else {
        mediaState.currentTrack?.title ?: "Aucune musique en cours"
    }

    val subtitleText = if (isExternal) {
        when {
            isFmActive -> UniversalMediaManager.buildFmSubtitle(ext.fmFrequencyMhz, ext.fmRdsText)
            ext.hasActiveSession -> listOf(cleanExtArtist, cleanExtApp).filter { it.isNotBlank() }.joinToString(" • ")
                .ifBlank { "Lecture Bluetooth / Externe" }
            cleanBtName.isNotBlank() -> "Bluetooth A2DP • AVRCP"
            !mediaState.hasNotificationListenerAccess -> "Activez l'accès média dans Réglages"
            else -> "Lancez Spotify, Radio FM ou connectez un BT"
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

    val swipeThresholdPx = with(LocalDensity.current) { 60.dp.toPx() }
    var artDragX by remember(mediaState.currentTrack?.id, ext.title) { mutableFloatStateOf(0f) }
    val animatedArtOffset by animateFloatAsState(
        targetValue = artDragX * 0.45f,
        animationSpec = spring(stiffness = 450f),
        label = "cockpit_art_drag_offset",
    )

    BoxWithConstraints(
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
    ) {
        // Dimensionnement adaptatif de la pochette centrée (130.dp à 150.dp) selon la hauteur disponible
        val artSize = when {
            maxHeight < 340.dp -> 112.dp
            isCompactHeight || maxHeight < 420.dp -> 130.dp
            else -> 148.dp
        }
        val controlBtnSize = if (isCompactHeight) 48.dp else 52.dp
        val playControlSize = 60.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 1. Haut : deux boutons compacts de sélection de source
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
                    icon = if (isFmActive) Icons.Rounded.Radio else Icons.Rounded.Bluetooth,
                    label = when {
                        isFmActive -> "Radio FM"
                        ext.hasActiveSession && cleanExtApp.isNotBlank() -> cleanExtApp.take(16)
                        else -> "Externe • BT • FM"
                    },
                    selected = isExternal,
                    onClick = { onSwitchAudioSource(AudioSourceMode.EXTERNAL) },
                    modifier = Modifier.weight(1f),
                    testTag = "cockpit_source_bt",
                )
            }

            // 2. Centre-haut : grande pochette carrée centrée aux coins arrondis avec swipe horizontal
            Box(
                modifier = Modifier
                    .size(artSize)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, CarbonBorder, RoundedCornerShape(20.dp))
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
                    .clip(RoundedCornerShape(20.dp))
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

            // 3. Milieu (centré) : badge Radio FM si actif + Titre gras 19sp (1 ligne, marquee) + Artiste / Sous-titre 14sp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (isFmActive) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(container.copy(alpha = 0.85f))
                            .border(1.dp, primary.copy(alpha = 0.7f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                            .testTag("cockpit_fm_radio_badge"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = "Radio FM",
                            tint = accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Radio FM",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = accent,
                            maxLines = 1,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .basicMarquee(),
                    )
                    if (playing) {
                        Spacer(Modifier.width(8.dp))
                        LightweightEqualizerBars(isPlaying = true)
                    }
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 4. Bas-milieu : barre de progression pleine largeur avec temps écoulé à gauche et durée totale à droite
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val progress = if (displayDur > 0L) {
                    (displayPos.toFloat() / displayDur.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    color = primary,
                    trackColor = CarbonBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (displayDur > 0L) AudioEngine.formatDuration(displayPos) else if (isFmActive) "EN DIRECT" else "0:00",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontFamily = JetBrainsMonoFamily,
                        ),
                        color = TextSecondary,
                    )
                    Text(
                        text = if (displayDur > 0L) AudioEngine.formatDuration(displayDur) else "--:--",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontFamily = JetBrainsMonoFamily,
                        ),
                        color = TextSecondary,
                    )
                }
            }

            // 5. Bas (centré) : boutons de contrôle bien espacés (Précédent, Lecture/Pause 60.dp, Suivant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
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
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Précédent",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp),
                    )
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
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Suivant",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
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
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    modifier = Modifier.size(34.dp),
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
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = app.label,
                                tint = accent,
                                modifier = Modifier.size(36.dp),
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
                        fontSize = 14.sp,
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
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (editMode) AmberWarn.copy(alpha = 0.2f) else CarbonSurface)
                    .testTag("dock_edit_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Modifier le dock",
                    tint = if (editMode) AmberWarn else TextSecondary,
                    modifier = Modifier.size(24.dp),
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
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) container else CarbonSurface)
            .border(1.dp, if (selected) primary else CarbonBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent else TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = if (selected) accent else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
