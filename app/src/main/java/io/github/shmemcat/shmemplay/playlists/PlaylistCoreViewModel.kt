package io.github.shmemcat.shmemplay.playlists

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shmemcat.shmemplay.domain.PhonePathV1
import io.github.shmemcat.shmemplay.domain.BatchAction
import io.github.shmemcat.shmemplay.operations.BatchPreview
import io.github.shmemcat.shmemplay.operations.CompanionAction
import io.github.shmemcat.shmemplay.operations.CompanionTestOperationCoordinator
import io.github.shmemcat.shmemplay.operations.MultiTargetJournalOperation
import io.github.shmemcat.shmemplay.operations.MultiTargetOperationCoordinator
import io.github.shmemcat.shmemplay.operations.OperationOutcome
import io.github.shmemcat.shmemplay.operations.PlaylistDocumentStorageResolver
import io.github.shmemcat.shmemplay.operations.RoomMultiTargetOperationJournal
import io.github.shmemcat.shmemplay.operations.RoomOperationJournal
import io.github.shmemcat.shmemplay.persistence.AppDatabase
import io.github.shmemcat.shmemplay.storage.CompanionTestPlaylistGate
import io.github.shmemcat.shmemplay.storage.CompanionTestProvisionResult
import io.github.shmemcat.shmemplay.storage.ExactByteBackupRepository
import io.github.shmemcat.shmemplay.storage.TreePlaylistDocumentStorageFactory
import io.github.shmemcat.shmemplay.tracks.ResolvedTrackIdentity
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
    val phaseSixTestMode: Boolean = false,
    val testPlaylistReady: Boolean = false,
    val operationState: PhaseSixOperationState = PhaseSixOperationState.Disabled,
    val phaseSeven: PhaseSevenOperationState = PhaseSevenOperationState.Idle,
    val operationHistory: List<MultiTargetJournalOperation> = emptyList(),
    val error: String? = null,
)

sealed interface PhaseSixOperationState {
    data object Disabled : PhaseSixOperationState
    data object Ready : PhaseSixOperationState
    data object Recovering : PhaseSixOperationState
    data class Applying(val action: CompanionAction) : PhaseSixOperationState
    data class Result(val outcome: OperationOutcome) : PhaseSixOperationState
    data class RecoveryRequired(val reason: String) : PhaseSixOperationState
}

sealed interface PhaseSevenOperationState {
    data object Idle : PhaseSevenOperationState
    data object Previewing : PhaseSevenOperationState
    data class PreviewReady(val preview: BatchPreview) : PhaseSevenOperationState
    data class ReconfirmationRequired(val preview: BatchPreview) : PhaseSevenOperationState
    data class ConfirmedReady(val preview: BatchPreview) : PhaseSevenOperationState
    data object Applying : PhaseSevenOperationState
    data class Result(val outcome: OperationOutcome) : PhaseSevenOperationState
    data object Recovering : PhaseSevenOperationState
    data class RecoveryRequired(val operationId: String) : PhaseSevenOperationState
}

class PlaylistCoreViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = PlaylistTreeSettings(application)
    private val trees = SafPlaylistTreeService(application)
    private val scanner = PlaylistMembershipScanner(application)
    private val testGate = CompanionTestPlaylistGate(application)
    private val database = AppDatabase.get(application)
    private val backups = ExactByteBackupRepository(application)
    private val documentStorage = TreePlaylistDocumentStorageFactory(application)
    private val mutableState = mutableStateOf(PlaylistCoreState(grant = settings.revalidate()))
    private var automaticScanJob: Job? = null
    private var automaticScanKey: String? = null
    private var coordinator: CompanionTestOperationCoordinator? = null
    private var lastSuccessfulOperationId: String? = null
    private var confirmedPhaseSevenPreview: BatchPreview? = null
    private val multiTargetCoordinator = MultiTargetOperationCoordinator(
        storageResolver = PlaylistDocumentStorageResolver(::openDiscoveredStorage),
        backups = backups,
        journal = RoomMultiTargetOperationJournal(database),
    )

    val state: State<PlaylistCoreState> = mutableState

    init {
        runCatching { buildCoordinator() }.getOrNull()?.let { existing ->
            coordinator = existing
            mutableState.value = mutableState.value.copy(testPlaylistReady = true)
            recover(existing)
        }
    }

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

    fun enablePhaseSixTestMode() {
        val grant = settings.revalidate()
        val ready = grant is PlaylistTreeGrantState.Valid &&
            grant.canWrite &&
            mutableState.value.capabilityReport?.fullySupported == true
        mutableState.value = mutableState.value.copy(
            grant = grant,
            phaseSixTestMode = ready,
            operationState = if (ready) PhaseSixOperationState.Ready else PhaseSixOperationState.Disabled,
            error = if (ready) null else "phase6-requires-writable-grant-and-capability-proof",
        )
    }

    fun provisionCompanionTestPlaylist(resolvedTrack: ResolvedTrackIdentity?) {
        if (!mutableState.value.phaseSixTestMode) {
            mutableState.value = mutableState.value.copy(error = "phase6-test-mode-disabled")
            return
        }
        val grant = settings.revalidate() as? PlaylistTreeGrantState.Valid ?: return
        if (!grant.canWrite || mutableState.value.capabilityReport?.fullySupported != true) {
            mutableState.value = mutableState.value.copy(error = "phase6-prerequisites-not-ready")
            return
        }
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { testGate.provision(grant.treeUri) }
            when (result) {
                is CompanionTestProvisionResult.Ready -> {
                    val built = buildCoordinator()
                    coordinator = built
                    mutableState.value = mutableState.value.copy(
                        busy = false,
                        testPlaylistReady = true,
                        operationState = PhaseSixOperationState.Ready,
                    )
                    recover(built)
                    if (resolvedTrack != null) refreshAndScanMembership(resolvedTrack, force = true)
                }
                is CompanionTestProvisionResult.Refused -> mutableState.value = mutableState.value.copy(
                    busy = false,
                    error = result.reason,
                )
            }
        }
    }

    fun applyCompanionTestOperation(action: CompanionAction, resolvedTrack: ResolvedTrackIdentity) {
        val active = coordinator ?: run {
            mutableState.value = mutableState.value.copy(error = "test-playlist-not-ready")
            return
        }
        if (!mutableState.value.phaseSixTestMode) {
            mutableState.value = mutableState.value.copy(error = "phase6-test-mode-disabled")
            return
        }
        val path = safeCandidatePath(resolvedTrack) ?: run {
            mutableState.value = mutableState.value.copy(error = "unsafe-track-path")
            return
        }
        mutableState.value = mutableState.value.copy(
            busy = true,
            operationState = PhaseSixOperationState.Applying(action),
            error = null,
        )
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                active.apply(
                    action = action,
                    canonicalNormalizedTrackPath = path,
                    trackIdentityRedacted = resolvedTrack.candidate.identity.let {
                        "${it.volumeName.hashCode().toUInt().toString(16)}:${it.mediaId}"
                    },
                    approvalSource = resolvedTrack.tier.name,
                )
            }
            if (outcome is OperationOutcome.Changed) lastSuccessfulOperationId = outcome.operationId
            mutableState.value = mutableState.value.copy(
                busy = false,
                operationState = outcome.toUiState(),
            )
            refreshChangedTestPlaylistMembership(resolvedTrack)
        }
    }

    fun undoLastCompanionTestOperation(resolvedTrack: ResolvedTrackIdentity) {
        val active = coordinator ?: return
        val id = lastSuccessfulOperationId ?: run {
            mutableState.value = mutableState.value.copy(error = "no-undoable-operation")
            return
        }
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { active.undo(id) }
            if (outcome is OperationOutcome.Changed) lastSuccessfulOperationId = null
            mutableState.value = mutableState.value.copy(
                busy = false,
                operationState = outcome.toUiState(),
            )
            refreshChangedTestPlaylistMembership(resolvedTrack)
        }
    }

    fun previewPlaylistBatch(
        action: BatchAction,
        selected: List<PlaylistDocument>,
        resolvedTrack: ResolvedTrackIdentity,
    ) {
        val path = safeCandidatePath(resolvedTrack) ?: run {
            mutableState.value = mutableState.value.copy(error = "unsafe-track-path")
            return
        }
        val grant = settings.revalidate() as? PlaylistTreeGrantState.Valid ?: run {
            mutableState.value = mutableState.value.copy(error = "playlist-tree-unavailable")
            return
        }
        if (!grant.canWrite) {
            mutableState.value = mutableState.value.copy(error = "playlist-tree-read-only")
            return
        }
        mutableState.value = mutableState.value.copy(
            busy = true,
            phaseSeven = PhaseSevenOperationState.Previewing,
            error = null,
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    multiTargetCoordinator.preview(
                        action,
                        path,
                        selected.map { documentStorage.open(grant.treeUri, it) },
                    )
                }
            }.fold(
                onSuccess = { preview ->
                    confirmedPhaseSevenPreview = null
                    mutableState.value = mutableState.value.copy(
                        busy = false,
                        phaseSeven = PhaseSevenOperationState.PreviewReady(preview),
                    )
                },
                onFailure = { fail(it) },
            )
        }
    }

    fun confirmPlaylistBatch(preview: BatchPreview) {
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { multiTargetCoordinator.confirm(preview) }
            }.fold(
                onSuccess = { confirmation ->
                    confirmedPhaseSevenPreview = confirmation.preview
                    mutableState.value = mutableState.value.copy(
                        busy = false,
                        phaseSeven = if (confirmation.requiresReconfirmation) {
                            PhaseSevenOperationState.ReconfirmationRequired(confirmation.preview)
                        } else {
                            PhaseSevenOperationState.ConfirmedReady(confirmation.preview)
                        },
                    )
                },
                onFailure = { fail(it) },
            )
        }
    }

    fun applyConfirmedPlaylistBatch(
        resolvedTrack: ResolvedTrackIdentity,
        preview: BatchPreview? = confirmedPhaseSevenPreview,
    ) {
        val confirmed = preview ?: run {
            mutableState.value = mutableState.value.copy(error = "batch-not-confirmed")
            return
        }
        mutableState.value = mutableState.value.copy(
            busy = true,
            phaseSeven = PhaseSevenOperationState.Applying,
            error = null,
        )
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                multiTargetCoordinator.apply(
                    confirmed,
                    resolvedTrack.candidate.identity.let {
                        "${it.volumeName.hashCode().toUInt().toString(16)}:${it.mediaId}"
                    },
                    resolvedTrack.tier.name,
                )
            }
            confirmedPhaseSevenPreview = null
            mutableState.value = mutableState.value.copy(
                busy = false,
                phaseSeven = PhaseSevenOperationState.Result(outcome),
            )
            refreshAndScanMembership(resolvedTrack, force = true)
        }
    }

    fun undoPlaylistOperation(operationId: String, resolvedTrack: ResolvedTrackIdentity) {
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                multiTargetCoordinator.undo(operationId)
            }
            mutableState.value = mutableState.value.copy(
                busy = false,
                phaseSeven = PhaseSevenOperationState.Result(outcome),
            )
            refreshAndScanMembership(resolvedTrack, force = true)
        }
    }

    fun loadOperationHistory() {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { multiTargetCoordinator.history() }
            mutableState.value = mutableState.value.copy(operationHistory = history)
        }
    }

    fun recoverPlaylistOperations() {
        mutableState.value = mutableState.value.copy(
            busy = true,
            phaseSeven = PhaseSevenOperationState.Recovering,
        )
        viewModelScope.launch {
            val outcomes = withContext(Dispatchers.IO) { multiTargetCoordinator.recover() }
            val blocked = outcomes.firstOrNull { it.state == io.github.shmemcat.shmemplay.operations.JournalState.RECOVERY_REQUIRED }
            mutableState.value = mutableState.value.copy(
                busy = false,
                phaseSeven = if (blocked == null) {
                    PhaseSevenOperationState.Idle
                } else {
                    PhaseSevenOperationState.RecoveryRequired(blocked.operationId)
                },
            )
        }
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
            val cachedPlaylists = mutableState.value.playlists
            val playlists = if (!force && cachedPlaylists.isNotEmpty()) {
                cachedPlaylists
            } else {
                val discovery = withContext(Dispatchers.IO) { trees.discoverDirectChildren(treeUri) }
                discovery.getOrElse {
                    fail(it)
                    return@launch
                }
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

    private fun buildCoordinator() = CompanionTestOperationCoordinator(
        storage = testGate.open(),
        backups = backups,
        journal = RoomOperationJournal(database),
    )

    private fun openDiscoveredStorage(documentIdentity: String) =
        (settings.revalidate() as? PlaylistTreeGrantState.Valid)?.let { grant ->
            mutableState.value.playlists.asSequence()
                .map { documentStorage.open(grant.treeUri, it) }
                .firstOrNull { it.handle.documentIdentity == documentIdentity }
        } ?: error("playlist-document-not-discovered")

    private fun recover(active: CompanionTestOperationCoordinator) {
        mutableState.value = mutableState.value.copy(operationState = PhaseSixOperationState.Recovering)
        viewModelScope.launch {
            val outcomes = withContext(Dispatchers.IO) { active.recover() }
            val blocked = outcomes.firstOrNull {
                it.state.name == "RECOVERY_REQUIRED"
            }
            mutableState.value = mutableState.value.copy(
                operationState = if (blocked == null) {
                    PhaseSixOperationState.Ready
                } else {
                    PhaseSixOperationState.RecoveryRequired("operation-${blocked.operationId.take(8)}")
                },
            )
        }
    }

    private fun safeCandidatePath(resolvedTrack: ResolvedTrackIdentity): String? {
        val candidate = resolvedTrack.candidate
        if (!candidate.identity.volumeName.equals("external_primary", ignoreCase = true)) return null
        val relative = candidate.relativePath?.takeIf(String::isNotBlank) ?: return null
        return PhonePathV1.normalize("$relative/${candidate.displayName}")?.takeIf(String::isNotBlank)
    }

    private fun refreshChangedTestPlaylistMembership(resolvedTrack: ResolvedTrackIdentity) {
        val current = mutableState.value.membership as? MembershipScanResult.Success ?: run {
            refreshAndScanMembership(resolvedTrack)
            return
        }
        val testPlaylist = mutableState.value.playlists.firstOrNull {
            it.displayName == "Shmemplaylist Companion Test.m3u"
        } ?: run {
            refreshAndScanMembership(resolvedTrack, force = true)
            return
        }
        viewModelScope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                scanner.scan(listOf(testPlaylist), resolvedTrack)
            } as? MembershipScanResult.Success ?: run {
                refreshAndScanMembership(resolvedTrack, force = true)
                return@launch
            }
            val replacement = refreshed.playlists.singleOrNull() ?: return@launch
            val merged = current.playlists
                .filterNot { it.playlist.uri == testPlaylist.uri }
                .plus(replacement)
            val warnings = current.warnings
                .filterNot { it.playlist.uri == testPlaylist.uri }
                .plus(refreshed.warnings)
            mutableState.value = mutableState.value.copy(
                membership = current.copy(playlists = merged, warnings = warnings),
                busy = false,
            )
        }
    }

    private fun OperationOutcome.toUiState(): PhaseSixOperationState = when (this) {
        is OperationOutcome.RecoveryRequired -> PhaseSixOperationState.RecoveryRequired(reason)
        else -> PhaseSixOperationState.Result(this)
    }
}
