package io.github.shmemcat.shmemplaylist.playlists

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.UUID

class SafPlaylistTreeService(context: Context) {
    private val resolver = context.contentResolver

    fun discoverDirectChildren(treeUri: Uri): Result<List<PlaylistDocument>> = runCatching {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn)
                    if (!name.isPlaylistName()) continue
                    add(
                        PlaylistDocument(
                            uri = DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                cursor.getString(idColumn),
                            ),
                            displayName = name,
                            mimeType = cursor.getString(mimeColumn),
                        ),
                    )
                }
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        } ?: error("Provider returned no cursor")
    }

    /**
     * Creates and exclusively mutates a uniquely named disposable text document. Existing
     * documents, including playlists, are never opened for writing.
     */
    fun testDisposableCapabilities(treeUri: Uri): ProviderCapabilityReport {
        val results = linkedMapOf<Capability, CapabilityResult>()
        var disposableUri: Uri? = null
        var cleanupSucceeded = false
        val initial = "shmemplaylist-capability-v1:${UUID.randomUUID()}"
        val updated = "$initial:updated"

        fun supported(capability: Capability) {
            results[capability] = CapabilityResult(capability, CapabilityStatus.SUPPORTED)
        }

        fun unsupported(capability: Capability, failure: Throwable?) {
            results[capability] = CapabilityResult(
                capability,
                CapabilityStatus.UNSUPPORTED,
                failure?.javaClass?.simpleName ?: "verification-failed",
            )
        }

        try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            try {
                resolver.query(
                    parent,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null,
                    null,
                    null,
                )?.use { check(it.moveToFirst()) }
                supported(Capability.READ)
            } catch (failure: Exception) {
                unsupported(Capability.READ, failure)
            }

            try {
                disposableUri = DocumentsContract.createDocument(
                    resolver,
                    parent,
                    "text/plain",
                    ".shmemplaylist-capability-${UUID.randomUUID()}.tmp",
                ) ?: error("Provider returned null")
                supported(Capability.CREATE)
            } catch (failure: Exception) {
                unsupported(Capability.CREATE, failure)
            }

            val created = disposableUri
            if (created != null) {
                verifyWrite(created, initial, Capability.WRITE, results)
                verifyRead(created, initial, Capability.REREAD, results)
                try {
                    disposableUri = DocumentsContract.renameDocument(
                        resolver,
                        created,
                        ".shmemplaylist-capability-${UUID.randomUUID()}.tmp",
                    ) ?: error("Provider returned null")
                    check(documentExists(checkNotNull(disposableUri))) {
                        "Renamed document could not be located"
                    }
                    supported(Capability.RENAME)
                } catch (failure: Exception) {
                    unsupported(Capability.RENAME, failure)
                }
                val current = disposableUri ?: created
                verifyWrite(current, updated, Capability.UPDATE, results)
                verifyRead(current, updated, Capability.REREAD, results)
            }
        } finally {
            val current = disposableUri
            if (current != null) {
                try {
                    val deleteReportedSuccess = DocumentsContract.deleteDocument(resolver, current)
                    cleanupSucceeded = deleteReportedSuccess && !documentExists(current)
                    if (cleanupSucceeded) supported(Capability.DELETE)
                    else unsupported(Capability.DELETE, null)
                } catch (failure: Exception) {
                    unsupported(Capability.DELETE, failure)
                }
            }
        }

        Capability.entries.forEach { capability ->
            results.putIfAbsent(
                capability,
                CapabilityResult(capability, CapabilityStatus.NOT_ATTEMPTED, "prerequisite-failed"),
            )
        }
        return ProviderCapabilityReport(
            Capability.entries.map { checkNotNull(results[it]) },
            cleanupSucceeded,
        )
    }

    private fun verifyWrite(
        uri: Uri,
        value: String,
        capability: Capability,
        results: MutableMap<Capability, CapabilityResult>,
    ) {
        try {
            resolver.openOutputStream(uri, "wt")?.use {
                it.write(value.toByteArray(Charsets.UTF_8))
            } ?: error("Provider returned no output stream")
            results[capability] = CapabilityResult(capability, CapabilityStatus.SUPPORTED)
        } catch (failure: Exception) {
            results[capability] = CapabilityResult(
                capability,
                CapabilityStatus.UNSUPPORTED,
                failure.javaClass.simpleName,
            )
        }
    }

    private fun verifyRead(
        uri: Uri,
        expected: String,
        capability: Capability,
        results: MutableMap<Capability, CapabilityResult>,
    ) {
        try {
            val actual = resolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("Provider returned no input stream")
            check(actual == expected) { "Content mismatch" }
            results[capability] = CapabilityResult(capability, CapabilityStatus.SUPPORTED)
        } catch (failure: Exception) {
            results[capability] = CapabilityResult(
                capability,
                CapabilityStatus.UNSUPPORTED,
                failure.javaClass.simpleName,
            )
        }
    }

    private fun documentExists(uri: Uri): Boolean = runCatching {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null,
        )?.use { it.moveToFirst() } == true
    }.getOrDefault(false)

    private fun String.isPlaylistName(): Boolean =
        endsWith(".m3u", ignoreCase = true) || endsWith(".m3u8", ignoreCase = true)

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}
