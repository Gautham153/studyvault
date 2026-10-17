package com.example.data.repository

import android.content.Context
import com.example.data.model.MaterialItem
import kotlinx.coroutines.flow.StateFlow

interface StudyMaterialRepository {
    val materials: StateFlow<List<MaterialItem>>
    val isUploading: StateFlow<Boolean>
    val uploadProgress: StateFlow<Float?>
    val errorMessage: StateFlow<String?>

    suspend fun loadMaterials(subjectId: String? = null): Result<List<MaterialItem>>
    suspend fun getMaterialsForSubject(subjectId: String): List<MaterialItem>
    suspend fun getMaterialsForUnit(subjectId: String, unitId: String): List<MaterialItem>
    suspend fun getMaterialsForTopic(topicId: String): List<MaterialItem>
    suspend fun uploadMaterial(
        subjectId: String,
        unitId: String? = null,
        topicId: String? = null,
        originalFilename: String,
        mimeType: String,
        fileBytes: ByteArray,
        onProgress: ((Float) -> Unit)? = null
    ): Result<MaterialItem>
    suspend fun deleteMaterial(material: MaterialItem): Result<Unit>
    suspend fun openMaterial(context: Context, material: MaterialItem): Result<Unit>
    fun clearData()
}
