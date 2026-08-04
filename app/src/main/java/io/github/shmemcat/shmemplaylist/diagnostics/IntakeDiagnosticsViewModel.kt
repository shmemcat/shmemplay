package io.github.shmemcat.shmemplaylist.diagnostics

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shmemcat.shmemplaylist.intake.AndroidUriEvidenceProbe
import io.github.shmemcat.shmemplaylist.intake.DeliveryKind
import io.github.shmemcat.shmemplaylist.intake.IntakeResult
import io.github.shmemcat.shmemplaylist.intake.ShareIntentIntake
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class IntakeDiagnosticsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val intake = ShareIntentIntake()
    private val probe = AndroidUriEvidenceProbe(application)
    private val mutableState = mutableStateOf<DiagnosticsState>(DiagnosticsState.Ready)
    private var probeJob: Job? = null

    val state: State<DiagnosticsState> = mutableState

    fun receive(intent: Intent, deliveryKind: DeliveryKind) {
        when (val result = intake.receive(intent)) {
            is IntakeResult.NoShare -> {
                if (deliveryKind == DeliveryKind.WARM) {
                    mutableState.value = DiagnosticsState.Ready
                }
            }

            is IntakeResult.InvalidShare -> {
                probeJob?.cancel()
                mutableState.value = DiagnosticsState.Invalid(
                    result.evidence.issue ?: "The shared payload is invalid.",
                )
            }

            is IntakeResult.Share -> {
                val uri = checkNotNull(result.evidence.uri)
                probeJob?.cancel()
                mutableState.value = DiagnosticsState.Probing
                probeJob = viewModelScope.launch {
                    val evidence = probe.probe(uri)
                    mutableState.value = DiagnosticsState.Complete(
                        DiagnosticReportFormatter.format(deliveryKind, result.evidence, evidence),
                    )
                }
            }
        }
    }
}
