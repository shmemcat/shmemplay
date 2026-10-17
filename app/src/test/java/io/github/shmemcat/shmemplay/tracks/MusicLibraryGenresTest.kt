package io.github.shmemcat.shmemplay.tracks

import android.Manifest
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MusicLibraryGenresTest {
    @Test fun embeddedGenresRecoverValuesMissingFromAndroidAndBadFilesKeepFallback() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        ShadowContentResolver.registerProviderInternal("media", object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): MatrixCursor {
                if (uri.path!!.endsWith("/genres")) return MatrixCursor(arrayOf("_id", "name")).apply { addRow(arrayOf<Any>(1L, "Rock")) }
                if (uri.path!!.contains("/genres/")) return MatrixCursor(arrayOf("audio_id")).apply { (1L..3).forEach { addRow(arrayOf(it)) } }
                return MatrixCursor(arrayOf("_id", "_display_name", "_data", "title", "artist", "album", "duration", "album_id")).apply {
                    (1L..3).forEach { addRow(arrayOf<Any>(it, "$it.mp3", "/storage/emulated/0/Music/$it.mp3", "Song $it", "Artist", "Album", 60000L, 1L)) }
                }
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        })
        val collection = MediaStore.Audio.Media.getContentUri("external")
        val payload = byteArrayOf(3) + "Rock\u0000Dream pop; Shoegaze".toByteArray()
        fun sync(size: Int) = ByteArray(4) { (size ushr ((3 - it) * 7) and 127).toByte() }
        val frame = "TCON".toByteArray() + sync(payload.size) + byteArrayOf(0, 0) + payload
        val tag = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + sync(frame.size) + frame
        shadowOf(app.contentResolver).registerInputStream(ContentUris.withAppendedId(collection, 1), tag.inputStream())
        shadowOf(app.contentResolver).registerInputStream(ContentUris.withAppendedId(collection, 2), byteArrayOf(1, 2).inputStream())
        // File 3 has no readable stream; it must not discard the rest of the library.
        val tracks = (MediaStoreMusicLibraryRepository(app).loadAll() as MusicLibraryResult.Success).tracks
        assertEquals(listOf("Rock", "Dream pop", "Shoegaze"), tracks[0].genres)
        assertEquals(listOf("Rock"), tracks[1].genres)
        assertEquals(listOf("Rock"), tracks[2].genres)
    }
}
