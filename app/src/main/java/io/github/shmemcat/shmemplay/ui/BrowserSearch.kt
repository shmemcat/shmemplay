package io.github.shmemcat.shmemplay.ui

import io.github.shmemcat.shmemplay.domain.SearchText
import io.github.shmemcat.shmemplay.domain.SubstringSearchIndex
import io.github.shmemcat.shmemplay.player.QueueTrack
import io.github.shmemcat.shmemplay.player.toLibraryTrack
import io.github.shmemcat.shmemplay.playlists.PlaylistEntry
import io.github.shmemcat.shmemplay.playlists.PlaylistSnapshot
import io.github.shmemcat.shmemplay.tracks.LibraryTrack

internal enum class BrowserSection(val label: String, val glyph: String) {
    QUEUES("Queues", "list-music"), NOW_PLAYING("Now Playing", "circle-play"), SONGS("All Songs", "music-2"),
    ALBUMS("Albums", "▣"), ARTISTS("Artists", "♟"), GENRES("Genres", "◆"), PLAYLISTS("Playlists", "▤"),
}
internal enum class DetailKind { ALBUM, ARTIST, GENRE, PLAYLIST }
internal data class BrowserDetail(val kind: DetailKind, val key: String)

internal fun showsSearchPlaybackActions(section: BrowserSection, detail: BrowserDetail?, query: String): Boolean =
    detail == null && query.isNotBlank() && when (section) {
        BrowserSection.SONGS,
        BrowserSection.ALBUMS,
        BrowserSection.ARTISTS,
        BrowserSection.GENRES,
        BrowserSection.PLAYLISTS -> true
        BrowserSection.QUEUES, BrowserSection.NOW_PLAYING -> false
    }

/** Metadata groups publish first; the substring accelerator is attached in a second background stage. */
internal class BrowserLibraryIndex private constructor(
    val tracks: List<LibraryTrack>,
    val groups: Map<DetailKind, List<Group>>,
    private val search: SubstringSearchIndex?,
) {
    data class Group(val name: String, val normalized: String, val tracks: List<LibraryTrack>)

    val searchReady: Boolean get() = search != null

    fun withSearch(checkCancelled: () -> Unit = {}): BrowserLibraryIndex = BrowserLibraryIndex(
        tracks,
        groups,
        SubstringSearchIndex(
            tracks.mapIndexed { index, track ->
                if (index % 256 == 0) checkCancelled()
                "${track.title} ${track.artist} ${track.album} ${track.genre} ${track.displayName}"
            },
            checkCancelled,
        ),
    )

    fun matches(query: SearchText.Query, checkCancelled: () -> Unit): List<LibraryTrack>? = when {
        query.empty -> tracks
        search == null -> null
        else -> search.search(query, checkCancelled).rows.map(tracks::get)
    }

    companion object {
        fun browse(tracks: List<LibraryTrack>, checkCancelled: () -> Unit = {}): BrowserLibraryIndex {
            val groups = listOf(DetailKind.ALBUM, DetailKind.ARTIST, DetailKind.GENRE).associateWith { kind ->
                checkCancelled()
                tracks.groupBy { groupValue(it, kind) }.toList()
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })
                    .map { (name, songs) -> Group(name, SearchText.normalize(name), songs) }
            }
            return BrowserLibraryIndex(tracks, groups, null)
        }

        fun indexed(tracks: List<LibraryTrack>, checkCancelled: () -> Unit = {}): BrowserLibraryIndex =
            browse(tracks, checkCancelled).withSearch(checkCancelled)
    }
}

internal class BrowserQueueIndex(val entries: List<QueueTrack>, checkCancelled: () -> Unit) {
    val tracks = entries.map(QueueTrack::toLibraryTrack)
    private val search = SubstringSearchIndex(tracks.map { "${it.title} ${it.artist} ${it.album} ${it.genre} ${it.displayName}" }, checkCancelled)
    fun rows(query: SearchText.Query, checkCancelled: () -> Unit) = search.search(query, checkCancelled).rows
}

/** Playlist names/paths and entry sorting are also independent of the query. */
internal class BrowserPlaylistIndex(val snapshots: List<PlaylistSnapshot>, checkCancelled: () -> Unit = {}) {
    data class Entry(val source: PlaylistEntry, val path: String)
    data class Playlist(val snapshot: PlaylistSnapshot, val name: String, val entries: List<Entry>) {
        val tracks = snapshot.resolvedTracks
        val missingFiles = snapshot.entries.any { it.track == null }
    }
    val playlists = snapshots.map { snapshot ->
        checkCancelled()
        Playlist(snapshot, SearchText.normalize(snapshot.document.displayName), snapshot.entries.mapIndexed { i, entry ->
            if (i % 256 == 0) checkCancelled()
            Entry(entry, SearchText.normalize(entry.normalizedPath))
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.source.track?.title ?: it.source.normalizedPath.substringAfterLast('/') }))
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.snapshot.document.displayName })
}

internal data class BrowserSearchRequest(
    val library: BrowserLibraryIndex?, val playlists: BrowserPlaylistIndex?, val queue: BrowserQueueIndex?,
    val section: BrowserSection, val detail: BrowserDetail?, val query: String, val playlistFilter: String,
)

