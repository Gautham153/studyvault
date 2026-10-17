package com.example.data.repository

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.MaterialItem
import com.example.data.model.extractFileType
import com.example.data.model.formatByteSize
import com.example.data.remote.StudyMaterialDto
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class SupabaseStudyMaterialRepository private constructor() : StudyMaterialRepository {

    private val _materials = MutableStateFlow<List<MaterialItem>>(emptyList())
    override val materials: StateFlow<List<MaterialItem>> = _materials.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    override val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    override val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var loadToken = 0
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val displayDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    private fun getUserId(): String? {
        val client = SupabaseClientProvider.client ?: return null
        return client.auth.currentUserOrNull()?.id
    }

    override fun clearData() {
        loadToken++
        _materials.value = emptyList()
        _isUploading.value = false
        _uploadProgress.value = null
        _errorMessage.value = null
    }

    override suspend fun loadMaterials(subjectId: String?): Result<List<MaterialItem>> = withContext(Dispatchers.IO) {
        val currentToken = ++loadToken
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("No authenticated user."))

        try {
            val dtos: List<StudyMaterialDto> = if (subjectId != null) {
                client.from("study_materials")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("subject_id", subjectId)
                        }
                    }
                    .decodeList()
            } else {
                client.from("study_materials")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList()
            }

            if (currentToken != loadToken || getUserId() != userId) {
                return@withContext Result.success(emptyList())
            }

            val domainItems = dtos.map { dtoToDomain(it) }
            _materials.update { current ->
                if (subjectId != null) {
                    current.filter { it.subjectId != subjectId } + domainItems
                } else {
                    domainItems
                }
            }
            Result.success(domainItems)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading study materials from Supabase", e)
            if (currentToken == loadToken && getUserId() == userId) {
                _errorMessage.value = e.message ?: "Failed to load study materials."
            }
            Result.failure(e)
        }
    }

    override suspend fun getMaterialsForSubject(subjectId: String): List<MaterialItem> {
        val cached = _materials.value.filter { it.subjectId == subjectId }
        if (cached.isNotEmpty()) return cached
        loadMaterials(subjectId)
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
    ): Result<MaterialItem> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated."))

        // 1. Enforce 25 MB max file size constraint
        if (fileBytes.size > MAX_FILE_SIZE_BYTES) {
            val sizeFormatted = formatByteSize(fileBytes.size.toLong())
            val msg = "File size ($sizeFormatted) exceeds maximum allowed limit of 25 MB."
            _errorMessage.value = msg
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        if (fileBytes.isEmpty()) {
            val msg = "Cannot upload an empty file."
            _errorMessage.value = msg
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        _isUploading.value = true
        _uploadProgress.value = 0.1f
        _errorMessage.value = null
        onProgress?.invoke(0.1f)

        // 2. Build user-scoped secure path: {userId}/{subjectId}/{uniqueFileId}_{safeFileName}
        val safeFileName = sanitizeFilename(originalFilename)
        val uniqueFileId = UUID.randomUUID().toString()
        val storagePath = "$userId/$subjectId/${uniqueFileId}_$safeFileName"

        var storageUploaded = false
        try {
            // 3. Upload object to private Supabase Storage bucket 'study-materials'
            onProgress?.invoke(0.4f)
            _uploadProgress.value = 0.4f

            val bucket = client.storage.from(BUCKET_NAME)
            bucket.upload(path = storagePath, data = fileBytes) {
                upsert = false
            }
            storageUploaded = true

            onProgress?.invoke(0.8f)
            _uploadProgress.value = 0.8f

            // 4. Insert metadata record in PostgreSQL 'study_materials' table
            val title = originalFilename.substringBeforeLast(".").ifBlank { originalFilename }
            val materialDto = StudyMaterialDto(
                userId = userId,
                subjectId = subjectId,
                unitId = unitId,
                topicId = topicId,
                title = title,
                originalFilename = originalFilename,
                storagePath = storagePath,
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                fileSizeBytes = fileBytes.size.toLong()
            )

            val insertedDto: StudyMaterialDto = client.from("study_materials")
                .insert(materialDto) {
                    select()
                }
                .decodeSingle()

            val domainItem = dtoToDomain(insertedDto)
            _materials.update { current ->
                listOf(domainItem) + current.filter { it.id != domainItem.id }
            }

            onProgress?.invoke(1.0f)
            _uploadProgress.value = 1.0f
            Log.i(TAG, "Successfully uploaded study material: ${domainItem.title} (${domainItem.fileSize})")
            Result.success(domainItem)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload study material or insert metadata", e)
            // Rollback: If storage object was uploaded but metadata insertion failed, delete the orphaned storage object
            if (storageUploaded) {
                try {
                    client.storage.from(BUCKET_NAME).delete(listOf(storagePath))
                    Log.i(TAG, "Successfully rolled back storage object at $storagePath after metadata error")
                } catch (cleanupEx: Exception) {
                    Log.w(TAG, "Failed to clean up storage object during rollback: $storagePath", cleanupEx)
                }
            }
            _errorMessage.value = e.message ?: "Failed to upload study material."
            Result.failure(e)
        } finally {
            _isUploading.value = false
            _uploadProgress.value = null
        }
    }

    private fun isObjectNotFound(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("404") ||
               msg.contains("not found") ||
               msg.contains("does not exist") ||
               msg.contains("no such") ||
               msg.contains("object_not_found")
    }

    override suspend fun deleteMaterial(material: MaterialItem): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated."))

        try {
            // 1. Delete storage object. If already missing (404/not found), allow retry/metadata deletion to proceed.
            // If network, permission, or other storage error occurs, fail and preserve metadata.
            if (material.storagePath.isNotBlank()) {
                try {
                    client.storage.from(BUCKET_NAME).delete(listOf(material.storagePath))
                } catch (storageEx: Exception) {
                    Log.e(TAG, "Storage deletion error for path: ${material.storagePath}", storageEx)
                    if (isObjectNotFound(storageEx)) {
                        Log.i(TAG, "Storage object already missing for ${material.storagePath}, proceeding with metadata deletion.")
                    } else {
                        val errorMsg = "Storage error: ${storageEx.message}. Metadata retained so you can retry."
                        _errorMessage.value = errorMsg
                        return@withContext Result.failure(Exception(errorMsg, storageEx))
                    }
                }
            }

            // 2. Delete database metadata record
            try {
                client.from("study_materials").delete {
                    filter {
                        eq("id", material.id)
                        eq("user_id", userId)
                    }
                }
            } catch (dbEx: Exception) {
                Log.e(TAG, "Database metadata deletion failed for material: ${material.id}", dbEx)
                val errorMsg = "Failed to delete database record: ${dbEx.message}. Storage was removed/missing, metadata retained for safe retry."
                _errorMessage.value = errorMsg
                return@withContext Result.failure(Exception(dbEx.message ?: "Failed to delete database record", dbEx))
            }

            // 3. Remove material from local state ONLY after database metadata deletion succeeds
            _materials.update { current ->
                current.filter { it.id != material.id }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete study material: ${material.id}", e)
            _errorMessage.value = e.message ?: "Failed to delete material."
            Result.failure(e)
        }
    }

    override suspend fun openMaterial(context: Context, material: MaterialItem): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        if (getUserId() == null) {
            return@withContext Result.failure(IllegalStateException("User is not authenticated."))
        }

        if (material.storagePath.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Material storage path is missing."))
        }

        try {
            // 1. Authenticated download from private bucket
            val bytes = client.storage.from(BUCKET_NAME).downloadAuthenticated(material.storagePath)

            // 2. Write to secure cache directory
            val cacheFolder = File(context.cacheDir, "study_materials").apply { mkdirs() }
            val safeName = sanitizeFilename(material.title)
            val extension = material.storagePath.substringAfterLast(".", "")
            val finalFilename = if (extension.isNotBlank() && !safeName.endsWith(".$extension", ignoreCase = true)) {
                "$safeName.$extension"
            } else {
                safeName
            }
            val cachedFile = File(cacheFolder, "${material.id.take(8)}_$finalFilename")
            cachedFile.writeBytes(bytes)

            // 3. Grant URI permission via FileProvider and launch Intent
            withContext(Dispatchers.Main) {
                try {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        cachedFile
                    )

                    val mime = material.mimeType.ifBlank {
                        when (material.fileType.uppercase()) {
                            "PDF" -> "application/pdf"
                            "PPT" -> "application/vnd.ms-powerpoint"
                            "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            "ZIP" -> "application/zip"
                            else -> "application/octet-stream"
                        }
                    }

                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    context.startActivity(viewIntent)
                } catch (notFoundEx: ActivityNotFoundException) {
                    val errorMsg = "No application available on device to open ${material.fileType} files."
                    Log.w(TAG, errorMsg, notFoundEx)
                    throw IllegalStateException(errorMsg)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download or open material: ${material.title}", e)
            _errorMessage.value = e.message ?: "Failed to open study material."
            Result.failure(e)
        }
    }

    private fun dtoToDomain(dto: StudyMaterialDto): MaterialItem {
        val sizeFormatted = formatByteSize(dto.fileSizeBytes)
        val fileType = extractFileType(dto.originalFilename, dto.mimeType)
        val uploadDateFormatted = formatDisplayDate(dto.createdAt)

        return MaterialItem(
            id = dto.id ?: UUID.randomUUID().toString(),
            title = dto.title,
            fileType = fileType,
            fileSize = sizeFormatted,
            uploadDate = uploadDateFormatted,
            storagePath = dto.storagePath,
            mimeType = dto.mimeType,
            fileSizeBytes = dto.fileSizeBytes,
            subjectId = dto.subjectId,
            unitId = dto.unitId,
            topicId = dto.topicId
        )
    }

    private fun formatDisplayDate(isoTimestamp: String?): String {
        if (isoTimestamp.isNullOrBlank()) return "Recent"
        return try {
            val date = isoDateFormat.parse(isoTimestamp.take(19))
            if (date != null) displayDateFormat.format(date) else "Recent"
        } catch (_: Exception) {
            "Recent"
        }
    }

    private fun sanitizeFilename(filename: String): String {
        return filename.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
    }

    companion object {
        private const val TAG = "SupabaseStudyMaterialRepository"
        const val BUCKET_NAME = "study-materials"
        const val MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L // 25 MB safe practical limit

        val instance: SupabaseStudyMaterialRepository by lazy {
            SupabaseStudyMaterialRepository()
        }
    }
}
