package io.github.aedev.flow.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.StandardProtocolFamily
import java.net.URI
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

internal data class DesktopPlaybackState(
    val mediaUrl: String? = null,
    val loadingMediaUrl: String? = null,
    val lastLoadedMediaUrl: String? = null,
    val paused: Boolean = false,
    val error: String? = null,
    val loadGeneration: Long = 0L,
)

internal sealed interface MpvIpcMessage {
    data class CommandResponse(
        val requestId: Long,
        val error: String,
    ) : MpvIpcMessage

    data object FileLoaded : MpvIpcMessage

    data class EndFile(
        val reason: String,
        val detail: String? = null,
    ) : MpvIpcMessage

    data class PauseChanged(
        val paused: Boolean,
    ) : MpvIpcMessage
}

internal data class MpvPlaybackTracker(
    val activeMedia: String? = null,
    val pendingMedia: String? = null,
    val pendingLoads: List<String> = emptyList(),
    val lastLoadedMedia: String? = null,
    val paused: Boolean = false,
    val error: String? = null,
    val loadGeneration: Long = 0L,
)

internal fun reduceMpvPlayback(
    state: MpvPlaybackTracker,
    message: MpvIpcMessage,
): MpvPlaybackTracker =
    when (message) {
        is MpvIpcMessage.CommandResponse -> {
            state
        }

        MpvIpcMessage.FileLoaded -> {
            val media = state.pendingLoads.firstOrNull() ?: state.pendingMedia ?: state.activeMedia
            if (media == null) {
                state
            } else {
                val remainingLoads = state.pendingLoads.drop(1)
                state.copy(
                    activeMedia = media,
                    pendingMedia = remainingLoads.lastOrNull(),
                    pendingLoads = remainingLoads,
                    lastLoadedMedia = media,
                    error = null,
                    loadGeneration = state.loadGeneration + 1,
                )
            }
        }

        is MpvIpcMessage.EndFile -> {
            if (state.pendingMedia != null) {
                if (state.activeMedia != null) {
                    state.copy(activeMedia = null, paused = false, error = null)
                } else {
                    val remainingLoads = state.pendingLoads.drop(1)
                    state.copy(
                        pendingMedia = remainingLoads.lastOrNull(),
                        pendingLoads = remainingLoads,
                        paused = false,
                        error =
                            if (remainingLoads.isEmpty() && message.reason == "error") {
                                message.detail?.takeIf(String::isNotBlank) ?: "mpv failed to load the media."
                            } else {
                                null
                            },
                    )
                }
            } else {
                state.copy(
                    activeMedia = null,
                    pendingMedia = null,
                    pendingLoads = emptyList(),
                    paused = false,
                    error =
                        if (message.reason == "error") {
                            message.detail?.takeIf(String::isNotBlank) ?: "mpv failed to load the media."
                        } else {
                            null
                        },
                )
            }
        }

        is MpvIpcMessage.PauseChanged -> {
            state.copy(paused = message.paused)
        }
    }

