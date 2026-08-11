package io.github.shmemcat.shmemplaylist

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import io.github.shmemcat.shmemplaylist.diagnostics.IntakeDiagnosticsViewModel
import io.github.shmemcat.shmemplaylist.domain.BatchAction
import io.github.shmemcat.shmemplaylist.domain.BatchTargetDecision
import io.github.shmemcat.shmemplaylist.intake.DeliveryKind
import io.github.shmemcat.shmemplaylist.operations.OperationOutcome
import io.github.shmemcat.shmemplaylist.playlists.PhaseSevenOperationState
import io.github.shmemcat.shmemplaylist.playlists.PlaylistCoreViewModel
import io.github.shmemcat.shmemplaylist.playlists.PlaylistTreeGrantState
import io.github.shmemcat.shmemplaylist.playlists.PlaylistTreeSettings
import io.github.shmemcat.shmemplaylist.tracks.AudioPermissionPolicy
import io.github.shmemcat.shmemplaylist.tracks.ResolutionUiState
import io.github.shmemcat.shmemplaylist.ui.MembershipOperation
import io.github.shmemcat.shmemplaylist.ui.MembershipOperationUiState
import io.github.shmemcat.shmemplaylist.ui.MembershipOperationsUiModel
import io.github.shmemcat.shmemplaylist.ui.ShmemplaylistApp

