package io.github.shmemcat.shmemplay.domain

enum class BatchAction { ADD_ONE, REMOVE_ALL }

data class BatchTargetInput(
    val documentIdentity: String,
    val displayName: String,
    val originalBytes: ByteArray,
)

sealed interface BatchTargetDecision {
    val documentIdentity: String
    val displayName: String

    data class Change(
        override val documentIdentity: String,
        override val displayName: String,
        val originalBytes: ByteArray,
        val expectedBytes: ByteArray,
        val generatedPath: String,
        val originalOccurrenceIndexes: List<Int>,
        val expectedOccurrenceCount: Int,
    ) : BatchTargetDecision

    data class Skip(
        override val documentIdentity: String,
        override val displayName: String,
        val reason: String,
    ) : BatchTargetDecision

    data class Ineligible(
        override val documentIdentity: String,
        override val displayName: String,
        val reasons: List<String>,
    ) : BatchTargetDecision
}

data class BatchOperationPlan(
    val action: BatchAction,
    val generatedPath: String,
    val targets: List<BatchTargetDecision>,
) {
    val changes: List<BatchTargetDecision.Change>
        get() = targets.filterIsInstance<BatchTargetDecision.Change>()
}

/**
 * Pure planner for an ordered selection. It never drops or reorders targets and only emits
 * canonical writer bytes. Add appends exactly one absent occurrence; remove deletes all matches.
 */
object BatchOperationPlannerV1 {
    fun plan(
        action: BatchAction,
        canonicalNormalizedTrackPath: String,
        targets: List<BatchTargetInput>,
    ): BatchOperationPlan {
        val generatedPath = CanonicalAbsolutePrimaryPathPlan.generate(canonicalNormalizedTrackPath)
        require(targets.map { it.documentIdentity }.distinct().size == targets.size) {
            "duplicate-document-identity"
        }
        val decisions = targets.map { target ->
            val profile = CanonicalGoneMadProfileV1.validate(target.originalBytes)
            if (!profile.writable) {
                BatchTargetDecision.Ineligible(
                    target.documentIdentity,
                    target.displayName,
                    profile.reasons.map(CanonicalIneligibility::code),
                )
            } else {
                val indexes = profile.paths.indices.filter {
                    pathsEqual(profile.paths[it], generatedPath)
                }
                val expectedPaths = when (action) {
                    BatchAction.ADD_ONE ->
                        if (indexes.isEmpty()) profile.paths + generatedPath else profile.paths
                    BatchAction.REMOVE_ALL ->
                        profile.paths.filterNot { pathsEqual(it, generatedPath) }
                }
                if (expectedPaths == profile.paths) {
                    BatchTargetDecision.Skip(
                        target.documentIdentity,
                        target.displayName,
                        if (action == BatchAction.ADD_ONE) "already-present" else "already-absent",
                    )
                } else {
                    BatchTargetDecision.Change(
                        documentIdentity = target.documentIdentity,
                        displayName = target.displayName,
                        originalBytes = target.originalBytes.copyOf(),
                        expectedBytes = M3uWriterV1.write(expectedPaths),
                        generatedPath = generatedPath,
                        originalOccurrenceIndexes = indexes,
                        expectedOccurrenceCount = when (action) {
                            BatchAction.ADD_ONE -> 1
                            BatchAction.REMOVE_ALL -> 0
                        },
                    )
                }
            }
        }
        return BatchOperationPlan(action, generatedPath, decisions)
    }

    private fun pathsEqual(left: String, right: String): Boolean =
        PhonePathV1.normalize(left).equals(PhonePathV1.normalize(right), ignoreCase = true)
}
