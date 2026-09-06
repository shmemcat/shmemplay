package io.github.shmemcat.shmemplay.diagnostics

sealed interface DiagnosticsState {
    data object Ready : DiagnosticsState

    data object Probing : DiagnosticsState

    data class Complete(val report: DiagnosticReport) : DiagnosticsState

    data class Invalid(val message: String) : DiagnosticsState
}
