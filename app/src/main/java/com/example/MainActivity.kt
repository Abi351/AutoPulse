package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.PreferencesRepository
import com.example.hardware.FloatingPillService
import com.example.hardware.ScreenDimmerService
import com.example.ui.components.RailClockBadge
import com.example.ui.components.TopStatusBar
import com.example.ui.screens.AppLibraryOverlay
import com.example.ui.screens.CockpitHomeScreen
import com.example.ui.screens.MediaScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TripScreen
import com.example.ui.theme.AccentThemeColor
import com.example.ui.theme.AutoPulseTheme
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AutoPulseViewModel
import kotlinx.coroutines.delay
import java.util.Calendar

const val ROUTE_COCKPIT = "cockpit"
const val ROUTE_MEDIA = "media"
const val ROUTE_TRIP = "trip"
const val ROUTE_SETTINGS = "settings"

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
)

private val navDestinations = listOf(
    NavDestinationItem(ROUTE_COCKPIT, "Accueil", Icons.Rounded.Home, "nav_item_cockpit"),
    NavDestinationItem(ROUTE_MEDIA, "Musique", Icons.Rounded.MusicNote, "nav_item_media"),
    NavDestinationItem(ROUTE_TRIP, "Trajet", Icons.Rounded.Route, "nav_item_trip"),
    NavDestinationItem(ROUTE_SETTINGS, "Réglages", Icons.Rounded.Settings, "nav_item_settings"),
)

class MainActivity : ComponentActivity() {

    private val autoPulseViewModel: AutoPulseViewModel by lazy {
        ViewModelProvider(this)[AutoPulseViewModel::class.java]
    }

