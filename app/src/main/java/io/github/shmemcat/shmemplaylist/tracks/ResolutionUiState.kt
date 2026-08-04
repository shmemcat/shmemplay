package io.github.shmemcat.shmemplaylist.tracks

import io.github.shmemcat.shmemplaylist.diagnostics.DiagnosticReport

sealed interface ResolutionUiState {
    data class Ready(
        val aliases: List<ApprovedAlias> = emptyList(),
    ) : ResolutionUiState

    data object PermissionRequired : ResolutionUiState

    data object Probing : ResolutionUiState

    data object Querying : ResolutionUiState

    data class Resolved(
        val identity: ResolvedTrackIdentity,
        val report: DiagnosticReport,
    ) : ResolutionUiState

    data class Ambiguous(
        val result: ResolutionResult.Ambiguous,
        val report: DiagnosticReport,
    ) : ResolutionUiState

    data class NotFound(
        val evidence: SharedTrackEvidence,
        val report: DiagnosticReport,
    ) : ResolutionUiState

    data class Invalid(val message: String) : ResolutionUiState

    data class Failure(val message: String) : ResolutionUiState
}
