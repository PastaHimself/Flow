package io.github.aedev.flow.desktop

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal enum class DesktopDestination(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    SHORTS("Shorts", Icons.Default.SmartDisplay),
    MUSIC("Music", Icons.Default.MusicNote),
    SUBSCRIPTIONS("Subscriptions", Icons.Default.Subscriptions),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    SEARCH("Search", Icons.Default.Search),
    EXPLORE("Explore", Icons.Default.Explore),
    DOWNLOADS("Downloads", Icons.Default.SmartDisplay),
    SETTINGS("Settings", Icons.Default.Settings),
}

internal fun DesktopSettings.visibleRootDestinations(): List<DesktopDestination> =
    buildList {
        if (showHome) add(DesktopDestination.HOME)
        if (showShorts) add(DesktopDestination.SHORTS)
        if (showMusic) add(DesktopDestination.MUSIC)
        add(DesktopDestination.SUBSCRIPTIONS)
        add(DesktopDestination.LIBRARY)
        if (showSearch) add(DesktopDestination.SEARCH)
        if (showExplore) add(DesktopDestination.EXPLORE)
    }

@Composable
internal fun DesktopNavigationRail(
    destination: DesktopDestination,
    settings: DesktopSettings,
    onNavigate: (DesktopDestination) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        header = {
            Text(
                text = "Flow",
                modifier = Modifier.padding(vertical = 18.dp),
            )
        },
    ) {
        settings.visibleRootDestinations().forEach { item ->
            NavigationRailItem(
                selected = destination == item,
                onClick = { onNavigate(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
internal fun DesktopGlobalActions(
    destination: DesktopDestination,
    settings: DesktopSettings,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) {
            Icon(Icons.Default.Search, contentDescription = "Search")
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}
