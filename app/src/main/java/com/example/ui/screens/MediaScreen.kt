package com.example.ui.screens

import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.AudioTrackEntity
import com.example.hardware.AudioEngine
import com.example.hardware.AudioSourceMode
import com.example.hardware.EqPreset
import com.example.hardware.ExternalRepeatMode
import com.example.hardware.MediaPlayerState
import com.example.hardware.MetadataFetcher
import com.example.ui.components.LightweightEqualizerBars
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LibraryCategory(val label: String) {
    ALL("Tout"),
    ARTISTS("Artistes"),
    ALBUMS("Albums"),
    GENRES("Genres"),
    YEARS("Années"),
}

private data class CategoryGroupEntry(
    val name: String,
    val count: Int,
)

@Composable
fun MediaScreen(
    mediaState: MediaPlayerState,
    tracks: List<AudioTrackEntity>,
    onSelectSource: (AudioSourceMode) -> Unit,
    onPlayTrack: (AudioTrackEntity) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onImportFiles: (List<android.net.Uri>) -> Unit,
    onImportFolder: (android.net.Uri) -> Unit,
    onDeleteTrack: (Int) -> Unit,
    onSetEqEnabled: (Boolean) -> Unit,
    onApplyEqPreset: (Int) -> Unit,
    onSetEqBand: (Short, Short) -> Unit,
    onSetVolume: (Int) -> Unit,
    onRefreshBluetooth: () -> Unit,
    onRequestBtPermission: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSidePanelOpen by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    val thumbCache = remember { LruCache<String, ImageBitmap>(24) }

    val filesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onImportFiles(uris)
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) onImportFolder(uri)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactHeight = maxHeight < 440.dp
        val outerPad = if (isCompactHeight) 10.dp else 16.dp
        val paneGap = if (maxWidth >= 840.dp) 16.dp else 12.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPad),
            horizontalArrangement = Arrangement.spacedBy(if (isSidePanelOpen) paneGap else 0.dp),
        ) {
            PlayerDeckPane(
                mediaState = mediaState,
                isSidePanelOpen = isSidePanelOpen,
                isCompactHeight = isCompactHeight,
                onSelectSource = onSelectSource,
                onToggleSidePanel = { isSidePanelOpen = !isSidePanelOpen },
                onTogglePlayPause = onTogglePlayPause,
                onNextTrack = onNextTrack,
                onPrevTrack = onPrevTrack,
                onSeekTo = onSeekTo,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onOpenEq = { showEqDialog = true },
                onSetVolume = onSetVolume,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )

            AnimatedVisibility(
                visible = isSidePanelOpen,
                enter = expandHorizontally(
                    animationSpec = tween(200),
                    expandFrom = Alignment.End,
                ) + fadeIn(animationSpec = tween(180)),
                exit = shrinkHorizontally(
                    animationSpec = tween(170),
                    shrinkTowards = Alignment.End,
                ) + fadeOut(animationSpec = tween(140)),
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .fillMaxHeight(),
            ) {
                SourceLibraryPane(
                    mediaState = mediaState,
                    tracks = tracks,
                    thumbCache = thumbCache,
                    onPlayTrack = onPlayTrack,
                    onDeleteTrack = onDeleteTrack,
                    onPickFiles = { filesPicker.launch(arrayOf("audio/*")) },
                    onPickFolder = { folderPicker.launch(null) },
                    onRefreshBluetooth = onRefreshBluetooth,
                    onRequestBtPermission = onRequestBtPermission,
                    onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                    onClosePanel = { isSidePanelOpen = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showEqDialog) {
        EqualizerDialog(
            mediaState = mediaState,
            onDismiss = { showEqDialog = false },
            onSetEqEnabled = onSetEqEnabled,
            onApplyPreset = onApplyEqPreset,
            onSetBand = onSetEqBand,
        )
    }
}

@Composable
private fun PlayerDeckPane(
    mediaState: MediaPlayerState,
    isSidePanelOpen: Boolean,
    isCompactHeight: Boolean,
    onSelectSource: (AudioSourceMode) -> Unit,
    onToggleSidePanel: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onOpenEq: () -> Unit,
    onSetVolume: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    val isExternal = mediaState.sourceMode == AudioSourceMode.EXTERNAL
    val ext = mediaState.externalMedia

    val playing = if (isExternal) {
        if (ext.hasActiveSession) ext.isPlaying else mediaState.btAudioActive
    } else {
        mediaState.isPlaying
    }

    val playScale by animateFloatAsState(
        targetValue = if (playing) 1.06f else 1.0f,
        animationSpec = spring(stiffness = 300f),
        label = "media_play_scale",
    )

    val titleText = if (isExternal) {
        when {
            ext.hasActiveSession -> ext.title
            mediaState.connectedBtDevice != null -> mediaState.connectedBtDevice.name
            else -> "Audio Externe / BT"
        }
    } else {
        mediaState.currentTrack?.title ?: "Prêt pour la lecture"
    }

    val subtitleText = if (isExternal) {
        when {
            ext.hasActiveSession -> "${ext.artist} • ${ext.appName}"
            mediaState.connectedBtDevice != null -> "Contrôle AVRCP actif"
            else -> "Spotify, YouTube Music, Deezer ou Bluetooth"
        }
    } else {
        val track = mediaState.currentTrack
        if (track != null) {
            buildString {
                append(track.artist)
                if (track.album.isNotBlank() && track.album != track.folderName) {
                    append(" • ")
                    append(track.album)
                }
                if (track.year.isNotBlank()) {
                    append(" (${track.year})")
                }
            }
        } else {
            "Ajoutez des MP3 / FLAC hors ligne"
        }
    }

    val activeArt = if (isExternal) ext.albumArt else mediaState.currentAlbumArt
    val panelToggleIcon = if (isExternal) Icons.Rounded.Bluetooth else Icons.AutoMirrored.Rounded.QueueMusic
    val panelToggleDesc = if (isExternal) {
        "Afficher les appareils Bluetooth appairés"
    } else {
        "Afficher la bibliothèque hors ligne"
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CarbonSurfaceElevated)
            .border(1.dp, if (playing) primary.copy(alpha = 0.55f) else CarbonBorder, RoundedCornerShape(24.dp))
            .padding(if (isCompactHeight) 14.dp else 20.dp),
    ) {
        val useWideSplitPlayer = !isSidePanelOpen && maxWidth >= 560.dp
        val ctrlBtnSize = if (isCompactHeight) 46.dp else 54.dp
        val playBtnSize = if (isCompactHeight) 56.dp else 66.dp

        if (useWideSplitPlayer) {
            val largeArtSize = minOf(maxHeight * 0.72f, maxWidth * 0.34f).coerceIn(150.dp, 310.dp)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // En-tête : titre Lecture en cours + bouton panneau repliable tout en haut à droite
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
                                .width(22.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(primary),
                        )
                        Text(
                            text = "LECTURE EN COURS",
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                        )
                        LightweightEqualizerBars(isPlaying = playing)
                    }

                    PanelToggleIconButton(
                        icon = panelToggleIcon,
                        contentDescription = panelToggleDesc,
                        isOpen = isSidePanelOpen,
                        onClick = onToggleSidePanel,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Corps plein écran : Grande pochette carrée à gauche + Contrôles complets à droite
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(largeArtSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        AlbumArtworkBox(
                            bitmap = activeArt,
                            size = largeArtSize,
                            cornerRadius = 24.dp,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SourceTabButton(
                                label = "Stockage / USB",
                                icon = Icons.Rounded.FolderOpen,
                                selected = !isExternal,
                                onClick = { onSelectSource(AudioSourceMode.LOCAL) },
                                testTag = "media_tab_local",
                                modifier = Modifier.weight(1f),
                            )
                            SourceTabButton(
                                label = if (ext.hasActiveSession) ext.appName.take(14) else "Externe / BT",
                                icon = Icons.Rounded.Bluetooth,
                                selected = isExternal,
                                onClick = { onSelectSource(AudioSourceMode.EXTERNAL) },
                                testTag = "media_tab_bt",
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = titleText,
                                style = if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            EqQuickBadge(
                                eqEnabled = mediaState.eqEnabled,
                                presetName = mediaState.eqPresetName,
                                onClick = onOpenEq,
                            )
                        }

                        PlayerSeekSection(
                            mediaState = mediaState,
                            isExternal = isExternal,
                            onSeekTo = onSeekTo,
                        )

                        PlayerTransportRow(
                            mediaState = mediaState,
                            isExternal = isExternal,
                            playing = playing,
                            playScale = playScale,
                            ctrlBtnSize = ctrlBtnSize,
                            playBtnSize = playBtnSize,
                            onToggleShuffle = onToggleShuffle,
                            onPrevTrack = onPrevTrack,
                            onTogglePlayPause = onTogglePlayPause,
                            onNextTrack = onNextTrack,
                            onToggleRepeat = onToggleRepeat,
                        )

                        PlayerVolumeRow(
                            mediaState = mediaState,
                            onSetVolume = onSetVolume,
                        )
                    }
                }
            }
        } else {
            // Mode panneau ouvert (ou écran étroit) : disposition verticale compacte sans débordement
            val compactArtSize = if (isCompactHeight) 64.dp else 82.dp

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SourceTabButton(
                        label = "Stockage / USB",
                        icon = Icons.Rounded.FolderOpen,
                        selected = !isExternal,
                        onClick = { onSelectSource(AudioSourceMode.LOCAL) },
                        testTag = "media_tab_local",
                        modifier = Modifier.weight(1f),
                    )
                    SourceTabButton(
                        label = if (ext.hasActiveSession) ext.appName.take(12) else "Externe / BT",
                        icon = Icons.Rounded.Bluetooth,
                        selected = isExternal,
                        onClick = { onSelectSource(AudioSourceMode.EXTERNAL) },
                        testTag = "media_tab_bt",
                        modifier = Modifier.weight(1f),
                    )
                    PanelToggleIconButton(
                        icon = panelToggleIcon,
                        contentDescription = panelToggleDesc,
                        isOpen = isSidePanelOpen,
                        onClick = onToggleSidePanel,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AlbumArtworkBox(
                        bitmap = activeArt,
                        size = compactArtSize,
                        cornerRadius = 16.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
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
                        Spacer(Modifier.height(4.dp))
                        EqQuickBadge(
                            eqEnabled = mediaState.eqEnabled,
                            presetName = mediaState.eqPresetName,
                            onClick = onOpenEq,
                        )
                    }
                }

                PlayerSeekSection(
                    mediaState = mediaState,
                    isExternal = isExternal,
                    onSeekTo = onSeekTo,
                )

                PlayerTransportRow(
                    mediaState = mediaState,
                    isExternal = isExternal,
                    playing = playing,
                    playScale = playScale,
                    ctrlBtnSize = if (isCompactHeight) 42.dp else 48.dp,
                    playBtnSize = if (isCompactHeight) 52.dp else 60.dp,
                    onToggleShuffle = onToggleShuffle,
                    onPrevTrack = onPrevTrack,
                    onTogglePlayPause = onTogglePlayPause,
                    onNextTrack = onNextTrack,
                    onToggleRepeat = onToggleRepeat,
                )

                PlayerVolumeRow(
                    mediaState = mediaState,
                    onSetVolume = onSetVolume,
                )
            }
        }
    }
}

