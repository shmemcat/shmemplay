package io.github.shmemcat.shmemplay.playlists

import io.github.shmemcat.shmemplay.domain.*
import io.github.shmemcat.shmemplay.tracks.LibrarySearch
import io.github.shmemcat.shmemplay.tracks.LibraryTrack

data class RuleValueChoice(val value: String, val label: String, val songCount: Int, val searchKey: String)

/** Built off the UI thread once per library revision; search never rescans audio metadata. */
class RuleLibraryIndex(val tracks: List<LibraryTrack>) {
    val universe = tracks.mapTo(linkedSetOf(), LibraryTrack::stableId)
    val metadata = tracks.associate { track ->
        fun known(value: String, fallback: String) = value.takeUnless { it == fallback || it == "<unknown>" }.orEmpty()
        track.stableId to RuleTrackMetadata(known(track.title, "Unknown title"), known(track.artist, "Unknown artist"), known(track.genre, "Unknown genre"))
    }
    private val choices by lazy { RuleField.entries.associateWith { field ->
        val values = linkedMapOf<String, Pair<String, Int>>()
        metadata.values.forEach { track ->
            val value = track.value(field)
            if (value.isNotBlank()) {
                val key = NestedPlaylistRules.normalize(value)
                val previous = values[key]
                values[key] = (previous?.first ?: value) to ((previous?.second ?: 0) + 1)
            }
        }
        values.values.map { (label, count) -> RuleValueChoice(label, label, count, LibrarySearch.normalize(label)) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, RuleValueChoice::label))
    }
    }
    fun choices(field: RuleField): List<RuleValueChoice> = choices.getValue(field)
    fun evaluate(rule: PlaylistRuleNode, sources: List<PlaylistSnapshot>, excludeDuplicates: Boolean): RecipeEvaluation = NestedPlaylistRules.evaluate(
        universe, sources.filter { it.sourceError == null }.associate { it.document.uri.toString() to it.resolvedTrackIds },
        rule, metadata, excludeDuplicates,
    )
    companion object {
        fun filter(choices: List<RuleValueChoice>, query: String): List<RuleValueChoice> {
            val terms = LibrarySearch.normalize(query).split(' ').filter(String::isNotBlank)
            return if (terms.isEmpty()) choices else choices.filter { choice -> terms.all(choice.searchKey::contains) }
        }
    }
}
