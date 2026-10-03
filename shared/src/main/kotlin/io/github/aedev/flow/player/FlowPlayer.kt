package io.github.aedev.flow.player

/** Platform-neutral playback boundary. Android keeps Media3; desktop implements this with mpv. */
interface FlowPlayer : AutoCloseable {
    val isAvailable: Boolean
    val unavailableReason: String?

    fun play(mediaUrl: String)

    fun pause()

    fun seekTo(positionMs: Long)

    fun stop()

    override fun close()
}
