package io.github.shmemcat.shmemplaylist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.github.shmemcat.shmemplaylist.diagnostics.DiagnosticsState
import io.github.shmemcat.shmemplaylist.ui.theme.ShmemplaylistTheme

@Composable
fun ShmemplaylistApp(
    state: DiagnosticsState = DiagnosticsState.Ready,
    onExport: (String) -> Unit = {},
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
                    DiagnosticsState.Ready -> Text(
                        "Share a playing file from GoneMAD to inspect its redacted Android payload.",
                    )

                    DiagnosticsState.Probing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.semantics { testTag = "probe-progress" },
                        )
                        Text("Inspecting the shared URI…")
                    }

                    is DiagnosticsState.Invalid -> {
                        Text(
                            text = "Share could not be inspected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(state.message)
                    }

                    is DiagnosticsState.Complete -> {
                        Text(
                            text = "Redacted intake evidence",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.report.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { testTag = "diagnostic-report" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { onExport(state.report.text) },
                            modifier = Modifier.semantics { testTag = "export-diagnostics" },
                        ) {
                            Text("Export redacted diagnostics")
                        }
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
