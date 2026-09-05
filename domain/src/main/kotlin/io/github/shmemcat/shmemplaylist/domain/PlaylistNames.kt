package io.github.shmemcat.shmemplaylist.domain

object PlaylistNames {
    private val forbidden = Regex("[\\\\/:*?\"<>|\\r\\n]")
    private val reserved = setOf("CON", "PRN", "AUX", "NUL") +
        (1..9).flatMap { listOf("COM$it", "LPT$it") }

    fun fileName(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "playlist-name-is-blank" }
        require(!forbidden.containsMatchIn(trimmed)) { "playlist-name-has-reserved-character" }
        require(!trimmed.endsWith('.') && !trimmed.endsWith(' ')) { "playlist-name-has-reserved-ending" }
        val base = when {
            trimmed.endsWith(".m3u8", true) -> trimmed.dropLast(5)
            trimmed.endsWith(".m3u", true) -> trimmed.dropLast(4)
            else -> trimmed
        }
        require(base.uppercase() !in reserved) { "playlist-name-is-reserved" }
        require(base.isNotBlank()) { "playlist-name-is-blank" }
        return if (trimmed.endsWith(".m3u", true) || trimmed.endsWith(".m3u8", true)) {
            trimmed
        } else {
            "$trimmed.m3u"
        }
    }
}
