package io.github.aedev.flow.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
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
        .firstOrNull { candidate -> Files.isRegularFile(candidate) && Files.isExecutable(candidate) }

internal suspend fun runProcess(
    command: List<String>,
    timeout: Duration = Duration.ofSeconds(45),
): ProcessResult =
    coroutineScope {
        require(command.isNotEmpty())
        val process = ProcessBuilder(command).start()
        val stdout = async(Dispatchers.IO) { process.inputStream.bufferedReader().use { it.readText() } }
        val stderr = async(Dispatchers.IO) { process.errorStream.bufferedReader().use { it.readText() } }
        try {
            val finished =
                runInterruptible(Dispatchers.IO) {
                    process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
                }
            if (!finished) throw IllegalStateException("Timed out running ${command.first()}")
            ProcessResult(
                exitCode = process.exitValue(),
                stdout = stdout.await(),
                stderr = stderr.await(),
            )
        } finally {
            if (process.isAlive) terminateProcessTree(process)
        }
    }

internal fun terminateProcessTree(process: Process) {
    val descendants = process.descendants().toList().asReversed()
    descendants.forEach { child -> runCatching { child.destroy() } }
    runCatching { process.destroy() }
    descendants.filter(ProcessHandle::isAlive).forEach { child -> runCatching { child.destroyForcibly() } }
    if (process.isAlive) runCatching { process.destroyForcibly() }
}

internal fun defaultDataDirectory(
    home: String = System.getProperty("user.home"),
    xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
): Path {
    val base = absoluteXdgPath(xdgDataHome) ?: Path.of(home, ".local", "share")
    return base.resolve("flow")
}

internal fun defaultCacheDirectory(
    home: String = System.getProperty("user.home"),
    xdgCacheHome: String? = System.getenv("XDG_CACHE_HOME"),
): Path {
    val base = absoluteXdgPath(xdgCacheHome) ?: Path.of(home, ".cache")
    return base.resolve("flow")
}

internal fun defaultConfigDirectory(
    home: String = System.getProperty("user.home"),
    xdgConfigHome: String? = System.getenv("XDG_CONFIG_HOME"),
): Path {
    val base = absoluteXdgPath(xdgConfigHome) ?: Path.of(home, ".config")
    return base.resolve("flow")
}

internal fun defaultDownloadDirectory(
    home: String = System.getProperty("user.home"),
    xdgConfigHome: String? = System.getenv("XDG_CONFIG_HOME"),
    xdgDownloadDir: String? = System.getenv("XDG_DOWNLOAD_DIR"),
): Path {
    val configured =
        xdgDownloadDir
            ?.takeIf(String::isNotBlank)
            ?.replace("${'$'}HOME", home)
            ?.let(::absoluteXdgPath)
    if (configured != null) return configured
    val configHome = absoluteXdgPath(xdgConfigHome) ?: Path.of(home, ".config")
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
        absoluteXdgPath(expanded)?.let { return it }
    }
    return Path.of(home, "Downloads")
}

private fun absoluteXdgPath(value: String?): Path? =
    value
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?.takeIf(Path::isAbsolute)

internal suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
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

internal fun prepareDirectoryForOpening(path: Path): Path {
    val target = path.toAbsolutePath().normalize()
    Files.createDirectories(target)
    check(Files.isDirectory(target)) { "Path is not a directory: $target" }
    return target
}

internal fun prepareFileForOpening(path: Path): Path {
    val target = path.toAbsolutePath().normalize()
    target.parent?.let(Files::createDirectories)
    check(Files.notExists(target) || Files.isRegularFile(target)) { "Path is not a regular file: $target" }
    Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { }
    return target
}

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
