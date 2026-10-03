package io.github.aedev.flow.desktop

import io.github.aedev.flow.player.FlowPlayer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class DesktopMpvPlayer : FlowPlayer {
    private val mpv = findExecutable("mpv")
    private val youtubeResolver = findExecutable("yt-dlp") ?: findExecutable("youtube-dl")

    override val isAvailable: Boolean = mpv != null && youtubeResolver != null
    override val unavailableReason: String? =
        when {
            mpv == null -> "Install mpv to enable desktop playback."
            youtubeResolver == null -> "Install yt-dlp (or youtube-dl) so mpv can resolve YouTube URLs."
            else -> null
        }

    private var process: Process? = null
    private var socketPath: Path? = null

    @Synchronized
    override fun play(mediaUrl: String) {
        check(isAvailable) { unavailableReason ?: "mpv is unavailable" }
        ensureStarted()
        send("loadfile", mediaUrl, "replace")
        send("set_property", "pause", false)
    }

    @Synchronized
    override fun pause() {
        if (process?.isAlive == true) send("set_property", "pause", true)
    }

    @Synchronized
    override fun seekTo(positionMs: Long) {
        if (process?.isAlive == true) send("seek", positionMs.coerceAtLeast(0L) / 1000.0, "absolute")
    }

    @Synchronized
    override fun stop() {
        if (process?.isAlive == true) send("stop")
    }

    @Synchronized
    override fun close() {
        runCatching { if (process?.isAlive == true) send("quit") }
        process?.destroy()
        process = null
        socketPath?.let { runCatching { Files.deleteIfExists(it) } }
        socketPath = null
    }

    private fun ensureStarted() {
        if (process?.isAlive == true && socketPath?.let(Files::exists) == true) return
        close()
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
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

        repeat(40) {
            if (Files.exists(socket)) return
            if (process?.isAlive != true) error("mpv exited before its IPC socket became available")
            Thread.sleep(50)
        }
        error("Timed out waiting for mpv IPC")
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

    private fun findExecutable(name: String): Path? =
        System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .asSequence()
            .filter(String::isNotBlank)
            .map { directory -> Path.of(directory, name) }
            .firstOrNull(Files::isExecutable)
}
