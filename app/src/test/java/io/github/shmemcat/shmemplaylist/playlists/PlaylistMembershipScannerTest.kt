package io.github.shmemcat.shmemplaylist.playlists

import io.github.shmemcat.shmemplaylist.tracks.MediaStoreIdentity
import io.github.shmemcat.shmemplaylist.tracks.ResolutionTier
import io.github.shmemcat.shmemplaylist.tracks.ResolvedTrackIdentity
import io.github.shmemcat.shmemplaylist.tracks.TrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistMembershipScannerTest {
    private val scanner = PlaylistMembershipScanner(RuntimeEnvironment.getApplication())

    @Test
    fun resolvedCandidateUsesRelativePathAndDisplayName() {
        val result = scanner.scan(
            playlists = emptyList(),
            resolvedTrack = resolvedTrack(relativePath = "Music/Artist/"),
        ) as MembershipScanResult.Success

        assertEquals("Music/Artist/song.mp3", result.candidatePath)
        assertTrue(result.playlists.isEmpty())
    }

    @Test
    fun missingRelativePathDoesNotFallBackToUnsafeFilenameOnlyMatch() {
        val result = scanner.scan(
            playlists = emptyList(),
            resolvedTrack = resolvedTrack(relativePath = null),
        ) as MembershipScanResult.CandidatePathUnavailable

        assertEquals("relative-path-unavailable", result.reason)
    }

    private fun resolvedTrack(relativePath: String?) = ResolvedTrackIdentity(
        candidate = TrackCandidate(
            identity = MediaStoreIdentity("external_primary", 42),
            displayName = "song.mp3",
            relativePath = relativePath,
            mimeType = "audio/mpeg",
            sizeBytes = null,
            durationMs = null,
            title = null,
            artist = null,
            album = null,
            trackNumber = null,
            discNumber = null,
            dateModifiedSeconds = null,
        ),
        tier = ResolutionTier.DIRECT_MEDIASTORE,
        userApproved = false,
    )
}