class MainActivity : ComponentActivity() {
    private val diagnosticsViewModel: IntakeDiagnosticsViewModel by viewModels()
    private val playlistViewModel: PlaylistCoreViewModel by viewModels()
    /** When true, finish after an uncomplicated successful add/remove so share callers return to the music app. */
    private var autoReturnAfterSuccessfulMutation = false
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        diagnosticsViewModel.onPermissionResult(granted)
    }
    private val treeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        playlistViewModel.acceptTreeGrant(uri, result.data?.flags ?: 0)
        val resolved = diagnosticsViewModel.state.value as? ResolutionUiState.Resolved
        if (resolved != null) playlistViewModel.refreshAndScanMembership(resolved.identity, force = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val resolutionState = diagnosticsViewModel.state.value
            val resolved = resolutionState as? ResolutionUiState.Resolved
            val playlistState = playlistViewModel.state.value
            LaunchedEffect(resolved?.identity?.candidate?.identity) {
                if (resolved != null) {
                    playlistViewModel.refreshAndScanMembership(resolved.identity)
                }
            }
            LaunchedEffect(Unit) {
                playlistViewModel.recoverPlaylistOperations()
                playlistViewModel.loadOperationHistory()
            }
            val phaseSeven = playlistState.phaseSeven
            LaunchedEffect(phaseSeven, resolved?.identity?.candidate?.identity) {
                when (phaseSeven) {
                    is PhaseSevenOperationState.PreviewReady ->
                        playlistViewModel.confirmPlaylistBatch(phaseSeven.preview)
                    is PhaseSevenOperationState.ConfirmedReady -> {
                        if (resolved != null) {
                            playlistViewModel.applyConfirmedPlaylistBatch(
                                resolved.identity,
                                phaseSeven.preview,
                            )
                        }
                    }
                    is PhaseSevenOperationState.Result -> {
                        val shouldReturn =
                            autoReturnAfterSuccessfulMutation &&
                                phaseSeven.outcome is OperationOutcome.Changed
                        autoReturnAfterSuccessfulMutation = false
                        if (shouldReturn) finish()
                    }
                    else -> Unit
                }
            }
            val membershipOperations = MembershipOperationsUiModel(
                enabled = resolved != null &&
                    (playlistState.grant as? PlaylistTreeGrantState.Valid)?.canWrite == true &&
                    phaseSeven !is PhaseSevenOperationState.RecoveryRequired,
                state = when (phaseSeven) {
                    PhaseSevenOperationState.Idle -> MembershipOperationUiState.Ready
                    PhaseSevenOperationState.Previewing ->
                        MembershipOperationUiState.Running("Reading selected playlists…")
                    is PhaseSevenOperationState.PreviewReady ->
                        MembershipOperationUiState.Running("Checking playlist contents…")
                    is PhaseSevenOperationState.ReconfirmationRequired ->
                        MembershipOperationUiState.Confirmation(
                            phaseSeven.preview.summary(),
                            requiresReconfirmation = true,
                        )
                    is PhaseSevenOperationState.ConfirmedReady ->
                        MembershipOperationUiState.Running("Starting verified changes…")
                    PhaseSevenOperationState.Applying ->
                        MembershipOperationUiState.Running(
                            "Backing up, writing, rereading, and verifying…",
                        )
                    is PhaseSevenOperationState.Result -> phaseSeven.outcome.toMembershipUiState()
                    PhaseSevenOperationState.Recovering ->
                        MembershipOperationUiState.Running("Recovering interrupted operations…")
                    is PhaseSevenOperationState.RecoveryRequired ->
                        MembershipOperationUiState.RecoveryRequired(
                            "Recovery is required for operation ${phaseSeven.operationId.take(8)}. " +
                                "New writes are blocked.",
                        )
                },
                onApply = { operation, selectedUris ->
                    val selected = playlistState.playlists.filter {
                        it.uri.toString() in selectedUris
                    }
                    if (resolved != null) {
                        autoReturnAfterSuccessfulMutation = true
                        playlistViewModel.previewPlaylistBatch(
                            action = if (operation == MembershipOperation.ADD) {
                                BatchAction.ADD_ONE
                            } else {
                                BatchAction.REMOVE_ALL
                            },
                            selected = selected,
                            resolvedTrack = resolved.identity,
                        )
                    }
                },
                onConfirm = {
                    when (val current = playlistViewModel.state.value.phaseSeven) {
                        is PhaseSevenOperationState.ReconfirmationRequired ->
                            playlistViewModel.confirmPlaylistBatch(current.preview)
                        else -> Unit
                    }
                },
                onUndo = {
                    val outcome = (
                        playlistViewModel.state.value.phaseSeven as? PhaseSevenOperationState.Result
                        )?.outcome as? OperationOutcome.Changed
                    if (resolved != null && outcome != null) {
                        playlistViewModel.undoPlaylistOperation(outcome.operationId, resolved.identity)
                    }
                },
                onRecover = playlistViewModel::recoverPlaylistOperations,
            )
            ShmemplaylistApp(
                state = resolutionState,
                playlistState = playlistState,
                membershipOperations = membershipOperations,
                onExport = ::shareRedactedDiagnostics,
                onRequestPermission = {
                    permissionLauncher.launch(AudioPermissionPolicy.requiredPermission())
                },
                onCandidateSelected = diagnosticsViewModel::selectCandidate,
                onForgetAlias = diagnosticsViewModel::forgetAlias,
                onSelectPlaylistTree = {
                    treeLauncher.launch(PlaylistTreeSettings.pickerIntent())
                },
                onClearPlaylistTree = playlistViewModel::clearTree,
                onDiscoverPlaylists = {
                    if (resolved != null) {
                        playlistViewModel.refreshAndScanMembership(resolved.identity, force = true)
                    } else {
                        playlistViewModel.discoverPlaylists()
                    }
                },
                onTestProviderCapabilities = playlistViewModel::testProviderCapabilities,
                onScanMembership = {
                    if (resolved != null) {
                        playlistViewModel.refreshAndScanMembership(resolved.identity, force = true)
                    }
                },
                onShowHistory = playlistViewModel::loadOperationHistory,
            )
        }
        if (savedInstanceState == null) {
            diagnosticsViewModel.receive(intent, DeliveryKind.COLD)
            if (intent.action != Intent.ACTION_SEND) {
                diagnosticsViewModel.loadAliases()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        diagnosticsViewModel.receive(intent, DeliveryKind.WARM)
    }

    private fun shareRedactedDiagnostics(text: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Shmemplaylist redacted intake diagnostic")
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Export redacted diagnostics",
            ),
        )
    }
}

private fun io.github.shmemcat.shmemplaylist.operations.BatchPreview.summary(): String {
    val changed = plan.targets.count { it is BatchTargetDecision.Change }
    val skipped = plan.targets.count { it is BatchTargetDecision.Skip }
    val blocked = plan.targets.count { it is BatchTargetDecision.Ineligible }
    return buildString {
        append(changed)
        append(" playlist(s) will change")
        if (skipped > 0) append(", $skipped will be skipped")
        if (blocked > 0) append(", and $blocked are read-only")
        append(". Every changed playlist will be backed up and verified.")
    }
}

private fun OperationOutcome.toMembershipUiState(): MembershipOperationUiState = when (this) {
    is OperationOutcome.Changed -> MembershipOperationUiState.Result(
        "Verified $occurrencesChanged occurrence change(s).",
        canUndo = true,
    )
    is OperationOutcome.Skipped -> MembershipOperationUiState.Result("No change: $reason.")
    is OperationOutcome.FailedSafe ->
        MembershipOperationUiState.Result("Operation failed safely: $reason.")
    is OperationOutcome.RecoveryRequired ->
        MembershipOperationUiState.RecoveryRequired("Recovery required: $reason.")
    is OperationOutcome.UndoRefused ->
        MembershipOperationUiState.Result("Undo was refused safely: $reason.")
}
