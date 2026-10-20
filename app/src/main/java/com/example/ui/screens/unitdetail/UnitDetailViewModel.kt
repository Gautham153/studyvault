package com.example.ui.screens.unitdetail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MaterialItem
import com.example.data.model.Subject
import com.example.data.model.UnitModel
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

class UnitDetailViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository,
    private val materialRepository: StudyMaterialRepository = StudyMaterialRepositoryProvider.repository
) : ViewModel() {

    private val _unit = MutableStateFlow<UnitModel?>(null)
    val unit: StateFlow<UnitModel?> = _unit.asStateFlow()

    private val _subject = MutableStateFlow<Subject?>(null)
    val subject: StateFlow<Subject?> = _subject.asStateFlow()

    private var currentSubjectId: String? = null
    private var currentUnitId: String? = null
    private val _loadedUnitId = MutableStateFlow<String?>(null)

    val isUploading: StateFlow<Boolean> = materialRepository.isUploading
    val uploadProgress: StateFlow<Float?> = materialRepository.uploadProgress

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Live materials for this unit, sourced exclusively from the material repository.
    // No cached unit-materials fallback: an empty repository list stays empty after loads
    // and deletions, so removed files cannot reappear.
    val materials: StateFlow<List<MaterialItem>> = combine(
        _unit,
        materialRepository.materials,
        _loadedUnitId
    ) { currentU, allMats, loadedId ->
        val uId = currentU?.id
        val sId = currentSubjectId
        if (uId != null && sId != null && loadedId == uId) {
            allMats.filter { it.subjectId == sId && it.unitId == uId }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.subjects.collect { _ ->
                val sId = currentSubjectId
                val uId = currentUnitId
                if (sId != null && uId != null) {
                    _subject.value = repository.getSubjectById(sId)
                    _unit.value = repository.getUnitById(sId, uId)
                }
            }
        }
    }

    fun loadUnit(subjectId: String, unitId: String) {
        if (currentUnitId != unitId || currentSubjectId != subjectId) {
            // Resource changed: reset load tracking and messages so nothing from the
            // previously viewed unit/subject leaks into this one.
            _loadedUnitId.value = null
            _statusMessage.value = null
            _errorMessage.value = null
        }
        currentSubjectId = subjectId
        currentUnitId = unitId
        _subject.value = repository.getSubjectById(subjectId)
        _unit.value = repository.getUnitById(subjectId, unitId)
        val requestedUnitId = unitId
        viewModelScope.launch {
            val result = materialRepository.loadMaterials(subjectId)
            // Mark loaded only on a genuine, current success for this unit.
            if (result.getOrNull() != null && currentUnitId == requestedUnitId && currentSubjectId == subjectId) {
                _loadedUnitId.value = requestedUnitId
            }
        }
    }

    fun uploadMaterial(filename: String, mimeType: String, bytes: ByteArray) {
        val sId = currentSubjectId ?: return
        val uId = currentUnitId ?: return
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = materialRepository.uploadMaterial(
                subjectId = sId,
                unitId = uId,
                topicId = null,
                originalFilename = filename,
                mimeType = mimeType,
                fileBytes = bytes
            )
            result.fold(
                onSuccess = { item ->
                    _statusMessage.value = "Successfully uploaded ${item.title}"
                    loadUnit(sId, uId)
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to upload file."
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
                    val sId = currentSubjectId
                    val uId = currentUnitId
                    if (sId != null && uId != null) {
                        _unit.value = repository.getUnitById(sId, uId)
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to delete file."
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

    fun createTopic(title: String, onCreated: (String) -> Unit = {}) {
        val sId = currentSubjectId ?: return
        val uId = currentUnitId ?: return
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = repository.createTopic(subjectId = sId, unitId = uId, title = title)
            result.fold(
                onSuccess = { topic ->
                    _statusMessage.value = "Added topic ${topic.title}"
                    _subject.value = repository.getSubjectById(sId)
                    _unit.value = repository.getUnitById(sId, uId)
                    onCreated(topic.id)
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to add topic."
                }
            )
        }
    }
}
