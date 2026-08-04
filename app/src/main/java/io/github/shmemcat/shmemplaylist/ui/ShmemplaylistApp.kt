package io.github.shmemcat.shmemplaylist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.github.shmemcat.shmemplaylist.tracks.ResolutionUiState
import io.github.shmemcat.shmemplaylist.tracks.TrackCandidate
import io.github.shmemcat.shmemplaylist.ui.theme.ShmemplaylistTheme

@Composable
fun ShmemplaylistApp(
    state: ResolutionUiState = ResolutionUiState.Ready(),
    onExport: (String) -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onCandidateSelected: (TrackCandidate, Boolean) -> Unit = { _, _ -> },
    onForgetAlias: (Long) -> Unit = {},
) {
    ShmemplaylistTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Shmemplaylist",
                    modifier = Modifier.semantics { testTag = "app-title" },
                    style = MaterialTheme.typography.headlineMedium,
                )
                when (state) {
                    is ResolutionUiState.Ready -> {
                        Text(
                            "Share a playing file from GoneMAD to identify its Android audio-library track.",
                        )
                        if (state.aliases.isNotEmpty()) {
                            Text("Remembered matches", style = MaterialTheme.typography.titleMedium)
                            state.aliases.forEach { alias ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(alias.displayName)
                                    Text(alias.target.volumeName, style = MaterialTheme.typography.bodySmall)
                                    Button(onClick = { onForgetAlias(alias.aliasId) }) {
                                        Text("Forget")
                                    }
                                }
                            }
                        }
                    }

                    ResolutionUiState.Probing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.semantics { testTag = "probe-progress" },
                        )
                        Text("Inspecting the shared URI…")
                    }

                    ResolutionUiState.Querying -> {
                        CircularProgressIndicator()
                        Text("Finding the matching track in the Android audio library…")
                    }

                    ResolutionUiState.PermissionRequired -> {
                        Text(
                            "Audio access required",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Shmemplaylist needs audio-library access to match GoneMAD's " +
                                "temporary share to exactly one Android track.",
                        )
                        Button(onClick = onRequestPermission) {
                            Text("Allow audio access")
                        }
                    }

                    is ResolutionUiState.Invalid -> {
                        Text(
                            text = "Share could not be inspected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(state.message)
                    }

                    is ResolutionUiState.Failure -> {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is ResolutionUiState.NotFound -> {
                        Text("No safe match found", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "The shared file was readable, but no MediaStore track matched it. " +
                                "Rescan the library in GoneMAD/Android and share the file again.",
                        )
                        DiagnosticReport(state.report.text, onExport)
                    }

                    is ResolutionUiState.Resolved -> {
                        Text("Track resolved", style = MaterialTheme.typography.titleMedium)
                        Text("Filename: ${state.identity.candidate.displayName}")
                        Text(
                            "Location: ${state.identity.candidate.relativePath ?: "Path unavailable"}",
                        )
                        Text("Evidence tier: ${state.identity.tier.name.lowercase()}")
                        DiagnosticReport(state.report.text, onExport)
                    }

                    is ResolutionUiState.Ambiguous -> {
                        ManualResolution(
                            state = state,
                            onCandidateSelected = onCandidateSelected,
                        )
                        DiagnosticReport(state.report.text, onExport)
                    }
                }
                Text(
                    text = "Read-only proof: playlist access is not requested and playlist writes are disabled.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticReport(
    text: String,
    onExport: (String) -> Unit,
) {
    Text(
        text = "Redacted intake evidence",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "diagnostic-report" },
        style = MaterialTheme.typography.bodySmall,
    )
    Button(
        onClick = { onExport(text) },
        modifier = Modifier.semantics { testTag = "export-diagnostics" },
    ) {
        Text("Export redacted diagnostics")
    }
}

@Composable
private fun ManualResolution(
    state: ResolutionUiState.Ambiguous,
    onCandidateSelected: (TrackCandidate, Boolean) -> Unit,
) {
    var selected by remember(state) { mutableStateOf<TrackCandidate?>(null) }
    var rememberMatch by remember(state) { mutableStateOf(false) }
    Text("Choose the matching track", style = MaterialTheme.typography.titleMedium)
    Text("Several Android tracks remain possible. Nothing is selected automatically.")
    state.result.candidates.forEach { assessment ->
        val candidate = assessment.candidate
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected?.identity == candidate.identity,
                    onClick = { selected = candidate },
                )
                .padding(vertical = 8.dp),
        ) {
            RadioButton(
                selected = selected?.identity == candidate.identity,
                onClick = { selected = candidate },
            )
            Text(candidate.displayName, style = MaterialTheme.typography.titleSmall)
            Text(candidate.relativePath ?: candidate.identity.volumeName)
            Text(
                assessment.matches.joinToString { match ->
                    "${match.label}: ${if (match.matched) "match" else "different/unknown"}"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = rememberMatch,
                onClick = { rememberMatch = !rememberMatch },
            ),
    ) {
        Checkbox(
            checked = rememberMatch,
            onCheckedChange = { rememberMatch = it },
        )
        Text("Remember this match")
    }
    Button(
        enabled = selected != null,
        onClick = { selected?.let { onCandidateSelected(it, rememberMatch) } },
    ) {
        Text("Use selected track")
    }
}