    private val swcKeyCodes = setOf(
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by autoPulseViewModel.settings.collectAsStateWithLifecycle()
            val accentColor = remember(settings.accentColorId) {
                AccentThemeColor.fromId(settings.accentColorId)
            }
            AutoPulseTheme(accentColor = accentColor) {
                AutoPulseAppRoot(viewModel = autoPulseViewModel)
            }
        }
        applyFullscreenMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullscreenMode()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in swcKeyCodes) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (autoPulseViewModel.handleSteeringWheelKey(event.keyCode)) {
                    return true
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun applyFullscreenMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun AutoPulseAppRoot(
    viewModel: AutoPulseViewModel,
) {
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle()
    val speedState by viewModel.speedState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val tripStats by viewModel.tripStats.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val dockApps by viewModel.dockApps.collectAsStateWithLifecycle()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsStateWithLifecycle()
    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsStateWithLifecycle()
    val coverSyncState by viewModel.coverSyncState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAppLibrary by remember { mutableStateOf(false) }
    var nowMinutes by remember { mutableIntStateOf(currentTimeMinutes()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMinutes = currentTimeMinutes()
            delay(30_000L)
        }
    }

    val isNightFilterActiveNow = remember(nowMinutes, settings.nightStartMinutes, settings.nightEndMinutes) {
        PreferencesRepository.isNightTimeNow(
            nowMinutes = nowMinutes,
            startMinutes = settings.nightStartMinutes,
            endMinutes = settings.nightEndMinutes,
        )
    }

    val effectiveDimmerAlpha = remember(settings, nowMinutes) {
        PreferencesRepository.computeEffectiveDimmerAlpha(settings, nowMinutes)
    }

    LaunchedEffect(effectiveDimmerAlpha, hasOverlayPermission) {
        ScreenDimmerService.syncDimmer(context, effectiveDimmerAlpha)
    }

    LaunchedEffect(settings.floatingPillEnabled, hasOverlayPermission) {
        FloatingPillService.sync(context, settings.floatingPillEnabled)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_COCKPIT
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResumeScreen()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPauseScreen()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Un Launcher Home ne se ferme pas sur le bouton Retour à l'écran principal
    BackHandler(enabled = currentRoute == ROUTE_COCKPIT && !showAppLibrary) {
        // Reste sur l'écran d'accueil du launcher
    }

    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.onPermissionsUpdated()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    LaunchedEffect(feedback) {
        val msg = feedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearFeedback()
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
    ) {
        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (currentRoute == ROUTE_COCKPIT) {
                    TopStatusBar(
                        btDeviceName = mediaState.connectedBtDevice?.name,
                        externalAppName = mediaState.externalMedia.takeIf { it.hasActiveSession }?.appName,
                        isMuted = mediaState.isMuted,
                        onToggleMute = { viewModel.audioEngine.toggleMute() },
                        onOpenBluetooth = { navigateTo(ROUTE_MEDIA) },
                    )
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                NavigationRail(
                    containerColor = CarbonSurface,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    if (currentRoute != ROUTE_COCKPIT) {
                        RailClockBadge()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        navDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = accent,
                                    selectedTextColor = accent,
                                    indicatorColor = container,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                        NavigationRailItem(
                            selected = showAppLibrary,
                            onClick = { showAppLibrary = true },
                            icon = { Icon(Icons.Rounded.Apps, contentDescription = "Bibliothèque d'applications") },
                            label = { Text("Apps", maxLines = 1) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = accent,
                                selectedTextColor = accent,
                                indicatorColor = container,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                            modifier = Modifier.testTag("nav_item_apps"),
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_COCKPIT,
                    enterTransition = { fadeIn(animationSpec = tween(160)) },
                    exitTransition = { fadeOut(animationSpec = tween(120)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    composable(ROUTE_COCKPIT) {
                        CockpitHomeScreen(
                            mediaState = mediaState,
                            speedState = speedState,
                            speedLimitKmh = settings.speedLimitKmh,
                            dockApps = dockApps,
                            getCachedIcon = { viewModel.getCachedAppIcon(it) },
                            loadAppIcon = { viewModel.loadAppIcon(it) },
                            onSelectSpeedLimit = { viewModel.setSpeedLimitKmh(it) },
                            onOpenMedia = { navigateTo(ROUTE_MEDIA) },
                            onTogglePlayPause = { viewModel.audioEngine.togglePlayPause() },
                            onNextTrack = { viewModel.audioEngine.nextTrack() },
                            onPrevTrack = { viewModel.audioEngine.previousTrack() },
                            onSwitchAudioSource = { viewModel.setAudioSource(it) },
                            onRequestLocationPermission = { permissionLauncher.launch(permissionsToRequest) },
                            onOpenAppLibrary = { showAppLibrary = true },
                            onLaunchApp = { viewModel.launchApp(it) },
                            onRemoveDockApp = { viewModel.removeFromDock(it) },
                        )
                    }
                    composable(ROUTE_MEDIA) {
                        MediaScreen(
                            mediaState = mediaState,
                            tracks = tracks,
                            onSelectSource = { viewModel.setAudioSource(it) },
                            onPlayTrack = { viewModel.audioEngine.playTrack(it) },
                            onTogglePlayPause = { viewModel.audioEngine.togglePlayPause() },
                            onNextTrack = { viewModel.audioEngine.nextTrack() },
                            onPrevTrack = { viewModel.audioEngine.previousTrack() },
                            onSeekTo = { viewModel.audioEngine.seekTo(it) },
                            onToggleShuffle = { viewModel.audioEngine.toggleShuffle() },
                            onToggleRepeat = { viewModel.audioEngine.toggleRepeatOne() },
                            onImportFiles = { viewModel.importAudioFiles(it) },
                            onImportFolder = { viewModel.importAudioFolder(it) },
                            onDeleteTrack = { viewModel.deleteTrack(it) },
                            onSetEqEnabled = { viewModel.setEqEnabled(it) },
                            onApplyEqPreset = { viewModel.applyEqPreset(it) },
                            onSetEqBand = { idx, level -> viewModel.setEqBandLevel(idx, level) },
                            onSetVolume = { viewModel.audioEngine.setVolume(it) },
                            onRefreshBluetooth = { viewModel.audioEngine.refreshBluetoothDevices() },
                            onRequestBtPermission = { permissionLauncher.launch(permissionsToRequest) },
                            onOpenNotificationListenerSettings = { viewModel.openNotificationListenerSettings() },
                        )
                    }
                    composable(ROUTE_TRIP) {
                        TripScreen(
                            tripStats = tripStats,
                            speedState = speedState,
                            onResetTrip = { viewModel.resetTrip() },
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            settings = settings,
                            mediaState = mediaState,
                            speedState = speedState,
                            installedApps = installedApps,
                            isDefaultLauncher = isDefaultLauncher,
                            hasOverlayPermission = hasOverlayPermission,
                            isNightFilterActiveNow = isNightFilterActiveNow,
                            coverSyncState = coverSyncState,
                            onAccentColorSelected = { viewModel.setAccentColorId(it) },
                            onNightDimmerChange = { viewModel.setNightDimmerAlpha(it) },
                            onAutoNightModeChange = { viewModel.setAutoNightModeEnabled(it) },
                            onNightScheduleChange = { s, e -> viewModel.setNightSchedule(s, e) },
                            onSetEqEnabled = { viewModel.setEqEnabled(it) },
                            onApplyEqPreset = { viewModel.applyEqPreset(it) },
                            onSetEqBand = { idx, level -> viewModel.setEqBandLevel(idx, level) },
                            onSdvcChange = { viewModel.setSdvcEnabled(it) },
                            onFloatingPillChange = { viewModel.setFloatingPillEnabled(it) },
                            onExportConfig = { uri -> viewModel.exportConfigToUri(uri) },
                            onImportConfig = { uri -> viewModel.importConfigFromUri(uri) },
                            onFetchOnlineCovers = { force -> viewModel.fetchOnlineCoversAndMetadata(force) },
                            onAutoResumeMusicChange = { viewModel.setAutoResumeMusicEnabled(it) },
                            onAutoLaunchPackageChange = { viewModel.setAutoLaunchPackageName(it) },
                            onOpenLauncherSettings = { viewModel.openLauncherSettings() },
                            onOpenOverlayPermissionSettings = { viewModel.openOverlayPermissionSettings() },
                            onOpenNotificationListenerSettings = { viewModel.openNotificationListenerSettings() },
                            onOpenAppLibrary = { showAppLibrary = true },
                            onRequestPermissions = { permissionLauncher.launch(permissionsToRequest) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showAppLibrary,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            AppLibraryOverlay(
                installedApps = installedApps,
                dockPackageNames = settings.dockPackageNames,
                getCachedIcon = { viewModel.getCachedAppIcon(it) },
                loadAppIcon = { viewModel.loadAppIcon(it) },
                onLaunchApp = { viewModel.launchApp(it) },
                onToggleDockApp = { viewModel.toggleDockApp(it) },
                onRefreshApps = { viewModel.refreshInstalledApps() },
                onDismiss = { showAppLibrary = false },
            )
        }
    }
}

private fun currentTimeMinutes(): Int {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}
