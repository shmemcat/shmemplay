package io.github.shmemcat.shmemplaylist.domain

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class ExtInf(val durationSeconds: Int?, val title: String?)

data class M3uRecord(
    val source: String,
    val normalizedPath: String,
    val lineNumber: Int,
    val extInf: ExtInf? = null,
)

sealed interface ParseError {
    val code: String

    data object MissingDisplayName : ParseError {
        override val code = "missing-display-name"
    }

    data class MalformedEncoding(val encoding: String) : ParseError {
        override val code = "malformed-encoding"
    }

    data class InvalidNormalizedPath(val lineNumber: Int, val source: String) : ParseError {
        override val code = "normalized-path-empty"
    }

    data class LimitExceeded(val limit: ParseLimit, val maximum: Int) : ParseError {
        override val code = "limit-exceeded"
    }

    data class InputFailure(val message: String?) : ParseError {
        override val code = "input-failure"
    }
}

enum class ParseLimit { BYTES, LINES, RECORDS, LINE_CHARACTERS }

sealed interface ParseResult {
    data class Success(val records: List<M3uRecord>) : ParseResult
    data class Failure(val error: ParseError) : ParseResult
}

data class ParserLimits(
    val maxBytes: Int = 4 * 1024 * 1024,
    val maxLines: Int = 100_000,
    val maxRecords: Int = 50_000,
    val maxLineCharacters: Int = 16_384,
)

object M3uParserV1 {
    fun parse(
        bytes: ByteArray,
        displayName: String,
        limits: ParserLimits = ParserLimits(),
        preserveExtendedInfo: Boolean = true,
    ): ParseResult = parse(ByteArrayInputStream(bytes), displayName, limits, preserveExtendedInfo)

    /**
     * Reads from the stream's current position and deliberately leaves the caller-owned stream open.
     */
    fun parse(
        input: InputStream,
        displayName: String,
        limits: ParserLimits = ParserLimits(),
        preserveExtendedInfo: Boolean = true,
    ): ParseResult {
        if (displayName.isBlank()) return ParseResult.Failure(ParseError.MissingDisplayName)
        val bytes = try {
            val output = ArrayList<Byte>()
            while (true) {
                val value = input.read()
                if (value < 0) break
                if (output.size >= limits.maxBytes) {
                    return ParseResult.Failure(
                        ParseError.LimitExceeded(ParseLimit.BYTES, limits.maxBytes),
                    )
                }
                output += value.toByte()
            }
            output.toByteArray()
        } catch (failure: Exception) {
            return ParseResult.Failure(ParseError.InputFailure(failure.message))
        }

        val decoded = decodeDesktopCompatible(bytes)
        if (decoded is DecodeResult.Failure) {
            return ParseResult.Failure(ParseError.MalformedEncoding(decoded.encoding))
        }
        val records = mutableListOf<M3uRecord>()
        var pendingExtInf: ExtInf? = null
        for ((index, raw) in physicalLines((decoded as DecodeResult.Success).text).withIndex()) {
            val lineNumber = index + 1
            if (lineNumber > limits.maxLines) {
                return ParseResult.Failure(
                    ParseError.LimitExceeded(ParseLimit.LINES, limits.maxLines),
                )
            }
            if (raw.length > limits.maxLineCharacters) {
                return ParseResult.Failure(
                    ParseError.LimitExceeded(ParseLimit.LINE_CHARACTERS, limits.maxLineCharacters),
                )
            }
            val source = raw.trim()
            if (source.isEmpty()) continue
            if (source.startsWith("#EXTINF:", ignoreCase = true)) {
                pendingExtInf = parseExtInf(source.substring(8))
                continue
            }
            if (source.startsWith('#')) continue
            if (records.size >= limits.maxRecords) {
                return ParseResult.Failure(
                    ParseError.LimitExceeded(ParseLimit.RECORDS, limits.maxRecords),
                )
            }
            val normalizedPath = PhonePathV1.normalize(source)
            if (normalizedPath.isNullOrEmpty()) {
                return ParseResult.Failure(ParseError.InvalidNormalizedPath(lineNumber, source))
            }
            records += M3uRecord(
                source = source,
                normalizedPath = normalizedPath,
                lineNumber = lineNumber,
                extInf = pendingExtInf.takeIf { preserveExtendedInfo },
            )
            pendingExtInf = null
        }
        return ParseResult.Success(records)
    }

    private fun physicalLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var start = 0
        var index = 0
        while (index < text.length) {
            if (text[index] == '\r' || text[index] == '\n') {
                lines += text.substring(start, index)
                if (text[index] == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                    index++
                }
                start = index + 1
            }
            index++
        }
        if (start < text.length) lines += text.substring(start)
        return lines
    }

    private fun parseExtInf(value: String): ExtInf {
        val comma = value.indexOf(',')
        val durationText = if (comma >= 0) value.substring(0, comma) else value
        val title = if (comma >= 0) value.substring(comma + 1).takeIf(String::isNotEmpty) else null
        return ExtInf(durationText.toIntOrNull(), title)
    }

    private sealed interface DecodeResult {
        data class Success(val text: String) : DecodeResult
        data class Failure(val encoding: String) : DecodeResult
    }

    private fun decodeDesktopCompatible(bytes: ByteArray): DecodeResult {
        val (charset, offset, label) = when {
            bytes.startsWith(0x00, 0x00, 0xFE, 0xFF) ->
                Triple(Charsets.UTF_32BE, 4, "UTF-32BE")
            bytes.startsWith(0xFF, 0xFE, 0x00, 0x00) ->
                Triple(Charsets.UTF_32LE, 4, "UTF-32LE")
            bytes.startsWith(0xEF, 0xBB, 0xBF) ->
                Triple(StandardCharsets.UTF_8, 3, "UTF-8")
            bytes.startsWith(0xFE, 0xFF) ->
                Triple(StandardCharsets.UTF_16BE, 2, "UTF-16BE")
            bytes.startsWith(0xFF, 0xFE) ->
                Triple(StandardCharsets.UTF_16LE, 2, "UTF-16LE")
            else -> Triple(StandardCharsets.UTF_8, 0, "UTF-8")
        }
        return try {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            DecodeResult.Success(
                decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString(),
            )
        } catch (_: CharacterCodingException) {
            DecodeResult.Failure(label)
        }
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { this[it].toInt() and 0xff == expected[it] }
}
