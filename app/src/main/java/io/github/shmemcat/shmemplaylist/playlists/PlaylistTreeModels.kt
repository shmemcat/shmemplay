package io.github.shmemcat.shmemplaylist.playlists

import android.net.Uri

data class PlaylistDocument(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
)

enum class Capability {
    READ,
    CREATE,
    WRITE,
    REREAD,
    RENAME,
    UPDATE,
    DELETE,
}

enum class CapabilityStatus {
    SUPPORTED,
    UNSUPPORTED,
    NOT_ATTEMPTED,
}

data class CapabilityResult(
    val capability: Capability,
    val status: CapabilityStatus,
    val detail: String? = null,
)

data class ProviderCapabilityReport(
    val results: List<CapabilityResult>,
    val cleanupSucceeded: Boolean,
) {
    val fullySupported: Boolean
        get() = results.all { it.status == CapabilityStatus.SUPPORTED } && cleanupSucceeded
}

sealed interface PlaylistTreeGrantState {
    data object NotConfigured : PlaylistTreeGrantState

    data class Valid(
        val treeUri: Uri,
        val canRead: Boolean,
        val canWrite: Boolean,
    ) : PlaylistTreeGrantState

    data class Invalid(
        val treeUri: Uri,
        val reason: String,
    ) : PlaylistTreeGrantState
}
