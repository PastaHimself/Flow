package io.github.aedev.flow.data.local

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal data class OpmlSubscriptionEntry(
    val channelId: String,
    val channelName: String,
)

/** XML-backed OPML reader for YouTube subscription exports. */
internal object OpmlSubscriptionParser {
    private val youtubeChannelIdRegex = Regex("""UC[0-9A-Za-z_-]{22}""")

    fun parse(xml: String): List<OpmlSubscriptionEntry> {
        if (!xml.trimStart().startsWith("<")) return emptyList()

        val document =
            runCatching {
                val factory = DocumentBuilderFactory.newInstance()
                runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { factory.isXIncludeAware = false }
                runCatching { factory.isExpandEntityReferences = false }

                factory
                    .newDocumentBuilder()
                    .apply {
                        setEntityResolver { _, _ -> InputSource(StringReader("")) }
                    }.parse(InputSource(StringReader(xml)))
            }.getOrNull() ?: return emptyList()

        val seen = LinkedHashSet<String>()
        val entries = mutableListOf<OpmlSubscriptionEntry>()

        fun visit(node: Node) {
            if (node is Element && node.tagName.equals("outline", ignoreCase = true)) {
                val attributes = node.attributes.toAttributeMap()
                val channelId = extractChannelId(attributes)
                if (channelId != null && seen.add(channelId)) {
                    val channelName =
                        sequenceOf("title", "text")
                            .mapNotNull(attributes::get)
                            .map(String::trim)
                            .firstOrNull(String::isNotEmpty)
                            ?: channelId
                    entries += OpmlSubscriptionEntry(channelId = channelId, channelName = channelName)
                }
            }

            val children = node.childNodes
            for (index in 0 until children.length) {
                visit(children.item(index))
            }
        }

        document.documentElement?.let(::visit)
        return entries
    }

    private fun org.w3c.dom.NamedNodeMap.toAttributeMap(): Map<String, String> =
        buildMap {
            for (index in 0 until length) {
                val attribute = item(index)
                put(attribute.nodeName.lowercase(), attribute.nodeValue.orEmpty())
            }
        }

    private fun extractChannelId(attributes: Map<String, String>): String? {
        sequenceOf("channelid", "channel_id")
            .mapNotNull(attributes::get)
            .map(String::trim)
            .firstOrNull(youtubeChannelIdRegex::matches)
            ?.let { return it }

        return sequenceOf("xmlurl", "htmlurl", "url", "href")
            .mapNotNull(attributes::get)
            .mapNotNull(::extractChannelIdFromYouTubeUrl)
            .firstOrNull()
    }

    private fun extractChannelIdFromYouTubeUrl(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null

        val host = uri.host?.lowercase() ?: return null
        if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null

        val path = uri.path.orEmpty()
        if (path.equals("/feeds/videos.xml", ignoreCase = true)) {
            return uri.rawQuery
                .orEmpty()
                .split("&")
                .asSequence()
                .mapNotNull { part ->
                    val key = part.substringBefore("=", missingDelimiterValue = part)
                    if (!key.equals("channel_id", ignoreCase = true)) return@mapNotNull null
                    URLDecoder.decode(part.substringAfter("=", ""), Charsets.UTF_8.name())
                }.map(String::trim)
                .firstOrNull(youtubeChannelIdRegex::matches)
        }

        val segments = path.split('/').filter(String::isNotBlank)
        if (segments.size >= 2 && segments[0].equals("channel", ignoreCase = true)) {
            return segments[1].trim().takeIf(youtubeChannelIdRegex::matches)
        }

        return null
    }
}
