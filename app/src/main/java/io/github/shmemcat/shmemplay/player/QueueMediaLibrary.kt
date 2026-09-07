package io.github.shmemcat.shmemplay.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** Stable, queue-qualified IDs: the same song can have different positions in different queues. */
internal object QueueMediaLibrary {
    const val ROOT = "shmemplay:root"
    const val RECENT = "shmemplay:recent"
    const val QUEUES = "shmemplay:queues"
    const val CURRENT = "shmemplay:current"
    private const val GROUP_SIZE = 100

    data class Selection(val queueId: String, val entryId: String?)

    fun id(queueId: String, entryId: String? = null): String = Uri.Builder()
        .scheme("shmemplay").authority("queue").appendPath(queueId)
        .apply { if (entryId != null) appendPath(entryId) }.build().toString()

    fun selection(id: String): Selection? {
        val uri = Uri.parse(id)
        if (uri.scheme != "shmemplay" || uri.authority != "queue" || uri.query != null || uri.fragment != null) return null
        val parts = uri.pathSegments
        if (parts.size !in 1..2) return null
        return Selection(parts[0], parts.getOrNull(1))
    }

    fun folder(id: String, title: String, subtitle: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId(id).setMediaMetadata(MediaMetadata.Builder().setTitle(title)
            .setSubtitle(subtitle).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()

    fun track(queue: MusicQueue, track: QueueTrack, resume: Boolean = false): MediaItem = track.mediaItem(
        id(queue.id, if (resume) null else track.id),
        if (resume) "Resume ${queue.name}" else track.title,
    )

    fun item(book: QueueBook, mediaId: String): MediaItem? = when (mediaId) {
        ROOT -> folder(ROOT, "Shmemplay")
        RECENT -> folder(RECENT, "Recently played")
        QUEUES -> folder(QUEUES, "Saved queues")
        CURRENT -> folder(CURRENT, "Current queue")
        else -> selection(mediaId)?.let { selection ->
            val queue = book.queues.find { it.id == selection.queueId } ?: return@let null
            val track = if (selection.entryId == null) queue.current?.takeUnless { it.unavailable }
                ?: queue.entries.firstOrNull { !it.unavailable }
            else queue.entries.find { it.id == selection.entryId && !it.unavailable }
            track?.let { track(queue, it, selection.entryId == null) }
        } ?: browseQueue(book, mediaId)?.let { (queue, group) ->
            folder(mediaId, if (group == null) queue.name else "Songs ${group * GROUP_SIZE + 1}–${minOf((group + 1) * GROUP_SIZE, queue.entries.size)}")
        }
    }

    private fun browseId(queueId: String, group: Int? = null): String = Uri.Builder()
        .scheme("shmemplay").authority("browse").appendPath(queueId)
        .apply { if (group != null) appendPath(group.toString()) }.build().toString()

    private fun browseQueue(book: QueueBook, id: String): Pair<MusicQueue, Int?>? {
        val uri = Uri.parse(id)
        if (uri.scheme != "shmemplay" || uri.authority != "browse" || uri.query != null || uri.fragment != null) return null
        val parts = uri.pathSegments
        if (parts.size !in 1..2) return null
        val queue = book.queues.find { it.id == parts[0] } ?: return null
        val group = if (parts.size == 2) parts[1].toIntOrNull()?.takeIf { it >= 0 && it.toLong() * GROUP_SIZE < queue.entries.size } ?: return null else null
        return queue to group
    }

    fun children(book: QueueBook, parentId: String): List<MediaItem>? = when (parentId) {
        ROOT -> listOf(folder(CURRENT, "Current queue"), folder(QUEUES, "Saved queues",
            if (book.queues.isEmpty()) "Create a queue in Shmemplay on your phone" else null))
        RECENT -> book.active?.let { item(book, id(it.id)) }?.let(::listOf).orEmpty()
        CURRENT -> book.active?.let { queueChildren(it, null) }.orEmpty()
        QUEUES -> book.queues.map { folder(browseId(it.id), it.name, "${it.entries.size} songs") }
        else -> browseQueue(book, parentId)?.let { (queue, group) -> queueChildren(queue, group) }
    }

    private fun queueChildren(queue: MusicQueue, group: Int?): List<MediaItem> {
        if (group != null) return queue.entries.drop(group * GROUP_SIZE).take(GROUP_SIZE)
            .filterNot { it.unavailable }.map { track(queue, it) }
        val resume = (queue.current?.takeUnless { it.unavailable } ?: queue.entries.firstOrNull { !it.unavailable })
            ?.let { listOf(track(queue, it, resume = true)) }.orEmpty()
        return resume + if (queue.entries.size <= GROUP_SIZE) queue.entries.filterNot { it.unavailable }.map { track(queue, it) }
        else queue.entries.indices.step(GROUP_SIZE).map { start ->
            folder(browseId(queue.id, start / GROUP_SIZE), "Songs ${start + 1}–${minOf(start + GROUP_SIZE, queue.entries.size)}")
        }
    }

    /** Bound legacy Binder responses; prioritize the active queue when songs occur more than once. */
    fun search(book: QueueBook, query: String): List<MediaItem> {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (terms.isEmpty()) return emptyList()
        val queues = listOfNotNull(book.active) + book.queues.filterNot { it.id == book.activeId }
        return queues.asSequence().flatMap { queue -> queue.entries.asSequence()
            .filter { song -> !song.unavailable && terms.all { term ->
                listOf(song.title, song.artist, song.album, queue.name).any { it.contains(term, ignoreCase = true) }
            } }.map { queue to it } }
            .distinctBy { it.second.id }.take(GROUP_SIZE).map { (queue, song) -> track(queue, song) }.toList()
    }

    fun page(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        if (page < 0 || pageSize <= 0) return emptyList()
        val start = page.toLong() * pageSize
        if (start >= items.size) return emptyList()
        return items.subList(start.toInt(), minOf(start + pageSize, items.size.toLong()).toInt())
    }
}

internal fun QueueTrack.mediaItem(mediaId: String = id, displayTitle: String = title): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId).setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(displayTitle).setArtist(artist).setAlbumTitle(album)
        .setDurationMs(durationMs.takeIf { it > 0 }).setIsPlayable(true).setIsBrowsable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setArtworkUri(albumId?.takeIf { it > 0 }?.let { Uri.parse("content://media/$volume/audio/albumart/$it") })
        .build()).build()
