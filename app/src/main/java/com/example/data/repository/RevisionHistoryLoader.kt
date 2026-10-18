package com.example.data.repository

import com.example.data.remote.RevisionHistoryDto

/**
 * Wraps the revision_history query so a genuine empty history stays distinct from a failed
 * query (missing table, RLS denial, network error, ...).
 *
 * A successful [Result] with an empty list means "the user has no revisions yet".
 * A failed [Result] means the load could not be confirmed and must never be shown to the
 * user as "no revisions recorded". Keeping this pure makes the contract unit-testable
 * without a live Supabase connection.
 */
object RevisionHistoryLoader {

    /**
     * Runs [query] and converts any thrown exception into a failed [Result].
     * An empty list returned by [query] is a successful, confirmed empty history.
     */
    suspend fun load(query: suspend () -> List<RevisionHistoryDto>): Result<List<RevisionHistoryDto>> =
        try {
            Result.success(query())
        } catch (e: Exception) {
            Result.failure(e)
        }
}