@Composable
private fun PanelToggleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isOpen: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isOpen) container else CarbonSurface)
            .border(
                1.dp,
                if (isOpen) primary else CarbonBorder,
                RoundedCornerShape(14.dp),
            )
            .testTag("media_toggle_panel_btn"),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isOpen) accent else TextPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AlbumArtworkBox(
    bitmap: android.graphics.Bitmap?,
    size: Dp,
    cornerRadius: Dp,
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Pochette album",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .border(1.dp, CarbonBorder, RoundedCornerShape(cornerRadius)),
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.img_album_red),
            contentDescription = "Vinyle rouge par défaut",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .border(1.dp, CarbonBorder, RoundedCornerShape(cornerRadius)),
        )
    }
}

@Composable
private fun EqQuickBadge(
    eqEnabled: Boolean,
    presetName: String,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val label = if (eqEnabled) "EQ : $presetName" else "EQ : Désactivé"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonSurface)
            .border(
                1.dp,
                if (eqEnabled) primary.copy(alpha = 0.45f) else CarbonBorder,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("media_eq_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Equalizer,
            contentDescription = "Égaliseur",
            tint = if (eqEnabled) accent else TextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (eqEnabled) accent else TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerSeekSection(
    mediaState: MediaPlayerState,
    isExternal: Boolean,
    onSeekTo: (Long) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val ext = mediaState.externalMedia

    if (!isExternal) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val maxDur = mediaState.durationMs.coerceAtLeast(1L)
            Slider(
                value = mediaState.positionMs.toFloat().coerceIn(0f, maxDur.toFloat()),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..maxDur.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = primary,
                    activeTrackColor = primary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = AudioEngine.formatDuration(mediaState.positionMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = TextSecondary,
                )
                Text(
                    text = AudioEngine.formatDuration(mediaState.durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = TextSecondary,
                )
            }
        }
    } else {
        var isSeeking by remember(ext.title, ext.durationMs) { mutableStateOf(false) }
        var dragPositionMs by remember(ext.title, ext.durationMs) { mutableFloatStateOf(0f) }
        val maxExtDur = ext.durationMs.coerceAtLeast(1L)
        val displayedPosMs = if (isSeeking) {
            dragPositionMs.toLong()
        } else {
            ext.positionMs.coerceIn(0L, maxExtDur)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = if (ext.durationMs > 0L) displayedPosMs.toFloat() else 0f,
                onValueChange = { newVal ->
                    isSeeking = true
                    dragPositionMs = newVal
                },
                onValueChangeFinished = {
                    onSeekTo(dragPositionMs.toLong())
                    isSeeking = false
                },
                enabled = ext.hasActiveSession && ext.durationMs > 0L && ext.supportsSeek,
                valueRange = 0f..maxExtDur.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = primary,
                    activeTrackColor = primary,
                    disabledThumbColor = TextSecondary.copy(alpha = 0.35f),
                    disabledActiveTrackColor = TextSecondary.copy(alpha = 0.35f),
                ),
                modifier = Modifier.testTag("media_external_seek_slider"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = AudioEngine.formatDuration(displayedPosMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = TextSecondary,
                )
                Text(
                    text = AudioEngine.formatDuration(ext.durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun PlayerTransportRow(
    mediaState: MediaPlayerState,
    isExternal: Boolean,
    playing: Boolean,
    playScale: Float,
    ctrlBtnSize: Dp,
    playBtnSize: Dp,
    onToggleShuffle: () -> Unit,
    onPrevTrack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onToggleRepeat: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val ext = mediaState.externalMedia

    val shuffleSupported = !isExternal || (ext.hasActiveSession && ext.supportsShuffle)
    val shuffleActive = if (isExternal) ext.shuffleEnabled else mediaState.shuffleEnabled
    val repeatSupported = !isExternal || (ext.hasActiveSession && ext.supportsRepeat)
    val repeatActive = if (isExternal) ext.repeatMode != ExternalRepeatMode.NONE else mediaState.repeatOne
    val repeatIcon = if (
        (!isExternal) ||
        (isExternal && ext.repeatMode == ExternalRepeatMode.ONE)
    ) {
        Icons.Rounded.RepeatOne
    } else {
        Icons.Rounded.Repeat
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleShuffle,
            enabled = shuffleSupported,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("media_shuffle_btn"),
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "Aléatoire",
                tint = when {
                    !shuffleSupported -> TextSecondary.copy(alpha = 0.32f)
                    shuffleActive -> primary
                    else -> TextSecondary
                },
            )
        }
        IconButton(
            onClick = onPrevTrack,
            modifier = Modifier
                .size(ctrlBtnSize)
                .clip(CircleShape)
                .background(CarbonSurface)
                .testTag("media_prev_btn"),
        ) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Précédent", tint = TextPrimary, modifier = Modifier.size(28.dp))
        }
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .size(playBtnSize)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                }
                .clip(CircleShape)
                .background(primary)
                .testTag("media_play_pause_btn"),
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
                .size(ctrlBtnSize)
                .clip(CircleShape)
                .background(CarbonSurface)
                .testTag("media_next_btn"),
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant", tint = TextPrimary, modifier = Modifier.size(28.dp))
        }
        IconButton(
            onClick = onToggleRepeat,
            enabled = repeatSupported,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("media_repeat_btn"),
        ) {
            Icon(
                repeatIcon,
                contentDescription = "Répéter",
                tint = when {
                    !repeatSupported -> TextSecondary.copy(alpha = 0.32f)
                    repeatActive -> primary
                    else -> TextSecondary
                },
            )
        }
    }
}

@Composable
private fun PlayerVolumeRow(
    mediaState: MediaPlayerState,
    onSetVolume: (Int) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.secondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Volume", tint = TextSecondary, modifier = Modifier.size(20.dp))
        Slider(
            value = mediaState.mediaVolume.toFloat(),
            onValueChange = { onSetVolume(it.toInt()) },
            valueRange = 0f..mediaState.maxVolume.coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${mediaState.mediaVolume}",
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
        )
    }
}

