package io.github.shmemcat.shmemplay.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object SemanticChecksumV1 {
    fun ofNormalizedPaths(paths: List<String>): String {
        require(paths.none(String::isEmpty)) { "Normalized paths must not be empty" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(paths.joinToString("\n").toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
