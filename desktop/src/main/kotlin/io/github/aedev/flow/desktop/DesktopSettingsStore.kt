package io.github.aedev.flow.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
internal data class DesktopSettings(
    val darkTheme: Boolean = true,
    val showHome: Boolean = true,
    val showShorts: Boolean = true,
    val showMusic: Boolean = true,
    val showSearch: Boolean = false,
    val showExplore: Boolean = false,
)

internal class DesktopSettingsStore(
    val configDirectory: Path = defaultConfigDirectory(),
) {
    val file: Path = configDirectory.resolve("settings.json")
    var loadError: Throwable? = null
        private set
    private val json = Json { prettyPrint = true }

    @Synchronized
    fun load(): DesktopSettings {
        if (!Files.isRegularFile(file)) {
            loadError = null
            return DesktopSettings()
        }
        return runCatching { json.decodeFromString<DesktopSettings>(Files.readString(file)) }
            .onSuccess { loadError = null }
            .onFailure { loadError = it }
            .getOrDefault(DesktopSettings())
    }

    @Synchronized
    fun save(settings: DesktopSettings) {
        check(loadError == null) {
            "Existing settings could not be read; refusing to overwrite $file. Repair or remove that file first."
        }
        Files.createDirectories(configDirectory)
        val temporary = Files.createTempFile(configDirectory, "settings-", ".tmp")
        try {
            Files.writeString(temporary, json.encodeToString(settings))
            runCatching {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
