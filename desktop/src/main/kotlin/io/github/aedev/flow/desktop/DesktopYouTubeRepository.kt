package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

class DesktopYouTubeRepository {
    init {
        ensureNewPipeInitialized()
    }

    suspend fun trending(): List<Video> =
        withContext(Dispatchers.IO) {
            val service = ServiceList.YouTube
            val extractor = service.kioskList.defaultKioskExtractor
            KioskInfo.getInfo(extractor).relatedItems.mapNotNull(::toVideo)
        }

    suspend fun searchVideos(query: String): List<Video> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val service = ServiceList.YouTube
            val handler = service.searchQHFactory.fromQuery(query.trim())
            SearchInfo
                .getInfo(service, handler)
                .relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull(::toVideo)
        }

    private fun toVideo(item: StreamInfoItem): Video? {
        val id = youtubeVideoId(item.url) ?: return null
        val streamType = item.streamType
        return Video(
            id = id,
            title = item.name,
            channelName = item.uploaderName.orEmpty(),
            channelId = channelId(item.uploaderUrl.orEmpty()),
            thumbnailUrl = item.thumbnails.bestUrl(),
            duration =
                item.duration
                    .coerceAtLeast(0L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt(),
            viewCount = item.viewCount.coerceAtLeast(0L),
            uploadDate = item.textualUploadDate.orEmpty(),
            description = item.shortDescription.orEmpty(),
            channelThumbnailUrl = item.uploaderAvatars.bestUrl(),
            isLive = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
            isShort = item.isShortFormContent,
            isUpcoming = streamType == StreamType.NONE,
            isVerifiedChannel = item.isUploaderVerified,
        )
    }

    private fun List<org.schabi.newpipe.extractor.Image>.bestUrl(): String =
        maxByOrNull { image -> image.width.coerceAtLeast(0) * image.height.coerceAtLeast(0) }?.url.orEmpty()

    private fun channelId(url: String): String =
        runCatching {
            val path = URI(url).path.orEmpty()
            if (path.startsWith("/channel/")) path.removePrefix("/channel/").substringBefore('/') else ""
        }.getOrDefault("")

    companion object {
        private val initLock = Any()

        @Volatile
        private var initialized = false

        private fun ensureNewPipeInitialized() {
            if (initialized) return
            synchronized(initLock) {
                if (!initialized) {
                    NewPipe.init(DesktopNewPipeDownloader())
                    initialized = true
                }
            }
        }
    }
}

internal fun youtubeVideoId(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val path = uri.path.orEmpty()
    if (uri.host?.contains("youtu.be", ignoreCase = true) == true) {
        return path
            .trim('/')
            .substringBefore('/')
            .takeIf(String::isNotBlank)
    }
    if (path.startsWith("/shorts/")) {
        return path
            .removePrefix("/shorts/")
            .substringBefore('/')
            .takeIf(String::isNotBlank)
    }
    if (path.startsWith("/live/")) {
        return path
            .removePrefix("/live/")
            .substringBefore('/')
            .takeIf(String::isNotBlank)
    }
    return uri.rawQuery
        ?.split('&')
        ?.firstOrNull { it.startsWith("v=") }
        ?.substringAfter("v=")
        ?.takeIf(String::isNotBlank)
}

private class DesktopNewPipeDownloader : Downloader() {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val body = request.dataToSend()?.toRequestBody()
        val builder =
            okhttp3.Request
                .Builder()
                .url(request.url())
                .header("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { value -> builder.addHeader(name, value) }
        }

        when (method) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            else -> builder.method(method, body ?: ByteArray(0).toRequestBody())
        }

        return client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
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
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/154.0.0.0 Safari/537.36"
    }
}
