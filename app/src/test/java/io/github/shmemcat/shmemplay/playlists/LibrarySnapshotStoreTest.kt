package io.github.shmemcat.shmemplay.playlists

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.ContextWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import io.github.shmemcat.shmemplay.tracks.LibraryTrack
import io.github.shmemcat.shmemplay.tracks.MediaStoreIdentity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class LibrarySnapshotStoreTest {
    private fun track(id: Long = 1) = LibraryTrack(MediaStoreIdentity("external", id), Uri.parse("content://media/external/audio/media/$id"),
        "Café $id.mp3", "Music/Artist/", "Café $id", "Artist", "Album", "Rock", 123456, 42)

    @Test fun cacheSurvivesRecreationAndPreservesEmptyLibraryAndOptionalMetadata() {
        val directory = Files.createTempDirectory("library-cache-test").toFile()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) { override fun getFilesDir() = directory }
        try {
            val store = LibrarySnapshotStore(context)
            assertNull(store.load())
            val tracks = listOf(track(), track(2).copy(relativePath = null, albumId = null))
            store.save(LibrarySnapshot(tracks), store.generation)
            assertEquals(tracks, LibrarySnapshotStore(context).load()!!.tracks)
            store.save(LibrarySnapshot(emptyList()), store.generation)
            assertEquals(emptyList<LibraryTrack>(), LibrarySnapshotStore(context).load()!!.tracks)
        } finally { directory.deleteRecursively() }
    }

    @Test fun invalidationRejectsAnOlderScanAndCorruptionRequestsRebuild() {
        val directory = Files.createTempDirectory("library-cache-invalid").toFile()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) { override fun getFilesDir() = directory }
        try {
            val store = LibrarySnapshotStore(context)
            val oldGeneration = store.generation
            store.save(LibrarySnapshot(listOf(track())), oldGeneration)
            LibrarySnapshotStore(context).invalidate()
            store.save(LibrarySnapshot(listOf(track(2))), oldGeneration)
            assertNull(store.load())
            store.save(LibrarySnapshot(listOf(track(3))), store.generation)
            assertEquals(3, store.load()!!.tracks.single().identity.mediaId)
            File(directory, "library-snapshot-v1.bin").writeBytes(byteArrayOf(0, 1, 2))
            assertNull(store.load())
        } finally { directory.deleteRecursively() }
    }

    @Test fun folderChangesRelinkMembershipWithoutLosingUnresolvedEntriesOrDuplicates() {
        val track = track()
        val scan = PlaylistLibraryScan(listOf(PlaylistSnapshot(PlaylistDocument(Uri.parse("content://playlists/1"), "Test.m3u", null),
            listOf(PlaylistEntry(track.canonicalPlaylistPath!!, track), PlaylistEntry("Music/missing.mp3", null),
                PlaylistEntry(track.canonicalPlaylistPath!!, track)))), emptyList())
        val excluded = LibrarySnapshotStore.resolve(scan, emptyList())
        assertEquals(3, excluded.playlists.single().entries.size)
        assertTrue(excluded.playlists.single().resolvedTracks.isEmpty())
        assertEquals(listOf(track, track), LibrarySnapshotStore.resolve(excluded, listOf(track)).playlists.single().resolvedTracks)
    }

    @Test fun coldStartAndReturnUseSavedSongsUntilExplicitRescan() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        var queries = 0
        var providerUnavailable = false
        ShadowContentResolver.registerProviderInternal("media", object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): MatrixCursor? {
                queries++
                if (providerUnavailable) return null
                return MatrixCursor(arrayOf("_id"))
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        })
        val cache = LibrarySnapshotStore(app)
        cache.save(LibrarySnapshot(listOf(track())), cache.generation)
        val models = ViewModelStore()
        try {
            val model = LibraryBrowserViewModel(app)
            models.put("library", model)
            waitUntil { !model.state.value.loadingLibrary }
            assertEquals(listOf(track()), model.state.value.allTracks)
            model.revalidateAccess()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, queries)
            providerUnavailable = true
            model.refreshLibrary()
            waitUntil { !model.state.value.loadingLibrary && model.state.value.error != null }
            assertEquals(listOf(track()), model.state.value.allTracks)
            assertNull(cache.load()) // Provider failure must not be persisted as an empty library.
            providerUnavailable = false
            model.refreshLibrary()
            waitUntil { !model.state.value.loadingLibrary && queries > 0 }
            assertTrue(model.state.value.allTracks.isEmpty())
        } finally { models.clear(); cache.invalidate() }
    }

    private fun waitUntil(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10000
        while (!check() && System.currentTimeMillis() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertTrue("Timed out waiting for library", check())
    }
}
