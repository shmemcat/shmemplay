package io.github.shmemcat.shmemplaylist.intake

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RedactedUriShape(
    val scheme: String?,
    val authority: String?,
    val pathSegmentCount: Int,
    val hasQuery: Boolean,
    val hasFragment: Boolean,
) {
    override fun toString(): String =
        "${scheme ?: "unknown"}://${authority ?: "unknown"}/<${pathSegmentCount} segments>" +
            (if (hasQuery) "?<redacted>" else "") +
            (if (hasFragment) "#<redacted>" else "")
}

data class DisplayNameEvidence(
    val extension: String?,
    val length: Int?,
)

data class MetadataEvidence(
    val durationMs: Long?,
    val titlePresent: Boolean,
    val artistPresent: Boolean,
    val albumPresent: Boolean,
    val trackNumberPresent: Boolean,
    val discNumberPresent: Boolean,
)

data class DirectMediaStoreEvidence(
    val isMediaStoreUri: Boolean,
    val volume: String?,
    val id: Long?,
    val relativePathDepth: Int?,
    val querySucceeded: Boolean,
)

data class UriEvidence(
    val uriShape: RedactedUriShape,
    val resolverMimeType: String?,
    val displayName: DisplayNameEvidence,
    val sizeBytes: Long?,
    val streamAccessible: Boolean,
    val descriptorAccessible: Boolean,
    val descriptorLength: Long?,
    val seekable: Boolean?,
    val metadata: MetadataEvidence?,
    val directMediaStore: DirectMediaStoreEvidence,
    val errors: List<String>,
)

interface UriEvidenceProbe {
    suspend fun probe(uri: Uri): UriEvidence
}

class AndroidUriEvidenceProbe(
    private val context: Context,
) : UriEvidenceProbe {
    private val resolver: ContentResolver = context.contentResolver

    override suspend fun probe(uri: Uri): UriEvidence = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val openable = queryOpenable(uri, errors)
        val streamAccessible = runProbe(errors, "stream") {
            resolver.openInputStream(uri)?.use { stream -> stream.read() }
                ?: error("provider returned no stream")
        }
        var descriptorLength: Long? = null
        var seekable: Boolean? = null
        val descriptorAccessible = runProbe(errors, "descriptor") {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptorLength = descriptor.statSize.takeIf { it >= 0 }
                seekable = descriptor.isSeekable()
            } ?: error("provider returned no descriptor")
        }
        val metadata = probeMetadata(uri, errors)
        UriEvidence(
            uriShape = uri.redactedShape(),
            resolverMimeType = runCatching { resolver.getType(uri) }
                .onFailure { errors += "MIME query failed: ${it.safeCategory()}" }
                .getOrNull(),
            displayName = openable.first,
            sizeBytes = openable.second,
            streamAccessible = streamAccessible,
            descriptorAccessible = descriptorAccessible,
            descriptorLength = descriptorLength,
            seekable = seekable,
            metadata = metadata,
            directMediaStore = inspectDirectMediaStore(uri, errors),
            errors = errors,
        )
    }

    private fun queryOpenable(
        uri: Uri,
        errors: MutableList<String>,
    ): Pair<DisplayNameEvidence, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { name = cursor.getString(it) }
                    cursor.getColumnIndex(OpenableColumns.SIZE)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { size = cursor.getLong(it) }
                }
            }
        }.onFailure { errors += "Openable-columns query failed: ${it.safeCategory()}" }
        return DisplayNameEvidence(
            extension = name?.substringAfterLast('.', missingDelimiterValue = "")
                ?.takeIf(String::isNotEmpty)
                ?.lowercase()
                ?.take(16),
            length = name?.length,
        ) to size
    }

    private fun probeMetadata(uri: Uri, errors: MutableList<String>): MetadataEvidence? =
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                MetadataEvidence(
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull(),
                    titlePresent = retriever.hasMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artistPresent = retriever.hasMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    albumPresent = retriever.hasMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    trackNumberPresent = retriever.hasMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                    discNumberPresent = retriever.hasMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                )
            } finally {
                retriever.release()
            }
        }.onFailure { errors += "Metadata probe failed: ${it.safeCategory()}" }.getOrNull()

    private fun inspectDirectMediaStore(
        uri: Uri,
        errors: MutableList<String>,
    ): DirectMediaStoreEvidence {
        val volume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getVolumeName(uri) }.getOrNull()
        } else {
            uri.pathSegments.firstOrNull()
        }
        val id = uri.lastPathSegment?.toLongOrNull()
        val isDirect = uri.authority == MediaStore.AUTHORITY &&
            volume != null &&
            id != null &&
            uri.pathSegments.any { it.equals("audio", ignoreCase = true) }
        if (!isDirect) {
            return DirectMediaStoreEvidence(false, volume, id, null, false)
        }
        var relativePathDepth: Int? = null
        val succeeded = runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (index >= 0 && !cursor.isNull(index)) {
                        relativePathDepth = cursor.getString(index)
                            .split('/')
                            .count(String::isNotBlank)
                    }
                }
            } ?: error("provider returned no cursor")
        }.onFailure {
            errors += "Direct MediaStore query failed: ${it.safeCategory()}"
        }.isSuccess
        return DirectMediaStoreEvidence(true, volume, id, relativePathDepth, succeeded)
    }
}

private fun Uri.redactedShape() = RedactedUriShape(
    scheme = scheme?.take(32),
    authority = authority?.take(128),
    pathSegmentCount = pathSegments.size,
    hasQuery = query != null,
    hasFragment = fragment != null,
)

private fun ParcelFileDescriptor.isSeekable(): Boolean? =
    runCatching {
        val original = seekTo(0)
        original >= 0
    }.getOrNull()

private fun ParcelFileDescriptor.seekTo(offset: Long): Long =
    android.system.Os.lseek(fileDescriptor, offset, android.system.OsConstants.SEEK_CUR)

private fun MediaMetadataRetriever.hasMetadata(key: Int): Boolean =
    !extractMetadata(key).isNullOrBlank()

private inline fun runProbe(
    errors: MutableList<String>,
    label: String,
    probe: () -> Unit,
): Boolean = runCatching(probe)
    .onFailure { errors += "${label.replaceFirstChar(Char::uppercase)} probe failed: ${it.safeCategory()}" }
    .isSuccess

private fun Throwable.safeCategory(): String = when (this) {
    is SecurityException -> "permission denied"
    is java.io.FileNotFoundException -> "not found or grant expired"
    is IllegalArgumentException -> "invalid provider response"
    else -> this::class.java.simpleName.take(64)
}
