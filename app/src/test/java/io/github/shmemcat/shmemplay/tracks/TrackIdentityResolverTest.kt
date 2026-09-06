package io.github.shmemcat.shmemplay.tracks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackIdentityResolverTest {
    private val resolver = TrackIdentityResolver()
    private val evidence = SharedTrackEvidence(
        sourceAuthority = "gonemad.gmmp.provider",
        displayName = "Song.mp3",
        mimeType = "audio/mpeg",
        sizeBytes = 1000,
        durationMs = 180_000,
        title = "Song",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        discNumber = 1,
    )

    @Test
    fun exactNameSizeAndDurationResolveUniqueCandidate() {
        val result = resolver.resolve(
            evidence,
            listOf(candidate(1), candidate(2, size = 2000)),
        ) as ResolutionResult.Resolved

        assertEquals(1, result.identity.candidate.identity.mediaId)
        assertEquals(ResolutionTier.DISPLAY_NAME_SIZE_DURATION, result.identity.tier)
    }

    @Test
    fun equallyStrongCandidatesRemainAmbiguous() {
        val result = resolver.resolve(evidence, listOf(candidate(1), candidate(2)))

        assertTrue(result is ResolutionResult.Ambiguous)
        assertEquals(2, (result as ResolutionResult.Ambiguous).candidates.size)
    }

    @Test
    fun noCandidateIsNotFound() {
        assertTrue(resolver.resolve(evidence, emptyList()) is ResolutionResult.NotFound)
    }

    @Test
    fun unicodeNfcAndDecomposedNamesAgree() {
        val unicodeEvidence = evidence.copy(displayName = "Café.mp3")
        val result = resolver.resolve(
            unicodeEvidence,
            listOf(candidate(1, name = "Cafe\u0301.mp3")),
        )

        assertTrue(result is ResolutionResult.Resolved)
    }

    @Test
    fun aliasMustStillMateriallyMatch() {
        val alias = MediaStoreIdentity("external_primary", 1)
        val result = resolver.resolve(
            evidence,
            listOf(candidate(1, size = 9999), candidate(2)),
            alias,
        ) as ResolutionResult.Resolved

        assertEquals(2, result.identity.candidate.identity.mediaId)
        assertTrue(result.identity.tier != ResolutionTier.REVALIDATED_ALIAS)
    }

    private fun candidate(
        id: Long,
        name: String = "Song.mp3",
        size: Long = 1000,
    ) = TrackCandidate(
        identity = MediaStoreIdentity("external_primary", id),
        displayName = name,
        relativePath = "Music/",
        mimeType = "audio/mpeg",
        sizeBytes = size,
        durationMs = 180_200,
        title = "Song",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        discNumber = 1,
        dateModifiedSeconds = 1,
    )
}
