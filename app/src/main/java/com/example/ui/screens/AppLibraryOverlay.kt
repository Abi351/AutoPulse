package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MAX_DOCK_APPS
import com.example.hardware.InstalledAppInfo
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AppLibraryOverlay(
    installedApps: List<InstalledAppInfo>,
    dockPackageNames: List<String>,
    getCachedIcon: (String) -> ImageBitmap?,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    onLaunchApp: (String) -> Unit,
    onToggleDockApp: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val dockSet = remember(dockPackageNames) { dockPackageNames.toHashSet() }
    val filteredApps = remember(installedApps, query) {
        if (query.isBlank()) installedApps
        else installedApps.filter { it.label.contains(query, ignoreCase = true) }
    }

    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    BackHandler(onBack = onDismiss)

    // Overlay in-tree léger et responsive (1280×720 à 1920×1080)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val isCompactHeight = maxHeight < 460.dp
        val tileMinWidth = if (maxWidth >= 1000.dp) 160.dp else 136.dp
        val overlayPad = if (isCompactHeight) 14.dp else 20.dp

        Surface(
            color = CarbonSurface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .border(1.dp, CarbonBorder, RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(overlayPad),
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 12.dp),
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
                        Icon(Icons.Rounded.Apps, contentDescription = null, tint = primary, modifier = Modifier.size(26.dp))
                        Column {
                            Text(
                                text = "Bibliothèque d'applications (${installedApps.size})",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Touchez pour ouvrir ou épinglez jusqu'à $MAX_DOCK_APPS apps sur le dock (${dockPackageNames.size}/$MAX_DOCK_APPS)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onRefreshApps,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("app_library_refresh"),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser", tint = accent)
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(CarbonSurfaceElevated)
                                .testTag("app_library_close"),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = TextPrimary)
                        }
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Rechercher une application...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_library_search"),
                )

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Aucune application trouvée.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = tileMinWidth),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(
                            items = filteredApps,
                            key = { it.packageName },
                            contentType = { "app_tile" },
                        ) { app ->
                            val isPinned = app.packageName in dockSet
                            AppLibraryTile(
                                app = app,
                                isPinned = isPinned,
                                getCachedIcon = getCachedIcon,
                                loadAppIcon = loadAppIcon,
                                onLaunch = {
                                    onLaunchApp(app.packageName)
                                    onDismiss()
                                },
                                onToggleDock = { onToggleDockApp(app.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLibraryTile(
    app: InstalledAppInfo,
    isPinned: Boolean,
    getCachedIcon: (String) -> ImageBitmap?,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    onLaunch: () -> Unit,
    onToggleDock: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val container = MaterialTheme.colorScheme.primaryContainer

    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = getCachedIcon(app.packageName),
        key1 = app.packageName,
    ) {
        if (value == null) {
            value = loadAppIcon(app.packageName)
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CarbonSurfaceElevated)
            .border(
                1.dp,
                if (isPinned) primary.copy(alpha = 0.78f) else CarbonBorder,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onLaunch)
            .padding(10.dp)
            .testTag("app_tile_${app.packageName}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val bmp = iconBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = app.label,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Apps, contentDescription = app.label, tint = accent, modifier = Modifier.size(28.dp))
            }
        }

        Text(
            text = app.label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isPinned) container else CarbonSurface)
                .border(
                    1.dp,
                    if (isPinned) primary else CarbonBorder,
                    RoundedCornerShape(10.dp),
                )
                .clickable(onClick = onToggleDock)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = if (isPinned) "Retirer du dock" else "Ajouter au dock",
                tint = if (isPinned) EmeraldOk else AmberWarn,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (isPinned) "Sur le dock" else "+ Dock",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = if (isPinned) accent else TextSecondary,
                maxLines = 1,
            )
        }
    }
}
