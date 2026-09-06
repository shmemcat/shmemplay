package io.github.shmemcat.shmemplay.player

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class QueuesTest {
    private fun tracks(vararg ids: String) = ids.map { QueueTrack(it, "content://music/$it", it) }
    private fun book() = QueueBook().create("one", "One", tracks("A", "B", "C", "D"), "B")
        .edit("one") { it.copy(positionMs = 12345) }
    @Test fun creationCollapsesFileDuplicatesAndUsesChosenIdentity() {
        val book = QueueBook().create("one", "", tracks("A", "B", "A"), "A")
        assertEquals(listOf("A", "B"), book.active!!.entries.map { it.id })
        assertEquals("A", book.active!!.currentId)
    }
    @Test fun shuffledCreationStartsRandomlyPreservesOriginalOrderAndExistingQueues() {
        val original = book().edit("one") { it.copy(policy = QueuePolicy(songEnd = SongEnd.REPEAT)) }
        val source = tracks("E", "F", "G", "E", "H")
        val next = original.createShuffled("new", "Playlist", source, Random(42))
        val queue = next.active!!
        assertEquals(original.queues, next.queues.dropLast(1))
        assertEquals("new", next.viewedId)
        assertTrue(queue.policy.shuffle)
        assertEquals(SongEnd.REPEAT, queue.policy.songEnd)
        assertEquals(queue.entries.first().id, queue.currentId)
        assertEquals(source.distinctBy { it.id }, queue.shuffled(false).entries)
        assertEquals(4, queue.entries.size)
        assertEquals(0, queue.positionMs)
        val startingSongs = (0..30).map { original.createShuffled("new", "Playlist", source, Random(it)).active!!.currentId }.toSet()
        assertTrue("The initial song must also be shuffled", startingSongs.size > 1)
    }
    @Test fun emptyShuffledPlaylistDoesNotReplaceExistingPlayback() {
        val original = book()
        assertEquals(original, original.createShuffled("new", "Empty", emptyList()))
    }
    @Test fun browsingAndRenamingDoNotActivateQueue() {
        val two = book().create("two", "Two", tracks("E"), "E")
        val changed = two.view("one").edit("one") { it.copy(name = "Renamed") }
        assertEquals("two", changed.activeId)
        assertEquals(12345, changed.viewed!!.positionMs)
    }
    @Test fun nextInsertionReanchorsAfterRemovingEarlierEntry() {
        val q = book().active!!.insert(tracks("A"), true)
        assertEquals(listOf("B", "A", "C", "D"), q.entries.map { it.id })
        assertEquals("B", q.currentId); assertEquals(12345, q.positionMs)
        assertEquals(q, q.insert(tracks("B"), true))
    }
    @Test fun bulkInsertionMovesExistingAndAppendsMissingInRequestedOrder() {
        val q = book().active!!.insert(tracks("C", "A", "E", "C"), false)
        assertEquals(listOf("B", "D", "C", "A", "E"), q.entries.map { it.id })
        assertEquals(12345, q.positionMs)
    }
    @Test fun unshufflePreservesAdvancedCurrentAndReconcilesEdits() {
        var q = book().active!!.shuffled(true, Random(7))
        assertEquals("B", q.entries.first().id)
        q = q.positioned("C", 4567).insert(tracks("E"), false).removed(setOf("A"))
        q = q.shuffled(false)
        assertEquals(listOf("B", "C", "D", "E"), q.entries.map { it.id })
        assertEquals("C", q.currentId); assertEquals(4567, q.positionMs)
    }
    @Test fun settingsInheritanceUsesCreationChronologyNotPickerOrder() {
        val two = book().create("two", "Two", tracks("E"), "E")
            .edit("two") { it.copy(policy = it.policy.copy(songEnd = SongEnd.REPEAT)) }
            .reordered(listOf("two", "one")).view("one")
        val three = two.create("three", "Three", tracks("F"), "F")
        assertEquals(SongEnd.REPEAT, three.active!!.policy.songEnd)
        assertEquals(SongEnd.PLAY_NEXT, three.queues.first { it.id == "one" }.policy.songEnd)
    }
    @Test fun removingCurrentAdvancesAndFinalRemovalStopsExplicitly() {
        val q = book().active!!.removed(setOf("B", "C"))
        assertEquals("D", q.currentId); assertEquals(0, q.positionMs)
        assertNull(q.removed(setOf("D")).currentId)
        assertEquals(12345, book().active!!.removed(setOf("A")).positionMs)
    }
    @Test fun deletingActiveUsesFollowingPickerQueueWithoutWrapping() {
        val b = book().create("two", "Two", tracks("E"), "E").activate("one")
        assertEquals("two", b.delete("one").activeId)
        assertNull(b.activate("two").delete("two").activeId)
        assertEquals("one", b.delete("two").activeId)
    }
    @Test fun previousThresholdIsStrictlyGreaterThanFiveSeconds() {
        assertEquals("B", book().previous().active!!.currentId)
        assertEquals("A", book().edit("one") { it.copy(positionMs = 5000) }.previous().active!!.currentId)
    }
    @Test fun repeatSongTakesPrecedenceOverQueueEnd() {
        val b = book().edit("one") { it.positioned("D").copy(policy = QueuePolicy(SongEnd.REPEAT, QueueEnd.NEXT_QUEUE)) }
        val result = b.ended()
        assertTrue(result.play); assertEquals("D", result.book.active!!.currentId)
        assertEquals(0, result.book.active!!.positionMs)
    }
    @Test fun loadAndPauseNeverAutoplaysDestinationAndResumesSavedPosition() {
        val b = book().create("two", "Two", tracks("E"), "E").edit("two") { it.copy(positionMs = 777) }
            .activate("one", "D").edit("one") { it.copy(policy = QueuePolicy(SongEnd.LOAD_AND_PAUSE, QueueEnd.NEXT_QUEUE)) }
        val result = b.ended()
        assertFalse(result.play); assertEquals("two", result.book.activeId)
        assertEquals(777, result.book.active!!.positionMs)
    }
    @Test fun noWrapStopsAtLastAndWrapUsesPickerOrder() {
        val b = book().edit("one") { it.positioned("D").copy(policy = QueuePolicy(queueEnd = QueueEnd.NEXT_QUEUE)) }
        assertFalse(b.ended().play)
        assertTrue(b.edit("one") { it.copy(policy = it.policy.copy(wrapQueues = true)) }.ended().play)
    }
    @Test fun stopAfterIsOneShotAndQueueSpecific() {
        val b = book().edit("one") { it.copy(stopAfterId = "B", policy = QueuePolicy(songEnd = SongEnd.REPEAT)) }
        val result = b.ended()
        assertFalse(result.play); assertNull(result.book.active!!.stopAfterId)
        assertTrue(result.book.ended().play)
    }
    @Test fun filteredSnapshotDoesNotModifySourceAndRestoreDoesNotContainAutoplay() {
        val b = book().create("two", "Filtered", tracks("B", "D"), "D")
        assertEquals(book().active, b.queues.first())
        val output = ByteArrayOutputStream(); QueueCodec.write(b, output)
        assertEquals(b, QueueCodec.read(ByteArrayInputStream(output.toByteArray())))
    }
    @Test fun largeQueueRoundTripAndShuffleRestore() {
        val tracks = (0 until 30000).map { QueueTrack("$it", "content://music/$it", "노래 $it") }
        val b = QueueBook().create("one", "Large", tracks, "23042")
        val q = b.active!!.shuffled(true, Random(1)).positioned("29000", 234).shuffled(false)
        assertEquals(tracks, q.entries); assertEquals("29000", q.currentId)
        val output = ByteArrayOutputStream(); QueueCodec.write(b.edit("one") { q }, output)
        assertEquals(q, QueueCodec.read(ByteArrayInputStream(output.toByteArray())).active)
    }
    @Test fun largeQueueStorageUsesBoundedBulkIoAndLeavesStreamsOpen() {
        val tracks = (0 until 30000).map { QueueTrack("$it", "content://music/$it", "Track $it") }
        val book = QueueBook().create("large", "Large", tracks, "29000")
        var writes = 0
        var reads = 0
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun write(value: Int) { writes++; super.write(value) }
            override fun write(bytes: ByteArray, offset: Int, length: Int) { writes++; super.write(bytes, offset, length) }
            override fun close() { closed = true; super.close() }
        }
        QueueCodec.write(book, output)
        val input = object : ByteArrayInputStream(output.toByteArray()) {
            override fun read(): Int { reads++; return super.read() }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int { reads++; return super.read(bytes, offset, length) }
            override fun close() { closed = true; super.close() }
        }
        assertEquals(book, QueueCodec.read(input))
        assertTrue("Large queue writes must be batched: $writes calls", writes < 1000)
        assertTrue("Large queue reads must be batched: $reads calls", reads < 1000)
        assertFalse("AtomicFile owns stream completion", closed)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidFileIsRejected() {
        QueueCodec.read(ByteArrayInputStream(byteArrayOf(0, 0, 0, 0)))
    }
    @Test fun unavailableEntriesAreRetainedAndSkippedOnAdvance() {
        val b = book().edit("one") { it.copy(entries = it.entries.map { t -> t.copy(unavailable = t.id == "C") }) }
        assertEquals("D", b.ended().book.active!!.currentId)
        assertEquals(4, b.ended().book.active!!.entries.size)
    }
}
