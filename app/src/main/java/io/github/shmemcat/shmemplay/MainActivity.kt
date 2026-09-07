package io.github.shmemcat.shmemplay

import android.content.ComponentName
import android.content.Intent
import android.app.SearchManager
import android.provider.MediaStore
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import io.github.shmemcat.shmemplay.player.PlaybackService
import io.github.shmemcat.shmemplay.player.PlayerRepository
import io.github.shmemcat.shmemplay.player.QueueMediaLibrary
import io.github.shmemcat.shmemplay.playlists.LibraryBrowserViewModel
import io.github.shmemcat.shmemplay.playlists.PlaylistTreeSettings
import io.github.shmemcat.shmemplay.tracks.AudioPermissionPolicy
import io.github.shmemcat.shmemplay.ui.LibraryBrowserActions
import io.github.shmemcat.shmemplay.ui.LibraryBrowserApp
import io.github.shmemcat.shmemplay.ui.theme.ShmemplayTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val browserViewModel: LibraryBrowserViewModel by viewModels()
    private val player by lazy { PlayerRepository.get(this) }
    private var controller: ListenableFuture<MediaController>? = null
    private var firstResume = true
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        browserViewModel.refreshLibrary()
    }
    private val treeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { browserViewModel.acceptTreeGrant(it, result.data?.flags ?: 0) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShmemplayTheme {
                LibraryBrowserApp(browserViewModel.state.value, LibraryBrowserActions(
                    requestAudioPermission = { permissionLauncher.launch(AudioPermissionPolicy.requiredPermission()) },
                    selectPlaylistFolder = { treeLauncher.launch(PlaylistTreeSettings.pickerIntent()) },
                    refreshLibrary = browserViewModel::refreshLibrary,
                    refreshPlaylists = browserViewModel::refreshPlaylists,
                    setFolderIncluded = browserViewModel::setFolderIncluded,
                    applyMembership = browserViewModel::applyMembership,
                    confirmMutation = browserViewModel::confirmPendingMutation,
                    createPlaylist = { name, tracks -> browserViewModel.createPlaylist(name, tracks) },
                    createNestedPlaylist = browserViewModel::createNestedPlaylist,
                    createRulePlaylist = browserViewModel::createRulePlaylist,
                    rerunRecipe = browserViewModel::rerunRecipe,
                    renamePlaylist = browserViewModel::renamePlaylist,
                    deletePlaylist = browserViewModel::deletePlaylist,
                    clearMutationMessage = browserViewModel::clearMutationMessage,
                ), player)
            }
        }
    }
    override fun onStart() {
        super.onStart()
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        controller = future
        future.addListener({ runCatching { handleSearchIntent(future.get()) }.onFailure { if (!future.isCancelled) player.reportError("Could not connect to playback: ${it.message}") } }, ContextCompat.getMainExecutor(this))
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        controller?.takeIf { it.isDone && !it.isCancelled }?.let { future ->
            runCatching { handleSearchIntent(future.get()) }.onFailure { player.reportError("Could not start voice search: ${it.message}") }
        }
    }
    private fun handleSearchIntent(mediaController: MediaController) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        // Consume once, so returning to the activity or rotating it cannot restart the song.
        intent.action = Intent.ACTION_MAIN
        lifecycleScope.launch {
            try {
                val book = player.awaitBook()
                val selection = if (query.isBlank()) book.activeId?.let { QueueMediaLibrary.Selection(it, null) }
                else withContext(Dispatchers.Default) {
                    QueueMediaLibrary.search(book, query).firstOrNull()?.let { QueueMediaLibrary.selection(it.mediaId) }
                }
                requireNotNull(selection) { "No matching song found in saved queues" }
                // Wait for successful resolution and preparation; a failed search must not play the old song.
                player.selectFromController(selection, null)
                mediaController.play()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                player.reportError("Could not play search: ${failure.message}")
            }
        }
    }
    override fun onStop() {
        player.checkpoint()
        controller?.let(MediaController::releaseFuture)
        controller = null
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        if (firstResume) firstResume = false else browserViewModel.refreshLibrary()
    }
}