internal data class PlaylistSearchRow(val snapshot: PlaylistSnapshot, val songs: List<LibraryTrack>, val missingFiles: Boolean)
internal data class BrowserProjection(
    val tracks: List<LibraryTrack> = emptyList(),
    val groups: List<Pair<String, List<LibraryTrack>>> = emptyList(),
    val playlists: List<PlaylistSearchRow> = emptyList(),
    val entries: List<PlaylistEntry> = emptyList(),
    val queueRows: List<QueueTrack> = emptyList(),
    val trackIds: List<String> = tracks.map(LibraryTrack::stableId),
    val stats: String = "",
    val loading: Boolean = false,
)

internal fun projectionWhileIndexing(
    section: BrowserSection,
    detail: BrowserDetail?,
    query: String,
    cachedTracks: List<LibraryTrack>,
): BrowserProjection = if (
    section == BrowserSection.SONGS && detail == null && query.isBlank()
) {
    BrowserProjection(tracks = cachedTracks)
} else {
    BrowserProjection(loading = true)
}

/** One projection feeds visible rows, counts, selection and new queues. */
internal fun searchBrowser(request: BrowserSearchRequest, checkCancelled: () -> Unit = {}): BrowserProjection {
    val query = SearchText.Query(request.query)
    if (request.section == BrowserSection.NOW_PLAYING) return BrowserProjection()
    if (request.section == BrowserSection.QUEUES) {
        val queue = request.queue ?: return BrowserProjection(loading = true)
        val rows = queue.rows(query, checkCancelled)
        return BrowserProjection(tracks = rows.map(queue.tracks::get), queueRows = rows.map(queue.entries::get))
    }
    val library = request.library ?: return BrowserProjection(loading = true)
    val matching = library.matches(query, checkCancelled) ?: return BrowserProjection(loading = true)
    if (request.section == BrowserSection.SONGS && request.detail == null) return BrowserProjection(matching, stats = songStats(matching))
    val matches = matching.mapTo(hashSetOf(), LibraryTrack::stableId)
    fun matched(songs: List<LibraryTrack>) = songs.filterIndexed { i, track ->
        if (i % 256 == 0) checkCancelled()
        track.stableId in matches
    }
    fun result(tracks: List<LibraryTrack>, groups: List<Pair<String, List<LibraryTrack>>> = emptyList(),
               playlists: List<PlaylistSearchRow> = emptyList(), entries: List<PlaylistEntry> = emptyList(), stats: String? = null): BrowserProjection {
        val unique = tracks.distinctBy(LibraryTrack::stableId)
        return BrowserProjection(unique, groups, playlists, entries, stats = stats ?: songStats(unique))
    }
    val detail = request.detail
    if (detail != null) {
        if (detail.kind != DetailKind.PLAYLIST) {
            val group = library.groups[detail.kind].orEmpty().firstOrNull { it.name == detail.key }
            val songs = group?.let { if (query.matches(it.normalized)) it.tracks else matched(it.tracks) }.orEmpty()
            return result(songs)
        }
        val playlist = request.playlists?.playlists?.firstOrNull { it.snapshot.document.uri.toString() == detail.key }
            ?: return result(emptyList())
        if (playlist.snapshot.sourceError != null) return result(emptyList())
        val all = query.matches(playlist.name)
        val entries = playlist.entries.filterIndexed { i, entry ->
            if (i % 256 == 0) checkCancelled()
            all || entry.source.track?.stableId in matches || query.matches(entry.path)
        }.map { it.source }
        return result(entries.mapNotNull(PlaylistEntry::track), entries = entries)
    }
    return when (request.section) {
        BrowserSection.SONGS -> result(matching)
        BrowserSection.ALBUMS, BrowserSection.ARTISTS, BrowserSection.GENRES -> {
            val kind = when (request.section) { BrowserSection.ALBUMS -> DetailKind.ALBUM; BrowserSection.ARTISTS -> DetailKind.ARTIST; else -> DetailKind.GENRE }
            val groups = library.groups.getValue(kind).mapNotNull { group ->
                checkCancelled()
                val songs = if (query.matches(group.normalized)) group.tracks else matched(group.tracks)
                songs.takeIf(List<LibraryTrack>::isNotEmpty)?.let { group.name to it }
            }
            result(groups.flatMap { it.second }, groups = groups, stats = countLabel(groups.size, kind.name.lowercase()))
        }
        BrowserSection.PLAYLISTS -> {
            val playlists = request.playlists?.playlists.orEmpty().mapNotNull { playlist ->
                checkCancelled()
                val snapshot = playlist.snapshot
                if (request.playlistFilter != "All" && snapshot.live != (request.playlistFilter == "Live")) return@mapNotNull null
                val songs = playlist.tracks
                val all = query.matches(playlist.name)
                val found = if (all) songs else matched(songs)
                if (all || found.isNotEmpty()) PlaylistSearchRow(snapshot, found, playlist.missingFiles) else null
            }
            result(playlists.flatMap { it.songs }, playlists = playlists, stats = countLabel(playlists.size, "playlist"))
        }
        else -> BrowserProjection()
    }
}

private fun groupValue(track: LibraryTrack, kind: DetailKind) = when (kind) {
    DetailKind.ALBUM -> track.album; DetailKind.ARTIST -> track.artist; DetailKind.GENRE -> track.genre
    DetailKind.PLAYLIST -> error("Not a metadata group")
}
private fun countLabel(count: Int, singular: String) = "$count $singular${if (count == 1) "" else "s"}"
private fun songStats(tracks: List<LibraryTrack>): String {
    val minutes = tracks.sumOf(LibraryTrack::durationMs).coerceAtLeast(0L) / 60000
    return "${countLabel(tracks.size, "song")} · ${minutes / 60}h ${minutes % 60}m"
}
