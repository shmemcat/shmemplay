package io.github.shmemcat.shmemplay.domain

import java.text.Normalizer
import java.util.Locale

enum class RuleField { ARTIST, GENRE, TITLE }
enum class RuleOperator { IS, IS_NOT, IS_ANY_OF, CONTAINS, STARTS_WITH, HAS_NO_VALUE }

data class RuleTrackMetadata(val title: String, val artist: String, val genre: String) {
    fun value(field: RuleField): String = when (field) {
        RuleField.ARTIST -> artist
        RuleField.GENRE -> genre
        RuleField.TITLE -> title
    }
}

sealed interface PlaylistRuleNode {
    data class Membership(val source: String, val present: Boolean = true) : PlaylistRuleNode
    data class Metadata(val field: RuleField, val operator: RuleOperator, val values: List<String> = emptyList()) : PlaylistRuleNode
    data class Group(val match: RecipeMatch = RecipeMatch.ALL, val children: List<PlaylistRuleNode> = emptyList()) : PlaylistRuleNode
}

object NestedPlaylistRules {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    fun sources(node: PlaylistRuleNode): Set<String> = when (node) {
        is PlaylistRuleNode.Membership -> setOf(node.source)
        is PlaylistRuleNode.Metadata -> emptySet()
        is PlaylistRuleNode.Group -> node.children.flatMapTo(linkedSetOf()) { sources(it) }
    }

    fun evaluate(
        universe: Set<String>,
        memberships: Map<String, Set<String>>,
        node: PlaylistRuleNode,
        metadata: Map<String, RuleTrackMetadata> = emptyMap(),
        excludeDuplicates: Boolean = false,
    ): RecipeEvaluation {
        var count = 0
        val fields = linkedSetOf<RuleField>()
        fun validate(n: PlaylistRuleNode, depth: Int) {
            require(depth <= 8) { "Use at most eight levels of rule groups." }
            require(++count <= 256) { "Use at most 256 rules and groups." }
            when (n) {
                is PlaylistRuleNode.Membership -> require(n.source.isNotBlank()) { "Choose a source playlist." }
                is PlaylistRuleNode.Metadata -> {
                    fields += n.field
                    if (n.operator != RuleOperator.HAS_NO_VALUE) {
                        require(n.values.isNotEmpty() && n.values.all { it.isNotBlank() }) { "Choose a value for every rule." }
                        require(n.operator == RuleOperator.IS_ANY_OF || n.values.size == 1) { "Choose one value for this condition." }
                    }
                }
                is PlaylistRuleNode.Group -> {
                    require(n.children.isNotEmpty()) { "Add a rule to every group." }
                    n.children.forEach { validate(it, depth + 1) }
                }
            }
        }
        validate(node, 0)
        val unknown = sources(node).filterNot(memberships::containsKey).toSet()
        if (unknown.isNotEmpty()) return RecipeEvaluation.UnknownSources(unknown)
        require(fields.isEmpty() || universe.all(metadata::containsKey)) { "Song metadata is unavailable. Refresh the library." }
        val normalized = fields.associateWith { field -> metadata.mapValues { normalize(it.value.value(field)) } }
        fun compile(n: PlaylistRuleNode): (String) -> Boolean = when (n) {
            is PlaylistRuleNode.Membership -> { id -> (id in memberships.getValue(n.source)) == n.present }
            is PlaylistRuleNode.Metadata -> {
                val values = n.values.mapTo(hashSetOf(), ::normalize)
                val first = values.firstOrNull().orEmpty()
                val byId = normalized.getValue(n.field)
                val test: (String) -> Boolean = { id ->
                    val value = byId.getValue(id)
                    when (n.operator) {
                        RuleOperator.IS, RuleOperator.IS_ANY_OF -> value in values
                        RuleOperator.IS_NOT -> value !in values
                        RuleOperator.CONTAINS -> value.contains(first)
                        RuleOperator.STARTS_WITH -> value.startsWith(first)
                        RuleOperator.HAS_NO_VALUE -> value.isEmpty()
                    }
                }
                test
            }
            is PlaylistRuleNode.Group -> {
                val children = n.children.map(::compile)
                val test: (String) -> Boolean = { id ->
                    if (n.match == RecipeMatch.ALL) children.all { it(id) } else children.any { it(id) }
                }
                test
            }
        }
        val matches = compile(node)
        val seen = hashSetOf<Pair<String, String>>()
        return RecipeEvaluation.Success(universe.filterTo(linkedSetOf()) { id ->
            if (!matches(id)) false else if (!excludeDuplicates) true else {
                val track = metadata[id]
                // Missing tags must never collapse unrelated files into a single song.
                if (track == null || track.title.isBlank() || track.artist.isBlank()) true
                else seen.add(normalize(track.title) to normalize(track.artist))
            }
        })
    }

    fun replaceSource(node: PlaylistRuleNode, before: String, after: String): PlaylistRuleNode = when (node) {
        is PlaylistRuleNode.Membership -> if (node.source == before) node.copy(source = after) else node
        is PlaylistRuleNode.Metadata -> node
        is PlaylistRuleNode.Group -> node.copy(children = node.children.map { replaceSource(it, before, after) })
    }
}
