package io.github.shmemcat.shmemplay.playlists

import android.content.Context
import android.net.Uri
import io.github.shmemcat.shmemplay.domain.*
import io.github.shmemcat.shmemplay.tracks.LibraryTrack
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LocalPlaylistRecipe(val id: String, val name: String, val rule: PlaylistRuleNode, val live: Boolean = true, val excludeDuplicates: Boolean = false)

class LocalPlaylistRecipes(context: Context) {
    private val preferences = context.getSharedPreferences("nested-playlist-recipes-v1", Context.MODE_PRIVATE)
    fun load(): List<LocalPlaylistRecipe> {
        val array = JSONArray(preferences.getString("definitions", "[]"))
        return List(array.length()) { index -> array.getJSONObject(index).let {
            LocalPlaylistRecipe(it.getString("id"), it.getString("name"), decodeRule(it.getJSONObject("rule")), it.optBoolean("live",true), it.optBoolean("excludeDuplicates", false))
        } }
    }
    fun save(recipe: LocalPlaylistRecipe) {
        val existing = load()
        require(existing.none { it.id != recipe.id && it.name.equals(recipe.name,true) }) { "A local playlist already has this name." }
        write(existing.filterNot { it.id == recipe.id } + recipe)
    }
    fun remove(id: String) = write(load().filterNot { it.id == id })
    fun renameSource(before: String, after: String) = write(load().map { it.copy(rule = NestedPlaylistRules.replaceSource(it.rule,before,after)) })
    private fun write(recipes: List<LocalPlaylistRecipe>) {
        val array = JSONArray()
        recipes.forEach { array.put(JSONObject().put("id",it.id).put("name",it.name).put("live",it.live).put("excludeDuplicates",it.excludeDuplicates).put("rule",encodeRule(it.rule))) }
        check(preferences.edit().putString("definitions",array.toString()).commit()) { "Could not save local recipes." }
    }
    companion object {
        private data class CachedResult(val recipe: LocalPlaylistRecipe, val tracks: List<LibraryTrack>, val sources: List<PlaylistSnapshot>, val result: PlaylistSnapshot)
        private val evaluated = java.util.concurrent.ConcurrentHashMap<String,CachedResult>()
        fun encodeRule(rule: PlaylistRuleNode): JSONObject = when(rule) {
            is PlaylistRuleNode.Membership -> JSONObject().put("source",rule.source).put("present",rule.present)
            is PlaylistRuleNode.Metadata -> JSONObject().put("field",rule.field.name).put("operator",rule.operator.name).put("values",JSONArray(rule.values))
            is PlaylistRuleNode.Group -> JSONObject().put("match",rule.match.name).put("children",JSONArray().apply { rule.children.forEach { put(encodeRule(it)) } })
        }
        fun decodeRule(json: JSONObject, depth: Int = 0): PlaylistRuleNode {
            require(depth <= 8)
            return if(json.has("source")) PlaylistRuleNode.Membership(json.getString("source"),json.getBoolean("present"))
            else if(json.has("field")) PlaylistRuleNode.Metadata(RuleField.valueOf(json.getString("field")), RuleOperator.valueOf(json.getString("operator")), json.getJSONArray("values").let { a -> List(a.length()) { a.getString(it) } })
            else PlaylistRuleNode.Group(RecipeMatch.valueOf(json.getString("match")),json.getJSONArray("children").let { a ->
                require(a.length() <= 256); List(a.length()) { decodeRule(a.getJSONObject(it), depth+1) }
            })
        }
        fun evaluate(recipes: List<LocalPlaylistRecipe>, scan: PlaylistLibraryScan, tracks: List<LibraryTrack>): List<PlaylistSnapshot> {
            val library by lazy { RuleLibraryIndex(tracks) }
            evaluated.keys.retainAll(recipes.map { it.id }.toSet())
            return recipes.filter { it.live }.map { recipe ->
                val dependencies = NestedPlaylistRules.sources(recipe.rule)
                val sourceSnapshots = scan.playlists.filter { it.document.uri.toString() in dependencies }
                val previous = evaluated[recipe.id]
                if(previous != null && previous.recipe == recipe && previous.tracks == tracks && previous.sources == sourceSnapshots) return@map previous.result
                val result = runCatching { library.evaluate(recipe.rule, scan.playlists, recipe.excludeDuplicates) }
                val ids = (result.getOrNull() as? RecipeEvaluation.Success)?.trackIdentities
                PlaylistSnapshot(PlaylistDocument(Uri.parse("shmemplay-live:"+recipe.id),recipe.name,null),
                    if(ids == null) emptyList() else tracks.filter { it.stableId in ids }.map { PlaylistEntry(it.canonicalPlaylistPath.orEmpty(),it) },
                    live = true, sourceError = if(ids == null) "Source unavailable or rules invalid. Refresh or repair this live playlist." else null).also { evaluated[recipe.id] = CachedResult(recipe,tracks,sourceSnapshots,it) }
            }
        }
    }
}
