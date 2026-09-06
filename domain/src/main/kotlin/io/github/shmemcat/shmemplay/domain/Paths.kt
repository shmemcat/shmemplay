package io.github.shmemcat.shmemplay.domain

import java.text.Normalizer

const val PRIMARY_STORAGE_ROOT = "/storage/emulated/0/"

object PhonePathV1 {
    fun normalize(value: String?): String? {
        if (value.isNullOrBlank()) return null
        var path = Normalizer.normalize(value.trim().replace('\\', '/'), Normalizer.Form.NFC)
        while (path.startsWith("./")) path = path.substring(2)
        if (path.startsWith(PRIMARY_STORAGE_ROOT, ignoreCase = true)) {
            path = path.substring(PRIMARY_STORAGE_ROOT.length)
        }
        val segments = ArrayDeque<String>()
        path.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/")
    }
}

@JvmInline
value class VolumeId(val value: String) {
    init {
        require(value.isNotBlank()) { "Volume identifier must not be blank" }
    }
}

data class VolumePath(val volume: VolumeId, val normalizedPath: String) {
    init {
        require(normalizedPath.isNotEmpty()) { "Normalized path must not be empty" }
    }
}

enum class PathCasePolicy { SENSITIVE, INSENSITIVE }

object VolumeAwareComparison {
    fun matches(left: VolumePath, right: VolumePath, casePolicy: PathCasePolicy): Boolean =
        left.volume == right.volume && when (casePolicy) {
            PathCasePolicy.SENSITIVE -> left.normalizedPath == right.normalizedPath
            PathCasePolicy.INSENSITIVE ->
                left.normalizedPath.equals(right.normalizedPath, ignoreCase = true)
        }
}

object CanonicalAbsolutePrimaryPathPlan {
    fun generate(normalizedPath: String): String {
        require(normalizedPath.isNotBlank()) { "Path must not be blank" }
        require(PhonePathV1.normalize(normalizedPath) == normalizedPath) {
            "Path must already be phone-path-v1 canonical"
        }
        return PRIMARY_STORAGE_ROOT + normalizedPath
    }
}
