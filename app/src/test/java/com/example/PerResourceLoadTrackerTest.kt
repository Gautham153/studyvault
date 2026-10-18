package com.example

import com.example.data.repository.PerResourceLoadTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerResourceLoadTrackerTest {

    @Test
    fun `new request for one resource does not invalidate another resource`() {
        val tracker = PerResourceLoadTracker()
        val subjectA = tracker.next("s_java")
        val subjectB = tracker.next("s_kotlin")

        // A newer request for an unrelated resource must not affect subject A.
        tracker.next("s_kotlin")

        assertTrue("Subject A request should still be current", tracker.isCurrent("s_java", subjectA))
        assertFalse("Superseded subject B request must not be current", tracker.isCurrent("s_kotlin", subjectB))
    }

    @Test
    fun `superseded request for same resource is no longer current`() {
        val tracker = PerResourceLoadTracker()
        val first = tracker.next("s_java")
        val second = tracker.next("s_java")

        assertFalse("Older token must be stale after a newer request", tracker.isCurrent("s_java", first))
        assertTrue("Latest token must be current", tracker.isCurrent("s_java", second))
    }

    @Test
    fun `invalidateAll invalidates every outstanding token`() {
        val tracker = PerResourceLoadTracker()
        val subjectToken = tracker.next("s_java")
        val topicToken = tracker.next("t_java_u2_1")

        tracker.invalidateAll()

        assertFalse(
            "Session change must invalidate subject requests",
            tracker.isCurrent("s_java", subjectToken)
        )
        assertFalse(
            "Session change must invalidate topic requests",
            tracker.isCurrent("t_java_u2_1", topicToken)
        )

        val after = tracker.next("s_java")
        assertTrue("A request started after invalidation is current", tracker.isCurrent("s_java", after))
    }
}
