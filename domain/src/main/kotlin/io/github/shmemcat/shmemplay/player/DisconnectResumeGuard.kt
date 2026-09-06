package io.github.shmemcat.shmemplay.player

/** A reconnect can only resume the exact queue/entry paused by a route loss. */
class DisconnectResumeGuard {
    private var ticket: Pair<String, String>? = null
    fun disconnected(queue: String?, entry: String?) { ticket = if(queue != null && entry != null) queue to entry else null }
    fun invalidate() { ticket = null }
    fun consume(queue: String?, entry: String?, enabled: Boolean): Boolean {
        val allowed = enabled && ticket != null && ticket == queue to entry
        ticket = null
        return allowed
    }
}
