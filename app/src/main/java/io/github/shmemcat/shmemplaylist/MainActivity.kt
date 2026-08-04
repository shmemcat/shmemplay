package io.github.shmemcat.shmemplaylist

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import io.github.shmemcat.shmemplaylist.diagnostics.IntakeDiagnosticsViewModel
import io.github.shmemcat.shmemplaylist.intake.DeliveryKind
import io.github.shmemcat.shmemplaylist.playlists.PlaylistCoreViewModel
import io.github.shmemcat.shmemplaylist.playlists.PlaylistTreeSettings
import io.github.shmemcat.shmemplaylist.tracks.AudioPermissionPolicy
import io.github.shmemcat.shmemplaylist.tracks.ResolutionUiState
import io.github.shmemcat.shmemplaylist.ui.ShmemplaylistApp

class MainActivity : ComponentActivity() {
    private val diagnosticsViewModel: IntakeDiagnosticsViewModel by viewModels()
    private val playlistViewModel: PlaylistCoreViewModel by viewModels()
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
            LaunchedEffect(resolved?.identity?.candidate?.identity) {
                if (resolved != null) {
                    playlistViewModel.refreshAndScanMembership(resolved.identity, force = true)
                }
            }
            ShmemplaylistApp(
                state = resolutionState,
                playlistState = playlistViewModel.state.value,
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
