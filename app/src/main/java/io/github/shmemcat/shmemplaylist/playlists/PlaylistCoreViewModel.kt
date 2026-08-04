package io.github.shmemcat.shmemplaylist.playlists

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shmemcat.shmemplaylist.tracks.ResolvedTrackIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaylistCoreState(
    val grant: PlaylistTreeGrantState = PlaylistTreeGrantState.NotConfigured,
    val playlists: List<PlaylistDocument> = emptyList(),
    val capabilityReport: ProviderCapabilityReport? = null,
    val membership: MembershipScanResult? = null,
    val busy: Boolean = false,
    val scanCompleted: Int = 0,
    val scanTotal: Int = 0,
    val error: String? = null,
)

/**
 * Non-UI integration surface for Phase 5. No method mutates a real playlist.
 */
class PlaylistCoreViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = PlaylistTreeSettings(application)
    private val trees = SafPlaylistTreeService(application)
    private val scanner = PlaylistMembershipScanner(application)
    private val mutableState = mutableStateOf(PlaylistCoreState(grant = settings.revalidate()))
    private var automaticScanJob: Job? = null
    private var automaticScanKey: String? = null

    val state: State<PlaylistCoreState> = mutableState

    fun acceptTreeGrant(treeUri: Uri, grantFlags: Int) {
        mutableState.value = PlaylistCoreState(grant = settings.saveGrantedTree(treeUri, grantFlags))
    }

    fun revalidateGrant() {
        mutableState.value = mutableState.value.copy(grant = settings.revalidate(), error = null)
    }

    fun clearTree() {
        settings.clear()
        mutableState.value = PlaylistCoreState()
    }

    fun discoverPlaylists() = withValidTree { treeUri ->
        val result = withContext(Dispatchers.IO) { trees.discoverDirectChildren(treeUri) }
        result.fold(
            onSuccess = { playlists ->
                mutableState.value = mutableState.value.copy(
                    playlists = playlists,
                    membership = null,
                    busy = false,
                    error = null,
                )
            },
            onFailure = { fail(it) },
        )
    }

    fun testProviderCapabilities() = withValidTree { treeUri ->
        val report = withContext(Dispatchers.IO) {
            trees.testDisposableCapabilities(treeUri)
        }
        mutableState.value = mutableState.value.copy(
            capabilityReport = report,
            busy = false,
            error = null,
        )
    }

    fun scanMembership(resolvedTrack: ResolvedTrackIdentity) {
        val playlists = mutableState.value.playlists
        mutableState.value = mutableState.value.copy(
            busy = true,
            scanCompleted = 0,
            scanTotal = playlists.size,
            error = null,
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                scanner.scan(playlists, resolvedTrack) { completed, total ->
                    mutableState.value = mutableState.value.copy(
                        scanCompleted = completed,
                        scanTotal = total,
                    )
                }
            }
            mutableState.value = mutableState.value.copy(
                membership = result,
                busy = false,
                error = null,
            )
        }
    }

    fun refreshAndScanMembership(resolvedTrack: ResolvedTrackIdentity, force: Boolean = false) {
        val identity = resolvedTrack.candidate.identity
        val key = "${identity.volumeName}:${identity.mediaId}"
        if (!force && automaticScanKey == key && automaticScanJob?.isActive == true) return
        if (!force && automaticScanKey == key && mutableState.value.membership != null) return
        automaticScanKey = key
        automaticScanJob?.cancel()

        val grant = settings.revalidate()
        mutableState.value = mutableState.value.copy(
            grant = grant,
            membership = null,
            busy = grant is PlaylistTreeGrantState.Valid,
            scanCompleted = 0,
            scanTotal = 0,
            error = if (
                grant is PlaylistTreeGrantState.Valid ||
                grant is PlaylistTreeGrantState.NotConfigured
            ) null else "playlist-tree-unavailable",
        )
        val treeUri = (grant as? PlaylistTreeGrantState.Valid)?.treeUri ?: return
        automaticScanJob = viewModelScope.launch {
            val discovery = withContext(Dispatchers.IO) { trees.discoverDirectChildren(treeUri) }
            val playlists = discovery.getOrElse {
                fail(it)
                return@launch
            }
            mutableState.value = mutableState.value.copy(
                playlists = playlists,
                scanTotal = playlists.size,
                error = null,
            )
            val result = withContext(Dispatchers.IO) {
                scanner.scan(playlists, resolvedTrack) { completed, total ->
                    mutableState.value = mutableState.value.copy(
                        scanCompleted = completed,
                        scanTotal = total,
                    )
                }
            }
            mutableState.value = mutableState.value.copy(
                membership = result,
                busy = false,
                error = null,
            )
        }
    }

    private fun withValidTree(block: suspend (Uri) -> Unit) {
        val grant = settings.revalidate()
        mutableState.value = mutableState.value.copy(grant = grant, busy = true, error = null)
        val treeUri = (grant as? PlaylistTreeGrantState.Valid)?.treeUri
        if (treeUri == null) {
            mutableState.value = mutableState.value.copy(busy = false, error = "playlist-tree-unavailable")
            return
        }
        viewModelScope.launch {
            block(treeUri)
        }
    }

    private fun fail(failure: Throwable) {
        mutableState.value = mutableState.value.copy(
            busy = false,
            error = failure.javaClass.simpleName,
        )
    }
}
