package io.github.shmemcat.shmemplaylist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import io.github.shmemcat.shmemplaylist.diagnostics.IntakeDiagnosticsViewModel
import io.github.shmemcat.shmemplaylist.intake.DeliveryKind
import io.github.shmemcat.shmemplaylist.ui.ShmemplaylistApp

class MainActivity : ComponentActivity() {
    private val diagnosticsViewModel: IntakeDiagnosticsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShmemplaylistApp(
                state = diagnosticsViewModel.state.value,
                onExport = ::shareRedactedDiagnostics,
            )
        }
        if (savedInstanceState == null) {
            diagnosticsViewModel.receive(intent, DeliveryKind.COLD)
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
