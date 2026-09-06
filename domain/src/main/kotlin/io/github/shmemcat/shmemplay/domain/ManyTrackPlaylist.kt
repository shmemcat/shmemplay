package io.github.shmemcat.shmemplay.domain

/**
 * Plans one verified rewrite per selected playlist for an ordered set of tracks.
 * Existing playlist order and duplicate occurrences are preserved. ADD_ONE appends only
 * selected paths that are completely absent; REMOVE_ALL removes every occurrence of every
 * selected path.
 */
object ManyTrackBatchOperationPlannerV2 {
    fun plan(
        action: BatchAction,
        canonicalNormalizedTrackPaths: List<String>,
        targets: List<BatchTargetInput>,
    ): BatchOperationPlan {
        require(targets.map { it.documentIdentity }.distinct().size == targets.size) {
            "duplicate-document-identity"
        }
        val normalizedPaths = canonicalNormalizedTrackPaths
            .map { path ->
                require(path.isNotBlank()) { "track-path-is-blank" }
                require(PhonePathV1.normalize(path) == path) { "track-path-is-not-canonical" }
                path
            }
            .distinctBy(::pathKey)
        require(normalizedPaths.isNotEmpty()) { "no-track-paths" }

        val generatedPaths = normalizedPaths.map(CanonicalAbsolutePrimaryPathPlan::generate)
        val selectedKeys = generatedPaths.mapTo(linkedSetOf(), ::pathKey)
        val operationLabel = if (generatedPaths.size == 1) {
            generatedPaths.single()
        } else {
            "${generatedPaths.first()} (+${generatedPaths.size - 1})"
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
                val originalIndexes = profile.paths.indices.filter { index ->
                    pathKey(profile.paths[index]) in selectedKeys
                }
                val expectedPaths = when (action) {
                    BatchAction.ADD_ONE -> {
                        val existingKeys = profile.paths.mapTo(hashSetOf(), ::pathKey)
                        profile.paths + generatedPaths.filter { pathKey(it) !in existingKeys }
                    }
                    BatchAction.REMOVE_ALL -> profile.paths.filter { pathKey(it) !in selectedKeys }
                }
                if (expectedPaths == profile.paths) {
                    BatchTargetDecision.Skip(
                        target.documentIdentity,
                        target.displayName,
                        if (action == BatchAction.ADD_ONE) "all-already-present" else "all-already-absent",
                    )
                } else {
                    BatchTargetDecision.Change(
                        documentIdentity = target.documentIdentity,
                        displayName = target.displayName,
                        originalBytes = target.originalBytes.copyOf(),
                        expectedBytes = M3uWriterV1.write(expectedPaths),
                        generatedPath = operationLabel,
                        originalOccurrenceIndexes = originalIndexes,
                        expectedOccurrenceCount = expectedPaths.count { pathKey(it) in selectedKeys },
                    )
                }
            }
        }
        return BatchOperationPlan(action, operationLabel, decisions)
    }

    private fun pathKey(path: String): String =
        PhonePathV1.normalize(path).orEmpty().lowercase()
}

enum class RecipeMatch { ALL, ANY }

data class PlaylistRule(
    val playlistIdentity: String,
    val mustBePresent: Boolean,
)

data class PlaylistRecipe(
    val name: String,
    val match: RecipeMatch,
    val rules: List<PlaylistRule>,
)

sealed interface RecipeEvaluation {
    data class Success(val trackIdentities: Set<String>) : RecipeEvaluation
    data class UnknownSources(val playlistIdentities: Set<String>) : RecipeEvaluation
}

/** Pure set evaluator used by playlist recipe previews and explicit reruns. */
object PlaylistRecipeEvaluatorV1 {
    fun evaluate(
        allTrackIdentities: Set<String>,
        memberships: Map<String, Set<String>>,
        recipe: PlaylistRecipe,
    ): RecipeEvaluation {
        val unknown = recipe.rules.map(PlaylistRule::playlistIdentity)
            .filterNot(memberships::containsKey)
            .toSet()
        if (unknown.isNotEmpty()) return RecipeEvaluation.UnknownSources(unknown)
        if (recipe.rules.isEmpty()) return RecipeEvaluation.Success(emptySet())

        val result = allTrackIdentities.filterTo(linkedSetOf()) { track ->
            val matches = recipe.rules.map { rule ->
                val present = track in checkNotNull(memberships[rule.playlistIdentity])
                if (rule.mustBePresent) present else !present
            }
            when (recipe.match) {
                RecipeMatch.ALL -> matches.all { it }
                RecipeMatch.ANY -> matches.any { it }
            }
        }
        return RecipeEvaluation.Success(result)
    }
}
