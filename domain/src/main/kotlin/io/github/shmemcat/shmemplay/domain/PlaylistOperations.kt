package io.github.shmemcat.shmemplay.domain

object PlaylistOperationsV1 {
    fun occurrenceIndexes(
        paths: List<VolumePath>,
        target: VolumePath,
        casePolicy: PathCasePolicy,
    ): List<Int> = paths.indices.filter {
        VolumeAwareComparison.matches(paths[it], target, casePolicy)
    }

    fun addOneIfAbsent(
        paths: List<VolumePath>,
        target: VolumePath,
        casePolicy: PathCasePolicy,
    ): List<VolumePath> =
        if (occurrenceIndexes(paths, target, casePolicy).isEmpty()) paths + target else paths.toList()

    fun removeAll(
        paths: List<VolumePath>,
        target: VolumePath,
        casePolicy: PathCasePolicy,
    ): List<VolumePath> =
        paths.filterNot { VolumeAwareComparison.matches(it, target, casePolicy) }
}
