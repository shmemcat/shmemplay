package io.github.shmemcat.shmemplaylist.playlists

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

class PlaylistTreeSettings(context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun saveGrantedTree(treeUri: Uri, grantFlags: Int): PlaylistTreeGrantState {
        val persistableFlags = grantFlags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return try {
            resolver.takePersistableUriPermission(treeUri, persistableFlags)
            preferences.edit().putString(KEY_TREE_URI, treeUri.toString()).apply()
            revalidate()
        } catch (failure: Exception) {
            PlaylistTreeGrantState.Invalid(treeUri, failure.javaClass.simpleName)
        }
    }

    fun revalidate(): PlaylistTreeGrantState {
        val value = preferences.getString(KEY_TREE_URI, null)
            ?: return PlaylistTreeGrantState.NotConfigured
        val uri = runCatching { Uri.parse(value) }.getOrNull()
            ?: return PlaylistTreeGrantState.NotConfigured
        val permission = resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
            ?: return PlaylistTreeGrantState.Invalid(uri, "persisted-grant-missing")
        if (!permission.isReadPermission) {
            return PlaylistTreeGrantState.Invalid(uri, "persisted-read-grant-missing")
        }
        val queryable = runCatching {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri),
            )
            resolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
        return if (queryable) {
            PlaylistTreeGrantState.Valid(uri, canRead = true, canWrite = permission.isWritePermission)
        } else {
            PlaylistTreeGrantState.Invalid(uri, "tree-not-queryable")
        }
    }

    fun clear() {
        val uri = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)
        preferences.edit().remove(KEY_TREE_URI).apply()
        if (uri != null) {
            runCatching {
                resolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }

    companion object {
        private const val PREFERENCES = "playlist-tree-settings-v1"
        private const val KEY_TREE_URI = "playlist-tree-uri"

        fun pickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
        }
    }
}
