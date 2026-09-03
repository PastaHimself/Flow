package io.github.aedev.flow.data.local

internal data class OpmlSubscriptionEntry(
    val channelId: String,
    val channelName: String,
)

/** Lightweight OPML reader for YouTube subscription exports. */
internal object OpmlSubscriptionParser {
    private val outlineRegex =
        Regex(
            pattern = """<outline\b([^>]*)/?>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    private val attributeRegex =
        Regex(
            pattern = """([A-Za-z_:][A-Za-z0-9_:.-]*)\s*=\s*([\"'])(.*?)\2""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    private val youtubeChannelIdRegex = Regex("""UC[0-9A-Za-z_-]{22}""")
    private val decimalEntityRegex = Regex("""&#(\d+);""")
    private val hexEntityRegex = Regex("""&#x([0-9A-Fa-f]+);""")

    fun parse(xml: String): List<OpmlSubscriptionEntry> {
        if (!xml.trimStart().startsWith("<")) return emptyList()

        val seen = LinkedHashSet<String>()
        return outlineRegex
            .findAll(xml)
            .mapNotNull { match ->
                val attributes = parseAttributes(match.groupValues[1])
                val channelId = extractChannelId(attributes) ?: return@mapNotNull null
                if (!seen.add(channelId)) return@mapNotNull null

                val channelName =
                    sequenceOf("title", "text")
                        .mapNotNull(attributes::get)
                        .map(::decodeXml)
                        .map(String::trim)
                        .firstOrNull(String::isNotEmpty)
                        ?: channelId

                OpmlSubscriptionEntry(
                    channelId = channelId,
                    channelName = channelName,
                )
            }.toList()
    }

    private fun parseAttributes(raw: String): Map<String, String> =
        attributeRegex
            .findAll(raw)
            .associate { match ->
                match.groupValues[1].lowercase() to match.groupValues[3]
            }

    private fun extractChannelId(attributes: Map<String, String>): String? {
        val preferredKeys =
            listOf(
                "channelid",
                "channel_id",
                "xmlurl",
                "htmlurl",
                "url",
                "href",
            )
        val candidates =
            buildList {
                preferredKeys.mapNotNullTo(this) { attributes[it] }
                attributes.values.forEach { value ->
                    if (value !in this) add(value)
                }
            }

        return candidates
            .asSequence()
            .map(::decodeXml)
            .mapNotNull { value -> youtubeChannelIdRegex.find(value)?.value }
            .firstOrNull()
    }

    private fun decodeXml(value: String): String =
        value
            .replace(hexEntityRegex) { match -> decodeCodePoint(match.groupValues[1], 16) }
            .replace(decimalEntityRegex) { match -> decodeCodePoint(match.groupValues[1], 10) }
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

    private fun decodeCodePoint(
        value: String,
        radix: Int,
    ): String =
        value
            .toIntOrNull(radix)
            ?.takeIf(Character::isValidCodePoint)
            ?.let { String(Character.toChars(it)) }
            .orEmpty()
}
