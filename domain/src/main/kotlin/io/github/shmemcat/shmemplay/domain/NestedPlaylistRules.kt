package io.github.shmemcat.shmemplay.domain

sealed interface PlaylistRuleNode {
    data class Membership(val source: String, val present: Boolean = true) : PlaylistRuleNode
    data class Group(val match: RecipeMatch = RecipeMatch.ALL, val children: List<PlaylistRuleNode> = emptyList()) : PlaylistRuleNode
}

object NestedPlaylistRules {
    fun sources(node: PlaylistRuleNode): Set<String> = when (node) {
        is PlaylistRuleNode.Membership -> setOf(node.source)
        is PlaylistRuleNode.Group -> node.children.flatMapTo(linkedSetOf()) { sources(it) }
    }
    fun evaluate(universe: Set<String>, memberships: Map<String, Set<String>>, node: PlaylistRuleNode): RecipeEvaluation {
        fun validate(n: PlaylistRuleNode, depth: Int): Int {
            require(depth <= 8) { "Use at most eight levels of rule groups." }
            return when(n) {
                is PlaylistRuleNode.Membership -> { require(n.source.isNotBlank()); 1 }
                is PlaylistRuleNode.Group -> {
                    require(n.children.isNotEmpty()) { "Add a rule to every group." }
                    1 + n.children.sumOf { validate(it, depth + 1) }
                }
            }
        }
        require(validate(node, 0) <= 256) { "Use at most 256 rules and groups." }
        val unknown = sources(node).filterNot(memberships::containsKey).toSet()
        if (unknown.isNotEmpty()) return RecipeEvaluation.UnknownSources(unknown)
        fun matches(id: String, n: PlaylistRuleNode): Boolean = when(n) {
            is PlaylistRuleNode.Membership -> (id in memberships.getValue(n.source)) == n.present
            is PlaylistRuleNode.Group -> if(n.match == RecipeMatch.ALL) n.children.all { matches(id,it) } else n.children.any { matches(id,it) }
        }
        return RecipeEvaluation.Success(universe.filterTo(linkedSetOf()) { matches(it, node) })
    }
    fun replaceSource(node: PlaylistRuleNode, before: String, after: String): PlaylistRuleNode = when(node) {
        is PlaylistRuleNode.Membership -> if(node.source == before) node.copy(source = after) else node
        is PlaylistRuleNode.Group -> node.copy(children = node.children.map { replaceSource(it,before,after) })
    }
}
