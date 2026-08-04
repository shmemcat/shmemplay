package io.github.shmemcat.shmemplaylist.tracks

private const val DURATION_TOLERANCE_MS = 1_000L

class TrackIdentityResolver {
    fun resolve(
        evidence: SharedTrackEvidence,
        candidates: List<TrackCandidate>,
        aliasTarget: MediaStoreIdentity? = null,
    ): ResolutionResult {
        if (aliasTarget != null) {
            candidates.singleOrNull { it.identity == aliasTarget && it.materiallyMatches(evidence) }
                ?.let {
                    return ResolutionResult.Resolved(
                        ResolvedTrackIdentity(it, ResolutionTier.REVALIDATED_ALIAS, true),
                    )
                }
        }

        val provingTiers = listOf(
            ResolutionTier.DISPLAY_NAME_SIZE_DURATION to { candidate: TrackCandidate ->
                candidate.nameMatches(evidence) &&
                    candidate.sizeMatches(evidence) &&
                    candidate.durationMatches(evidence)
            },
            ResolutionTier.DISPLAY_NAME_SIZE_TAGS to { candidate: TrackCandidate ->
                candidate.nameMatches(evidence) &&
                    candidate.sizeMatches(evidence) &&
                    candidate.strongTagsMatch(evidence)
            },
            ResolutionTier.DISPLAY_NAME_DURATION_TAGS to { candidate: TrackCandidate ->
                candidate.nameMatches(evidence) &&
                    candidate.durationMatches(evidence) &&
                    candidate.strongTagsMatch(evidence)
            },
            ResolutionTier.STRONG_TAGS_DURATION to { candidate: TrackCandidate ->
                candidate.strongTagsMatch(evidence) && candidate.durationMatches(evidence)
            },
        )
        for ((tier, predicate) in provingTiers) {
            val matches = candidates.filter(predicate)
            if (matches.size == 1) {
                return ResolutionResult.Resolved(
                    ResolvedTrackIdentity(matches.single(), tier, false),
                )
            }
            if (matches.size > 1) {
                return ResolutionResult.Ambiguous(
                    evidence,
                    matches.sortedForDisplay().map { it.assess(evidence) },
                )
            }
        }
        return if (candidates.isEmpty()) {
            ResolutionResult.NotFound(evidence)
        } else {
            ResolutionResult.Ambiguous(
                evidence,
                candidates.sortedForDisplay().map { it.assess(evidence) },
            )
        }
    }
}

private fun TrackCandidate.materiallyMatches(evidence: SharedTrackEvidence): Boolean =
    nameMatches(evidence) &&
        (!bothKnown(sizeBytes, evidence.sizeBytes) || sizeMatches(evidence)) &&
        (!bothKnown(durationMs, evidence.durationMs) || durationMatches(evidence))

private fun TrackCandidate.nameMatches(evidence: SharedTrackEvidence): Boolean =
    displayName.matchesNonNull(evidence.displayName)

private fun TrackCandidate.sizeMatches(evidence: SharedTrackEvidence): Boolean =
    sizeBytes != null && evidence.sizeBytes != null && sizeBytes == evidence.sizeBytes

private fun TrackCandidate.durationMatches(evidence: SharedTrackEvidence): Boolean =
    durationMs != null && evidence.durationMs != null &&
        kotlin.math.abs(durationMs - evidence.durationMs) <= DURATION_TOLERANCE_MS

private fun TrackCandidate.strongTagsMatch(evidence: SharedTrackEvidence): Boolean {
    val available = listOf(
        title to evidence.title,
        artist to evidence.artist,
        album to evidence.album,
    ).filter { (candidate, shared) -> candidate != null && shared != null }
    return available.size >= 2 && available.all { (candidate, shared) ->
        candidate!!.matchesNonNull(shared)
    }
}

private fun String.matchesNonNull(other: String?): Boolean =
    other != null && normalizedEvidence() == other.normalizedEvidence()

private fun bothKnown(first: Long?, second: Long?): Boolean = first != null && second != null

private fun List<TrackCandidate>.sortedForDisplay(): List<TrackCandidate> =
    sortedWith(
        compareBy(
            { it.volumeOrder() },
            { it.relativePath.orEmpty().lowercase() },
            { it.displayName.lowercase() },
            { it.identity.mediaId },
        ),
    )

private fun TrackCandidate.volumeOrder(): String = identity.volumeName.lowercase()

private fun TrackCandidate.assess(evidence: SharedTrackEvidence): CandidateAssessment =
    CandidateAssessment(
        this,
        listOf(
            EvidenceMatch("filename", nameMatches(evidence)),
            EvidenceMatch("size", sizeMatches(evidence)),
            EvidenceMatch("duration", durationMatches(evidence)),
            EvidenceMatch("title", title.matchesKnown(evidence.title)),
            EvidenceMatch("artist", artist.matchesKnown(evidence.artist)),
            EvidenceMatch("album", album.matchesKnown(evidence.album)),
        ),
    )

private fun String?.matchesKnown(other: String?): Boolean =
    this != null && this.matchesNonNull(other)
