package io.github.shmemcat.shmemplay.playlists

import android.content.Context
import io.github.shmemcat.shmemplay.domain.M3uParserV1
import io.github.shmemcat.shmemplay.domain.ParseResult
import io.github.shmemcat.shmemplay.domain.PhonePathV1
import io.github.shmemcat.shmemplay.tracks.LibraryTrack

data class PlaylistEntry(val normalizedPath: String, val track: LibraryTrack?)

data class PlaylistSnapshot(
    val document: PlaylistDocument,
    val entries: List<PlaylistEntry>,
) {
    val resolvedTracks: List<LibraryTrack> get() = entries.mapNotNull(PlaylistEntry::track)
    val resolvedTrackIds: Set<String> get() = resolvedTracks.mapTo(linkedSetOf(), LibraryTrack::stableId)
}

data class PlaylistLibraryScan(
    val playlists: List<PlaylistSnapshot>,
    val warnings: List<String>,
)

data class SelectionMembership(
    val snapshot: PlaylistSnapshot,
    val selectedCount: Int,
    val presentTrackIds: Set<String>,
    val occurrenceCount: Int,
) {
    val presentCount: Int get() = presentTrackIds.size
    val containsAny: Boolean get() = presentTrackIds.isNotEmpty()
    val containsAll: Boolean get() = selectedCount > 0 && presentCount == selectedCount
}

class PlaylistLibraryScanner(context: Context) {
    private val resolver = context.contentResolver

    fun scan(documents: List<PlaylistDocument>, tracks: List<LibraryTrack>): PlaylistLibraryScan {
        val byPath = tracks.mapNotNull { track ->
            track.canonicalPlaylistPath?.let { path -> pathKey(path) to track }
        }.toMap()
        val snapshots = mutableListOf<PlaylistSnapshot>()
        val warnings = mutableListOf<String>()
        documents.forEach { document ->
            val parsed = runCatching {
                resolver.openInputStream(document.uri)?.use { M3uParserV1.parse(it, document.displayName) }
                    ?: error("playlist-input-unavailable")
            }.getOrElse {
                warnings += "${document.displayName}: ${it.javaClass.simpleName}"
                null
            }
            when (parsed) {
                is ParseResult.Success -> snapshots += PlaylistSnapshot(
                    document,
                    parsed.records.map { record ->
                        PlaylistEntry(record.normalizedPath, byPath[pathKey(record.normalizedPath)])
                    },
                )
                is ParseResult.Failure -> warnings += "${document.displayName}: ${parsed.error.code}"
                null -> Unit
            }
        }
        return PlaylistLibraryScan(snapshots, warnings)
    }

    companion object {
        fun memberships(
            snapshots: List<PlaylistSnapshot>,
            selectedTrackIds: Set<String>,
        ): List<SelectionMembership> = snapshots.map { snapshot ->
            val occurrences = snapshot.resolvedTracks.filter { it.stableId in selectedTrackIds }
            SelectionMembership(
                snapshot = snapshot,
                selectedCount = selectedTrackIds.size,
                presentTrackIds = occurrences.mapTo(linkedSetOf(), LibraryTrack::stableId),
                occurrenceCount = occurrences.size,
            )
        }

        private fun pathKey(path: String): String =
            PhonePathV1.normalize(path).orEmpty().lowercase()
    }
}
