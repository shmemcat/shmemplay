package io.github.shmemcat.shmemplay.tracks

import java.text.Normalizer
import java.util.Locale

data class FastScrollTarget(
    val label: String,
    val itemIndex: Int,
)

object FastScrollIndex {
    fun targets(labels: List<String>): List<FastScrollTarget> {
        val seen = hashSetOf<String>()
        return buildList {
            labels.forEachIndexed { index, value ->
                val bucket = bucketFor(value)
                if (seen.add(bucket)) add(FastScrollTarget(bucket, index))
            }
        }
    }

    fun bucketFor(value: String): String {
        val codePoints = value.codePoints().toArray()
        val first = codePoints.firstOrNull { !Character.isWhitespace(it) } ?: return SYMBOLS
        if (Character.isDigit(first)) return NUMBERS
        if (!Character.isLetter(first)) return SYMBOLS

        val script = Character.UnicodeScript.of(first)
        if (
            script == Character.UnicodeScript.HAN &&
            codePoints.any {
                val candidate = Character.UnicodeScript.of(it)
                candidate == Character.UnicodeScript.HIRAGANA || candidate == Character.UnicodeScript.KATAKANA
            }
        ) {
            return "JP"
        }
        if (script == Character.UnicodeScript.LATIN) return latinBucket(first)
        return scriptLabels[script] ?: fallbackScriptLabel(script)
    }

    private fun latinBucket(codePoint: Int): String {
        val source = String(Character.toChars(codePoint))
        val folded = Normalizer.normalize(source, Normalizer.Form.NFD)
            .codePoints()
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .findFirst()
            .orElse(codePoint)
        val upper = String(Character.toChars(folded)).uppercase(Locale.ROOT)
        return upper.firstOrNull()?.takeIf { it in 'A'..'Z' }?.toString()
            ?: upper.codePoints().limit(2).toArray().let { points ->
                buildString { points.forEach { appendCodePoint(it) } }
            }
    }

    private fun fallbackScriptLabel(script: Character.UnicodeScript): String =
        script.name
            .split('_')
            .joinToString("")
            .take(2)
            .ifBlank { "OT" }

    private const val NUMBERS = "#"
    private const val SYMBOLS = "•"

    private val scriptLabels = mapOf(
        Character.UnicodeScript.HANGUL to "KR",
        Character.UnicodeScript.HAN to "CN",
        Character.UnicodeScript.HIRAGANA to "JP",
        Character.UnicodeScript.KATAKANA to "JP",
        Character.UnicodeScript.THAI to "TH",
        Character.UnicodeScript.CYRILLIC to "CY",
        Character.UnicodeScript.ARABIC to "AR",
        Character.UnicodeScript.HEBREW to "HE",
        Character.UnicodeScript.GREEK to "GR",
        Character.UnicodeScript.DEVANAGARI to "DV",
        Character.UnicodeScript.ARMENIAN to "AM",
        Character.UnicodeScript.GEORGIAN to "GE",
        Character.UnicodeScript.BENGALI to "BN",
        Character.UnicodeScript.GURMUKHI to "PA",
        Character.UnicodeScript.GUJARATI to "GU",
        Character.UnicodeScript.ORIYA to "OD",
        Character.UnicodeScript.TAMIL to "TA",
        Character.UnicodeScript.TELUGU to "TE",
        Character.UnicodeScript.KANNADA to "KN",
        Character.UnicodeScript.MALAYALAM to "ML",
        Character.UnicodeScript.SINHALA to "SI",
        Character.UnicodeScript.MYANMAR to "MY",
        Character.UnicodeScript.KHMER to "KM",
        Character.UnicodeScript.LAO to "LO",
        Character.UnicodeScript.TIBETAN to "TB",
        Character.UnicodeScript.ETHIOPIC to "ET",
        Character.UnicodeScript.CHEROKEE to "CH",
        Character.UnicodeScript.CANADIAN_ABORIGINAL to "CA",
        Character.UnicodeScript.MONGOLIAN to "MN",
        Character.UnicodeScript.UNKNOWN to "OT",
    )
}
