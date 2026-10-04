package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class DesktopResolvedMedia(
    val title: String,
    val playbackUrl: String,
    val playbackAudioUrl: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val progressiveUrl: String? = null,
    val downloadUrl: String? = null,
    val video: Video? = null,
)

internal fun interface DesktopYouTubeMediaResolver {
    suspend fun resolve(url: String): DesktopResolvedMedia
}

internal class NewPipeYouTubeMediaResolver(
    private val downloader: Downloader = DesktopNewPipeDownloader(),
) : DesktopYouTubeMediaResolver {
    override suspend fun resolve(url: String): DesktopResolvedMedia =
        newPipeMutex.withLock {
            runInterruptible(Dispatchers.IO) {
                NewPipe.init(downloader)
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                val progressiveMp4 =
                    info.videoStreams
                        .asSequence()
                        .filter { it.isUrl && !it.content.isNullOrBlank() && it.format == MediaFormat.MPEG_4 }
                        .preferredVideoStream()
                val progressiveFallback =
                    info.videoStreams
                        .asSequence()
                        .filter { it.isUrl && !it.content.isNullOrBlank() }
                        .preferredVideoStream()
                val videoMp4 =
                    info.videoOnlyStreams
                        .asSequence()
                        .filter { it.isUrl && !it.content.isNullOrBlank() && it.format == MediaFormat.MPEG_4 }
                        .preferredVideoStream()
                val audioM4a =
                    info.audioStreams
                        .asSequence()
                        .filter { it.isUrl && !it.content.isNullOrBlank() && it.format == MediaFormat.M4A }
                        .maxByOrNull { it.averageBitrate }
                val hlsUrl = info.hlsUrl?.takeIf(String::isNotBlank)
                val adaptivePlayback =
                    videoMp4?.takeIf {
                        hlsUrl == null && progressiveMp4 == null && progressiveFallback == null && audioM4a != null
                    }
                val playbackUrl = hlsUrl ?: progressiveMp4?.content ?: progressiveFallback?.content ?: adaptivePlayback?.content
                check(playbackUrl != null) { "YouTube returned no playable stream for ${info.name ?: url}." }
                val compatibleSeparateStreams = videoMp4 != null && audioM4a != null
                DesktopResolvedMedia(
                    title = info.name?.takeIf(String::isNotBlank) ?: "YouTube video",
                    playbackUrl = playbackUrl,
                    playbackAudioUrl = audioM4a?.content.takeIf { adaptivePlayback != null },
                    videoUrl = videoMp4?.content.takeIf { compatibleSeparateStreams },
                    audioUrl = audioM4a?.content.takeIf { compatibleSeparateStreams },
                    progressiveUrl = progressiveMp4?.content,
                    downloadUrl = progressiveMp4?.content ?: hlsUrl,
                    video = info.toVideo(url),
                )
            }
        }

    private companion object {
        val newPipeMutex = Mutex()
    }
}

private fun Sequence<org.schabi.newpipe.extractor.stream.VideoStream>.preferredVideoStream():
    org.schabi.newpipe.extractor.stream.VideoStream? {
    val streams = toList()
    return streams
        .filter { it.height in 1..MAX_DOWNLOAD_HEIGHT }
        .maxByOrNull { it.height }
        ?: streams.filter { it.height > 0 }.minByOrNull { it.height }
        ?: streams.firstOrNull()
}

private const val MAX_DOWNLOAD_HEIGHT = 1080

private fun StreamInfo.toVideo(url: String): Video? {
    val id = youtubeVideoId(url) ?: return null
    val uploaderUrl = uploaderUrl.orEmpty()
    val channelId =
        when {
            "/channel/" in uploaderUrl -> uploaderUrl.substringAfter("/channel/").substringBefore('/')
            "/@" in uploaderUrl -> "@${uploaderUrl.substringAfter("/@").substringBefore('/')}"
            else -> ""
        }
    val thumbnail = thumbnails.maxByOrNull { image -> image.width.toLong() * image.height.toLong() }?.url.orEmpty()
    val avatar = uploaderAvatars.maxByOrNull { image -> image.width.toLong() * image.height.toLong() }?.url.orEmpty()
    return Video(
        id = id,
        title = name?.takeIf(String::isNotBlank) ?: "YouTube video",
        channelName = uploaderName.orEmpty(),
        channelId = channelId,
        thumbnailUrl = thumbnail,
        duration = duration.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        viewCount = viewCount.coerceAtLeast(0L),
        likeCount = likeCount.coerceAtLeast(0L),
        uploadDate = textualUploadDate.orEmpty(),
        description = description?.content.orEmpty(),
        channelThumbnailUrl = avatar,
        tags = tags.orEmpty(),
        isLive = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
        isShort = isShortFormContent,
        isVerifiedChannel = isUploaderVerified,
    )
}

private class DesktopNewPipeDownloader(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build(),
) : Downloader() {
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val method = request.httpMethod().uppercase()
        val body =
            request.dataToSend()?.toRequestBody()
                ?: if (method in BODY_REQUIRED_METHODS) ByteArray(0).toRequestBody() else null
        val builder =
            okhttp3.Request
                .Builder()
                .url(request.url())
                .method(method, body)
                .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { value -> builder.addHeader(name, value) }
        }
        return client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("YouTube requested a CAPTCHA challenge", request.url())
            }
            Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }

    private companion object {
        val BODY_REQUIRED_METHODS = setOf("POST", "PUT", "PATCH")
        const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
