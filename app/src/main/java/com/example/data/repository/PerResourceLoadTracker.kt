package com.example.data.repository

/**
 * Thrown when a repository load result was discarded because a newer request for the same
 * resource started, or because the user session changed while the request was in flight.
 * Callers must treat this as "ignore me" — never as a successful empty load.
 */
class StaleRequestException(message: String = "Request superseded by a newer request.") : Exception(message)

/**
 * Concurrency-safe per-resource request tracking.
 *
 * Each logical resource (e.g. a subject id, or a topic id) gets its own monotonically
 * increasing sequence number, so a request for one resource never invalidates a request
 * for an unrelated resource. A session generation counter additionally invalidates every
 * outstanding request at once (e.g. on sign-out or account switch).
 */
class PerResourceLoadTracker {

    data class Token(val generation: Int, val sequence: Int)

    private val lock = Any()
    private var generation = 0
    private val sequences = mutableMapOf<String, Int>()

    /** Registers a new request for [key] and returns its token. */
    fun next(key: String): Token = synchronized(lock) {
        val sequence = (sequences[key] ?: 0) + 1
        sequences[key] = sequence
        Token(generation, sequence)
    }

    /** True only if no newer request for [key] started and no session invalidation happened. */
    fun isCurrent(key: String, token: Token): Boolean = synchronized(lock) {
        generation == token.generation && sequences[key] == token.sequence
    }

    /** Invalidates every outstanding request (e.g. on user/session change). */
    fun invalidateAll() {
        synchronized(lock) {
            generation++
            sequences.clear()
        }
    }
}
