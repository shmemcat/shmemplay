package io.github.shmemcat.shmemplay.domain

import java.nio.charset.StandardCharsets

object M3uWriterV1 {
    fun write(
        paths: List<String>,
        prefix: String? = null,
        includeTrailingNewline: Boolean = true,
    ): ByteArray {
        val normalizedPrefix = prefix?.trim()?.replace('\\', '/')?.trimEnd('/')
            ?.takeIf(String::isNotEmpty)
        val records = paths.map { value ->
            require(value.isNotBlank()) { "Paths must not be blank" }
            require('\r' !in value && '\n' !in value) { "Paths must not contain line breaks" }
            val path = value.trim().replace('\\', '/')
            if (normalizedPrefix != null &&
                !path.startsWith("$normalizedPrefix/", ignoreCase = true) &&
                !path.equals(normalizedPrefix, ignoreCase = true)
            ) {
                "$normalizedPrefix/${path.trimStart('/')}"
            } else {
                path
            }
        }
        if (records.isEmpty()) return byteArrayOf()
        val suffix = if (includeTrailingNewline) "\n" else ""
        return (records.joinToString("\n") + suffix).toByteArray(StandardCharsets.UTF_8)
    }
}
