package io.github.shmemcat.shmemplay.player

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

object QueueCodec {
    fun write(book: QueueBook, output: OutputStream) {
        val out = DataOutputStream(output.buffered(64 * 1024))
        out.writeInt(0x53485131)
        out.writeInt(1)
        out.writeLong(book.nextCreation)
        out.writeLong(book.revision)
        out.optional(book.viewedId); out.optional(book.activeId)
        out.writeInt(book.queues.size)
        book.queues.forEach { q ->
            out.writeUTF(q.id); out.writeUTF(q.name); out.writeLong(q.created)
            out.optional(q.currentId); out.writeLong(q.positionMs); out.optional(q.stopAfterId)
            out.writeUTF(q.policy.songEnd.name); out.writeUTF(q.policy.queueEnd.name)
            out.writeBoolean(q.policy.resetAtEnd); out.writeBoolean(q.policy.resumeNext)
            out.writeBoolean(q.policy.wrapQueues); out.writeBoolean(q.policy.shuffle)
            out.writeInt(q.entries.size)
            q.entries.forEach { t ->
                out.writeUTF(t.id); out.writeUTF(t.uri); out.writeUTF(t.title)
                out.writeUTF(t.artist); out.writeUTF(t.album); out.writeUTF(t.genre)
                out.writeLong(t.durationMs); out.writeUTF(t.filename); out.optional(t.relativePath)
                out.writeUTF(t.volume); out.writeLong(t.mediaId)
                out.writeBoolean(t.albumId != null); t.albumId?.let(out::writeLong)
                out.writeBoolean(t.unavailable)
            }
            out.writeInt(q.originalOrder.size); q.originalOrder.forEach(out::writeUTF)
        }
        out.flush()
    }
    fun read(input: InputStream): QueueBook {
        val data = DataInputStream(input.buffered(64 * 1024))
        require(data.readInt() == 0x53485131) { "Unrecognized queue file" }
        require(data.readInt() == 1) { "Unsupported queue version" }
        val next = data.readLong()
        val revision = data.readLong().also { require(it >= 0) }
        val viewed = data.optional(); val active = data.optional()
        val queues = List(data.count(10000)) {
            val id = data.readUTF(); val name = data.readUTF(); val created = data.readLong()
            val current = data.optional(); val position = data.readLong(); val stop = data.optional()
            val policy = QueuePolicy(SongEnd.valueOf(data.readUTF()), QueueEnd.valueOf(data.readUTF()),
                data.readBoolean(), data.readBoolean(), data.readBoolean(), data.readBoolean())
            val tracks = List(data.count(1_000_000)) {
                QueueTrack(data.readUTF(), data.readUTF(), data.readUTF(), data.readUTF(), data.readUTF(), data.readUTF(),
                    data.readLong(), data.readUTF(), data.optional(), data.readUTF(), data.readLong(),
                    if (data.readBoolean()) data.readLong() else null, data.readBoolean())
            }
            val order = List(data.count(1_000_000)) { data.readUTF() }
            val ids = tracks.mapTo(hashSetOf()) { it.id }
            require(ids.size == tracks.size && order.size == ids.size && order.toSet() == ids)
            require(current == null || current in ids)
            require(stop == null || stop in ids)
            require(position >= 0)
            MusicQueue(id, name, created, tracks, current, position, policy, order, stop)
        }
        require(queues.map { it.id }.toSet().size == queues.size)
        require(active == null || queues.any { it.id == active })
        require(viewed == null || queues.any { it.id == viewed })
        require(next > (queues.maxOfOrNull { it.created } ?: 0))
        require(data.read() == -1) { "Unexpected queue data" }
        return QueueBook(queues, viewed, active, next, revision)
    }
    private fun DataOutputStream.optional(value: String?) { writeBoolean(value != null); value?.let(::writeUTF) }
    private fun DataInputStream.optional(): String? = if (readBoolean()) readUTF() else null
    private fun DataInputStream.count(max: Int): Int = readInt().also { require(it in 0..max) }
}