internal fun parseMpvIpcMessage(line: String): MpvIpcMessage? {
    val payload = runCatching { mpvJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
    val requestId = payload.long("request_id")
    if (requestId != null) {
        return MpvIpcMessage.CommandResponse(
            requestId = requestId,
            error = payload.string("error") ?: "unknown error",
        )
    }

    return when (payload.string("event")) {
        "file-loaded" -> {
            MpvIpcMessage.FileLoaded
        }

        "end-file" -> {
            MpvIpcMessage.EndFile(
                reason = payload.string("reason") ?: "unknown",
                detail = payload.string("file_error") ?: payload.string("error")?.takeUnless { it == "success" },
            )
        }

        "property-change" -> {
            if (payload.string("name") != "pause") return null
            val paused = payload["data"]?.jsonPrimitive?.booleanOrNull ?: return null
            MpvIpcMessage.PauseChanged(paused)
        }

        else -> {
            null
        }
    }
}

class DesktopMpvPlayer(
    private val mpv: Path? = findExecutable("mpv"),
    private val youtubeResolver: Path? = findExecutable("yt-dlp"),
    val diagnosticsFile: Path = defaultCacheDirectory().resolve("mpv.log"),
) {
    val canPlayLocal: Boolean = mpv != null
    val canPlayYouTube: Boolean = mpv != null && youtubeResolver != null

    val unavailableReason: String? = if (mpv == null) "Install mpv to enable desktop playback." else null
    val youtubeUnavailableReason: String? =
        when {
            mpv == null -> unavailableReason
            youtubeResolver == null -> "Install yt-dlp so mpv can resolve YouTube URLs."
            else -> null
        }

    private val operationLock = Any()
    private val playbackCommandLock = Any()
    private val trackerLock = Any()
    private val requestIds = AtomicLong(0L)
    private val responses = ConcurrentHashMap<Long, CompletableFuture<String>>()
    private var process: Process? = null
    private var socketPath: Path? = null
    private var ipcChannel: SocketChannel? = null
    private var tracker = MpvPlaybackTracker()
    private val mutablePlaybackState = MutableStateFlow(tracker.toPlaybackState())
    internal val playbackState: StateFlow<DesktopPlaybackState> = mutablePlaybackState.asStateFlow()

    val paused: Boolean
        get() = playbackState.value.paused

    private val isRunning: Boolean
        get() = synchronized(operationLock) { process?.isAlive == true }

    fun play(mediaUrl: String) =
        synchronized(playbackCommandLock) {
            check(canPlayLocal) { unavailableReason ?: "mpv is unavailable" }
            if (requiresYouTubeResolver(mediaUrl)) {
                check(canPlayYouTube) { youtubeUnavailableReason ?: "YouTube playback is unavailable" }
            }
            ensureStarted()
            updateTracker {
                it.copy(
                    pendingMedia = mediaUrl,
                    pendingLoads = it.pendingLoads + mediaUrl,
                    paused = false,
                    error = null,
                )
            }
            try {
                sendCommand("loadfile", mediaUrl, "replace")
                sendCommand("set_property", "pause", false)
            } catch (failure: Throwable) {
                failPlayback(mediaUrl, failure.message ?: failure.javaClass.simpleName)
                throw failure
            }
        }

    private fun pause() {
        if (isRunning) sendCommand("set_property", "pause", true)
    }

    private fun resume() {
        if (isRunning) sendCommand("set_property", "pause", false)
    }

    fun togglePause() {
        if (paused) resume() else pause()
    }

    fun seekBy(seconds: Double) {
        if (isRunning) sendCommand("seek", seconds, "relative")
    }

    fun stop() {
        updateTracker {
            it.copy(
                activeMedia = null,
                pendingMedia = null,
                pendingLoads = emptyList(),
                paused = false,
                error = null,
            )
        }
        if (isRunning) sendCommand("stop")
    }

    fun close() {
        synchronized(operationLock) {
            val channel = ipcChannel
            if (process?.isAlive == true && channel?.isOpen == true) {
                runCatching { writeCommand(channel, listOf("quit"), requestId = null) }
            }
            ipcChannel = null
            runCatching { channel?.close() }
            val currentProcess = process
            process = null
            currentProcess?.let(::terminateProcessTree)
            socketPath?.let { runCatching { Files.deleteIfExists(it) } }
            socketPath = null
        }
        failPendingResponses("mpv closed")
        updateTracker {
            MpvPlaybackTracker(
                lastLoadedMedia = it.lastLoadedMedia,
                loadGeneration = it.loadGeneration,
            )
        }
    }

    private fun ensureStarted() {
        val started =
            synchronized(operationLock) {
                if (process?.isAlive == true && ipcChannel?.isOpen == true) return
                cleanupDeadProcessLocked()
                startProcessLocked()
                true
            }
        if (started) {
            try {
                sendCommand("observe_property", PAUSE_OBSERVER_ID, "pause")
            } catch (failure: Throwable) {
                close()
                throw IllegalStateException("Could not initialize mpv IPC: ${failure.message}", failure)
            }
        }
    }

    private fun startProcessLocked() {
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
        val startedProcess =
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
        process = startedProcess
        startedProcess.onExit().thenAccept(::handleProcessExit)

        repeat(IPC_STARTUP_ATTEMPTS) {
            if (Files.exists(socket)) {
                val channel = connectIpc(socket, startedProcess)
                ipcChannel = channel
                startIpcReader(channel)
                return
            }
            if (!startedProcess.isAlive) failStartup("mpv exited before its IPC socket became available")
            Thread.sleep(IPC_STARTUP_DELAY_MILLIS)
        }
        terminateProcessTree(startedProcess)
        failStartup("Timed out waiting for mpv IPC")
    }

    private fun connectIpc(
        socket: Path,
        startedProcess: Process,
    ): SocketChannel {
        var lastFailure: Throwable? = null
        repeat(IPC_CONNECT_ATTEMPTS) {
            if (!startedProcess.isAlive) failStartup("mpv exited before IPC could connect")
            val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
            val failure = runCatching { channel.connect(UnixDomainSocketAddress.of(socket)) }.exceptionOrNull()
            if (failure == null) return channel
            lastFailure = failure
            runCatching { channel.close() }
            Thread.sleep(IPC_CONNECT_DELAY_MILLIS)
        }
        throw IllegalStateException("Could not connect to mpv IPC: ${lastFailure?.message}", lastFailure)
    }

    private fun startIpcReader(channel: SocketChannel) {
        Thread(
            {
                var failure: Throwable? = null
                try {
                    Channels
                        .newInputStream(channel)
                        .bufferedReader(StandardCharsets.UTF_8)
                        .use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                parseMpvIpcMessage(line)?.let(::handleIpcMessage)
                            }
                        }
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    handleIpcReaderClosed(channel, failure)
                }
            },
            "flow-mpv-ipc-reader",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun handleIpcMessage(message: MpvIpcMessage) {
        if (message is MpvIpcMessage.CommandResponse) {
            responses[message.requestId]?.complete(message.error)
            return
        }
        updateTracker { reduceMpvPlayback(it, message) }
    }

    private fun handleIpcReaderClosed(
        channel: SocketChannel,
        failure: Throwable?,
    ) {
        val processStillRunning =
            synchronized(operationLock) {
                if (ipcChannel !== channel) return
                ipcChannel = null
                process?.isAlive == true
            }
        failPendingResponses("mpv IPC connection closed")
        if (processStillRunning && hasPlayback()) {
            failPlayback(
                mediaUrl = null,
                message = failure?.message?.takeIf(String::isNotBlank) ?: "mpv IPC connection closed unexpectedly.",
            )
        }
    }

    private fun handleProcessExit(exitedProcess: Process) {
        val shouldHandle =
            synchronized(operationLock) {
                if (process !== exitedProcess) return
                process = null
                val channel = ipcChannel
                ipcChannel = null
                runCatching { channel?.close() }
                socketPath?.let { runCatching { Files.deleteIfExists(it) } }
                socketPath = null
                true
            }
        if (!shouldHandle) return
        failPendingResponses("mpv exited")
        if (hasPlayback()) {
            val exitCode = runCatching { exitedProcess.exitValue() }.getOrNull()
            failPlayback(
                mediaUrl = null,
                message = if (exitCode == null) "mpv exited unexpectedly." else "mpv exited unexpectedly with code $exitCode.",
            )
        } else {
            updateTracker {
                MpvPlaybackTracker(
                    lastLoadedMedia = it.lastLoadedMedia,
                    loadGeneration = it.loadGeneration,
                )
            }
        }
    }

    private fun sendCommand(vararg arguments: Any) {
        val requestId = requestIds.incrementAndGet()
        val response = CompletableFuture<String>()
        responses[requestId] = response
        try {
            synchronized(operationLock) {
                val channel = ipcChannel?.takeIf(SocketChannel::isOpen) ?: error("mpv IPC is unavailable")
                writeCommand(channel, arguments.toList(), requestId)
            }
            val commandError =
                try {
                    response.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (error: TimeoutException) {
                    throw IllegalStateException("Timed out waiting for mpv command '${arguments.firstOrNull()}'.", error)
                } catch (error: ExecutionException) {
                    val cause = error.cause
                    throw IllegalStateException(cause?.message ?: "mpv IPC command failed.", cause)
                }
            check(commandError == "success") { "mpv command '${arguments.firstOrNull()}' failed: $commandError" }
        } finally {
            responses.remove(requestId, response)
        }
    }

    private fun writeCommand(
        channel: SocketChannel,
        arguments: List<Any>,
        requestId: Long?,
    ) {
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
                requestId?.let { put("request_id", it) }
            }.toString() + "\n"
        val buffer = ByteBuffer.wrap(command.toByteArray(StandardCharsets.UTF_8))
        while (buffer.hasRemaining()) channel.write(buffer)
    }

    private fun updateTracker(transform: (MpvPlaybackTracker) -> MpvPlaybackTracker) {
        synchronized(trackerLock) {
            tracker = transform(tracker)
            mutablePlaybackState.value = tracker.toPlaybackState()
        }
    }

    private fun failPlayback(
        mediaUrl: String?,
        message: String,
    ) {
        updateTracker { current ->
            if (mediaUrl != null && current.pendingMedia != mediaUrl && current.activeMedia != mediaUrl) {
                current
            } else {
                current.copy(
                    activeMedia = null,
                    pendingMedia = null,
                    pendingLoads = emptyList(),
                    paused = false,
                    error = message,
                )
            }
        }
    }

    private fun hasPlayback(): Boolean = synchronized(trackerLock) { tracker.activeMedia != null || tracker.pendingMedia != null }

    private fun failPendingResponses(message: String) {
        val failure = IllegalStateException(message)
        responses.values.forEach { it.completeExceptionally(failure) }
    }

    private fun cleanupDeadProcessLocked() {
        val channel = ipcChannel
        ipcChannel = null
        runCatching { channel?.close() }
        process?.takeIf(Process::isAlive)?.let(::terminateProcessTree)
        process = null
        socketPath?.let { runCatching { Files.deleteIfExists(it) } }
        socketPath = null
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

    private fun requiresYouTubeResolver(mediaUrl: String): Boolean {
        val host = runCatching { URI(mediaUrl).host?.lowercase() }.getOrNull() ?: return false
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    private companion object {
        const val MAX_DIAGNOSTIC_LINES = 12
        const val IPC_STARTUP_ATTEMPTS = 40
        const val IPC_STARTUP_DELAY_MILLIS = 50L
        const val IPC_CONNECT_ATTEMPTS = 20
        const val IPC_CONNECT_DELAY_MILLIS = 25L
        const val COMMAND_TIMEOUT_SECONDS = 3L
        const val PAUSE_OBSERVER_ID = 1L
    }
}

private fun MpvPlaybackTracker.toPlaybackState(): DesktopPlaybackState =
    DesktopPlaybackState(
        mediaUrl = activeMedia,
        loadingMediaUrl = pendingMedia,
        lastLoadedMediaUrl = lastLoadedMedia,
        paused = paused,
        error = error,
        loadGeneration = loadGeneration,
    )

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

private val mpvJson = Json { ignoreUnknownKeys = true }
