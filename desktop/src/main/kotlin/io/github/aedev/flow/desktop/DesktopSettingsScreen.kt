package io.github.aedev.flow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.nio.file.Path

@Composable
internal fun SettingsScreen(
    player: DesktopMpvPlayer,
    repository: DesktopYouTubeRepository,
    downloader: DesktopDownloader,
    libraryStore: DesktopLibraryStore,
    settings: DesktopSettings,
    onSettingsChange: (DesktopSettings) -> Result<Unit>,
) {
    var actionError by remember { mutableStateOf<String?>(null) }

    fun open(
        path: Path,
        prepare: (Path) -> Path,
    ) {
        runCatching { prepare(path) }
            .mapCatching { preparedPath -> openPath(preparedPath).getOrThrow() }
            .onSuccess { actionError = null }
            .onFailure { actionError = it.message ?: it.javaClass.simpleName }
    }

    fun update(updated: DesktopSettings) {
        onSettingsChange(updated)
            .onSuccess { actionError = null }
            .onFailure { actionError = "Could not save settings: ${it.message ?: it.javaClass.simpleName}" }
    }

    ScreenColumn(title = "Settings", subtitle = "Linux desktop") {
        LazyColumn {
            actionError?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
            item {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                SettingToggleRow("Dark theme", settings.darkTheme) { update(settings.copy(darkTheme = it)) }
                Spacer(Modifier.height(12.dp))
            }
            item {
                Text("Navigation", style = MaterialTheme.typography.titleMedium)
                SettingToggleRow("Home", settings.showHome) { update(settings.copy(showHome = it)) }
                SettingToggleRow("Shorts", settings.showShorts) { update(settings.copy(showShorts = it)) }
                SettingToggleRow("Music", settings.showMusic) { update(settings.copy(showMusic = it)) }
                SettingToggleRow("Search tab", settings.showSearch) { update(settings.copy(showSearch = it)) }
                SettingToggleRow("Explore tab", settings.showExplore) { update(settings.copy(showExplore = it)) }
                Text(
                    "Subscriptions and Library are always available, matching Flow's navigation policy.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingCard(
                    title = "Playback",
                    body =
                        when {
                            !player.canPlayLocal -> {
                                player.unavailableReason.orEmpty()
                            }

                            player.canPlayYouTube -> {
                                "mpv detected. YouTube streams use Flow's built-in extractor and open in an mpv window. Logs: ${player.diagnosticsFile}"
                            }

                            else -> {
                                "mpv detected for local playback. ${player.youtubeUnavailableReason.orEmpty()} Logs: ${player.diagnosticsFile}"
                            }
                        },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { open(downloader.directory, ::prepareDirectoryForOpening) }) { Text("Open downloads") }
                    Button(onClick = { open(libraryStore.dataDirectory, ::prepareDirectoryForOpening) }) { Text("Open app data") }
                    Button(onClick = { open(player.diagnosticsFile, ::prepareFileForOpening) }) { Text("Open playback log") }
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingCard(
                    title = "YouTube access",
                    body =
                        if (repository.isAvailable) {
                            "yt-dlp detected for search and discovery. Playback and downloads use Flow's built-in extractor."
                        } else {
                            "Search and discovery are unavailable without yt-dlp. Direct YouTube links, local playback and local library features remain available."
                        },
                )
            }
            item {
                SettingCard(
                    title = "Downloads",
                    body = if (downloader.isAvailable) "Saved to ${downloader.directory}" else downloader.unavailableReason.orEmpty(),
                )
            }
            item {
                SettingCard(
                    title = "Local data",
                    body = "Library, history, subscriptions, playlists and search history: ${libraryStore.dataDirectory}",
                )
            }
            item {
                SettingCard(
                    title = "Privacy",
                    body = "Desktop recommendations are seeded from your local library. No Flow account or telemetry service is used.",
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
