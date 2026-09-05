package io.github.shmemcat.shmemplaylist.settings

import android.content.Context

class LibraryFolderSettings(context: Context) {
    private val preferences = context.getSharedPreferences("library-folders-v1", Context.MODE_PRIVATE)

    fun load(): Set<String>? = if (preferences.getBoolean(CONFIGURED, false)) {
        preferences.getStringSet(FOLDERS, emptySet())?.toSet().orEmpty()
    } else {
        null
    }

    fun save(folders: Set<String>) {
        preferences.edit()
            .putBoolean(CONFIGURED, true)
            .putStringSet(FOLDERS, folders)
            .apply()
    }

    private companion object {
        const val CONFIGURED = "configured"
        const val FOLDERS = "folders"
    }
}
