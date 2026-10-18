package com.example.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Flexible serializer that handles integer columns returned by Supabase Postgrest
 * as either numeric JSON integers (e.g. 3) or strings (e.g. "3" or "3rd Semester").
 * Serializes back to a clean integer primitive for PostgreSQL integer columns.
 */
object FlexibleIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            if (element is JsonNull) return null
            if (element is JsonPrimitive) {
                return element.intOrNull ?: Regex("""\d+""").find(element.content)?.value?.toIntOrNull()
            }
            return null
        }
        return try {
            decoder.decodeInt()
        } catch (_: Exception) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeInt(value)
        }
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class SubjectDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("semester") val semester: Int? = null,
    @SerialName("icon_key") val iconKey: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UnitDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("name") val name: String,
    @SerialName("order_index") val orderIndex: Int = 1,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class TopicDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("unit_id") val unitId: String,
    @SerialName("name") val name: String,
    @SerialName("status") val status: String = "NOT_STARTED", // "COMPLETED", "LEARNING", "NOT_STARTED"
    @SerialName("importance") val importance: String = "MEDIUM", // "LOW", "MEDIUM", "HIGH"
    @SerialName("difficulty") val difficulty: String = "MEDIUM", // "EASY", "MEDIUM", "HARD"
    @SerialName("last_studied_at") val lastStudiedAt: String? = null,
    @SerialName("next_revision_at") val nextRevisionAt: String? = null,
    @SerialName("revision_count") val revisionCount: Int = 0,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class QuestionDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("unit_id") val unitId: String? = null,
    @SerialName("topic_id") val topicId: String? = null,
    @SerialName("question") val question: String,
    @SerialName("marks") val marks: Int = 5,
    @SerialName("importance") val importance: String = "MEDIUM",
    @SerialName("status") val status: String = "NOT_PREPARED",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class NoteDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("topic_id") val topicId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("content") val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ExamDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("name") val name: String,
    @SerialName("exam_date") val examDate: String,
    @SerialName("venue") val venue: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class RevisionHistoryDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("topic_id") val topicId: String,
    @SerialName("studied_at") val studiedAt: String? = null,
    @SerialName("revision_number") val revisionNumber: Int = 1,
    @SerialName("previous_status") val previousStatus: String? = null,
    @SerialName("new_status") val newStatus: String,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class StudyMaterialDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("unit_id") val unitId: String? = null,
    @SerialName("topic_id") val topicId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("original_filename") val originalFilename: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_size_bytes") val fileSizeBytes: Long,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

