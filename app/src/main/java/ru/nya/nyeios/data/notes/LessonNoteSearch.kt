package ru.nya.nyeios.data.notes

import java.util.Locale

/**
 * Small in-memory search index for lesson notes. Notes are persisted as JSON and there are
 * usually only dozens to low hundreds of them, so a prebuilt token index plus an off-main
 * bounded fuzzy fallback is cheaper than migrating the whole repository to Room/FTS.
 */
class NoteSearchIndex internal constructor(
    private val documents: List<NoteSearchDocument>
) {
    private val tokenIndex: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        documents.forEach { document ->
            document.tokens.forEach { token ->
                getOrPut(token) { linkedSetOf() }.add(document.key)
            }
        }
    }
    private val uniqueTokens = tokenIndex.keys.toList()

    fun search(query: String, tagSlug: String?): Set<String> {
        val normalizedTag = normalizeTag(tagSlug)
        val queryTokens = tokenize(query)
        val candidates = documents.asSequence()
            .filter { normalizedTag == null || it.tagSlug == normalizedTag }

        if (queryTokens.isEmpty()) {
            return candidates.mapTo(linkedSetOf()) { it.key }
        }

        val terms = queryTokens.map { token ->
            matchingKeys(token)
        }
        if (terms.any { it.isEmpty() }) return emptySet()
        val matches = terms.drop(1).fold(terms.first().toMutableSet()) { acc, keys ->
            acc.apply { retainAll(keys) }
        }
        return matches.intersect(candidates.map { it.key }.toSet())
    }

    private fun matchingKeys(token: String): Set<String> {
        val exact = tokenIndex[token].orEmpty()
        if (exact.isNotEmpty()) return exact

        if (token.length >= 2) {
            val prefix = linkedSetOf<String>()
            uniqueTokens.forEach { candidate ->
                if (candidate.startsWith(token) && candidate != token) {
                    prefix.addAll(tokenIndex[candidate].orEmpty())
                }
            }
            if (prefix.isNotEmpty()) return prefix
        }

        val maxDistance = when {
            token.length >= 8 -> 2
            token.length >= 4 -> 1
            else -> 0
        }
        if (maxDistance == 0) return emptySet()

        val fuzzy = linkedSetOf<String>()
        uniqueTokens.forEach { candidate ->
            if (kotlin.math.abs(candidate.length - token.length) <= maxDistance &&
                damerauWithin(token, candidate, maxDistance)
            ) {
                fuzzy.addAll(tokenIndex[candidate].orEmpty())
            }
        }
        return fuzzy
    }

}

internal data class NoteSearchDocument(
    val key: String,
    val tagSlug: String?,
    val tokens: Set<String>
)

object LessonNoteSearch {

    fun build(notes: Collection<LessonNote>): NoteSearchIndex {
        val documents = notes.asSequence()
            .filter { it.hasVisibleContent }
            .map { note ->
                val captions = note.photoAttachments.orEmpty()
                    .joinToString(" ") { it.caption }
                val plain = stripMarkup(note.rawText)
                NoteSearchDocument(
                    key = note.key,
                    tagSlug = tagSlug(note),
                    tokens = tokenize("$plain $captions").toSet()
                )
            }
            .toList()
        return NoteSearchIndex(documents)
    }

    fun tagSlug(note: LessonNote): String? =
        LessonTag.fromSubject(note.subject)?.removePrefix("#")

    private fun stripMarkup(raw: String): String = raw
        .replace(Regex("(?i)<br\\s*/?>"), " ")
        .replace(Regex("(?i)</p\\s*>"), " ")
        .replace(Regex("<[^>]+>"), " ")
}

private fun normalizeTag(tag: String?): String? =
    tag?.trim()?.removePrefix("#")?.lowercase(Locale.ROOT)?.ifBlank { null }

private fun normalizeSearchText(value: String): String = buildString(value.length) {
    value.lowercase(Locale.ROOT).replace('ё', 'е').forEach { char ->
        append(if (Character.isLetterOrDigit(char)) char else ' ')
    }
}.replace(Regex("\\s+"), " ").trim()

private fun tokenize(value: String): List<String> =
    normalizeSearchText(value).split(' ').filter { it.isNotEmpty() }

/**
 * Optimal-string-alignment Damerau-Levenshtein with an early budget. This catches the common
 * Russian typos (swap, omission, extra or wrong letter) without pulling a fuzzy library.
 */
private fun damerauWithin(a: String, b: String, maxDistance: Int): Boolean {
    if (a == b) return true
    if (kotlin.math.abs(a.length - b.length) > maxDistance) return false

    var previousPrevious = IntArray(b.length + 1)
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)

    for (i in 1..a.length) {
        current[0] = i
        var rowMin = current[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + cost
            )
            if (i > 1 && j > 1 &&
                a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]
            ) {
                current[j] = minOf(current[j], previousPrevious[j - 2] + cost)
            }
            rowMin = minOf(rowMin, current[j])
        }
        if (rowMin > maxDistance) return false
        val oldPrevious = previous
        previousPrevious = oldPrevious
        previous = current
        current = oldPrevious
    }
    return previous[b.length] <= maxDistance
}
