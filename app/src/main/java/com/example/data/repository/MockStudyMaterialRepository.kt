package com.example.data.repository

import android.content.Context
import com.example.data.model.MaterialItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class MockStudyMaterialRepository private constructor() : StudyMaterialRepository {

    private val initialMaterials = listOf(
        MaterialItem("m1", "Unit 1 Fundamentals.pdf", "PDF", "3.1 MB", "Oct 6, 2026", subjectId = "s_java", unitId = "u_java_1"),
        MaterialItem("m2", "Unit 2 Notes.pdf", "PDF", "2.4 MB", "Oct 5, 2026", subjectId = "s_java", unitId = "u_java_2", topicId = "t_java_u2_1"),
        MaterialItem("m3", "Exception Handling PPT.pptx", "PPT", "5.8 MB", "Oct 4, 2026", subjectId = "s_java", unitId = "u_java_2", topicId = "t_java_u2_1"),
        MaterialItem("m4", "Important Questions.pdf", "PDF", "1.2 MB", "Oct 3, 2026", subjectId = "s_java", unitId = "u_java_2"),
        MaterialItem("m5", "Example Programs.zip", "ZIP", "0.8 MB", "Oct 2, 2026", subjectId = "s_java"),
        MaterialItem("m6", "Reference Material.pdf", "PDF", "1.6 MB", "Oct 1, 2026", subjectId = "s_java")
    )

    private val _materials = MutableStateFlow(initialMaterials)
    override val materials: StateFlow<List<MaterialItem>> = _materials.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    override val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    override val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    override suspend fun loadMaterials(subjectId: String?): Result<List<MaterialItem>> {
        return if (subjectId != null) {
            Result.success(_materials.value.filter { it.subjectId == subjectId })
        } else {
            Result.success(_materials.value)
        }
    }

    override suspend fun getMaterialsForSubject(subjectId: String): List<MaterialItem> {
        return _materials.value.filter { it.subjectId == subjectId }
    }

    override suspend fun getMaterialsForUnit(subjectId: String, unitId: String): List<MaterialItem> {
        return _materials.value.filter { it.subjectId == subjectId && it.unitId == unitId }
    }

    override suspend fun getMaterialsForTopic(topicId: String): List<MaterialItem> {
        return _materials.value.filter { it.topicId == topicId }
    }

    override suspend fun uploadMaterial(
        subjectId: String,
        unitId: String?,
        topicId: String?,
        originalFilename: String,
        mimeType: String,
        fileBytes: ByteArray,
        onProgress: ((Float) -> Unit)?
    ): Result<MaterialItem> {
        val newItem = MaterialItem(
            id = UUID.randomUUID().toString(),
            title = originalFilename.substringBeforeLast(".").ifBlank { originalFilename },
            fileType = originalFilename.substringAfterLast(".", "PDF").uppercase(),
            fileSize = "${(fileBytes.size / (1024.0 * 1024.0)).coerceAtLeast(0.1)} MB",
            uploadDate = "Today",
            subjectId = subjectId,
            unitId = unitId,
            topicId = topicId
        )
        _materials.update { listOf(newItem) + it }
        return Result.success(newItem)
    }

    override suspend fun deleteMaterial(material: MaterialItem): Result<Unit> {
        _materials.update { it.filter { m -> m.id != material.id } }
        return Result.success(Unit)
    }

    override suspend fun openMaterial(context: Context, material: MaterialItem): Result<Unit> {
        return Result.success(Unit)
    }

    override fun clearData() {
        _materials.value = emptyList()
        _errorMessage.value = null
    }

    companion object {
        val instance: MockStudyMaterialRepository by lazy {
            MockStudyMaterialRepository()
        }
    }
}
