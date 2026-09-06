package io.github.shmemcat.shmemplay

/**
 * Phase 1 exposes no playlist intake, storage grant, or mutation entry point.
 * Later phases must replace this scaffold only after their documented gates pass.
 */
object PhaseOneSafety {
    const val PLAYLIST_MUTATION_ENABLED = false
}
