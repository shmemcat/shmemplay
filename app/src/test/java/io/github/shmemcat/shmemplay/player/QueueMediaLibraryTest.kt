package io.github.shmemcat.shmemplay.player

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QueueMediaLibraryTest {
    private val tracks = (1..30000).map { QueueTrack("external:$it", "content://media/external/audio/media/$it",
        "Song $it", "Artist", "Album", durationMs = 60000, albumId = 12, volume = "external") }

    @Test fun largeQueueIsBrowsableWithoutSendingThirtyThousandSongsOverBinder() {
        val book = QueueBook().create("queue", "Library", tracks, tracks[123].id)
        val folders = QueueMediaLibrary.children(book, QueueMediaLibrary.CURRENT)!!
        assertEquals(301, folders.size) // Resume + 300 groups of 100 songs.
        val lastGroup = folders.last()
        val lastSongs = QueueMediaLibrary.children(book, lastGroup.mediaId)!!
        assertEquals(100, lastSongs.size)
        assertEquals("Song 30000", lastSongs.last().mediaMetadata.title)
        assertTrue(lastSongs.all { it.mediaMetadata.isPlayable == true && it.mediaMetadata.isBrowsable == false })
        assertNotNull(QueueMediaLibrary.item(book, lastGroup.mediaId))
        assertEquals(0, QueueMediaLibrary.page(lastSongs, Int.MAX_VALUE, Int.MAX_VALUE).size)
        assertEquals(25, QueueMediaLibrary.page(lastSongs, 3, 25).size)
    }

    @Test fun idsRetainQueueIdentityAndRejectForeignUrisAndStaleSelections() {
        val id = QueueMediaLibrary.id("queue/a?", "external:42/#")
        assertEquals(QueueMediaLibrary.Selection("queue/a?", "external:42/#"), QueueMediaLibrary.selection(id))
        assertNull(QueueMediaLibrary.selection("content://media/external/audio/media/42"))
        assertNull(QueueMediaLibrary.selection("shmemplay://queue/a/b/c"))
        assertNull(QueueMediaLibrary.item(QueueBook(), id))
        val missing = tracks.first().copy(unavailable = true)
        val book = QueueBook().create("q", "Queue", listOf(missing, tracks[1]), missing.id)
        assertNull(QueueMediaLibrary.item(book, QueueMediaLibrary.id("q", missing.id)))
        assertEquals(tracks[1].uri, QueueMediaLibrary.item(book, QueueMediaLibrary.id("q"))!!.localConfiguration!!.uri.toString())
    }

    @Test fun searchIsBoundedAndPrefersTheActiveCopyOfASong() {
        val book = QueueBook().create("first", "One", tracks, tracks.first().id)
            .create("second", "Two", tracks, tracks.first().id)
        val results = QueueMediaLibrary.search(book, "artist song")
        assertEquals(100, results.size)
        assertTrue(results.all { QueueMediaLibrary.selection(it.mediaId)!!.queueId == "second" })
        assertTrue(QueueMediaLibrary.search(book, "   ").isEmpty())
        assertEquals("Song 30000", QueueMediaLibrary.search(book, "song 30000").single().mediaMetadata.title)
    }

    @Test fun metadataIncludesTheRealTitleArtistDurationAndLocalArtwork() {
        val metadata = tracks.first().mediaItem().mediaMetadata
        assertEquals("Song 1", metadata.title)
        assertEquals("Artist", metadata.artist)
        assertEquals(60000L, metadata.durationMs)
        assertEquals("content://media/external/audio/albumart/12", metadata.artworkUri.toString())
        assertEquals("Shmemplay", QueueMediaLibrary.item(QueueBook(), QueueMediaLibrary.ROOT)!!.mediaMetadata.title)
        assertTrue(QueueMediaLibrary.children(QueueBook(), QueueMediaLibrary.CURRENT)!!.isEmpty())
    }
}
