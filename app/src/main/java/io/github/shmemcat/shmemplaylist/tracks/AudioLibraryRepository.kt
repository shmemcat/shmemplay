package io.github.shmemcat.shmemplaylist.tracks

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface CandidateQueryResult {
    data class Success(val candidates: List<TrackCandidate>) : CandidateQueryResult

    data object PermissionRequired : CandidateQueryResult

    data class Failed(val category: String) : CandidateQueryResult
}

interface AudioLibraryRepository {
    suspend fun findCandidates(evidence: SharedTrackEvidence): CandidateQueryResult

    suspend fun getByIdentity(identity: MediaStoreIdentity): TrackCandidate?
}

class MediaStoreAudioLibraryRepository(
    private val context: Context,
) : AudioLibraryRepository {
    private val resolver = context.contentResolver

    override suspend fun findCandidates(
        evidence: SharedTrackEvidence,
    ): CandidateQueryResult = withContext(Dispatchers.IO) {
        if (!AudioPermissionPolicy.isGranted(context)) {
            return@withContext CandidateQueryResult.PermissionRequired
        }
        runCatching {
            val candidates = mutableListOf<TrackCandidate>()
            for (volume in externalVolumes()) {
                candidates += queryVolume(volume, evidence)
                if (candidates.size > MAX_CANDIDATES) {
                    error("candidate query exceeded safe limit")
                }
            }
            CandidateQueryResult.Success(candidates.distinctBy(TrackCandidate::identity))
        }.getOrElse { CandidateQueryResult.Failed(it.safeCategory()) }
    }

    override suspend fun getByIdentity(identity: MediaStoreIdentity): TrackCandidate? =
        withContext(Dispatchers.IO) {
            if (!AudioPermissionPolicy.isGranted(context)) return@withContext null
            runCatching {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.getContentUri(identity.volumeName),
                    identity.mediaId,
                )
                resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.toCandidate(identity.volumeName) else null
                }
            }.getOrNull()
        }

    private fun queryVolume(
        volume: String,
        evidence: SharedTrackEvidence,
    ): List<TrackCandidate> {
        val collection = MediaStore.Audio.Media.getContentUri(volume)
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        evidence.displayName?.let {
            selectionParts += "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            selectionArgs += it
        }
        if (evidence.displayName == null) {
            evidence.sizeBytes?.let {
                selectionParts += "${MediaStore.MediaColumns.SIZE} = ?"
                selectionArgs += it.toString()
            }
        }
        if (selectionParts.isEmpty()) return emptyList()
        return buildList {
            resolver.query(
                collection,
                PROJECTION,
                selectionParts.joinToString(" AND "),
                selectionArgs.toTypedArray(),
                "${MediaStore.MediaColumns.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    add(cursor.toCandidate(volume))
                    require(size <= MAX_CANDIDATES) { "candidate query exceeded safe limit" }
                }
            }
        }
    }

    private fun externalVolumes(): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context)
        } else {
            setOf(MediaStore.VOLUME_EXTERNAL)
        }

    private fun android.database.Cursor.toCandidate(volume: String): TrackCandidate =
        TrackCandidate(
            identity = MediaStoreIdentity(volume, long(MediaStore.MediaColumns._ID)!!),
            displayName = string(MediaStore.MediaColumns.DISPLAY_NAME).orEmpty(),
            relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                string(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                null
            },
            mimeType = string(MediaStore.MediaColumns.MIME_TYPE),
            sizeBytes = long(MediaStore.MediaColumns.SIZE),
            durationMs = long(MediaStore.Audio.AudioColumns.DURATION),
            title = string(MediaStore.Audio.AudioColumns.TITLE),
            artist = string(MediaStore.Audio.AudioColumns.ARTIST),
            album = string(MediaStore.Audio.AudioColumns.ALBUM),
            trackNumber = long(MediaStore.Audio.AudioColumns.TRACK)?.toInt(),
            discNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                long(MediaStore.Audio.AudioColumns.DISC_NUMBER)?.toInt()
            } else {
                null
            },
            dateModifiedSeconds = long(MediaStore.MediaColumns.DATE_MODIFIED),
        )

    private fun android.database.Cursor.string(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun android.database.Cursor.long(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    private companion object {
        const val MAX_CANDIDATES = 100

        val PROJECTION: Array<String>
            get() = buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.MediaColumns.RELATIVE_PATH)
                }
                add(MediaStore.MediaColumns.MIME_TYPE)
                add(MediaStore.MediaColumns.SIZE)
                add(MediaStore.Audio.AudioColumns.DURATION)
                add(MediaStore.Audio.AudioColumns.TITLE)
                add(MediaStore.Audio.AudioColumns.ARTIST)
                add(MediaStore.Audio.AudioColumns.ALBUM)
                add(MediaStore.Audio.AudioColumns.TRACK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(MediaStore.Audio.AudioColumns.DISC_NUMBER)
                }
                add(MediaStore.MediaColumns.DATE_MODIFIED)
            }.toTypedArray()
    }
}

private fun Throwable.safeCategory(): String = when (this) {
    is SecurityException -> "permission denied"
    is IllegalArgumentException -> "provider rejected query"
    else -> this::class.java.simpleName.take(64)
}
