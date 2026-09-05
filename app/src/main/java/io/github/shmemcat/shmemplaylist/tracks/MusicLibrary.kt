package io.github.shmemcat.shmemplaylist.tracks

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import io.github.shmemcat.shmemplaylist.domain.PhonePathV1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

data class LibraryTrack(
    val identity: MediaStoreIdentity,
    val contentUri: Uri,
    val displayName: String,
    val relativePath: String?,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationMs: Long,
    val albumId: Long?,
) {
    val stableId: String get() = "${identity.volumeName}:${identity.mediaId}"
    val folderRoot: String get() = relativePath
        ?.trim('/')
        ?.substringBefore('/')
        ?.takeIf(String::isNotBlank)
        ?: "Unknown folder"
    val canonicalPlaylistPath: String?
        get() = if (
            identity.volumeName.equals("external_primary", true) ||
            identity.volumeName.equals("external", true)
        ) {
            relativePath?.takeIf(String::isNotBlank)?.let {
                PhonePathV1.normalize("$it/$displayName")
            }
        } else {
            null
        }
}

sealed interface MusicLibraryResult {
    data class Success(val tracks: List<LibraryTrack>) : MusicLibraryResult
    data object PermissionRequired : MusicLibraryResult
    data class Failed(val reason: String) : MusicLibraryResult
}

interface MusicLibraryRepository {
    suspend fun loadAll(): MusicLibraryResult
}

class MediaStoreMusicLibraryRepository(private val context: Context) : MusicLibraryRepository {
    private val resolver = context.contentResolver

    override suspend fun loadAll(): MusicLibraryResult = withContext(Dispatchers.IO) {
        if (!AudioPermissionPolicy.isGranted(context)) {
            return@withContext MusicLibraryResult.PermissionRequired
        }
        runCatching {
            val tracks = buildList {
                externalVolumes().forEach { volume -> addAll(queryVolume(volume)) }
            }.distinctBy(LibraryTrack::identity)
                .sortedWith(trackComparator)
            MusicLibraryResult.Success(tracks)
        }.getOrElse { MusicLibraryResult.Failed(it.safeLibraryReason()) }
    }

    private fun queryVolume(volume: String): List<LibraryTrack> {
        val collection = MediaStore.Audio.Media.getContentUri(volume)
        val genres = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) emptyMap() else queryGenres(volume)
        return buildList {
            resolver.query(
                collection,
                projection(),
                "${MediaStore.Audio.AudioColumns.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.AudioColumns.TITLE} COLLATE NOCASE ASC, " +
                    "${MediaStore.MediaColumns._ID} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.long(MediaStore.MediaColumns._ID) ?: continue
                    val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.string(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else {
                        cursor.string(MediaStore.MediaColumns.DATA)
                            ?.substringBeforeLast('/', "")
                            ?.let(PhonePathV1::normalize)
                            ?.let { if (it.isEmpty()) null else "$it/" }
                    }
                    val displayName = cursor.string(MediaStore.MediaColumns.DISPLAY_NAME).orEmpty()
                    add(
                        LibraryTrack(
                            identity = MediaStoreIdentity(volume, id),
                            contentUri = ContentUris.withAppendedId(collection, id),
                            displayName = displayName,
                            relativePath = relativePath,
                            title = cursor.string(MediaStore.Audio.AudioColumns.TITLE)
                                .cleanUnknown("Unknown title", displayName.substringBeforeLast('.')),
                            artist = cursor.string(MediaStore.Audio.AudioColumns.ARTIST)
                                .cleanUnknown("Unknown artist"),
                            album = cursor.string(MediaStore.Audio.AudioColumns.ALBUM)
                                .cleanUnknown("Unknown album"),
                            genre = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                cursor.string(MediaStore.Audio.AudioColumns.GENRE).cleanUnknown("Unknown genre")
                            } else {
                                genres[id].orEmpty().cleanUnknown("Unknown genre")
                            },
                            durationMs = cursor.long(MediaStore.Audio.AudioColumns.DURATION) ?: 0L,
                            albumId = cursor.long(MediaStore.Audio.AudioColumns.ALBUM_ID),
                        ),
                    )
                }
            }
        }
    }

    private fun queryGenres(volume: String): Map<Long, String> = runCatching {
        val result = linkedMapOf<Long, MutableList<String>>()
        val genresUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Genres.getContentUri(volume)
        } else {
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
        }
        resolver.query(
            genresUri,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            null,
        )?.use { genreCursor ->
            while (genreCursor.moveToNext()) {
                val genreId = genreCursor.long(MediaStore.Audio.Genres._ID) ?: continue
                val name = genreCursor.string(MediaStore.Audio.Genres.NAME)?.takeIf(String::isNotBlank)
                    ?: continue
                val membersUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Genres.Members.getContentUri(volume, genreId)
                } else {
                    MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                }
                resolver.query(
                    membersUri,
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                    null,
                    null,
                    null,
                )?.use { memberCursor ->
                    while (memberCursor.moveToNext()) {
                        val audioId = memberCursor.long(MediaStore.Audio.Genres.Members.AUDIO_ID) ?: continue
                        result.getOrPut(audioId, ::mutableListOf).add(name)
                    }
                }
            }
        }
        result.mapValues { (_, names) -> names.distinct().joinToString(" · ") }
    }.getOrDefault(emptyMap())

    private fun projection(): Array<String> = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
        else add(MediaStore.MediaColumns.DATA)
        add(MediaStore.Audio.AudioColumns.TITLE)
        add(MediaStore.Audio.AudioColumns.ARTIST)
        add(MediaStore.Audio.AudioColumns.ALBUM)
        add(MediaStore.Audio.AudioColumns.DURATION)
        add(MediaStore.Audio.AudioColumns.ALBUM_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.AudioColumns.GENRE)
    }.toTypedArray()

    private fun externalVolumes(): Set<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.getExternalVolumeNames(context)
    } else {
        setOf("external")
    }

    private fun android.database.Cursor.string(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun android.database.Cursor.long(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    private fun String?.cleanUnknown(fallback: String, secondary: String = fallback): String {
        val value = this?.trim()
        return if (value.isNullOrBlank() || value == "<unknown>") secondary.ifBlank { fallback } else value
    }

    private companion object {
        val trackComparator = compareBy<LibraryTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
            .thenBy { it.stableId }
    }
}

object LibrarySearch {
    fun matches(track: LibraryTrack, query: String): Boolean {
        val terms = normalize(query).split(' ').filter(String::isNotBlank)
        if (terms.isEmpty()) return true
        val searchable = normalize(
            listOf(track.title, track.artist, track.album, track.genre, track.displayName)
                .joinToString(" "),
        )
        return terms.all(searchable::contains)
    }

    fun matches(value: String, query: String): Boolean {
        val terms = normalize(query).split(' ').filter(String::isNotBlank)
        val searchable = normalize(value)
        return terms.all(searchable::contains)
    }

    fun normalize(value: String): String = Normalizer.normalize(
        value.replace('’', '\'').replace('‘', '\''),
        Normalizer.Form.NFD,
    ).asSequence()
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        .filterNot { it == '\'' }
        .joinToString("")
        .lowercase(Locale.ROOT)
        .trim()
        .replace(Regex("\\s+"), " ")
}

private fun Throwable.safeLibraryReason(): String = when (this) {
    is SecurityException -> "permission denied"
    is IllegalArgumentException -> "media provider rejected the library query"
    else -> this::class.java.simpleName.take(64)
}
