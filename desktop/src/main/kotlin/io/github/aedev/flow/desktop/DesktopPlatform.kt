package io.github.aedev.flow.desktop

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

internal fun findExecutable(
    name: String,
    path: String = System.getenv("PATH").orEmpty(),
): Path? =
    path
        .split(File.pathSeparator)
        .asSequence()
        .filter(String::isNotBlank)
        .map { directory -> Path.of(directory, name) }
        .firstOrNull(Files::isExecutable)

internal fun runProcess(
    command: List<String>,
    timeout: Duration = Duration.ofSeconds(45),
): ProcessResult {
    require(command.isNotEmpty())
    val process = ProcessBuilder(command).start()
    val stdout = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().use { it.readText() } }
    val stderr = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().use { it.readText() } }
    if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        throw IllegalStateException("Timed out running ${command.first()}")
    }
    return ProcessResult(
        exitCode = process.exitValue(),
        stdout = stdout.get(2, TimeUnit.SECONDS),
        stderr = stderr.get(2, TimeUnit.SECONDS),
    )
}

internal fun defaultDataDirectory(
    home: String = System.getProperty("user.home"),
    xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
): Path {
    val base = xdgDataHome?.takeIf(String::isNotBlank)?.let(Path::of) ?: Path.of(home, ".local", "share")
    return base.resolve("flow")
}

internal fun defaultCacheDirectory(
    home: String = System.getProperty("user.home"),
    xdgCacheHome: String? = System.getenv("XDG_CACHE_HOME"),
): Path {
    val base = xdgCacheHome?.takeIf(String::isNotBlank)?.let(Path::of) ?: Path.of(home, ".cache")
    return base.resolve("flow")
}

internal fun defaultDownloadDirectory(
    home: String = System.getProperty("user.home"),
    xdgConfigHome: String? = System.getenv("XDG_CONFIG_HOME"),
): Path {
    val configured = System.getenv("XDG_DOWNLOAD_DIR")?.takeIf(String::isNotBlank)?.let(Path::of)
    if (configured != null) return configured
    val configHome = xdgConfigHome?.takeIf(String::isNotBlank)?.let(Path::of) ?: Path.of(home, ".config")
    val userDirs = configHome.resolve("user-dirs.dirs")
    val value =
        runCatching {
            Files
                .readAllLines(userDirs)
                .firstOrNull { it.startsWith("XDG_DOWNLOAD_DIR=") }
                ?.substringAfter('=')
                ?.trim()
                ?.removeSurrounding("\"")
        }.getOrNull()
    if (!value.isNullOrBlank()) {
        val expanded = value.replace("${'$'}HOME", home)
        return Path.of(expanded)
    }
    return Path.of(home, "Downloads")
}

internal fun openExternalUrl(url: String): Result<Unit> =
    openWithDesktopOrXdg(
        target = url,
        action = Desktop.Action.BROWSE,
        desktopAction = { desktop -> desktop.browse(URI(url)) },
    )

internal fun openPath(path: Path): Result<Unit> =
    openWithDesktopOrXdg(
        target = path.toAbsolutePath().toString(),
        action = Desktop.Action.OPEN,
        desktopAction = { desktop -> desktop.open(path.toFile()) },
    )

internal fun copyToClipboard(text: String): Result<Unit> =
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

private fun openWithDesktopOrXdg(
    target: String,
    action: Desktop.Action,
    desktopAction: (Desktop) -> Unit,
): Result<Unit> =
    runCatching {
        val desktopFailure =
            runCatching {
                check(Desktop.isDesktopSupported()) { "Desktop integration is unavailable" }
                val desktop = Desktop.getDesktop()
                check(desktop.isSupported(action)) { "Desktop action $action is unavailable" }
                desktopAction(desktop)
            }.exceptionOrNull()
        if (desktopFailure == null) return@runCatching

        val xdgOpen = findExecutable("xdg-open") ?: throw desktopFailure
        launchXdgOpen(target, xdgOpen).getOrThrow()
    }

internal fun launchXdgOpen(
    target: String,
    xdgOpen: Path,
): Result<Unit> =
    runCatching {
        val process =
            ProcessBuilder(xdgOpen.toString(), target)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        if (process.waitFor(XDG_OPEN_FAILURE_WINDOW_MILLIS, TimeUnit.MILLISECONDS) && process.exitValue() != 0) {
            error("xdg-open failed with exit code ${process.exitValue()}")
        }
    }

private const val XDG_OPEN_FAILURE_WINDOW_MILLIS = 250L
