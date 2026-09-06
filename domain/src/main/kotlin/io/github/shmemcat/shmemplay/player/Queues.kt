package io.github.shmemcat.shmemplay.player

import kotlin.random.Random

enum class SongEnd { STOP, LOAD_AND_PAUSE, PLAY_NEXT, REPEAT }
enum class QueueEnd { STOP, NEXT_QUEUE, REPEAT }
data class QueuePolicy(
    val songEnd: SongEnd = SongEnd.PLAY_NEXT,
    val queueEnd: QueueEnd = QueueEnd.STOP,
    val resetAtEnd: Boolean = false,
    val resumeNext: Boolean = true,
    val wrapQueues: Boolean = false,
    val shuffle: Boolean = false,
)

/** File identity is independent of tags, list indices, and queue position. */
data class QueueTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val durationMs: Long = 0,
    val filename: String = "",
    val relativePath: String? = null,
    val volume: String = "external_primary",
    val mediaId: Long = 0,
    val albumId: Long? = null,
    val unavailable: Boolean = false,
)

data class MusicQueue(
    val id: String,
    val name: String,
    val created: Long,
    val entries: List<QueueTrack>,
    val currentId: String? = entries.firstOrNull()?.id,
    val positionMs: Long = 0,
    val policy: QueuePolicy = QueuePolicy(),
    val originalOrder: List<String> = entries.map { it.id },
    val stopAfterId: String? = null,
) {
    val current: QueueTrack? get() = entries.firstOrNull { it.id == currentId }
    fun positioned(id: String?, position: Long = 0) = copy(currentId = id, positionMs = position.coerceAtLeast(0))

    fun shuffled(enabled: Boolean, random: Random = Random.Default): MusicQueue {
        if (enabled == policy.shuffle) return this
        val byId = entries.associateBy { it.id }
        return if (enabled) copy(
            originalOrder = entries.map { it.id },
            entries = entries.filter { it.id == currentId } + entries.filter { it.id != currentId }.shuffled(random),
            policy = policy.copy(shuffle = true),
        ) else copy(
            entries = originalOrder.mapNotNull(byId::get),
            policy = policy.copy(shuffle = false),
        )
    }

    /** Moving a block reconciles both orders; unshuffle cannot lose additions or resurrect removals. */
    fun insert(tracks: List<QueueTrack>, afterCurrent: Boolean): MusicQueue {
        val block = tracks.distinctBy { it.id }.filterNot { afterCurrent && it.id == currentId }
        if (block.isEmpty()) return this
        val ids = block.mapTo(hashSetOf()) { it.id }
        fun moved(order: List<String>): List<String> {
            val remainder = order.filterNot(ids::contains).toMutableList()
            val anchor = if (afterCurrent) remainder.indexOf(currentId).let { if (it < 0) remainder.size else it + 1 } else remainder.size
            remainder.addAll(anchor, block.map { it.id })
            return remainder
        }
        val byId = (block + entries).associateBy { it.id }
        val next = moved(entries.map { it.id }).map(byId::getValue)
        return copy(entries = next, originalOrder = moved(originalOrder), currentId = currentId ?: next.firstOrNull()?.id)
    }

    fun reordered(order: List<String>): MusicQueue {
        require(order.size == entries.size && order.toSet() == entries.mapTo(hashSetOf()) { it.id })
        val byId = entries.associateBy { it.id }
        return copy(entries = order.map(byId::getValue), originalOrder = order.toList())
    }

    fun removed(ids: Set<String>): MusicQueue {
        val remaining = entries.filterNot { it.id in ids }
        val successor = if (currentId in ids) entries.drop(entries.indexOfFirst { it.id == currentId } + 1)
            .firstOrNull { it.id !in ids && !it.unavailable }?.id else currentId
        return copy(
            entries = remaining, originalOrder = originalOrder.filterNot(ids::contains),
            currentId = successor, positionMs = if (currentId in ids) 0 else positionMs,
            stopAfterId = stopAfterId?.takeUnless(ids::contains),
        )
    }
}

