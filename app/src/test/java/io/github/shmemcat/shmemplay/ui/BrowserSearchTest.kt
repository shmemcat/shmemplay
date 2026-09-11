package io.github.shmemcat.shmemplay.ui

import android.net.Uri
import io.github.shmemcat.shmemplay.player.toQueueTrack
import io.github.shmemcat.shmemplay.playlists.PlaylistDocument
import io.github.shmemcat.shmemplay.playlists.PlaylistEntry
import io.github.shmemcat.shmemplay.playlists.PlaylistSnapshot
import io.github.shmemcat.shmemplay.tracks.LibraryTrack
import io.github.shmemcat.shmemplay.tracks.MediaStoreIdentity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserSearchTest {
    private fun track(id: Long, title: String, album: String = "Album", artist: String = "Artist") = LibraryTrack(
        MediaStoreIdentity("external_primary", id), Uri.parse("content://media/$id"), "$id.mp3", "Music/", title,
        artist, album, "Rock", 60000, null,
    )
    private val tracks = listOf(track(1, "Don’t Look Bäck"), track(2, "Satellites", album = "album"), track(3, "Moon", "Elsewhere", "The Satellites"))
    private fun playlist(name: String, entries: List<PlaylistEntry>, live: Boolean = false) = PlaylistSnapshot(
        PlaylistDocument(Uri.parse("content://playlists/$name"), name, null), entries, live,
    )
    private fun request(section: BrowserSection, query: String, detail: BrowserDetail? = null, playlists: List<PlaylistSnapshot> = emptyList()) =
        BrowserSearchRequest(BrowserLibraryIndex.indexed(tracks), BrowserPlaylistIndex(playlists), null, section, detail, query, "All")

    @Test fun searchPlaybackActionsOnlyAppearForMainCategorySearches() {
        val main = listOf(
            BrowserSection.SONGS,
            BrowserSection.ALBUMS,
            BrowserSection.ARTISTS,
            BrowserSection.GENRES,
            BrowserSection.PLAYLISTS,
        )
        main.forEach { assertTrue(it.name, showsSearchPlaybackActions(it, null, "moon")) }
        assertFalse(showsSearchPlaybackActions(BrowserSection.QUEUES, null, "moon"))
        assertFalse(showsSearchPlaybackActions(BrowserSection.NOW_PLAYING, null, "moon"))
        assertFalse(showsSearchPlaybackActions(BrowserSection.SONGS, null, "   "))
        assertFalse(showsSearchPlaybackActions(BrowserSection.SONGS, BrowserDetail(DetailKind.ALBUM, "Album"), "moon"))
    }

    @Test fun cachedSongRowsAreNeverReplacedByAFalseEmptyStateWhileIndexesBuild() {
        val songs = projectionWhileIndexing(BrowserSection.SONGS, null, "", tracks)
        assertFalse(songs.loading)
        assertEquals(tracks, songs.tracks)
        assertEquals(tracks.map(LibraryTrack::stableId), songs.trackIds)
        assertTrue(projectionWhileIndexing(BrowserSection.ALBUMS, null, "", tracks).loading)
        assertTrue(projectionWhileIndexing(BrowserSection.SONGS, null, "moon", tracks).loading)
    }

    @Test fun browseStageShowsCachedSongsAndGroupsBeforeSearchIndexIsReady() {
        val browse = BrowserLibraryIndex.browse(tracks)
        val songs = searchBrowser(BrowserSearchRequest(browse, null, null, BrowserSection.SONGS, null, "", "All"))
        assertFalse(songs.loading)
        assertEquals(tracks, songs.tracks)
        val albums = searchBrowser(BrowserSearchRequest(browse, null, null, BrowserSection.ALBUMS, null, "", "All"))
        assertFalse(albums.loading)
        assertEquals(listOf("Album", "album", "Elsewhere"), albums.groups.map { it.first })
        assertTrue(searchBrowser(BrowserSearchRequest(browse, null, null, BrowserSection.SONGS, null, "moon", "All")).loading)
        assertTrue(searchBrowser(BrowserSearchRequest(null, null, null, BrowserSection.ALBUMS, null, "", "All")).loading)
    }

    @Test fun songsSelectionCountsAndQueueSourceShareTheSameOrderedResult() {
        val result = searchBrowser(request(BrowserSection.SONGS, "satellites"))
        assertEquals(listOf(tracks[1], tracks[2]), result.tracks)
        assertEquals(result.tracks.map(LibraryTrack::stableId), result.trackIds)
        assertEquals("2 songs · 0h 2m", result.stats)
        assertEquals(listOf(tracks.first()), searchBrowser(request(BrowserSection.SONGS, "dont back")).tracks)
    }

    @Test fun metadataGroupsKeepCaseDistinctNamesAndUseTheSameSongsAsTheirDetails() {
        val result = searchBrowser(request(BrowserSection.ALBUMS, "album"))
        assertEquals(listOf("Album", "album"), result.groups.map { it.first })
        assertEquals("2 albums", result.stats)
        result.groups.forEach { (name, songs) ->
            assertEquals(songs, searchBrowser(request(BrowserSection.ALBUMS, "album", BrowserDetail(DetailKind.ALBUM, name))).tracks)
        }
        val filtered = searchBrowser(request(BrowserSection.ARTISTS, "moon"))
        assertEquals(listOf(tracks.last()), filtered.groups.single().second)
        assertEquals(filtered.tracks, filtered.groups.flatMap { it.second })
    }

    @Test fun playlistNamesMatchAllSongsAndTrackQueriesFilterCountsWithoutLosingEmptyPlaylists() {
        val mixes = playlist("Favourites.m3u", tracks.map { PlaylistEntry("Music/${it.displayName}", it) })
        val empty = playlist("Empty.m3u", emptyList())
        val live = playlist("Live", listOf(PlaylistEntry("Music/3.mp3", tracks[2])), live = true)
        val source = listOf(mixes, empty, live)
        val named = searchBrowser(request(BrowserSection.PLAYLISTS, "favourites", playlists = source))
        assertEquals(tracks, named.tracks)
        assertEquals(3, named.playlists.single().songs.size)
        val filtered = searchBrowser(request(BrowserSection.PLAYLISTS, "satellites", playlists = source))
        assertEquals(listOf(2, 1), filtered.playlists.map { it.songs.size })
        assertEquals(listOf(tracks[1], tracks[2]), filtered.tracks)
        assertEquals("2 playlists", filtered.stats)
        assertEquals(listOf(empty), searchBrowser(request(BrowserSection.PLAYLISTS, "empty", playlists = source)).playlists.map { it.snapshot })
        assertEquals(listOf(live), searchBrowser(request(BrowserSection.PLAYLISTS, "", playlists = source).copy(playlistFilter = "Live")).playlists.map { it.snapshot })
    }

    @Test fun playlistDetailPreservesDuplicateAndMissingRowsAndMatchesTheirPaths() {
        val first = PlaylistEntry("Music/hidden-folder/1.mp3", tracks[0])
        val missing = PlaylistEntry("Music/hidden-folder/missing.mp3", null)
        val mix = playlist("Mix.m3u", listOf(first, missing, first, PlaylistEntry("Music/2.mp3", tracks[1])))
        val request = request(BrowserSection.PLAYLISTS, "hidden-folder", BrowserDetail(DetailKind.PLAYLIST, mix.document.uri.toString()), listOf(mix))
        val result = searchBrowser(request)
        assertEquals(listOf(first, first, missing), result.entries)
        assertEquals(listOf(tracks[0]), result.tracks)
        assertEquals("1 song · 0h 1m", result.stats)
        val broken = mix.copy(sourceError = "Missing source")
        assertTrue(searchBrowser(request.copy(playlists = BrowserPlaylistIndex(listOf(broken)))).entries.isEmpty())
    }

    @Test fun queueSearchUsesSavedMetadataAndQueueOrderRatherThanLibraryOrder() {
        val entries = listOf(tracks[2].toQueueTrack(), tracks[0].toQueueTrack().copy(title = "Archived Satellites"), tracks[1].toQueueTrack())
        val query = request(BrowserSection.QUEUES, "satellites").copy(queue = BrowserQueueIndex(entries) {})
        val result = searchBrowser(query)
        assertEquals(entries, result.queueRows)
        assertEquals(entries.map { it.id }, result.trackIds)
    }

    @Test fun replacementIndexReflectsTagChangesAndOldIndexRemainsAnImmutableSnapshot() {
        val old = request(BrowserSection.SONGS, "renamed")
        assertTrue(searchBrowser(old).tracks.isEmpty())
        val changed = tracks.map { if (it.identity.mediaId == 1L) it.copy(title = "Renamed") else it }
        assertEquals(listOf(changed.first()), searchBrowser(old.copy(library = BrowserLibraryIndex.indexed(changed))).tracks)
        assertTrue(searchBrowser(old).tracks.isEmpty())
    }
}
