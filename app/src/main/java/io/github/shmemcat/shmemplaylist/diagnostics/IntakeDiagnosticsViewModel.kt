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
import io.github.shmemcat.shmemplaylist.intake.UriEvidence
import io.github.shmemcat.shmemplaylist.persistence.AppDatabase
import io.github.shmemcat.shmemplaylist.tracks.ApprovedAliasRepository
import io.github.shmemcat.shmemplaylist.tracks.AudioPermissionPolicy
import io.github.shmemcat.shmemplaylist.tracks.CandidateQueryResult
import io.github.shmemcat.shmemplaylist.tracks.MediaStoreAudioLibraryRepository
import io.github.shmemcat.shmemplaylist.tracks.ResolutionResult
import io.github.shmemcat.shmemplaylist.tracks.ResolutionTier
import io.github.shmemcat.shmemplaylist.tracks.ResolutionUiState
import io.github.shmemcat.shmemplaylist.tracks.ResolvedTrackIdentity
import io.github.shmemcat.shmemplaylist.tracks.SharedTrackEvidence
import io.github.shmemcat.shmemplaylist.tracks.TrackCandidate
import io.github.shmemcat.shmemplaylist.tracks.TrackIdentityResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class IntakeDiagnosticsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val intake = ShareIntentIntake()
    private val probe = AndroidUriEvidenceProbe(application)
    private val library = MediaStoreAudioLibraryRepository(application)
    private val resolver = TrackIdentityResolver()
    private val aliases = ApprovedAliasRepository(AppDatabase.get(application).approvedAliasDao())
    private val mutableState = mutableStateOf<ResolutionUiState>(ResolutionUiState.Ready())
    private var probeJob: Job? = null
    private var pending: PendingResolution? = null
    private var generation = 0L

    val state: State<ResolutionUiState> = mutableState

    fun receive(intent: Intent, deliveryKind: DeliveryKind) {
        generation += 1
        val activeGeneration = generation
        when (val result = intake.receive(intent)) {
            is IntakeResult.NoShare -> {
                if (deliveryKind == DeliveryKind.WARM) {
                    probeJob = viewModelScope.launch {
                        mutableState.value = ResolutionUiState.Ready(aliases.list())
                    }
                }
            }

            is IntakeResult.InvalidShare -> {
                probeJob?.cancel()
                pending = null
                mutableState.value = ResolutionUiState.Invalid(
                    result.evidence.issue ?: "The shared payload is invalid.",
                )
            }

            is IntakeResult.Share -> {
                val uri = checkNotNull(result.evidence.uri)
                probeJob?.cancel()
                pending = null
                mutableState.value = ResolutionUiState.Probing
                probeJob = viewModelScope.launch {
                    val evidence = probe.probe(uri)
                    if (activeGeneration != generation) return@launch
                    val report = DiagnosticReportFormatter.format(deliveryKind, result.evidence, evidence)
                    val shared = evidence.toSharedEvidence()
                    pending = PendingResolution(shared, report)
                    if (AudioPermissionPolicy.isGranted(getApplication())) {
                        resolvePending(activeGeneration)
                    } else {
                        mutableState.value = ResolutionUiState.PermissionRequired
                    }
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!granted || pending == null) {
            mutableState.value = ResolutionUiState.PermissionRequired
            return
        }
        val activeGeneration = generation
        probeJob = viewModelScope.launch { resolvePending(activeGeneration) }
    }

    fun selectCandidate(candidate: TrackCandidate, remember: Boolean) {
        val current = mutableState.value as? ResolutionUiState.Ambiguous ?: return
        val selected = current.result.candidates.firstOrNull {
            it.candidate.identity == candidate.identity
        }?.candidate ?: return
        probeJob = viewModelScope.launch {
            if (remember) {
                aliases.remember(current.result.evidence, selected)
            }
            mutableState.value = ResolutionUiState.Resolved(
                ResolvedTrackIdentity(selected, ResolutionTier.MANUAL, true),
                current.report,
            )
        }
    }

    fun forgetAlias(aliasId: Long) {
        probeJob = viewModelScope.launch {
            aliases.forget(aliasId)
            mutableState.value = ResolutionUiState.Ready(aliases.list())
        }
    }

    fun loadAliases() {
        if (mutableState.value !is ResolutionUiState.Ready) return
        probeJob = viewModelScope.launch {
            mutableState.value = ResolutionUiState.Ready(aliases.list())
        }
    }

    private suspend fun resolvePending(activeGeneration: Long) {
        val session = pending ?: return
        if (!AudioPermissionPolicy.isGranted(getApplication())) {
            mutableState.value = ResolutionUiState.PermissionRequired
            return
        }
        mutableState.value = ResolutionUiState.Querying
        val savedAlias = aliases.find(session.evidence)
        val aliasCandidate = savedAlias?.let { library.getByIdentity(it.target) }
        val query = library.findCandidates(session.evidence)
        if (activeGeneration != generation) return
        when (query) {
            CandidateQueryResult.PermissionRequired ->
                mutableState.value = ResolutionUiState.PermissionRequired

            is CandidateQueryResult.Failed ->
                mutableState.value = ResolutionUiState.Failure(
                    "Audio-library query failed: ${query.category}",
                )

            is CandidateQueryResult.Success -> {
                val candidates = buildList {
                    addAll(query.candidates)
                    if (aliasCandidate != null && none { it.identity == aliasCandidate.identity }) {
                        add(aliasCandidate)
                    }
                }
                when (
                    val result = resolver.resolve(
                        session.evidence,
                        candidates,
                        aliasCandidate?.identity,
                    )
                ) {
                    is ResolutionResult.Resolved -> {
                        if (savedAlias != null && result.identity.tier == ResolutionTier.REVALIDATED_ALIAS) {
                            aliases.touch(savedAlias.aliasId)
                        }
                        mutableState.value = ResolutionUiState.Resolved(result.identity, session.report)
                    }

                    is ResolutionResult.Ambiguous ->
                        mutableState.value = ResolutionUiState.Ambiguous(result, session.report)

                    is ResolutionResult.NotFound ->
                        mutableState.value = ResolutionUiState.NotFound(result.evidence, session.report)
                }
            }
        }
    }
}

private data class PendingResolution(
    val evidence: SharedTrackEvidence,
    val report: DiagnosticReport,
)

private fun UriEvidence.toSharedEvidence() = SharedTrackEvidence(
    sourceAuthority = uriShape.authority,
    displayName = displayName.value,
    mimeType = resolverMimeType,
    sizeBytes = sizeBytes,
    durationMs = metadata?.durationMs,
    title = metadata?.title,
    artist = metadata?.artist,
    album = metadata?.album,
    trackNumber = metadata?.trackNumber,
    discNumber = metadata?.discNumber,
)
