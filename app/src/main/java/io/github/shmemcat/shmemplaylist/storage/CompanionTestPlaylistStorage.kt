package io.github.shmemcat.shmemplaylist.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException

const val COMPANION_TEST_PLAYLIST_NAME = "Shmemplaylist Companion Test.m3u"

class CompanionTestPlaylistIdentity internal constructor(
    internal val treeUri: String,
    internal val documentUri: String,
    internal val documentId: String,
) {
    val redactedDocumentIdentity: String
        get() = documentId.hashCode().toUInt().toString(16)
}

sealed interface CompanionTestProvisionResult {
    data class Ready(val identity: CompanionTestPlaylistIdentity) : CompanionTestProvisionResult
    data class Refused(val reason: String) : CompanionTestProvisionResult
}

/**
 * The only production storage interface with write access. It cannot name or accept a target:
 * every call revalidates the app-created document's persisted URI, document ID, parent tree,
 * and exact display name.
 */
interface CompanionTestPlaylistStorage {
    val redactedDocumentIdentity: String
    fun readExact(): ByteArray
    fun overwriteExact(bytes: ByteArray)
}

class CompanionTestPlaylistGate(private val context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun provision(treeUri: Uri): CompanionTestProvisionResult = runCatching {
        val existing = loadIdentity()
        if (existing != null) {
            if (existing.treeUri != treeUri.toString()) {
                return CompanionTestProvisionResult.Refused("test-playlist-bound-to-another-tree")
            }
            validate(existing)
            return CompanionTestProvisionResult.Ready(existing)
        }

        if (findChildrenNamed(treeUri, COMPANION_TEST_PLAYLIST_NAME).isNotEmpty()) {
            return CompanionTestProvisionResult.Refused("test-playlist-name-already-exists")
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val created = DocumentsContract.createDocument(
            resolver,
            parent,
            "audio/x-mpegurl",
            COMPANION_TEST_PLAYLIST_NAME,
        ) ?: error("provider-returned-null-document")
        val identity = CompanionTestPlaylistIdentity(
            treeUri = treeUri.toString(),
            documentUri = created.toString(),
            documentId = DocumentsContract.getDocumentId(created),
        )
        try {
            validate(identity)
            preferences.edit()
                .putString(KEY_TREE_URI, identity.treeUri)
                .putString(KEY_DOCUMENT_URI, identity.documentUri)
                .putString(KEY_DOCUMENT_ID, identity.documentId)
                .commit()
                .also { check(it) { "identity-publication-failed" } }
            CompanionTestProvisionResult.Ready(identity)
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw failure
        }
    }.getOrElse { CompanionTestProvisionResult.Refused(it.message ?: it.javaClass.simpleName) }

    fun open(): CompanionTestPlaylistStorage {
        val identity = loadIdentity() ?: error("test-playlist-not-provisioned")
        validate(identity)
        return SafCompanionTestPlaylistStorage(context, identity, ::validate)
    }

    internal fun loadIdentity(): CompanionTestPlaylistIdentity? {
        val tree = preferences.getString(KEY_TREE_URI, null) ?: return null
        val uri = preferences.getString(KEY_DOCUMENT_URI, null) ?: return null
        val id = preferences.getString(KEY_DOCUMENT_ID, null) ?: return null
        return CompanionTestPlaylistIdentity(tree, uri, id)
    }

    internal fun validate(identity: CompanionTestPlaylistIdentity) {
        val documentUri = Uri.parse(identity.documentUri)
        check(DocumentsContract.getDocumentId(documentUri) == identity.documentId) {
            "document-id-mismatch"
        }
        check(documentUri == DocumentsContract.buildDocumentUriUsingTree(
            Uri.parse(identity.treeUri),
            identity.documentId,
        )) { "document-not-below-bound-tree" }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        resolver.query(documentUri, projection, null, null, null)?.use { cursor ->
            check(cursor.moveToFirst()) { "test-playlist-missing" }
            check(cursor.getString(0) == identity.documentId) { "provider-document-id-mismatch" }
            check(cursor.getString(1) == COMPANION_TEST_PLAYLIST_NAME) { "test-playlist-renamed" }
        } ?: error("provider-returned-no-cursor")
    }

    private fun findChildrenNamed(treeUri: Uri, name: String): List<String> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        return resolver.query(children, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == name) add(cursor.getString(0))
                }
            }
        } ?: error("provider-returned-no-cursor")
    }

    private companion object {
        const val PREFERENCES = "phase6_companion_test_identity"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_DOCUMENT_URI = "document_uri"
        const val KEY_DOCUMENT_ID = "document_id"
    }
}

private class SafCompanionTestPlaylistStorage(
    context: Context,
    private val identity: CompanionTestPlaylistIdentity,
    private val validate: (CompanionTestPlaylistIdentity) -> Unit,
) : CompanionTestPlaylistStorage {
    private val resolver = context.contentResolver
    override val redactedDocumentIdentity = identity.redactedDocumentIdentity

    override fun readExact(): ByteArray {
        validate(identity)
        return resolver.openInputStream(Uri.parse(identity.documentUri))?.use { it.readBytes() }
            ?: throw FileNotFoundException("test-playlist-input-unavailable")
    }

    override fun overwriteExact(bytes: ByteArray) {
        validate(identity)
        resolver.openOutputStream(Uri.parse(identity.documentUri), "rwt")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: throw FileNotFoundException("test-playlist-output-unavailable")
    }
}
