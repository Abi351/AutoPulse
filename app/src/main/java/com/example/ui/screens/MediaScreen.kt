package com.example.ui.screens

import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInFull
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.ObsidianBg
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
    val keyLower: String,
    val displayName: String,
    val count: Int,
)

private fun categoryKeyForTrack(track: AudioTrackEntity, category: LibraryCategory): String {
    return when (category) {
        LibraryCategory.ARTISTS -> track.artist.trim().ifBlank { "Artiste inconnu" }
        LibraryCategory.ALBUMS -> track.album.trim().ifBlank {
            track.folderName.trim().ifBlank { "Album inconnu" }
        }
        LibraryCategory.GENRES -> track.genre.trim().ifBlank { "Inconnu" }
        LibraryCategory.YEARS -> track.year.trim().ifBlank { "Année inconnue" }
        LibraryCategory.ALL -> ""
    }
}

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
    closeOverlaysSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    var isSidePanelOpen by remember { mutableStateOf(false) }
    var isLibraryExpanded by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf(LibraryCategory.ALL) }
    var selectedGroupKey by remember { mutableStateOf<String?>(null) }
    var selectedGroupLabel by remember { mutableStateOf<String?>(null) }

    val thumbCache = remember { LruCache<String, ImageBitmap>(32) }
    val sideListState = rememberLazyListState()
    val expandedListState = rememberLazyListState()

    LaunchedEffect(closeOverlaysSignal) {
        if (closeOverlaysSignal > 0) {
            isSidePanelOpen = false
            isLibraryExpanded = false
            showEqDialog = false
            showImportDialog = false
        }
    }

    LaunchedEffect(mediaState.sourceMode) {
        if (mediaState.sourceMode != AudioSourceMode.LOCAL) {
            isLibraryExpanded = false
        }
    }

    BackHandler(enabled = isLibraryExpanded) {
        if (selectedGroupKey != null) {
            selectedGroupKey = null
            selectedGroupLabel = null
        } else {
            isLibraryExpanded = false
        }
    }

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
                visible = isSidePanelOpen && !isLibraryExpanded,
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
                    listState = sideListState,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    activeCategory = activeCategory,
                    onSelectCategory = { cat ->
                        activeCategory = cat
                        selectedGroupKey = null
                        selectedGroupLabel = null
                    },
                    selectedGroupKey = selectedGroupKey,
                    selectedGroupLabel = selectedGroupLabel,
                    onSelectGroup = { key, label ->
                        selectedGroupKey = key
                        selectedGroupLabel = label
                    },
                    onPlayTrack = onPlayTrack,
                    onDeleteTrack = onDeleteTrack,
                    onOpenImportDialog = { showImportDialog = true },
                    onRefreshBluetooth = onRefreshBluetooth,
                    onRequestBtPermission = onRequestBtPermission,
                    onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                    onExpandLibrary = { isLibraryExpanded = true },
                    onClosePanel = { isSidePanelOpen = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedVisibility(
            visible = isLibraryExpanded && mediaState.sourceMode == AudioSourceMode.LOCAL,
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(140)),
            modifier = Modifier.fillMaxSize(),
        ) {
            ExpandedLibraryOverlay(
                mediaState = mediaState,
                tracks = tracks,
                thumbCache = thumbCache,
                listState = expandedListState,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                activeCategory = activeCategory,
                onSelectCategory = { cat ->
                    activeCategory = cat
                    selectedGroupKey = null
                    selectedGroupLabel = null
                },
                selectedGroupKey = selectedGroupKey,
                selectedGroupLabel = selectedGroupLabel,
                onSelectGroup = { key, label ->
                    selectedGroupKey = key
                    selectedGroupLabel = label
                },
                onPlayTrack = onPlayTrack,
                onDeleteTrack = onDeleteTrack,
                onOpenImportDialog = { showImportDialog = true },
                onCollapse = { isLibraryExpanded = false },
                onCloseAll = {
                    isLibraryExpanded = false
                    isSidePanelOpen = false
                },
            )
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

    if (showImportDialog) {
        ImportAudioDialog(
            onDismiss = { showImportDialog = false },
            onPickFolder = {
                showImportDialog = false
                folderPicker.launch(null)
            },
            onPickFiles = {
                showImportDialog = false
                filesPicker.launch(arrayOf("audio/*"))
            },
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
    val activeArtBitmap = remember(
        mediaState.currentTrack?.id,
        mediaState.currentTrack?.coverPath,
        activeArt,
        isExternal,
        ext.title,
    ) {
        activeArt?.asImageBitmap()
    }
    val artTrackKey = if (isExternal) "ext_${ext.title}" else "loc_${mediaState.currentTrack?.id ?: -1}"
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
                            imageBitmap = activeArtBitmap,
                            trackKey = artTrackKey,
                            size = largeArtSize,
                            cornerRadius = 24.dp,
                            onSwipePrevious = onPrevTrack,
                            onSwipeNext = onNextTrack,
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
                        imageBitmap = activeArtBitmap,
                        trackKey = artTrackKey,
                        size = compactArtSize,
                        cornerRadius = 16.dp,
                        onSwipePrevious = onPrevTrack,
                        onSwipeNext = onNextTrack,
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
    imageBitmap: ImageBitmap?,
    trackKey: String,
    size: Dp,
    cornerRadius: Dp,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
) {
    val swipeThresholdPx = with(LocalDensity.current) { 60.dp.toPx() }
    var dragOffsetX by remember(trackKey) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetX * 0.45f,
        animationSpec = spring(stiffness = 450f),
        label = "media_album_art_drag",
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .border(1.dp, CarbonBorder, RoundedCornerShape(cornerRadius))
            .pointerInput(trackKey) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffsetX = 0f },
                    onDragCancel = { dragOffsetX = 0f },
                    onDragEnd = {
                        if (dragOffsetX > swipeThresholdPx) {
                            onSwipePrevious()
                        } else if (dragOffsetX < -swipeThresholdPx) {
                            onSwipeNext()
                        }
                        dragOffsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-swipeThresholdPx * 1.6f, swipeThresholdPx * 1.6f)
                    },
                )
            }
            .testTag("media_album_art"),
        contentAlignment = Alignment.Center,
    ) {
        val imageModifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = animatedOffset }
            .clip(RoundedCornerShape(cornerRadius))

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Pochette album (glisser pour changer de piste)",
                contentScale = ContentScale.Crop,
                modifier = imageModifier,
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_album_red),
                contentDescription = "Vinyle rouge par défaut (glisser pour changer de piste)",
                contentScale = ContentScale.Crop,
                modifier = imageModifier,
            )
        }
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
    listState: LazyListState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeCategory: LibraryCategory,
    onSelectCategory: (LibraryCategory) -> Unit,
    selectedGroupKey: String?,
    selectedGroupLabel: String?,
    onSelectGroup: (keyLower: String?, label: String?) -> Unit,
    onPlayTrack: (AudioTrackEntity) -> Unit,
    onDeleteTrack: (Int) -> Unit,
    onOpenImportDialog: () -> Unit,
    onRefreshBluetooth: () -> Unit,
    onRequestBtPermission: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onExpandLibrary: () -> Unit,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CarbonSurface)
            .border(1.dp, CarbonBorder, RoundedCornerShape(24.dp))
            .padding(14.dp),
    ) {
        // En-tête du panneau coulissant avec Importer (+), Agrandir et Fermer (X)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (mediaState.sourceMode == AudioSourceMode.LOCAL) {
                    "Bibliothèque (${tracks.size})"
                } else {
                    "Appareils Bluetooth & Média"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (mediaState.sourceMode == AudioSourceMode.LOCAL) {
                IconButton(
                    onClick = onOpenImportDialog,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(container.copy(alpha = 0.72f))
                        .border(1.dp, primary.copy(alpha = 0.65f), CircleShape)
                        .testTag("media_import_plus_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Importer",
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onExpandLibrary,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CarbonSurfaceElevated)
                        .border(1.dp, CarbonBorder, CircleShape)
                        .testTag("media_expand_library_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "Agrandir la bibliothèque",
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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
            LocalLibraryContent(
                mediaState = mediaState,
                tracks = tracks,
                thumbCache = thumbCache,
                listState = listState,
                isExpandedLayout = false,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                activeCategory = activeCategory,
                onSelectCategory = onSelectCategory,
                selectedGroupKey = selectedGroupKey,
                selectedGroupLabel = selectedGroupLabel,
                onSelectGroup = onSelectGroup,
                onPlayTrack = onPlayTrack,
                onDeleteTrack = onDeleteTrack,
                modifier = Modifier.weight(1f),
            )
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
private fun ExpandedLibraryOverlay(
    mediaState: MediaPlayerState,
    tracks: List<AudioTrackEntity>,
    thumbCache: LruCache<String, ImageBitmap>,
    listState: LazyListState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeCategory: LibraryCategory,
    onSelectCategory: (LibraryCategory) -> Unit,
    selectedGroupKey: String?,
    selectedGroupLabel: String?,
    onSelectGroup: (keyLower: String?, label: String?) -> Unit,
    onPlayTrack: (AudioTrackEntity) -> Unit,
    onDeleteTrack: (Int) -> Unit,
    onOpenImportDialog: () -> Unit,
    onCollapse: () -> Unit,
    onCloseAll: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    Surface(
        color = ObsidianBg,
        modifier = Modifier
            .fillMaxSize()
            .testTag("media_expanded_library_overlay"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CarbonSurface)
                .border(1.dp, CarbonBorder, RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(primary),
                    )
                    Text(
                        text = if (selectedGroupLabel != null) {
                            "${activeCategory.label} • $selectedGroupLabel"
                        } else {
                            "Bibliothèque hors ligne (${tracks.size})"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (selectedGroupKey != null) {
                    OutlinedButton(
                        onClick = { onSelectGroup(null, null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("media_expanded_back_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour aux catégories",
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Retour", color = accent, maxLines = 1)
                    }
                }

                IconButton(
                    onClick = onOpenImportDialog,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(container.copy(alpha = 0.72f))
                        .border(1.dp, primary.copy(alpha = 0.65f), CircleShape)
                        .testTag("media_expanded_import_plus_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Importer",
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }

                OutlinedButton(
                    onClick = onCollapse,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("media_collapse_library_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloseFullscreen,
                        contentDescription = "Réduire",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Réduire", color = TextPrimary, maxLines = 1)
                }

                IconButton(
                    onClick = onCloseAll,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(container.copy(alpha = 0.65f))
                        .border(1.dp, primary.copy(alpha = 0.5f), CircleShape)
                        .testTag("media_close_expanded_library_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Fermer la bibliothèque",
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            LocalLibraryContent(
                mediaState = mediaState,
                tracks = tracks,
                thumbCache = thumbCache,
                listState = listState,
                isExpandedLayout = true,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                activeCategory = activeCategory,
                onSelectCategory = onSelectCategory,
                selectedGroupKey = selectedGroupKey,
                selectedGroupLabel = selectedGroupLabel,
                onSelectGroup = onSelectGroup,
                onPlayTrack = onPlayTrack,
                onDeleteTrack = onDeleteTrack,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LocalLibraryContent(
    mediaState: MediaPlayerState,
    tracks: List<AudioTrackEntity>,
    thumbCache: LruCache<String, ImageBitmap>,
    listState: LazyListState,
    isExpandedLayout: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeCategory: LibraryCategory,
    onSelectCategory: (LibraryCategory) -> Unit,
    selectedGroupKey: String?,
    selectedGroupLabel: String?,
    onSelectGroup: (keyLower: String?, label: String?) -> Unit,
    onPlayTrack: (AudioTrackEntity) -> Unit,
    onDeleteTrack: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    var showCategoryChips by remember { mutableStateOf(true) }
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState, activeCategory, selectedGroupKey) {
        showCategoryChips = true
        prevIndex = listState.firstVisibleItemIndex
        prevOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (idx, off) ->
                if (idx == 0 && off <= 8) {
                    showCategoryChips = true
                } else if (idx > prevIndex || (idx == prevIndex && off > prevOffset + 12)) {
                    showCategoryChips = false
                } else if (idx < prevIndex || (idx == prevIndex && off < prevOffset - 12)) {
                    showCategoryChips = true
                }
                prevIndex = idx
                prevOffset = off
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
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
                    IconButton(onClick = { onSearchQueryChange("") }) {
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
                .testTag(if (isExpandedLayout) "media_expanded_search_input" else "media_search_input"),
        )

        AnimatedVisibility(
            visible = showCategoryChips,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(160)),
            exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(130)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryCategory.entries.forEach { category ->
                    val selected = activeCategory == category
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category.label, maxLines = 1) },
                        modifier = Modifier.testTag(
                            if (isExpandedLayout) "media_expanded_cat_${category.name.lowercase()}"
                            else "media_cat_${category.name.lowercase()}"
                        ),
                    )
                }
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
                            track.album.contains(queryTrimmed, ignoreCase = true) ||
                            track.genre.contains(queryTrimmed, ignoreCase = true) ||
                            track.year.contains(queryTrimmed, ignoreCase = true)
                    }
                }
            }

            val showGroupedList = activeCategory != LibraryCategory.ALL && selectedGroupKey == null

            if (showGroupedList) {
                val groups = remember(queryFilteredTracks, activeCategory) {
                    val grouped = queryFilteredTracks.groupBy {
                        categoryKeyForTrack(it, activeCategory).lowercase()
                    }
                    val entries = grouped.map { (keyLower, items) ->
                        val display = categoryKeyForTrack(items.first(), activeCategory)
                        CategoryGroupEntry(
                            keyLower = keyLower,
                            displayName = display,
                            count = items.size,
                        )
                    }
                    if (activeCategory == LibraryCategory.YEARS) {
                        entries.sortedByDescending { it.displayName }
                    } else {
                        entries.sortedBy { it.keyLower }
                    }
                }

                val groupIcon = when (activeCategory) {
                    LibraryCategory.ARTISTS -> Icons.Rounded.Person
                    LibraryCategory.ALBUMS -> Icons.Rounded.Album
                    LibraryCategory.GENRES -> Icons.Rounded.Category
                    LibraryCategory.YEARS -> Icons.Rounded.CalendarToday
                    LibraryCategory.ALL -> Icons.Rounded.MusicNote
                }

                if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Aucune catégorie trouvée pour votre recherche.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(groups, key = { "${activeCategory.name}_${it.keyLower}" }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CarbonSurfaceElevated)
                                    .border(1.dp, CarbonBorder, RoundedCornerShape(14.dp))
                                    .clickable { onSelectGroup(entry.keyLower, entry.displayName) }
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                                    .testTag("media_group_row_${entry.displayName}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(groupIcon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = entry.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${entry.count} morceau${if (entry.count > 1) "x" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
            } else {
                if (selectedGroupKey != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(container.copy(alpha = 0.78f))
                                .border(1.dp, primary.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .clickable { onSelectGroup(null, null) }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("media_back_to_categories_btn"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Retour aux catégories",
                                tint = accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "← Retour aux catégories",
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                                maxLines = 1,
                            )
                        }

                        Text(
                            text = "${activeCategory.label} : ${selectedGroupLabel.orEmpty()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                val displayedTracks = remember(queryFilteredTracks, activeCategory, selectedGroupKey) {
                    val grpKey = selectedGroupKey
                    if (grpKey == null || activeCategory == LibraryCategory.ALL) {
                        queryFilteredTracks
                    } else {
                        queryFilteredTracks.filter { track ->
                            categoryKeyForTrack(track, activeCategory).equals(grpKey, ignoreCase = true)
                        }
                    }
                }

                LaunchedEffect(mediaState.currentTrack?.id, displayedTracks) {
                    val activeId = mediaState.currentTrack?.id ?: return@LaunchedEffect
                    val targetIdx = displayedTracks.indexOfFirst { it.id == activeId }
                    if (targetIdx >= 0) {
                        runCatching { listState.animateScrollToItem(targetIdx) }
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
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(displayedTracks, key = { it.id }) { track ->
                            val isCurrent = mediaState.currentTrack?.id == track.id
                            val coverThumb by produceState<ImageBitmap?>(
                                initialValue = track.coverPath?.let { thumbCache.get(it) },
                                key1 = track.id,
                                key2 = track.coverPath,
                            ) {
                                val path = track.coverPath
                                if (path == null) {
                                    value = null
                                } else if (value == null) {
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
                                    .background(if (isCurrent) container.copy(alpha = 0.85f) else CarbonSurfaceElevated)
                                    .border(
                                        width = if (isCurrent) 1.5.dp else 1.dp,
                                        color = if (isCurrent) primary else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .clickable { onPlayTrack(track) }
                                    .padding(horizontal = if (isExpandedLayout) 14.dp else 10.dp, vertical = 10.dp)
                                    .testTag("media_track_row_${track.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val thumbSize = if (isExpandedLayout) 48.dp else 40.dp
                                val bmp = coverThumb
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(thumbSize)
                                            .clip(RoundedCornerShape(10.dp)),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(thumbSize)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CarbonSurface),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = if (isCurrent) primary else TextSecondary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                if (isExpandedLayout) {
                                    Column(modifier = Modifier.weight(1.4f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isCurrent) accent else TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (isCurrent) {
                                                LightweightEqualizerBars(isPlaying = mediaState.isPlaying)
                                            }
                                        }
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isCurrent) TextPrimary else TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = track.album.ifBlank { track.folderName },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(0.9f),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = track.year.ifBlank { "—" },
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                        color = TextSecondary,
                                        modifier = Modifier.width(52.dp),
                                    )
                                } else {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isCurrent) accent else TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (isCurrent) {
                                                LightweightEqualizerBars(isPlaying = mediaState.isPlaying)
                                            }
                                        }
                                        val metaLine = buildString {
                                            append(track.artist)
                                            if (track.album.isNotBlank() && track.album != track.folderName) {
                                                append(" • ")
                                                append(track.album)
                                            }
                                            if (track.year.isNotBlank()) {
                                                append(" • ")
                                                append(track.year)
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
                                }

                                Text(
                                    text = AudioEngine.formatDuration(track.durationMs),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = if (isCurrent) accent else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp),
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

@Composable
private fun ImportAudioDialog(
    onDismiss: () -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Importer de la musique",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choisissez la source audio à indexer dans votre bibliothèque locale :",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Button(
                    onClick = onPickFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = accent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("media_dialog_import_folder_btn"),
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Dossier USB / SD", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onPickFiles,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("media_dialog_import_files_btn"),
                ) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Fichiers audio", color = accent, maxLines = 1)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}
