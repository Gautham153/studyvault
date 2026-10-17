package com.example.ui.screens.subjectdetail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MaterialItem
import com.example.data.model.Subject
import com.example.data.repository.StudyMaterialRepository
import com.example.data.repository.StudyMaterialRepositoryProvider
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubjectDetailViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository,
    private val materialRepository: StudyMaterialRepository = StudyMaterialRepositoryProvider.repository
) : ViewModel() {

    private val _subject = MutableStateFlow<Subject?>(null)
    val subject: StateFlow<Subject?> = _subject.asStateFlow()

    private var currentSubjectId: String? = null
    private val _loadedSubjectId = MutableStateFlow<String?>(null)

    val isUploading: StateFlow<Boolean> = materialRepository.isUploading
    val uploadProgress: StateFlow<Float?> = materialRepository.uploadProgress

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Real study materials for this subject
    val materials: StateFlow<List<MaterialItem>> = combine(
        _subject,
        materialRepository.materials,
        _loadedSubjectId
    ) { currentSub, allMats, loadedId ->
        val sId = currentSub?.id
        if (sId != null) {
            val fromRepo = allMats.filter { it.subjectId == sId }
            if (loadedId == sId) {
                fromRepo
            } else {
                if (fromRepo.isNotEmpty()) fromRepo else currentSub.materials
            }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.subjects.collect { _ ->
                currentSubjectId?.let { id ->
                    _subject.value = repository.getSubjectById(id)
                }
            }
        }
    }

    fun loadSubject(subjectId: String) {
        if (currentSubjectId != subjectId) {
            _loadedSubjectId.value = null
        }
        currentSubjectId = subjectId
        _subject.value = repository.getSubjectById(subjectId)
        val requestedSubjectId = subjectId
        viewModelScope.launch {
            val result = materialRepository.loadMaterials(subjectId)
            if (result.isSuccess && currentSubjectId == requestedSubjectId) {
                _loadedSubjectId.value = requestedSubjectId
            }
        }
    }

    fun uploadMaterial(
        filename: String,
        mimeType: String,
        bytes: ByteArray,
        unitId: String? = null,
        topicId: String? = null
    ) {
        val sId = currentSubjectId ?: return
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = materialRepository.uploadMaterial(
                subjectId = sId,
                unitId = unitId,
                topicId = topicId,
                originalFilename = filename,
                mimeType = mimeType,
                fileBytes = bytes
            )
            result.fold(
                onSuccess = { item ->
                    _statusMessage.value = "Successfully uploaded ${item.title}"
                    // Refresh subject data to sync counts
                    currentSubjectId?.let { loadSubject(it) }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to upload study material."
                }
            )
        }
    }

    fun deleteMaterial(material: MaterialItem) {
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = materialRepository.deleteMaterial(material)
            result.fold(
                onSuccess = {
                    _statusMessage.value = "Deleted ${material.title}"
                    currentSubjectId?.let { id ->
                        _subject.value = repository.getSubjectById(id)
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to delete material."
                }
            )
        }
    }

    fun openMaterial(context: Context, material: MaterialItem) {
        viewModelScope.launch {
            _errorMessage.value = null
            val result = materialRepository.openMaterial(context, material)
            result.onFailure { error ->
                _errorMessage.value = error.message ?: "Could not open file."
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _statusMessage.value = null
    }
}
