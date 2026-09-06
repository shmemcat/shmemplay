package io.github.shmemcat.shmemplay.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.shmemcat.shmemplay.playlists.PlaylistDocument
import java.io.FileNotFoundException

data class PlaylistDocumentHandle(
    val treeUri: String,
    val documentUri: String,
    val documentId: String,
    val displayName: String,
) {
    val documentIdentity: String
        get() = documentId.hashCode().toUInt().toString(16)
}

interface PlaylistDocumentStorage {
    val handle: PlaylistDocumentHandle
    fun readExact(): ByteArray
    fun overwriteExact(bytes: ByteArray)
}

/**
 * Opens only a document discovered beneath the currently granted tree and revalidates its
 * provider identity and display name before every read or write.
 */
class TreePlaylistDocumentStorageFactory(private val context: Context) {
    private val resolver = context.contentResolver

    fun open(treeUri: Uri, document: PlaylistDocument): PlaylistDocumentStorage {
        val documentId = DocumentsContract.getDocumentId(document.uri)
        val expectedUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        require(document.uri == expectedUri) { "document-not-below-bound-tree" }
        require(document.displayName.isPlaylistName()) { "not-playlist-document" }
        val handle = PlaylistDocumentHandle(
            treeUri = treeUri.toString(),
            documentUri = document.uri.toString(),
            documentId = documentId,
            displayName = document.displayName,
        )
        validate(handle)
        return SafTreePlaylistDocumentStorage(context, handle, ::validate)
    }

    private fun validate(handle: PlaylistDocumentHandle) {
        val treeUri = Uri.parse(handle.treeUri)
        val documentUri = Uri.parse(handle.documentUri)
        check(DocumentsContract.getDocumentId(documentUri) == handle.documentId) {
            "document-id-mismatch"
        }
        check(documentUri == DocumentsContract.buildDocumentUriUsingTree(treeUri, handle.documentId)) {
            "document-not-below-bound-tree"
        }
        resolver.query(
            documentUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            check(cursor.moveToFirst()) { "playlist-document-missing" }
            check(cursor.getString(0) == handle.documentId) { "provider-document-id-mismatch" }
            check(cursor.getString(1) == handle.displayName) { "playlist-document-renamed" }
            check(cursor.getString(1).isPlaylistName()) { "not-playlist-document" }
        } ?: error("provider-returned-no-cursor")
    }

    private fun String.isPlaylistName() =
        endsWith(".m3u", ignoreCase = true) || endsWith(".m3u8", ignoreCase = true)
}

private class SafTreePlaylistDocumentStorage(
    context: Context,
    override val handle: PlaylistDocumentHandle,
    private val validate: (PlaylistDocumentHandle) -> Unit,
) : PlaylistDocumentStorage {
    private val resolver = context.contentResolver

    override fun readExact(): ByteArray {
        validate(handle)
        return resolver.openInputStream(Uri.parse(handle.documentUri))?.use { it.readBytes() }
            ?: throw FileNotFoundException("playlist-input-unavailable")
    }

    override fun overwriteExact(bytes: ByteArray) {
        validate(handle)
        resolver.openOutputStream(Uri.parse(handle.documentUri), "rwt")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: throw FileNotFoundException("playlist-output-unavailable")
    }
}