data class QueueBook(
    val queues: List<MusicQueue> = emptyList(),
    val viewedId: String? = null,
    val activeId: String? = null,
    val nextCreation: Long = 1,
    val revision: Long = 0,
) {
    val active: MusicQueue? get() = queues.firstOrNull { it.id == activeId }
    val viewed: MusicQueue? get() = queues.firstOrNull { it.id == viewedId }
    fun edit(id: String, transform: (MusicQueue) -> MusicQueue) = copy(queues = queues.map { if (it.id == id) transform(it) else it })
    fun view(id: String) = if (queues.any { it.id == id }) copy(viewedId = id) else this
    fun create(id: String, name: String, tracks: List<QueueTrack>, startId: String, shuffle: Boolean? = null, random: Random = Random.Default): QueueBook {
        require(queues.none { it.id == id })
        val entries = tracks.distinctBy { it.id }.toList()
        require(entries.any { it.id == startId })
        val inherited = queues.maxByOrNull { it.created }?.policy ?: QueuePolicy()
        val queue = MusicQueue(id, name.ifBlank { "Queue $nextCreation" }, nextCreation, entries, startId,
            policy = inherited.copy(shuffle = false)).shuffled(shuffle ?: inherited.shuffle, random)
        return copy(queues = queues + queue, viewedId = id, activeId = id, nextCreation = nextCreation + 1)
    }
    /** Shuffle the entire snapshot, including its starting song, while retaining source order for unshuffle. */
    fun createShuffled(id: String, name: String, tracks: List<QueueTrack>, random: Random = Random.Default): QueueBook {
        val entries = tracks.distinctBy { it.id }
        if (entries.isEmpty()) return this
        return create(id, name, entries, entries.random(random).id, shuffle = true, random = random)
    }
    fun activate(id: String, entryId: String? = null): QueueBook {
        val q = queues.firstOrNull { it.id == id } ?: return this
        val entry = entryId ?: q.current?.takeUnless { it.unavailable }?.id ?: q.entries.firstOrNull { !it.unavailable }?.id
        if (entry != null && q.entries.none { it.id == entry }) return this
        return edit(id) { it.positioned(entry, if (entryId == null && entry == q.currentId) q.positionMs else 0) }.copy(activeId = id)
    }
    fun delete(id: String): QueueBook {
        val index = queues.indexOfFirst { it.id == id }
        if (index < 0) return this
        val successor = queues.drop(index + 1).firstOrNull { it.entries.any { t -> !t.unavailable } }
        val remaining = queues.filterNot { it.id == id }
        val result = copy(queues = remaining,
            activeId = if (activeId == id) successor?.id else activeId,
            viewedId = if (viewedId == id) (successor ?: remaining.lastOrNull())?.id else viewedId)
        return if (activeId == id && successor != null) result.activate(successor.id) else result
    }
    fun reordered(order: List<String>): QueueBook {
        require(order.size == queues.size && order.toSet() == queues.mapTo(hashSetOf()) { it.id })
        val byId = queues.associateBy { it.id }
        return copy(queues = order.map(byId::getValue))
    }

    fun previous(): QueueBook {
        val q = active ?: return this
        if (q.positionMs > 5000) return edit(q.id) { it.positioned(q.currentId) }
        val previous = q.entries.take(q.entries.indexOfFirst { it.id == q.currentId }.coerceAtLeast(0))
            .lastOrNull { !it.unavailable }?.id ?: q.currentId
        return edit(q.id) { it.positioned(previous) }
    }

    /** One-shot stop-after wins over repeat, and is consumed only in its owning queue. */
    fun ended(manualNext: Boolean = false, random: Random = Random.Default): QueueTransition {
        val q = active ?: return QueueTransition(this, false)
        if (!manualNext && q.stopAfterId != null && q.stopAfterId == q.currentId) {
            return QueueTransition(edit(q.id) { it.copy(stopAfterId = null) }, false)
        }
        val songEnd = if (manualNext) SongEnd.PLAY_NEXT else q.policy.songEnd
        if (songEnd == SongEnd.STOP) return QueueTransition(this, false)
        if (songEnd == SongEnd.REPEAT) return QueueTransition(edit(q.id) { it.positioned(q.currentId) }, true)
        val autoplay = songEnd == SongEnd.PLAY_NEXT
        val next = q.entries.drop(q.entries.indexOfFirst { it.id == q.currentId } + 1).firstOrNull { !it.unavailable }
        if (next != null) return QueueTransition(edit(q.id) { it.positioned(next.id) }, autoplay)
        var book = this
        if (q.policy.resetAtEnd) book = book.edit(q.id) {
            val entries = if (it.policy.shuffle) it.entries.shuffled(random) else it.entries
            it.copy(entries = entries).positioned(entries.firstOrNull { t -> !t.unavailable }?.id)
        }
        return when (q.policy.queueEnd) {
            QueueEnd.STOP -> QueueTransition(book, false)
            QueueEnd.REPEAT -> {
                val first = book.active?.entries?.firstOrNull { !it.unavailable }
                QueueTransition(book.edit(q.id) { it.positioned(first?.id) }, autoplay && first != null)
            }
            QueueEnd.NEXT_QUEUE -> {
                val index = queues.indexOfFirst { it.id == q.id }
                val candidates = queues.drop(index + 1) + if (q.policy.wrapQueues) queues.take(index + 1) else emptyList()
                val destination = candidates.firstOrNull { it.entries.any { t -> !t.unavailable } }
                    ?: return QueueTransition(book, false)
                val resumeId = destination.current?.takeUnless { it.unavailable }?.id
                val targetId = if (q.policy.resumeNext) resumeId ?: destination.entries.first { !it.unavailable }.id
                    else destination.entries.first { !it.unavailable }.id
                val position = if (q.policy.resumeNext && targetId == resumeId) destination.positionMs else 0
                QueueTransition(book.edit(destination.id) { it.positioned(targetId, position) }.copy(activeId = destination.id), autoplay)
            }
        }
    }
}
data class QueueTransition(val book: QueueBook, val play: Boolean)
