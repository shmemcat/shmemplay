package io.github.shmemcat.shmemplay.domain

import org.junit.Assert.*
import org.junit.Test
import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random
import kotlin.system.measureNanoTime

class SubstringSearchIndexTest {
    // Keep the former implementation here as an independent oracle for matching semantics.
    private fun oldNormalize(value: String) = Normalizer.normalize(value.replace('’', '\'').replace('‘', '\''), Normalizer.Form.NFD)
        .asSequence().filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }.filterNot { it == '\'' }
        .joinToString("").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    private fun oldMatches(text: String, query: String): Boolean {
        val terms = oldNormalize(query).split(' ').filter(String::isNotBlank)
        if (terms.isEmpty()) return true
        return terms.all(oldNormalize(text)::contains)
    }

    @Test fun exactSubstringSemanticsIncludeShortQueriesUnicodeAndTermsAcrossFields() {
        val values = listOf("Don’t Look Bäck The Satellites café rock.mp3", "xxabcxxbcdxx", "abcd", "東京の歌 한국어 🎵", "a A A", "", "Ångström İSTANBUL", "a\tb\nc", "first", "FIRST")
        val index = SubstringSearchIndex(values)
        for (query in listOf("", "  ", "'", "do", "d", "BACK satellites", "don't", "cafe", "ock.mp", "abcd", "東京", "한국어", "🎵", "A a", "a \u00a0 b", "Angstrom", "istanbul", "a\tb", "first", "missing", "zzzzzz")) {
            assertEquals(query, values.indices.filter { oldMatches(values[it], query) }, index.search(SearchText.Query(query)).rows.toList())
        }
    }

    @Test fun randomizedQueriesMatchTheFormerLinearSearchAndPreserveSourceOrder() {
        val random = Random(61)
        val alphabet = "abcde éÄñ한東'’ -_/012"
        val values = List(2000) { buildString { repeat(random.nextInt(0, 130)) { append(alphabet.random(random)) } } }
        val index = SubstringSearchIndex(values)
        repeat(150) {
            val value = values.random(random)
            val start = random.nextInt(value.length + 1)
            val query = value.substring(start, (start + random.nextInt(0, 10)).coerceAtMost(value.length))
            assertEquals(query, values.indices.filter { oldMatches(values[it], query) }, index.search(SearchText.Query(query)).rows.toList())
        }
    }

    @Test fun rareTrigramNarrowsCandidatesAndFullTermVerificationRejectsFalsePositives() {
        val values = List(30000) { "Song $it Artist ${it % 500} Album ${it % 1500} Rock Music/Track$it.mp3" }.toMutableList()
        values[1234] += " uniquequasar"
        values[4567] += " xxabcxxbcdxx"
        val index = SubstringSearchIndex(values)
        val result = index.search(SearchText.Query("quasar"))
        assertEquals(listOf(1234), result.rows.toList())
        assertEquals(1, result.examined)
        assertTrue(index.search(SearchText.Query("abcd")).rows.isEmpty())
        assertEquals(0, index.search(SearchText.Query("notpresentanywhere")).examined)
    }

    @Test fun indexBuildAndSearchHonorCancellation() {
        val values = List(30000) { "Track $it Music" }
        var checks = 0
        assertThrows(InterruptedException::class.java) { SubstringSearchIndex(values) { if (++checks == 2) throw InterruptedException() } }
        val index = SubstringSearchIndex(values)
        checks = 0
        assertThrows(InterruptedException::class.java) { index.search(SearchText.Query("a")) { if (++checks == 2) throw InterruptedException() } }
    }

    @Test fun reportThirtyThousandSongSearchBenchmarkWithoutTimingAssertions() {
        val values = List(30000) { "Song $it Don't Look Bäck Artist ${it % 500} Album ${it % 1500} Rock Track$it.mp3" }
        lateinit var index: SubstringSearchIndex
        val build = measureNanoTime { index = SubstringSearchIndex(values) }
        val queries = listOf("song", "track23456", "back artist 42", "missing", "ro", "o")
        repeat(3) { queries.forEach { index.search(SearchText.Query(it)) } }
        for (query in queries) {
            var oldRows: List<Int> = emptyList()
            val oldTime = measureNanoTime { oldRows = values.indices.filter { oldMatches(values[it], query) } }
            var result = index.search(SearchText.Query(query))
            val indexedTimes = List(9) { measureNanoTime { result = index.search(SearchText.Query(query)) } }.sorted()
            assertEquals(oldRows, result.rows.toList())
            println("SEARCH_BENCH query=$query rows=${result.rows.size} examined=${result.examined} oldMs=${oldTime / 1e6} indexedMedianMs=${indexedTimes[4] / 1e6}")
        }
        println("SEARCH_BENCH buildMs=${build / 1e6} songs=${values.size}")
    }
}
