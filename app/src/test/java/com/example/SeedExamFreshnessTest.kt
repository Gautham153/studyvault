package com.example

import com.example.data.repository.MockStudyVaultRepository
import com.example.util.StudyDateUtils
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: the mock repository's seeded exams must always stay in the future. Hardcoded
 * calendar dates silently expire, which strips exam urgency from study recommendations and
 * reshuffles the capped Today's Plan, breaking plan/urgency behavior overnight.
 */
class SeedExamFreshnessTest {

    @Test
    fun `seeded exams never fall into the past`() {
        val repository = MockStudyVaultRepository.createIsolated()
        val seededIds = setOf("e1", "e2", "e3")
        val seeded = repository.upcomingExams.value.filter { it.id in seededIds }

        assertTrue("All seeded exams must remain upcoming", seeded.map { it.id }.toSet() == seededIds)

        seeded.forEach { exam ->
            val iso = exam.isoDate.ifBlank { StudyDateUtils.parseExamDateToIso(exam.examDate).orEmpty() }
            val days = StudyDateUtils.daysFromToday(iso)
            assertTrue(
                "Seeded exam ${exam.id} must not be in the past (examDate=${exam.examDate})",
                days != null && days >= 0
            )
        }
    }
}
