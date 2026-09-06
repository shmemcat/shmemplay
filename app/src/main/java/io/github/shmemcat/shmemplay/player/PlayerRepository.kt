package io.github.shmemcat.shmemplay.player

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import io.github.shmemcat.shmemplay.tracks.LibraryTrack
import io.github.shmemcat.shmemplay.tracks.MediaStoreIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

fun LibraryTrack.toQueueTrack() = QueueTrack(stableId, contentUri.toString(), title, artist, album, genre,
    durationMs, displayName, relativePath, identity.volumeName, identity.mediaId, albumId)
fun QueueTrack.toLibraryTrack() = LibraryTrack(MediaStoreIdentity(volume, mediaId), Uri.parse(uri), filename,
    relativePath, title, artist, album, genre, durationMs, albumId)

data class PlayerUiState(
    val book: QueueBook = QueueBook(),
    val ready: Boolean = false,
    val connected: Boolean = false,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val error: String? = null,
)

interface PlaybackEngine {
    val positionMs: Long
    val playing: Boolean
    fun apply(book: QueueBook, play: Boolean?, seek: Boolean)
}

/**
 * Serialize service and UI writes to prevent competing queue updates.
 * Separate position checkpoints avoid rewriting full queue snapshots on each checkpoint.
 */
class PlayerRepository internal constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val file = AtomicFile(File(context.filesDir, "player-queues-v1.bin"))
    private val progress = AtomicFile(File(context.filesDir, "player-positions-v1.json"))
    private val mutableState = MutableStateFlow(PlayerUiState())
    val state = mutableState.asStateFlow()
    private var engine: PlaybackEngine? = null

    init {
        scope.launch {
            try {
                val restored = withContext(Dispatchers.IO) {
                    var book = if ((file.baseFile.exists() || File(file.baseFile.path + ".bak").exists())) file.openRead().use(QueueCodec::read) else QueueBook()
                    if ((progress.baseFile.exists() || File(progress.baseFile.path + ".bak").exists())) runCatching {
                        val saved = JSONObject(progress.openRead().bufferedReader().use { it.readText() })
                        book = book.copy(queues = book.queues.map { q ->
                            val position = if (saved.optLong("revision", -1) == book.revision) saved.optJSONObject(q.id) else null
                            if (position != null && position.optString("entry") == q.currentId) {
                                q.copy(positionMs = position.optLong("position").coerceAtLeast(0))
                            } else q
                        })
                    }
                    book
                }
                mutableState.value = mutableState.value.copy(book = restored, ready = true)
                engine?.apply(restored, false, true)
            } catch (failure: Exception) {
                mutableState.value = mutableState.value.copy(error = "Saved queues could not be read. The original file was kept: ${failure.message}")
            }
            for (command in commands) try { command() } catch (failure: Exception) {
                mutableState.value = mutableState.value.copy(error = "Player change failed: ${failure.message}")
            }
        }
    }
    fun attach(value: PlaybackEngine) {
        engine = value
        mutableState.value = mutableState.value.copy(connected = true)
        if (state.value.ready) value.apply(state.value.book, false, true)
    }
    fun detach(value: PlaybackEngine) {
        if (engine !== value) return
        capture()
        engine = null
        mutableState.value = mutableState.value.copy(connected = false, playing = false)
        checkpoint()
    }
    private fun capture(): QueueBook {
        val book = state.value.book
        val active = book.active
        val captured = if (active != null && engine != null) book.edit(active.id) { it.copy(positionMs = engine!!.positionMs) } else book
        mutableState.value = state.value.copy(book = captured, playing = engine?.playing == true,
            positionMs = captured.active?.positionMs ?: 0)
        return captured
    }
    fun tick() { capture() }
    fun reportError(message: String) { mutableState.value = state.value.copy(error = message) }
    fun clearError() { mutableState.value = state.value.copy(error = null) }
    fun checkpoint() {
        commands.trySend {
            if (state.value.ready) {
                val book = capture()
                withContext(Dispatchers.IO) {
                    val json = JSONObject().put("revision",book.revision)
                    book.queues.forEach { q -> json.put(q.id, JSONObject().put("entry", q.currentId).put("position", q.positionMs)) }
                    atomicWrite(progress) { it.write(json.toString().toByteArray()) }
                }
            }
        }
    }
    private fun change(play: Boolean? = null, seek: Boolean = false, transform: (QueueBook) -> QueueBook) {
        commands.trySend {
            if (!state.value.ready) return@trySend
            val before = capture()
            val next = withContext(Dispatchers.Default) { transform(before).copy(revision = before.revision + 1) }
            withContext(Dispatchers.IO) {
                atomicWrite(file) { QueueCodec.write(next, it) }
                // The snapshot includes all positions, so the separate checkpoint is redundant.
                progress.delete()
            }
            mutableState.value = state.value.copy(book = next, error = null)
            engine?.apply(next, play, seek)
            capture()
        }
    }
    fun create(name: String, tracks: List<LibraryTrack>, start: LibraryTrack) = createSnapshot(name, tracks.map { it.toQueueTrack() }, start.stableId)
    fun shuffleAndPlay(name: String, tracks: List<LibraryTrack>) {
        if (tracks.isEmpty()) return
        change(true, true) { it.createShuffled(UUID.randomUUID().toString(), name, tracks.map(LibraryTrack::toQueueTrack)) }
    }
    fun createAdditional(tracks: List<LibraryTrack>) = change { before ->
        if (tracks.isEmpty()) before else before.create(UUID.randomUUID().toString(), "New queue", tracks.map { it.toQueueTrack() }, tracks.first().stableId).copy(activeId = before.activeId)
    }
    fun createSnapshot(name: String, tracks: List<QueueTrack>, start: String) = change(true, true) {
        it.create(UUID.randomUUID().toString(), name, tracks, start)
    }
    fun view(id: String) = change { it.view(id) }
    fun resume(id: String, entry: String? = null) = change(true, true) { it.activate(id, entry) }
    fun rename(id: String, name: String) = change { it.edit(id) { q -> q.copy(name = name.trim().take(120).ifBlank { q.name }) } }
    fun delete(id: String) = change { it.delete(id) }
    fun shuffle(id: String) = change { it.edit(id) { q -> q.shuffled(!q.policy.shuffle) } }
    fun policy(id: String, policy: QueuePolicy) = change { it.edit(id) { q -> q.copy(policy = policy.copy(shuffle = q.policy.shuffle)) } }
    fun insert(id: String, tracks: List<LibraryTrack>, next: Boolean = false) = change {
        it.edit(id) { q -> q.insert(tracks.map(LibraryTrack::toQueueTrack), next) }
    }
    fun remove(id: String, entry: String) = change { it.edit(id) { q -> q.removed(setOf(entry)) } }
    fun stopAfter(id: String, entry: String) = change { it.edit(id) { q -> q.copy(stopAfterId = if (q.stopAfterId == entry) null else entry) } }
    fun reorder(id: String, order: List<String>) = change { it.edit(id) { q -> q.reordered(order) } }
    fun reorderQueues(order: List<String>) = change { it.reordered(order) }
    fun sort(id: String, key: String) = change { it.edit(id) { q ->
        val sorted = when (key) {
            "Artist" -> q.entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { t -> t.artist })
            "Album" -> q.entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { t -> t.album })
            "Duration" -> q.entries.sortedBy { t -> t.durationMs }
            "Reverse" -> q.entries.reversed()
            else -> q.entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { t -> t.title })
        }
        q.reordered(sorted.map { t -> t.id })
    } }
    fun seek(position: Long) = change(seek = true) { b -> b.activeId?.let { id -> b.edit(id) { it.copy(positionMs = position.coerceAtLeast(0)) } } ?: b }
    fun previous() = change(seek = true) { it.previous() }
    fun playPause() {
        commands.trySend {
            if (state.value.ready) {
                val b = capture()
                engine?.apply(b, !state.value.playing, false)
                capture()
            }
        }
    }
    fun next(manual: Boolean = true) {
        commands.trySend {
            if (!state.value.ready) return@trySend
            val before = capture()
            val transition = withContext(Dispatchers.Default) { before.ended(manual) }
            val result = transition.copy(book = transition.book.copy(revision = before.revision + 1))
            withContext(Dispatchers.IO) { atomicWrite(file) { QueueCodec.write(result.book, it) }; progress.delete() }
            mutableState.value = state.value.copy(book = result.book)
            engine?.apply(result.book, engine?.playing == true && result.play,
                result.book.activeId != before.activeId || result.book.active?.currentId != before.active?.currentId || result.book.active?.positionMs != before.active?.positionMs)
            capture()
        }
    }
    fun unavailable(entry: String) = change { b ->
        b.copy(queues = b.queues.map { q -> q.copy(entries = q.entries.map { if (it.id == entry) it.copy(unavailable = true) else it }) })
    }
    internal fun close() { commands.close(); scope.cancel() }
    companion object {
        @Volatile private var instance: PlayerRepository? = null
        fun get(context: Context): PlayerRepository = instance ?: synchronized(this) {
            instance ?: PlayerRepository(context.applicationContext).also { instance = it }
        }
        private fun atomicWrite(file: AtomicFile, write: (java.io.FileOutputStream) -> Unit) {
            val stream = file.startWrite()
            try { write(stream); file.finishWrite(stream) } catch (failure: Throwable) { file.failWrite(stream); throw failure }
        }
    }
}

