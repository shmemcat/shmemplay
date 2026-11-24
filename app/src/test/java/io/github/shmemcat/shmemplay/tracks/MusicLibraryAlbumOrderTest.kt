package io.github.shmemcat.shmemplay.tracks

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.StorageVolumeBuilder
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MusicLibraryAlbumOrderTest {
    @Test @Config(sdk = [28])
    fun legacyLibraryUnpacksDiscAndTrackNumbersAndIgnoresUnknownPositions() = verifyPositions()

    @Test @Config(sdk = [35])
    fun modernLibraryPrefersExplicitDiscNumber() = verifyPositions()

    private fun verifyPositions() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val modern = Build.VERSION.SDK_INT >= 33
        shadowOf(app).grantPermissions(if (modern) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
        if (modern) {
            shadowOf(app.getSystemService(StorageManager::class.java)).addStorageVolume(
                StorageVolumeBuilder("primary", File("/storage/emulated/0"), "Primary", Process.myUserHandle(), Environment.MEDIA_MOUNTED)
                    .setIsPrimary(true).setIsEmulated(true).build(),
            )
        }
        ShadowContentResolver.registerProviderInternal("media", object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): MatrixCursor {
                val columns = requireNotNull(projection)
                if (uri.path!!.endsWith("/genres")) return MatrixCursor(columns)
                assertTrue("track" in columns)
                assertEquals(modern, "disc_number" in columns)
                return MatrixCursor(columns).apply {
                    listOf(1002, 2001, 0, null, 3000, -1).forEachIndexed { index, position ->
                        addRow(columns.map<String, Any?> { column ->
                            when (column) {
                                "_id" -> index + 1L
                                "_display_name" -> "$index.m4a"
                                "_data" -> "/storage/emulated/0/Music/$index.m4a"
                                "relative_path" -> "Music/"
                                "title" -> "Song $index"
                                "album" -> "Album"
                                "track" -> position
                                "disc_number" -> if (index == 1) 4 else 0
                                else -> null
                            }
                        }.toTypedArray())
                    }
                }
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        })
        val tracks = (MediaStoreMusicLibraryRepository(app).loadAll() as MusicLibraryResult.Success).tracks
        assertEquals(listOf(2, 1, null, null, null, null), tracks.map(LibraryTrack::trackNumber))
        assertEquals(listOf(1, if (modern) 4 else 2, null, null, 3, null), tracks.map(LibraryTrack::discNumber))
    }
}
