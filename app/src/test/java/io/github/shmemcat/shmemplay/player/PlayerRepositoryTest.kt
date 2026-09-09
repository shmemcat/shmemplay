package io.github.shmemcat.shmemplay.player

import android.content.ContextWrapper
import android.os.Looper
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerRepositoryTest {
    @Test fun deletingActiveResumesPreviousQueueAtItsOwnPositionAndPreservesPlayState() {
        for (playing in listOf(true, false)) {
            val directory = Files.createTempDirectory("queue-delete-test").toFile()
            val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) { override fun getFilesDir() = directory }
            val saved = QueueBook().create("one", "One", listOf(QueueTrack("a", "content://a", "A")), "a")
                .edit("one") { it.copy(positionMs = 12345) }
                .create("two", "Two", listOf(QueueTrack("b", "content://b", "B")), "b")
            File(directory, "player-queues-v1.bin").outputStream().use { QueueCodec.write(saved, it) }
            val repository = PlayerRepository(context)
            try {
                waitUntil { repository.state.value.ready }
                val engine = Engine(); repository.attach(engine)
                engine.playing = playing; engine.positionMs = 45678
                repository.delete("two")
                waitUntil { engine.queue == "one" }
                assertEquals("a", engine.current)
                assertEquals(12345, engine.positionMs)
                assertEquals(playing, engine.playing)
                assertEquals("one", File(directory, "player-queues-v1.bin").inputStream().use(QueueCodec::read).activeId)
                repository.delete("one")
                waitUntil { repository.state.value.book.queues.isEmpty() }
                assertNull(engine.current)
                assertFalse(engine.playing)
            } finally { repository.close(); directory.deleteRecursively() }
        }
    }
    @Test fun controllerSelectionAndPlayAreSerializedAndPersistTheSameQueue() {
        val directory = Files.createTempDirectory("car-queue-test").toFile()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) { override fun getFilesDir() = directory }
        val saved = QueueBook().create("one", "One", listOf(QueueTrack("a", "content://a", "A")), "a")
            .edit("one") { it.copy(positionMs = 12345) }
            .create("two", "Two", listOf(QueueTrack("a", "content://a", "A"), QueueTrack("b", "content://b", "B")), "a")
        File(directory, "player-queues-v1.bin").outputStream().use { QueueCodec.write(saved, it) }
        val repository = PlayerRepository(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val engine = Engine(); repository.attach(engine)
            // Issue commands before disk restoration finishes, as a cold car/headset connection does.
            val selection = scope.async { repository.selectFromController(QueueMediaLibrary.Selection("one", null), null) }
            val play = scope.async { repository.setPlayingFromController(true) }
            waitUntil { selection.isCompleted && play.isCompleted }
            runBlocking { selection.await(); play.await() }
            assertTrue(engine.playing)
            assertEquals("one", engine.queue)
            assertEquals(12345, engine.positionMs)
            assertEquals(2, repository.state.value.book.queues.size)
            val next = scope.async { repository.selectFromController(QueueMediaLibrary.Selection("two", "b"), null) }
            waitUntil { next.isCompleted }; runBlocking { next.await() }
            assertFalse(engine.playing) // prepare-from-ID must not start audio itself.
            assertEquals("b", engine.current)
            assertEquals(0, engine.positionMs)
            assertEquals(12345, repository.state.value.book.queues.first().positionMs)
            val revision = repository.state.value.book.revision
            val invalid = scope.async { runCatching { repository.selectFromController(QueueMediaLibrary.Selection("two", "gone"), null) } }
            waitUntil { invalid.isCompleted }
            assertTrue(runBlocking { invalid.await() }.isFailure)
            assertEquals(revision, repository.state.value.book.revision)
            assertEquals("two", File(directory, "player-queues-v1.bin").inputStream().use(QueueCodec::read).activeId)
        } finally { scope.cancel(); repository.close(); directory.deleteRecursively() }
    }
    private class Engine:PlaybackEngine {
        override var positionMs=0L
        override var playing=false
        var queue:String?=null
        var current:String?=null
        var starts=0
        override fun apply(book:QueueBook,play:Boolean?,seek:Boolean) {
            if(queue!=book.activeId || current!=book.active?.currentId || seek) {starts++;positionMs=book.active?.positionMs?:0}
            queue=book.activeId;current=book.active?.currentId
            if(play!=null)playing=play
            if(current==null)playing=false
        }
    }
    private fun waitUntil(check:()->Boolean) {
        val deadline=System.currentTimeMillis()+10000
        while(!check() && System.currentTimeMillis()<deadline){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)}
        assertTrue("Timed out waiting for queue repository",check())
    }
    @Test fun diskRestoreIsPausedAndBrowsingNeverRestartsAudio() {
        val directory=Files.createTempDirectory("queue-store-test").toFile()
        val context=object:ContextWrapper(RuntimeEnvironment.getApplication()) {override fun getFilesDir()=directory}
        val repository=PlayerRepository(context)
        try {
            waitUntil{repository.state.value.ready}
            val engine=Engine();repository.attach(engine)
            repository.createSnapshot("One",listOf(QueueTrack("A","content://a","A"),QueueTrack("B","content://b","B")),"A")
            waitUntil{repository.state.value.book.queues.size==1 && engine.playing}
            engine.positionMs=12345
            repository.createSnapshot("Two",listOf(QueueTrack("C","content://c","C")),"C")
            waitUntil{repository.state.value.book.queues.size==2}
            val first=repository.state.value.book.queues.first();val second=repository.state.value.book.activeId
            val starts=engine.starts
            repository.view(first.id)
            waitUntil{repository.state.value.book.viewedId==first.id}
            assertEquals(second,repository.state.value.book.activeId);assertEquals(starts,engine.starts)
            assertEquals(12345,repository.state.value.book.viewed!!.positionMs)
            repository.checkpoint()
            waitUntil{File(directory,"player-positions-v1.json").exists()}
            repository.close()
            val restored=PlayerRepository(context)
            try {
                waitUntil{restored.state.value.ready}
                val after=Engine();restored.attach(after)
                assertFalse(after.playing)
                assertEquals(first.id,restored.state.value.book.viewedId)
                assertEquals(second,restored.state.value.book.activeId)
                restored.resume(first.id)
                waitUntil{after.queue==first.id && after.playing}
                assertEquals(12345,after.positionMs)
            } finally {restored.close()}
        } finally {repository.close();directory.deleteRecursively()}
    }
    @Test fun shuffleAndPlayPersistsANewQueueAndStartsItsFirstRandomEntry() {
        val directory = Files.createTempDirectory("shuffle-play-test").toFile()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) { override fun getFilesDir() = directory }
        val repository = PlayerRepository(context)
        try {
            waitUntil { repository.state.value.ready }
            val engine = Engine(); repository.attach(engine)
            repository.createSnapshot("Original", listOf(QueueTrack("A", "content://a", "A")), "A")
            waitUntil { repository.state.value.book.queues.size == 1 }
            val source = (1L..4L).map { id -> io.github.shmemcat.shmemplay.tracks.LibraryTrack(
                io.github.shmemcat.shmemplay.tracks.MediaStoreIdentity("external_primary", id),
                android.net.Uri.parse("content://fixture/" + id), "Track" + id + ".mp3", "Music/",
                "Track " + id, "", "", "", 60000, null) }
            repository.shuffleAndPlay("Playlist", source + source.first())
            waitUntil { repository.state.value.book.queues.size == 2 && engine.playing }
            val queue = repository.state.value.book.active!!
            assertTrue(queue.policy.shuffle)
            assertEquals(4, queue.entries.size)
            assertEquals(queue.entries.first().id, engine.current)
            assertEquals(source.map { it.stableId }, queue.shuffled(false).entries.map { it.id })
            assertEquals("Original", repository.state.value.book.queues.first().name)
            val saved = File(directory, "player-queues-v1.bin").inputStream().use(QueueCodec::read)
            assertEquals(queue.id, saved.activeId)
            assertTrue(saved.active!!.policy.shuffle)
        } finally { repository.close(); directory.deleteRecursively() }
    }
    @Test fun corruptSavedQueuesAreRetainedAndBlockMutations() {
        val directory=Files.createTempDirectory("queue-corrupt-test").toFile()
        val file=File(directory,"player-queues-v1.bin");val bytes=byteArrayOf(1,2,3,4);file.writeBytes(bytes)
        val context=object:ContextWrapper(RuntimeEnvironment.getApplication()) {override fun getFilesDir()=directory}
        val repository=PlayerRepository(context)
        try {
            waitUntil{repository.state.value.error!=null}
            assertFalse(repository.state.value.ready)
            repository.createSnapshot("A",listOf(QueueTrack("A","content://a","A")),"A")
            shadowOf(Looper.getMainLooper()).idle()
            assertArrayEquals(bytes,file.readBytes())
        } finally {repository.close();directory.deleteRecursively()}
    }
    @Test fun staleCheckpointCannotUndoANewerSeek() {
        val directory=Files.createTempDirectory("queue-generation-test").toFile()
        val book=QueueBook().create("q","Queue",listOf(QueueTrack("a","content://a","A")),"a").copy(revision=2)
        File(directory,"player-queues-v1.bin").outputStream().use{QueueCodec.write(book,it)}
        File(directory,"player-positions-v1.json").writeText("""{"revision":1,"q":{"entry":"a","position":99999}}""")
        val context=object:ContextWrapper(RuntimeEnvironment.getApplication()) {override fun getFilesDir()=directory}
        val repository=PlayerRepository(context)
        try{waitUntil{repository.state.value.ready};assertEquals(0,repository.state.value.book.active!!.positionMs)}finally{repository.close();directory.deleteRecursively()}
    }
}

