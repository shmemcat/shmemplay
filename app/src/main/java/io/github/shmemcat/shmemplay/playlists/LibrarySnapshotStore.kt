package io.github.shmemcat.shmemplay.playlists

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import io.github.shmemcat.shmemplay.domain.PhonePathV1
import io.github.shmemcat.shmemplay.tracks.LibraryTrack
import io.github.shmemcat.shmemplay.tracks.MediaStoreIdentity
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale

/** App-owned browsing data. File writes still reread their actual source documents. */
internal data class LibrarySnapshot(
    val tracks: List<LibraryTrack>,
)

internal class LibrarySnapshotStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "library-snapshot-v1.bin"))
    val generation: Long get() = synchronized(lock) { revision }

    fun invalidate() = synchronized(lock) {
        revision++
        file.delete()
    }

    fun load(): LibrarySnapshot? = synchronized(lock) {
        runCatching {
            file.openRead().use { stream ->
                val input = DataInputStream(stream.buffered(64 * 1024))
                require(input.readInt() == 0x53484C31 && input.readInt() == 1)
                val tracks = List(input.count(1_000_000)) {
                    LibraryTrack(
                        MediaStoreIdentity(input.readUTF(), input.readLong()), Uri.parse(input.readUTF()),
                        input.readUTF(), input.optional(), input.readUTF(), input.readUTF(),
                        input.readUTF(), input.readUTF(), input.readLong(),
                        if (input.readBoolean()) input.readLong() else null,
                    )
                }
                require(input.read() == -1)
                LibrarySnapshot(tracks)
            }
        }.getOrNull() // Missing, truncated, and old caches are rebuilt from the library.
    }

    /** An edit or a newer scan must not be overwritten by an older scan finishing its disk write. */
    fun save(snapshot: LibrarySnapshot, expectedGeneration: Long) = synchronized(lock) {
        if (revision != expectedGeneration) return@synchronized
        val stream = file.startWrite()
        try {
            val out = DataOutputStream(stream.buffered(64 * 1024))
            out.writeInt(0x53484C31); out.writeInt(1)
            out.writeInt(snapshot.tracks.size)
            snapshot.tracks.forEach { t ->
                out.writeUTF(t.identity.volumeName); out.writeLong(t.identity.mediaId); out.writeUTF(t.contentUri.toString())
                out.writeUTF(t.displayName); out.optional(t.relativePath); out.writeUTF(t.title); out.writeUTF(t.artist)
                out.writeUTF(t.album); out.writeUTF(t.genre); out.writeLong(t.durationMs)
                out.writeBoolean(t.albumId != null); t.albumId?.let(out::writeLong)
            }
            out.flush()
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }

    private fun DataInputStream.count(max: Int) = readInt().also { require(it in 0..max) }
    private fun DataInputStream.optional(): String? = if (readBoolean()) readUTF() else null
    private fun DataOutputStream.optional(value: String?) { writeBoolean(value != null); value?.let(::writeUTF) }

    companion object {
        private val lock = Any()
        private var revision = 0L

        /** Folder changes and cached restores only relink paths; they never reopen the M3Us. */
        fun resolve(scan: PlaylistLibraryScan, tracks: List<LibraryTrack>): PlaylistLibraryScan {
            fun key(path: String) = PhonePathV1.normalize(path).orEmpty().lowercase(Locale.ROOT)
            val byPath = tracks.mapNotNull { t -> t.canonicalPlaylistPath?.let { key(it) to t } }.toMap()
            return scan.copy(playlists = scan.playlists.map { p ->
                p.copy(entries = p.entries.map { e -> e.copy(track = byPath[key(e.normalizedPath)]) })
            })
        }
    }
}
