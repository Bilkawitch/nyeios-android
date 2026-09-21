package ru.nya.nyeios.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import ru.nya.nyeios.data.model.FeedAttachment
import ru.nya.nyeios.data.model.FeedPost

object FeedParser {

    private const val BASE_URL = "https://eios.gukolomna.ru"

    fun parse(html: String, maxPosts: Int = 30): List<FeedPost> {
        if (html.isBlank()) return emptyList()

        val doc = Jsoup.parse(html)
        val postElements = doc.select("div.feed-post-block")
        val result = mutableListOf<FeedPost>()

        for ((index, postEl) in postElements.withIndex()) {
            if (index >= maxPosts) break

            val postId = postEl.id().ifEmpty { "post-$index" }

            // Author name
            val authorEl = postEl.selectFirst(".feed-post-user-name, .user-name, .author")
            val authorName = authorEl?.text()?.trim() ?: "Преподаватель"

            // Author avatar URL
            val avatarEl = postEl.selectFirst(".feed-user-avatar")
            var avatarUrl = ""
            val styleAttr = avatarEl?.attr("style").orEmpty()
            if (styleAttr.contains("url(")) {
                val match = Regex("""url\(['"]?([^'"\)]+)['"]?\)""").find(styleAttr)
                if (match != null) {
                    val rawUrl = match.groupValues[1]
                    avatarUrl = if (rawUrl.startsWith("/")) "$BASE_URL$rawUrl" else rawUrl
                }
            }

            // Destination / target group e.g. "23ан-о-41"
            val destEl = postEl.selectFirst(".feed-add-post-destination-new, .destination")
            var destination = destEl?.text()?.trim().orEmpty()
            if (destination.startsWith("->")) {
                destination = destination.removePrefix("->").trim()
            }

            // Timestamp e.g. "сегодня, 11:52", "09.09 10:01"
            val timeEl = postEl.selectFirst(".feed-time, .feed-post-time")
            val postTime = timeEl?.text()?.trim().orEmpty()

            // Post content
            val textEl = postEl.selectFirst(".feed-post-text, .feed-post-contentview, .feed-post-text-block-inner")
            var textHtml = ""
            var textClean = ""
            if (textEl != null) {
                // Remove scripts, styles and iframes
                textEl.select("script, style, iframe").remove()
                textHtml = textEl.html()

                // Clone for clean text extraction
                val clone = textEl.clone()
                clone.select("br").append("\\n")
                clone.select("p").prepend("\\n")
                textClean = clone.text()
                    .replace("\\n", "\n")
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n\n")
            }

            // Attachments
            val attachments = mutableListOf<FeedAttachment>()
            val attachLinks = postEl.select("a[href]")
            val attachPattern = Regex("""historyget|\.pdf|\.docx|\.doc|\.xlsx|\.xls|\.zip|\.rar|\.ppt|\.pptx""", RegexOption.IGNORE_CASE)

            for (a in attachLinks) {
                val href = a.attr("href").trim()
                if (attachPattern.containsMatchIn(href)) {
                    var fullUrl = href
                    if (fullUrl.startsWith("/")) {
                        fullUrl = "$BASE_URL$fullUrl"
                    }
                    var fileName = a.text().trim()
                    if (fileName.isEmpty() || fileName == "Скачать") {
                        fileName = a.attr("title").trim()
                    }
                    if (fileName.isEmpty()) {
                        fileName = href.substringAfterLast("/")
                    }
                    if (fileName.isEmpty()) {
                        fileName = "Вложение"
                    }

                    // Avoid duplicate URLs
                    if (attachments.none { it.url == fullUrl }) {
                        attachments.add(FeedAttachment(name = fileName, url = fullUrl))
                    }
                }
            }

            result.add(
                FeedPost(
                    id = postId,
                    authorName = authorName,
                    authorAvatar = avatarUrl,
                    destination = destination,
                    postTime = postTime,
                    textHtml = textHtml,
                    textClean = textClean,
                    attachments = attachments
                )
            )
        }

        return result
    }
}
