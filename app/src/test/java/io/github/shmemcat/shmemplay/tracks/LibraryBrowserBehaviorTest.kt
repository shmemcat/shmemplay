package io.github.shmemcat.shmemplay.tracks

import android.net.Uri
import io.github.shmemcat.shmemplay.playlists.PlaylistDocument
import io.github.shmemcat.shmemplay.playlists.PlaylistEntry
import io.github.shmemcat.shmemplay.playlists.PlaylistLibraryScanner
import io.github.shmemcat.shmemplay.playlists.PlaylistSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryBrowserBehaviorTest {
    @Test
    fun searchNormalizesApostrophesAccentsAndCase() {
        val track = track("1", "Don’t Look Bäck", "The Satellites")

        assertTrue(LibrarySearch.matches(track, "don't back"))
        assertTrue(LibrarySearch.matches(track, "DONT satellites"))
        assertFalse(LibrarySearch.matches(track, "dont moon"))
    }

    @Test
    fun playlistIsAllPresentOnlyWhenEverySelectedSongExists() {
        val first = track("1", "One")
        val second = track("2", "Two")
        val partial = snapshot("partial", listOf(first, first))
        val complete = snapshot("complete", listOf(first, second))

        val memberships = PlaylistLibraryScanner.memberships(
            listOf(partial, complete),
            setOf(first.stableId, second.stableId),
        )

        assertFalse(memberships[0].containsAll)
        assertTrue(memberships[0].containsAny)
        assertEquals(1, memberships[0].presentCount)
        assertEquals(2, memberships[0].occurrenceCount)
        assertTrue(memberships[1].containsAll)
    }

    @Test
    fun selectionOperationsStayScopedToCurrentFilteredList() {
        assertEquals(listOf("outside", "a", "b"), LibrarySelection.selectAll(listOf("outside"), listOf("a", "b")))
        assertEquals(listOf("outside"), LibrarySelection.deselectAll(listOf("outside", "a"), listOf("a", "b")))
        assertEquals(listOf("outside", "b"), LibrarySelection.invert(listOf("outside", "a"), listOf("a", "b")))
        assertEquals(
            listOf("outside", "a", "d", "b", "c"),
            LibrarySelection.selectBetween(listOf("outside", "a", "d"), listOf("a", "b", "c", "d")),
        )
    }

    private fun snapshot(name: String, tracks: List<LibraryTrack>) = PlaylistSnapshot(
        PlaylistDocument(Uri.parse("content://test/$name"), "$name.m3u", "audio/x-mpegurl"),
        tracks.map { PlaylistEntry("Music/${it.displayName}", it) },
    )

    private fun track(id: String, title: String, artist: String = "Artist") = LibraryTrack(
        identity = MediaStoreIdentity("external_primary", id.toLong()),
        contentUri = Uri.parse("content://media/$id"),
        displayName = "$title.mp3",
        relativePath = "Music/",
        title = title,
        artist = artist,
        album = "Album",
        genre = "Genre",
        durationMs = 60_000,
        albumId = 1,
    )
}
