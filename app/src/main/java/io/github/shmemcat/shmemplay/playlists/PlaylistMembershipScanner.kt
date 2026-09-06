package io.github.shmemcat.shmemplay.playlists

import android.content.Context
import io.github.shmemcat.shmemplay.domain.M3uParserV1
import io.github.shmemcat.shmemplay.domain.PathCasePolicy
import io.github.shmemcat.shmemplay.domain.ParseError
import io.github.shmemcat.shmemplay.domain.ParseResult
import io.github.shmemcat.shmemplay.domain.PhonePathV1
import io.github.shmemcat.shmemplay.domain.PlaylistOperationsV1
import io.github.shmemcat.shmemplay.domain.SemanticChecksumV1
import io.github.shmemcat.shmemplay.domain.VolumeId
import io.github.shmemcat.shmemplay.domain.VolumePath
import io.github.shmemcat.shmemplay.tracks.ResolvedTrackIdentity

data class PlaylistMembership(
    val playlist: PlaylistDocument,
    val containsResolvedTrack: Boolean,
    val matchingLineNumbers: List<Int>,
    val recordCount: Int,
    val semanticChecksum: String,
)

sealed interface MembershipScanResult {
    data class Success(
        val candidatePath: String,
        val playlists: List<PlaylistMembership>,
        val warnings: List<PlaylistScanWarning> = emptyList(),
    ) : MembershipScanResult

    data class CandidatePathUnavailable(
        val displayName: String,
        val reason: String,
    ) : MembershipScanResult

}

data class PlaylistScanWarning(
    val playlist: PlaylistDocument,
    val error: ParseError,
)

/**
 * Read-only scanner: this type has no output-stream or document-mutation operation.
 */
class PlaylistMembershipScanner(context: Context) {
    private val resolver = context.contentResolver

    fun scan(
        playlists: List<PlaylistDocument>,
        resolvedTrack: ResolvedTrackIdentity,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): MembershipScanResult {
        val candidate = resolvedTrack.candidate
        if (!candidate.identity.volumeName.equals(PRIMARY_MEDIASTORE_VOLUME, ignoreCase = true)) {
            return MembershipScanResult.CandidatePathUnavailable(
                candidate.displayName,
                "removable-volume-path-contract-unavailable",
            )
        }
        val relativePath = candidate.relativePath?.takeIf(String::isNotBlank)
            ?: return MembershipScanResult.CandidatePathUnavailable(
                candidate.displayName,
                "relative-path-unavailable",
            )
        val candidatePath = PhonePathV1.normalize(
            "$relativePath/${candidate.displayName}",
        )?.takeIf(String::isNotEmpty)
            ?: return MembershipScanResult.CandidatePathUnavailable(
                candidate.displayName,
                "normalized-path-unavailable",
            )
        val target = VolumePath(PRIMARY_VOLUME, candidatePath)

        val memberships = mutableListOf<PlaylistMembership>()
        val warnings = mutableListOf<PlaylistScanWarning>()
        for ((index, playlist) in playlists.withIndex()) {
            val parsed = try {
                resolver.openInputStream(playlist.uri)?.use {
                    M3uParserV1.parse(it, playlist.displayName)
                } ?: ParseResult.Failure(ParseError.InputFailure("Provider returned no input stream"))
            } catch (failure: Exception) {
                ParseResult.Failure(ParseError.InputFailure(failure.message))
            }
            when (parsed) {
                is ParseResult.Failure ->
                    warnings += PlaylistScanWarning(playlist, parsed.error)

                is ParseResult.Success -> {
                    val occurrenceIndexes = PlaylistOperationsV1.occurrenceIndexes(
                        parsed.records.map { VolumePath(PRIMARY_VOLUME, it.normalizedPath) },
                        target,
                        PathCasePolicy.INSENSITIVE,
                    )
                    val matchingLines = occurrenceIndexes.map { parsed.records[it].lineNumber }
                    memberships += PlaylistMembership(
                        playlist = playlist,
                        containsResolvedTrack = matchingLines.isNotEmpty(),
                        matchingLineNumbers = matchingLines,
                        recordCount = parsed.records.size,
                        semanticChecksum = SemanticChecksumV1.ofNormalizedPaths(
                            parsed.records.map { it.normalizedPath },
                        ),
                    )
                }
            }
            onProgress(index + 1, playlists.size)
        }
        return MembershipScanResult.Success(candidatePath, memberships, warnings)
    }

    private companion object {
        const val PRIMARY_MEDIASTORE_VOLUME = "external_primary"
        val PRIMARY_VOLUME = VolumeId("primary")
    }
}
