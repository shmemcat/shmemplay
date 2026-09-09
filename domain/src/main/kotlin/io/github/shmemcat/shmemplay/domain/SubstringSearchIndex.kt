package io.github.shmemcat.shmemplay.domain

import java.text.Normalizer
import java.util.Locale

object SearchText {
    private val whitespace = Regex("\\s+")

    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.replace('’', '\'').replace('‘', '\''), Normalizer.Form.NFD)
        val text = buildString(decomposed.length) {
            for (character in decomposed) {
                if (character != '\'' && Character.getType(character) != Character.NON_SPACING_MARK.toInt()) append(character)
            }
        }
        return text.lowercase(Locale.ROOT).trim().replace(whitespace, " ")
    }

    class Query(value: String) {
        val terms = normalize(value).split(' ').filter(String::isNotBlank).distinct()
        val empty: Boolean get() = terms.isEmpty()
        fun matches(normalized: String): Boolean = terms.all(normalized::contains)
    }
}

/** Immutable substring index. Postings preserve source order; exact checks eliminate false positives. */
class SubstringSearchIndex(values: List<String>, checkCancelled: () -> Unit = {}) {
    private val text = Array(values.size) { i ->
        if (i % 256 == 0) checkCancelled()
        SearchText.normalize(values[i])
    }
    private val postings = HashMap<Long, IntArray>()

    init {
        val building = HashMap<Long, PostingBuilder>()
        text.forEachIndexed { row, value ->
            if (row % 256 == 0) checkCancelled()
            for (offset in 0 until value.length - 2) building.getOrPut(gram(value, offset), ::PostingBuilder).add(row)
        }
        building.forEach { (gram, rows) -> postings[gram] = rows.toArray() }
        checkCancelled()
    }

    data class Result(val rows: IntArray, val examined: Int)

    fun search(query: SearchText.Query, checkCancelled: () -> Unit = {}): Result {
        checkCancelled()
        if (query.empty) return Result(IntArray(text.size) { it }, 0)
        var candidates: IntArray? = null
        for (term in query.terms) for (offset in 0 until term.length - 2) {
            val rows = postings[gram(term, offset)] ?: return Result(IntArray(0), 0)
            if (candidates == null || rows.size < candidates.size) candidates = rows
        }
        // One/two-character searches scan pre-normalized strings. Longer queries start with
        // the rarest trigram across all terms, then verify every complete term in each candidate.
        val found = PostingBuilder()
        val count = candidates?.size ?: text.size
        for (i in 0 until count) {
            if (i % 256 == 0) checkCancelled()
            val row = candidates?.get(i) ?: i
            if (query.matches(text[row])) found.add(row)
        }
        return Result(found.toArray(), count)
    }

    private class PostingBuilder {
        private var rows = IntArray(4)
        private var size = 0
        fun add(row: Int) {
            if (size > 0 && rows[size - 1] == row) return
            if (size == rows.size) rows = rows.copyOf(size * 2)
            rows[size++] = row
        }
        fun toArray() = rows.copyOf(size)
    }

    private companion object {
        fun gram(value: String, offset: Int): Long = (value[offset].code.toLong() shl 32) or
            (value[offset + 1].code.toLong() shl 16) or value[offset + 2].code.toLong()
    }
}
