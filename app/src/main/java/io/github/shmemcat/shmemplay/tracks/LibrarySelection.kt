package io.github.shmemcat.shmemplay.tracks

object LibrarySelection {
    fun selectAll(selected: List<String>, currentList: List<String>): List<String> =
        (selected + currentList).distinct()

    fun deselectAll(selected: List<String>, currentList: List<String>): List<String> {
        val local = currentList.toSet()
        return selected.filterNot { it in local }
    }

    fun invert(selected: List<String>, currentList: List<String>): List<String> {
        val selectedSet = selected.toSet()
        val local = currentList.distinct()
        val localSet = local.toSet()
        return selected.filterNot { it in localSet } + local.filterNot { it in selectedSet }
    }

    fun selectBetween(selected: List<String>, currentList: List<String>): List<String> {
        val selectedSet = selected.toSet()
        val local = currentList.distinct()
        val endpoints = local.indices.filter { local[it] in selectedSet }
        if (endpoints.size < 2) return selected
        return selectAll(selected, (endpoints.first()..endpoints.last()).map(local::get))
    }
}
