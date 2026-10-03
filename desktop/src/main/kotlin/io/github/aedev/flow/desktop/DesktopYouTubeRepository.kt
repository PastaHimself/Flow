package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

class DesktopYouTubeRepository(
    private val resolver: Path? = findExecutable("yt-dlp"),
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val channelFeedFetcher: ((String) -> String)? = null,
) {
    val isAvailable: Boolean = resolver != null
    val unavailableReason: String? = if (resolver == null) "Install yt-dlp to browse YouTube." else null

    suspend fun discover(seed: String? = null): List<Video> = searchVideos(seed?.takeIf(String::isNotBlank) ?: DEFAULT_DISCOVERY_QUERY)

    suspend fun searchVideos(query: String): List<Video> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val executable = resolver ?: error(unavailableReason ?: "yt-dlp is unavailable")
            val result =
                runProcess(
                    listOf(
                        executable.toString(),
                        "--flat-playlist",
                        "--dump-single-json",
                        "--no-warnings",
                        "--ignore-errors",
                        "ytsearch$SEARCH_LIMIT:${query.trim()}",
                    ),
                    timeout = Duration.ofSeconds(60),
                )
            check(result.exitCode == 0) {
                result.stderr.lineSequence().lastOrNull(String::isNotBlank)
                    ?: "yt-dlp search failed with exit code ${result.exitCode}"
            }
            parseVideoList(result.stdout)
        }

    suspend fun channelVideos(
        channelId: String,
        limit: Int = 12,
    ): List<Video> =
        withContext(Dispatchers.IO) {
            if (channelId.isBlank()) return@withContext emptyList()
            val executable = resolver ?: error(unavailableReason ?: "yt-dlp is unavailable")
            val channelUrl =
                if (channelId.startsWith("@")) {
                    "https://www.youtube.com/$channelId/videos"
                } else {
                    "https://www.youtube.com/channel/$channelId/videos"
                }
            var resolverFailure: Throwable? = null
            val videos =
                try {
                    val result =
                        runProcess(
                            listOf(
                                executable.toString(),
                                "--flat-playlist",
                                "--playlist-end",
                                limit.coerceIn(1, 50).toString(),
                                "--dump-single-json",
                                "--no-warnings",
                                "--ignore-errors",
                                channelUrl,
                            ),
                            timeout = Duration.ofSeconds(60),
                        )
                    check(result.exitCode == 0) {
                        result.stderr.lineSequence().lastOrNull(String::isNotBlank) ?: "yt-dlp channel request failed"
                    }
                    parseVideoList(result.stdout)
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    resolverFailure = failure
                    emptyList()
                }
            if (!YOUTUBE_CHANNEL_ID.matches(channelId)) {
                resolverFailure?.let { throw it }
                return@withContext videos
            }
            if (videos.isNotEmpty()) return@withContext videos
            try {
                parseChannelFeed(
                    payload = channelFeedFetcher?.invoke(channelId) ?: fetchChannelFeed(channelId),
                    channelId = channelId,
                    limit = limit,
                )
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                resolverFailure?.let(failure::addSuppressed)
                throw failure
            }
        }

    internal fun parseVideoList(payload: String): List<Video> {
        val root = json.parseToJsonElement(payload).jsonObject
        val entries = root["entries"] as? JsonArray ?: return emptyList()
        return entries.mapNotNull(::toVideo).distinctBy(Video::id)
    }

    internal fun parseChannelFeed(
        payload: String,
        channelId: String,
        limit: Int,
    ): List<Video> {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                setExpandEntityReferences(false)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(payload)))
        val entries = document.getElementsByTagNameNS(ATOM_NAMESPACE, "entry")
        return buildList {
            for (index in 0 until minOf(entries.length, limit.coerceIn(1, 50))) {
                val entry = entries.item(index) as? Element ?: continue
                val id = entry.text(YOUTUBE_NAMESPACE, "videoId")?.takeIf(String::isNotBlank) ?: continue
                val published = entry.text(ATOM_NAMESPACE, "published")
                val timestamp = published?.let { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() } ?: 0L
                val thumbnail = entry.first(MEDIA_NAMESPACE, "thumbnail")?.getAttribute("url").orEmpty()
                val statistics = entry.first(MEDIA_NAMESPACE, "statistics")
                add(
                    Video(
                        id = id,
                        title = entry.text(ATOM_NAMESPACE, "title").orEmpty().ifBlank { "YouTube video" },
                        channelName = entry.first(ATOM_NAMESPACE, "author")?.text(ATOM_NAMESPACE, "name").orEmpty(),
                        channelId = channelId,
                        thumbnailUrl = thumbnail,
                        duration = 0,
                        viewCount = statistics?.getAttribute("views")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                        uploadDate = published?.take(10).orEmpty(),
                        timestamp = timestamp,
                        description = entry.text(MEDIA_NAMESPACE, "description").orEmpty(),
                    ),
                )
            }
        }
    }

    private fun fetchChannelFeed(channelId: String): String {
        val request =
            Request
                .Builder()
                .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
                .build()
        return httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "YouTube channel feed failed with HTTP ${response.code}" }
            response.body.string()
        }
    }

    private fun toVideo(element: JsonElement): Video? {
        val item = element as? JsonObject ?: return null
        val id = item.string("id")?.takeIf(String::isNotBlank) ?: youtubeVideoId(item.string("url").orEmpty()) ?: return null
        val liveStatus = item.string("live_status").orEmpty()
        return Video(
            id = id,
            title = item.string("title").orEmpty().ifBlank { "YouTube video" },
            channelName = item.string("channel") ?: item.string("uploader").orEmpty(),
            channelId = item.string("channel_id") ?: item.string("uploader_id").orEmpty(),
            thumbnailUrl = item.bestThumbnail(),
            duration = item.number("duration")?.toInt()?.coerceAtLeast(0) ?: 0,
            viewCount = item.number("view_count")?.toLong()?.coerceAtLeast(0L) ?: 0L,
            uploadDate = item.string("upload_date")?.let(::formatUploadDate).orEmpty(),
            timestamp = item.videoTimestamp(),
            description = item.string("description").orEmpty(),
            isLive = liveStatus == "is_live" || item.boolean("is_live") == true,
            isShort = item.string("url").orEmpty().contains("/shorts/"),
            isUpcoming = liveStatus == "is_upcoming" || item.boolean("is_upcoming") == true,
        )
    }

    private fun JsonObject.bestThumbnail(): String {
        val thumbnails = this["thumbnails"] as? JsonArray ?: return string("thumbnail").orEmpty()
        return thumbnails
            .mapNotNull { it as? JsonObject }
            .maxByOrNull { thumbnail ->
                (thumbnail.number("width") ?: 0.0) * (thumbnail.number("height") ?: 0.0)
            }?.string("url")
            .orEmpty()
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.takeUnless { it == "NA" || it == "None" }

    private fun JsonObject.number(name: String): Double? =
        this[name]?.jsonPrimitive?.doubleOrNull ?: this[name]?.jsonPrimitive?.longOrNull?.toDouble()

    private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.videoTimestamp(): Long =
        number("timestamp")?.toLong()?.times(MILLIS_PER_SECOND)
            ?: number("release_timestamp")?.toLong()?.times(MILLIS_PER_SECOND)
            ?: string("upload_date")?.let(::parseUploadDateTimestamp)
            ?: 0L

    private fun parseUploadDateTimestamp(value: String): Long? =
        runCatching {
            LocalDate
                .parse(value, YT_DLP_DATE_FORMAT)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()

    private fun formatUploadDate(value: String): String =
        if (value.length == 8 && value.all(Char::isDigit)) {
            "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}"
        } else {
            value
        }

    private companion object {
        const val SEARCH_LIMIT = 24
        const val DEFAULT_DISCOVERY_QUERY = "documentary technology science music gaming"
        const val MILLIS_PER_SECOND = 1_000L
        const val ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
        const val YOUTUBE_NAMESPACE = "http://www.youtube.com/xml/schemas/2015"
        const val MEDIA_NAMESPACE = "http://search.yahoo.com/mrss/"
        val YT_DLP_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        val YOUTUBE_CHANNEL_ID = Regex("^UC[A-Za-z0-9_-]{22}$")
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun Element.first(
    namespace: String,
    localName: String,
): Element? = getElementsByTagNameNS(namespace, localName).item(0) as? Element

private fun Element.text(
    namespace: String,
    localName: String,
): String? = first(namespace, localName)?.textContent?.trim()

internal fun youtubeVideoId(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val path = uri.path.orEmpty()
    if (uri.host?.contains("youtu.be", ignoreCase = true) == true) {
        return path.trim('/').substringBefore('/').takeIf(String::isNotBlank)
    }
    if (path.startsWith("/shorts/")) {
        return path.removePrefix("/shorts/").substringBefore('/').takeIf(String::isNotBlank)
    }
    if (path.startsWith("/live/")) {
        return path.removePrefix("/live/").substringBefore('/').takeIf(String::isNotBlank)
    }
    return uri.rawQuery
        ?.split('&')
        ?.firstOrNull { it.startsWith("v=") }
        ?.substringAfter("v=")
        ?.takeIf(String::isNotBlank)
}
