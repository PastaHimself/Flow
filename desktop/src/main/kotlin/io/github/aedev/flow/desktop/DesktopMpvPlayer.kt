package io.github.aedev.flow.desktop

import io.github.aedev.flow.player.FlowPlayer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.StandardProtocolFamily
import java.net.URI
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class DesktopMpvPlayer(
    private val mpv: Path? = findExecutable("mpv"),
    private val youtubeResolver: Path? = findExecutable("yt-dlp") ?: findExecutable("youtube-dl"),
    val diagnosticsFile: Path = defaultCacheDirectory().resolve("mpv.log"),
) : FlowPlayer {
    val canPlayLocal: Boolean = mpv != null
    val canPlayYouTube: Boolean = mpv != null && youtubeResolver != null

    override val isAvailable: Boolean = canPlayLocal
    override val unavailableReason: String? = if (mpv == null) "Install mpv to enable desktop playback." else null
    val youtubeUnavailableReason: String? =
        when {
            mpv == null -> unavailableReason
            youtubeResolver == null -> "Install yt-dlp (or youtube-dl) so mpv can resolve YouTube URLs."
            else -> null
        }

    private var process: Process? = null
    private var socketPath: Path? = null
    var paused: Boolean = false
        private set
    var currentMedia: String? = null
        private set

    val isRunning: Boolean
        @Synchronized get() = process?.isAlive == true

    @Synchronized
    override fun play(mediaUrl: String) {
        check(canPlayLocal) { unavailableReason ?: "mpv is unavailable" }
        if (requiresYouTubeResolver(mediaUrl)) {
            check(canPlayYouTube) { youtubeUnavailableReason ?: "YouTube playback is unavailable" }
        }
        ensureStarted()
        send("loadfile", mediaUrl, "replace")
        send("set_property", "pause", false)
        currentMedia = mediaUrl
        paused = false
    }

    @Synchronized
    override fun pause() {
        if (process?.isAlive == true) {
            send("set_property", "pause", true)
            paused = true
        }
    }

    @Synchronized
    fun resume() {
        if (process?.isAlive == true) {
            send("set_property", "pause", false)
            paused = false
        }
    }

    @Synchronized
    fun togglePause() {
        if (paused) resume() else pause()
    }

    @Synchronized
    fun seekBy(seconds: Double) {
        if (process?.isAlive == true) send("seek", seconds, "relative")
    }

    @Synchronized
    override fun seekTo(positionMs: Long) {
        if (process?.isAlive == true) send("seek", positionMs.coerceAtLeast(0L) / 1000.0, "absolute")
    }

    @Synchronized
    override fun stop() {
        if (process?.isAlive == true) send("stop")
        paused = false
        currentMedia = null
    }

    @Synchronized
    override fun close() {
        runCatching { if (process?.isAlive == true) send("quit") }
        process?.destroy()
        process = null
        socketPath?.let { runCatching { Files.deleteIfExists(it) } }
        socketPath = null
        paused = false
        currentMedia = null
    }

    private fun ensureStarted() {
        if (process?.isAlive == true && socketPath?.let(Files::exists) == true) return
        close()
        diagnosticsFile.parent?.let(Files::createDirectories)
        Files.writeString(
            diagnosticsFile,
            "",
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        val socket = Files.createTempFile("flow-mpv-", ".sock")
        Files.deleteIfExists(socket)
        socketPath = socket
        process =
            ProcessBuilder(
                mpv.toString(),
                "--idle=yes",
                "--force-window=yes",
                "--input-terminal=no",
                "--terminal=no",
                "--input-ipc-server=$socket",
            ).redirectOutput(ProcessBuilder.Redirect.appendTo(diagnosticsFile.toFile()))
                .redirectError(ProcessBuilder.Redirect.appendTo(diagnosticsFile.toFile()))
                .start()

        repeat(40) {
            if (Files.exists(socket)) return
            if (process?.isAlive != true) failStartup("mpv exited before its IPC socket became available")
            Thread.sleep(50)
        }
        process?.destroyForcibly()
        failStartup("Timed out waiting for mpv IPC")
    }

    private fun failStartup(message: String): Nothing {
        val details =
            runCatching {
                Files
                    .readAllLines(diagnosticsFile)
                    .takeLast(MAX_DIAGNOSTIC_LINES)
                    .joinToString("\n")
                    .trim()
            }.getOrDefault("")
        val suffix = if (details.isBlank()) "See $diagnosticsFile." else "See $diagnosticsFile.\n$details"
        error("$message. $suffix")
    }

    private fun send(vararg arguments: Any) {
        val socket = socketPath ?: error("mpv is not running")
        val command =
            buildJsonObject {
                put(
                    "command",
                    buildJsonArray {
                        arguments.forEach { argument ->
                            when (argument) {
                                is Boolean -> add(JsonPrimitive(argument))
                                is Double -> add(JsonPrimitive(argument))
                                is Number -> add(JsonPrimitive(argument.toLong()))
                                else -> add(JsonPrimitive(argument.toString()))
                            }
                        }
                    },
                )
            }.toString() + "\n"
        val address = UnixDomainSocketAddress.of(socket)
        SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
            channel.connect(address)
            val bytes = command.toByteArray(StandardCharsets.UTF_8)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
        }
    }

    private fun requiresYouTubeResolver(mediaUrl: String): Boolean {
        val host = runCatching { URI(mediaUrl).host?.lowercase() }.getOrNull() ?: return false
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    private companion object {
        const val MAX_DIAGNOSTIC_LINES = 12
    }
}
