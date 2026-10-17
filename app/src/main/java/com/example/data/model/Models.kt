package com.example.data.model

enum class TopicStatus {
    COMPLETED,
    LEARNING,
    NOT_STARTED,
    NEEDS_REVISION
}

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD
}

enum class Importance {
    LOW,
    MEDIUM,
    HIGH
}

data class MaterialItem(
    val id: String,
    val title: String,
    val fileType: String, // "PDF", "PPT", "DOCX", "ZIP"
    val fileSize: String, // "2.4 MB"
    val uploadDate: String = "Oct 2026",
    val storagePath: String = "",
    val mimeType: String = "application/pdf",
    val fileSizeBytes: Long = 0L,
    val subjectId: String = "",
    val unitId: String? = null,
    val topicId: String? = null
)

data class QuestionItem(
    val id: String,
    val questionNumber: Int = 1,
    val questionText: String,
    val marks: Int = 5,
    val unitName: String = "",
    val subjectName: String = "",
    val status: String = "Not Prepared", // "Not Prepared", "Learning", "Prepared", "High Priority"
    val subjectId: String = "",
    val unitId: String? = null,
    val topicId: String? = null,
    val importance: Importance = Importance.MEDIUM,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class NoteItem(
    val id: String,
    val subjectId: String,
    val topicId: String? = null,
    val title: String,
    val content: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val subjectName: String = "",
    val topicName: String = ""
)

data class TopicModel(
    val id: String,
    val unitId: String,
    val subjectId: String,
    val title: String,
    val status: TopicStatus,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val importance: Importance = Importance.HIGH,
    val lastStudiedDate: String = "Oct 7, 2026",
    val nextRevisionDate: String = "Oct 10, 2026",
    val priorityScore: Int = 85,
    val materialsCount: Int = 2,
    val questionsCount: Int = 4,
    val notesCount: Int = 1,
    val relatedMaterials: List<MaterialItem> = emptyList(),
    val questions: List<QuestionItem> = emptyList(),
    val notes: List<NoteItem> = emptyList()
)

data class UnitModel(
    val id: String,
    val subjectId: String,
    val unitNumber: Int,
    val title: String,
    val preparationPercent: Int,
    val topics: List<TopicModel> = emptyList(),
    val materialsCount: Int = 7,
    val questionsCount: Int = 5,
    val materials: List<MaterialItem> = emptyList()
)

data class Subject(
    val id: String,
    val name: String,
    val code: String,
    val iconKey: String, // "coffee", "database", "ai", "book", "math", "os"
    val preparationPercent: Int,
    val unitsCount: Int,
    val topicsCount: Int,
    val accentColorHex: Long = 0xFF3B82F6,
    val units: List<UnitModel> = emptyList(),
    val materialsCount: Int = 47,
    val questionsCount: Int = 23,
    val needsAttentionTopics: List<String> = emptyList(),
    val semester: String = "3rd Semester",
    val semesterNumber: Int = 3,
    val questions: List<QuestionItem> = emptyList(),
    val materials: List<MaterialItem> = emptyList(),
    val notes: List<NoteItem> = emptyList()
)

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

fun extractFileType(fileName: String, mimeType: String? = null): String {
    val ext = fileName.substringAfterLast(".", "").uppercase()
    if (ext.isNotBlank()) {
        return when (ext) {
            "PDF" -> "PDF"
            "PPT", "PPTX" -> "PPT"
            "DOC", "DOCX" -> "DOCX"
            "XLS", "XLSX" -> "XLSX"
            "TXT" -> "TXT"
            "ZIP", "RAR", "7Z" -> "ZIP"
            "JPG", "JPEG", "PNG", "WEBP" -> "IMG"
            else -> ext.take(4)
        }
    }
    return when {
        mimeType?.contains("pdf", ignoreCase = true) == true -> "PDF"
        mimeType?.contains("presentation", ignoreCase = true) == true -> "PPT"
        mimeType?.contains("word", ignoreCase = true) == true -> "DOCX"
        mimeType?.contains("zip", ignoreCase = true) == true -> "ZIP"
        mimeType?.contains("image", ignoreCase = true) == true -> "IMG"
        else -> "DOC"
    }
}


fun formatSemesterLabel(semesterNumber: Int?): String {
    return when (semesterNumber) {
        1 -> "1st Semester"
        2 -> "2nd Semester"
        3 -> "3rd Semester"
        4 -> "4th Semester"
        5 -> "5th Semester"
        6 -> "6th Semester"
        7 -> "7th Semester"
        8 -> "8th Semester"
        null -> "3rd Semester"
        else -> "${semesterNumber}th Semester"
    }
}

fun parseSemesterNumber(label: String?): Int {
    if (label.isNullOrBlank()) return 3
    val digits = Regex("""\d+""").find(label)?.value?.toIntOrNull()
    return digits ?: 3
}

fun mapQuestionStatusToDisplay(status: String?): String {
    return when (status?.uppercase()?.replace(" ", "_")?.replace("-", "_")) {
        "NOT_PREPARED", "NOT_STARTED", "UNPREPARED" -> "Not Prepared"
        "LEARNING", "IN_PROGRESS" -> "Learning"
        "PREPARED", "COMPLETED", "DONE" -> "Prepared"
        "HIGH_PRIORITY" -> "High Priority"
        else -> status ?: "Not Prepared"
    }
}

data class StudyItem(
    val topicId: String,
    val subjectId: String,
    val unitId: String,
    val topicName: String,
    val subjectName: String,
    val unitName: String,
    val priorityScore: Int,
    val priorityLevel: String, // "High Priority", "Medium Priority"
    val statusNote: String // "Revision due today", "Not completed", "Revision due tomorrow"
)

data class ExamItem(
    val id: String,
    val subjectId: String,
    val subjectName: String,
    val iconKey: String,
    val examDate: String, // "Oct 20, 2026"
    val daysRemaining: Int, // 12
    val preparationPercent: Int, // 74
    val code: String = "CS301",
    val venue: String = "Exam Hall B, Desk 42"
)

data class OverallProgress(
    val semesterTitle: String = "Semester Preparation",
    val overallPercentage: Int = 78,
    val subjectsCount: Int = 6,
    val upcomingExamsCount: Int = 3,
    val studentName: String = "Gautham",
    val studentEmail: String = "gautham153@gmail.com",
    val semester: String = "3rd Semester",
    val cloudStorageUsed: String = "Usage unavailable"
)
