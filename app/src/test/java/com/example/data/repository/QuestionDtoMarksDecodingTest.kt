package com.example.data.repository

import com.example.data.model.QuestionItem
import com.example.data.remote.QuestionDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuestionDtoMarksDecodingTest {

    private fun questionArray(marksLiteral: String): String = """
        [
          {
            "id": "q1",
            "user_id": "u1",
            "subject_id": "s1",
            "unit_id": "u1",
            "topic_id": "t1",
            "question": "Explain the JVM class loader mechanism.",
            "marks": $marksLiteral,
            "importance": "HIGH",
            "status": "NOT_PREPARED",
            "created_at": "2026-10-01T10:00:00Z",
            "updated_at": "2026-10-01T10:00:00Z"
          }
        ]
    """.trimIndent()

    @Test
    fun `question row with null marks decodes without throwing`() {
        val questions = Json.decodeFromString<List<QuestionDto>>(questionArray("null"))

        assertEquals(1, questions.size)
        assertNull("Explicit JSON null must survive as a null DTO value", questions[0].marks)
        assertEquals("Explain the JVM class loader mechanism.", questions[0].question)
        assertEquals("s1", questions[0].subjectId)
    }

    @Test
    fun `question row with numeric marks decodes that value`() {
        val questions = Json.decodeFromString<List<QuestionDto>>(questionArray("10"))

        assertEquals(10, questions[0].marks)
    }

    @Test
    fun `mixed null and numeric marks decode in one batch`() {
        val payload = """
            [
              {"id": "q1", "subject_id": "s1", "question": "First", "marks": null},
              {"id": "q2", "subject_id": "s1", "question": "Second", "marks": 7}
            ]
        """.trimIndent()

        val questions = Json.decodeFromString<List<QuestionDto>>(payload)

        assertNull(questions[0].marks)
        assertEquals(7, questions[1].marks)
    }

    @Test
    fun `null marks map into the domain model using the default fallback`() {
        val dto = Json.decodeFromString<List<QuestionDto>>(questionArray("null")).first()

        val item = QuestionItem(
            id = dto.id ?: "q_0",
            questionNumber = 1,
            questionText = dto.question,
            marks = dto.marksOrDefault(),
            status = "Not Prepared"
        )

        assertEquals(DEFAULT_QUESTION_MARKS, item.marks)
        assertEquals(5, item.marks)
    }

    @Test
    fun `numeric marks map unchanged into the domain model`() {
        val dto = Json.decodeFromString<List<QuestionDto>>(questionArray("10")).first()

        val item = QuestionItem(
            id = dto.id ?: "q_0",
            questionText = dto.question,
            marks = dto.marksOrDefault()
        )

        assertEquals(10, item.marks)
    }

    @Test
    fun `question without a marks key still decodes and maps to the domain default`() {
        val payload = """[{"id": "q2", "subject_id": "s1", "question": "What is garbage collection?"}]"""

        val dto = Json.decodeFromString<List<QuestionDto>>(payload).first()

        assertEquals(DEFAULT_QUESTION_MARKS, dto.marksOrDefault())
    }
}
