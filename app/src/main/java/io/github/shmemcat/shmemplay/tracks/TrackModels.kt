package io.github.shmemcat.shmemplay.tracks

import java.text.Normalizer

data class MediaStoreIdentity(
    val volumeName: String,
    val mediaId: Long,
)

data class SharedTrackEvidence(
    val sourceAuthority: String?,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
)

data class TrackCandidate(
    val identity: MediaStoreIdentity,
    val displayName: String,
    val relativePath: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val dateModifiedSeconds: Long?,
)

enum class ResolutionTier {
    DIRECT_MEDIASTORE,
    REVALIDATED_ALIAS,
    DISPLAY_NAME_SIZE_DURATION,
    DISPLAY_NAME_SIZE_TAGS,
    DISPLAY_NAME_DURATION_TAGS,
    STRONG_TAGS_DURATION,
    MANUAL,
}

data class EvidenceMatch(
    val label: String,
    val matched: Boolean,
)

data class CandidateAssessment(
    val candidate: TrackCandidate,
    val matches: List<EvidenceMatch>,
)

data class ResolvedTrackIdentity(
    val candidate: TrackCandidate,
    val tier: ResolutionTier,
    val userApproved: Boolean,
    val resolverVersion: String = "track-resolver-v1",
)

sealed interface ResolutionResult {
    data class Resolved(val identity: ResolvedTrackIdentity) : ResolutionResult

    data class Ambiguous(
        val evidence: SharedTrackEvidence,
        val candidates: List<CandidateAssessment>,
    ) : ResolutionResult

    data class NotFound(val evidence: SharedTrackEvidence) : ResolutionResult
}

internal fun String.normalizedEvidence(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFC)