@Composable
private fun SourceLibraryPane(
    mediaState: MediaPlayerState,
    tracks: List<AudioTrackEntity>,
    thumbCache: LruCache<String, ImageBitmap>,
    onPlayTrack: (AudioTrackEntity) -> Unit,
    onDeleteTrack: (Int) -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onRefreshBluetooth: () -> Unit,
    onRequestBtPermission: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    var searchQuery by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf(LibraryCategory.ALL) }
    var selectedGroupValue by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CarbonSurface)
            .border(1.dp, CarbonBorder, RoundedCornerShape(24.dp))
            .padding(14.dp),
    ) {
        // En-tête du panneau coulissant avec bouton Fermer (X)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (mediaState.sourceMode == AudioSourceMode.LOCAL) {
                    "Bibliothèque hors ligne (${tracks.size})"
                } else {
                    "Appareils Bluetooth & Média"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onClosePanel,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CarbonSurfaceElevated)
                    .testTag("media_close_panel_btn"),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Fermer le panneau", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        if (mediaState.sourceMode == AudioSourceMode.LOCAL) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPickFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("media_import_folder_btn"),
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Dossier USB/SD", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onPickFiles,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("media_import_files_btn"),
                ) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fichiers", color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Barre de recherche instantanée
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Rechercher titre, artiste, album...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Effacer", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    unfocusedBorderColor = CarbonBorder,
                    focusedContainerColor = CarbonSurfaceElevated,
                    unfocusedContainerColor = CarbonSurfaceElevated,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("media_search_input"),
            )

            Spacer(Modifier.height(8.dp))

            // Puces de catégories défilables
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LibraryCategory.entries.forEach { category ->
                    val selected = activeCategory == category
                    FilterChip(
                        selected = selected,
                        onClick = {
                            activeCategory = category
                            selectedGroupValue = null
                        },
                        label = { Text(category.label, maxLines = 1) },
                        modifier = Modifier.testTag("media_cat_${category.name.lowercase()}"),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (mediaState.isScanningFolder) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = primary)
                    Text("Indexation des fichiers audio...", style = MaterialTheme.typography.bodyMedium, color = accent)
                }
            }

            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(44.dp))
                        Text("Bibliothèque hors ligne vide", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = "Sélectionnez un dossier de votre clé USB, carte SD ou mémoire interne.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            } else {
                val queryTrimmed = searchQuery.trim()
                val queryFilteredTracks = remember(tracks, queryTrimmed) {
                    if (queryTrimmed.isEmpty()) {
                        tracks
                    } else {
                        tracks.filter { track ->
                            track.title.contains(queryTrimmed, ignoreCase = true) ||
                                track.artist.contains(queryTrimmed, ignoreCase = true) ||
                                track.album.contains(queryTrimmed, ignoreCase = true)
                        }
                    }
                }

                val showGroupedList = activeCategory != LibraryCategory.ALL && selectedGroupValue == null

                if (showGroupedList) {
                    val groups = remember(queryFilteredTracks, activeCategory) {
                        val grouped = when (activeCategory) {
                            LibraryCategory.ARTISTS -> queryFilteredTracks.groupBy { it.artist.ifBlank { "Artiste inconnu" } }
                            LibraryCategory.ALBUMS -> queryFilteredTracks.groupBy { it.album.ifBlank { it.folderName } }
                            LibraryCategory.GENRES -> queryFilteredTracks.groupBy { it.genre.ifBlank { "Inconnu" } }
                            LibraryCategory.YEARS -> queryFilteredTracks.groupBy { it.year.ifBlank { "Année inconnue" } }
                            LibraryCategory.ALL -> emptyMap()
                        }
                        val entries = grouped.map { (name, items) -> CategoryGroupEntry(name, items.size) }
                        if (activeCategory == LibraryCategory.YEARS) {
                            entries.sortedByDescending { it.name }
                        } else {
                            entries.sortedBy { it.name.lowercase() }
                        }
                    }

                    val groupIcon = when (activeCategory) {
                        LibraryCategory.ARTISTS -> Icons.Rounded.Person
                        LibraryCategory.ALBUMS -> Icons.Rounded.Album
                        LibraryCategory.GENRES -> Icons.Rounded.Category
                        LibraryCategory.YEARS -> Icons.Rounded.CalendarToday
                        LibraryCategory.ALL -> Icons.Rounded.MusicNote
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups, key = { it.name }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CarbonSurfaceElevated)
                                    .border(1.dp, CarbonBorder, RoundedCornerShape(14.dp))
                                    .clickable { selectedGroupValue = entry.name }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(groupIcon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${entry.count} morceau${if (entry.count > 1) "x" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                } else {
                    if (selectedGroupValue != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(container.copy(alpha = 0.6f))
                                .clickable { selectedGroupValue = null }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = accent, modifier = Modifier.size(16.dp))
                            Text(
                                text = "${activeCategory.label} : $selectedGroupValue",
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    val displayedTracks = remember(queryFilteredTracks, activeCategory, selectedGroupValue) {
                        val grp = selectedGroupValue
                        if (grp == null) {
                            queryFilteredTracks
                        } else {
                            queryFilteredTracks.filter { track ->
                                when (activeCategory) {
                                    LibraryCategory.ARTISTS -> track.artist.ifBlank { "Artiste inconnu" } == grp
                                    LibraryCategory.ALBUMS -> track.album.ifBlank { track.folderName } == grp
                                    LibraryCategory.GENRES -> track.genre.ifBlank { "Inconnu" } == grp
                                    LibraryCategory.YEARS -> track.year.ifBlank { "Année inconnue" } == grp
                                    LibraryCategory.ALL -> true
                                }
                            }
                        }
                    }

                    if (displayedTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Aucun morceau ne correspond à votre recherche.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(displayedTracks, key = { it.id }) { track ->
                                val isCurrent = mediaState.currentTrack?.id == track.id
                                val coverThumb by produceState<ImageBitmap?>(
                                    initialValue = track.coverPath?.let { thumbCache.get(it) },
                                    key1 = track.coverPath,
                                ) {
                                    val path = track.coverPath
                                    if (path != null && value == null) {
                                        val loaded = withContext(Dispatchers.IO) {
                                            MetadataFetcher.loadCoverBitmapRgb565(path)?.asImageBitmap()
                                        }
                                        if (loaded != null) {
                                            thumbCache.put(path, loaded)
                                            value = loaded
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isCurrent) container.copy(alpha = 0.7f) else CarbonSurfaceElevated)
                                        .border(
                                            1.dp,
                                            if (isCurrent) primary.copy(alpha = 0.7f) else Color.Transparent,
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable { onPlayTrack(track) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val bmp = coverThumb
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CarbonSurface),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = if (isCurrent) primary else TextSecondary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isCurrent) accent else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val metaLine = buildString {
                                            append(track.artist)
                                            if (track.year.isNotBlank()) {
                                                append(" • ")
                                                append(track.year)
                                            }
                                            if (track.genre.isNotBlank() && track.genre != "Inconnu") {
                                                append(" • ")
                                                append(track.genre)
                                            }
                                        }
                                        Text(
                                            text = metaLine,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = AudioEngine.formatDuration(track.durationMs),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                    IconButton(onClick = { onDeleteTrack(track.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Retirer", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Contrôle Média Universel (MediaSessionManager) + Bluetooth A2DP
            Surface(
                color = CarbonSurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (mediaState.hasNotificationListenerAccess) EmeraldOk.copy(alpha = 0.4f) else AmberWarn.copy(alpha = 0.5f),
                        RoundedCornerShape(14.dp),
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = if (mediaState.hasNotificationListenerAccess) EmeraldOk else AmberWarn,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Contrôle Média Universel",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (mediaState.hasNotificationListenerAccess) {
                                if (mediaState.externalMedia.hasActiveSession) {
                                    "Session détectée : ${mediaState.externalMedia.appName}"
                                } else {
                                    "Actif — lancez Spotify, YouTube Music ou Deezer"
                                }
                            } else {
                                "Autorisez les notifications pour afficher titres & pochettes"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!mediaState.hasNotificationListenerAccess) {
                        Button(
                            onClick = onOpenNotificationListenerSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.testTag("media_enable_notif_listener_btn"),
                        ) {
                            Text("Activer")
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Appareils Bluetooth appairés", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                IconButton(onClick = onRefreshBluetooth, modifier = Modifier.testTag("bt_refresh_btn")) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser Bluetooth", tint = primary)
                }
            }

            Spacer(Modifier.height(6.dp))

            if (!mediaState.hasBluetoothPermission) {
                Surface(
                    color = CarbonSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Permission Bluetooth requise", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = "Autorisez la connexion Bluetooth pour afficher votre smartphone et piloter la lecture AVRCP.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Button(onClick = onRequestBtPermission) {
                            Text("Autoriser Bluetooth")
                        }
                    }
                }
            } else if (!mediaState.bluetoothEnabled) {
                Text(
                    text = "Bluetooth désactivé sur l'autoradio. Activez-le dans les réglages système Android.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else if (mediaState.pairedBtDevices.isEmpty()) {
                Text(
                    text = "Aucun appareil Bluetooth appairé trouvé.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mediaState.pairedBtDevices, key = { it.address }) { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (dev.isConnectedA2dp) container.copy(alpha = 0.65f) else CarbonSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (dev.isConnectedA2dp) primary else CarbonBorder,
                                    RoundedCornerShape(14.dp),
                                )
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (dev.isConnectedA2dp) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
                                contentDescription = null,
                                tint = if (dev.isConnectedA2dp) primary else TextSecondary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    text = if (dev.isConnectedA2dp) "Audio A2DP connecté • ${dev.address}" else dev.address,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dev.isConnectedA2dp) EmeraldOk else TextSecondary,
                                )
                            }
                            if (dev.batteryPercent != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.BatteryFull, contentDescription = null, tint = EmeraldOk, modifier = Modifier.size(16.dp))
                                    Text("${dev.batteryPercent}%", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) container else CarbonSurface)
            .border(1.dp, if (selected) primary else CarbonBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) accent else TextSecondary, modifier = Modifier.size(18.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerDialog(
    mediaState: MediaPlayerState,
    onDismiss: () -> Unit,
    onSetEqEnabled: (Boolean) -> Unit,
    onApplyPreset: (Int) -> Unit,
    onSetBand: (Short, Short) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Égaliseur 5 bandes")
                Switch(
                    checked = mediaState.eqEnabled,
                    onCheckedChange = onSetEqEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = primary),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    EqPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = mediaState.eqPresetIndex == preset.index,
                            onClick = { onApplyPreset(preset.index) },
                            label = { Text(preset.label, maxLines = 1) },
                        )
                    }
                }

                mediaState.eqBands.forEach { band ->
                    val freqText = if (band.centerFreqHz >= 1000) {
                        val k = band.centerFreqHz / 1000f
                        if (k % 1f == 0f) "${k.toInt()} kHz" else "%.1f kHz".format(k)
                    } else {
                        "${band.centerFreqHz} Hz"
                    }
                    val db = band.levelMb / 100
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = freqText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.width(52.dp),
                        )
                        Slider(
                            value = band.levelMb.toFloat(),
                            onValueChange = { onSetBand(band.index, it.toInt().toShort()) },
                            enabled = mediaState.eqEnabled,
                            valueRange = band.minMb.toFloat()..band.maxMb.toFloat(),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${if (db > 0) "+" else ""}${db} dB",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = TextPrimary,
                            modifier = Modifier.width(48.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}
