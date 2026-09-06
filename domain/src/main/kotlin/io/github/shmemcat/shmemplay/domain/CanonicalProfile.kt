package io.github.shmemcat.shmemplay.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

sealed interface CanonicalIneligibility {
    val code: String

    data object Utf8Bom : CanonicalIneligibility {
        override val code = "utf8-bom"
    }

    data object MalformedUtf8 : CanonicalIneligibility {
        override val code = "malformed-utf8"
    }

    data object NonLfLineEnding : CanonicalIneligibility {
        override val code = "non-lf-line-ending"
    }

    data object MissingTrailingLf : CanonicalIneligibility {
        override val code = "missing-trailing-lf"
    }

    data class NonPathRecord(val lineNumber: Int) : CanonicalIneligibility {
        override val code = "non-path-record"
    }

    data class BlankRecord(val lineNumber: Int) : CanonicalIneligibility {
        override val code = "blank-record"
    }

    data class NotCanonicalAbsolutePrimaryPath(val lineNumber: Int, val value: String) :
        CanonicalIneligibility {
        override val code = "not-canonical-absolute-primary-path"
    }

    data object WriterMismatch : CanonicalIneligibility {
        override val code = "writer-mismatch"
    }
}

data class CanonicalProfileResult(
    val writable: Boolean,
    val paths: List<String>,
    val reasons: List<CanonicalIneligibility>,
)

object CanonicalGoneMadProfileV1 {
    fun validate(bytes: ByteArray): CanonicalProfileResult {
        if (bytes.isEmpty()) return CanonicalProfileResult(true, emptyList(), emptyList())
        val reasons = mutableListOf<CanonicalIneligibility>()
        if (bytes.startsWith(0xEF, 0xBB, 0xBF)) reasons += CanonicalIneligibility.Utf8Bom
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            return CanonicalProfileResult(
                false,
                emptyList(),
                listOf(CanonicalIneligibility.MalformedUtf8),
            )
        }
        val content = text.removePrefix("\uFEFF")
        if ('\r' in content) reasons += CanonicalIneligibility.NonLfLineEnding
        if (!content.endsWith('\n')) reasons += CanonicalIneligibility.MissingTrailingLf
        val rawRecords = content.removeSuffix("\n").split('\n')
        val paths = mutableListOf<String>()
        rawRecords.forEachIndexed { index, rawValue ->
            val value = rawValue.removeSuffix("\r")
            when {
                value.isBlank() -> reasons += CanonicalIneligibility.BlankRecord(index + 1)
                value.startsWith('#') ->
                    reasons += CanonicalIneligibility.NonPathRecord(index + 1)
                !isCanonicalAbsolutePrimaryPath(value) ->
                    reasons += CanonicalIneligibility.NotCanonicalAbsolutePrimaryPath(
                        index + 1,
                        value,
                    )
                else -> paths += value
            }
        }
        if (reasons.isEmpty() && !M3uWriterV1.write(paths).contentEquals(bytes)) {
            reasons += CanonicalIneligibility.WriterMismatch
        }
        return CanonicalProfileResult(reasons.isEmpty(), paths, reasons)
    }

    private fun isCanonicalAbsolutePrimaryPath(value: String): Boolean {
        if (!value.startsWith(PRIMARY_STORAGE_ROOT) || value != value.trim() || '\\' in value) {
            return false
        }
        val relative = value.substring(PRIMARY_STORAGE_ROOT.length)
        return relative.isNotBlank() &&
            PhonePathV1.normalize(value) == relative &&
            relative.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { this[it].toInt() and 0xff == expected[it] }
}
