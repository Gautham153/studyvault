package com.example.ui.screens.topicdetail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Importance
import com.example.data.model.MaterialItem
import com.example.data.model.NoteItem
import com.example.data.model.QuestionItem
import com.example.data.model.TopicModel
import com.example.data.model.TopicStatus
import com.example.data.repository.StudyMaterialRepository
import com.example.data.repository.StudyMaterialRepositoryProvider
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TopicDetailViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository,
    private val materialRepository: StudyMaterialRepository = StudyMaterialRepositoryProvider.repository
) : ViewModel() {

    private val _topic = MutableStateFlow<TopicModel?>(null)
    val topic: StateFlow<TopicModel?> = _topic.asStateFlow()

    private var currentTopicId: String? = null
    private val _loadedTopicId = MutableStateFlow<String?>(null)

    val isUploading: StateFlow<Boolean> = materialRepository.isUploading
    val uploadProgress: StateFlow<Float?> = materialRepository.uploadProgress

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val materials: StateFlow<List<MaterialItem>> = combine(
        _topic,
        materialRepository.materials,
        _loadedTopicId
    ) { currentTop, allMats, loadedId ->
        val tId = currentTop?.id
        if (tId != null) {
            val fromRepo = allMats.filter { it.topicId == tId }
            if (loadedId == tId) {
                fromRepo
            } else {
                if (fromRepo.isNotEmpty()) fromRepo else currentTop.relatedMaterials
            }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questions: StateFlow<List<QuestionItem>> = _topic
        .map { it?.questions ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteItem>> = _topic
        .map { it?.notes ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.subjects.collect { _ ->
                currentTopicId?.let { id ->
                    _topic.value = repository.getTopicById(id)
                }
            }
        }
    }

    fun loadTopic(topicId: String) {
        if (currentTopicId != topicId) {
            _loadedTopicId.value = null
        }
        currentTopicId = topicId
        _topic.value = repository.getTopicById(topicId)
        val subjectId = _topic.value?.subjectId
        if (subjectId != null) {
            val requestedTopicId = topicId
            viewModelScope.launch {
                val result = materialRepository.loadMaterials(subjectId)
                if (result.isSuccess && currentTopicId == requestedTopicId) {
                    _loadedTopicId.value = requestedTopicId
                }
            }
        }
    }

    fun updateStatus(newStatus: TopicStatus) {
        _topic.value?.let { current ->
            repository.updateTopicStatus(current.id, newStatus)
            _topic.value = current.copy(status = newStatus)
        }
    }

    fun uploadMaterial(filename: String, mimeType: String, bytes: ByteArray) {
        val current = _topic.value ?: return
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = materialRepository.uploadMaterial(
                subjectId = current.subjectId,
                unitId = current.unitId,
                topicId = current.id,
                originalFilename = filename,
                mimeType = mimeType,
                fileBytes = bytes
            )
            result.fold(
                onSuccess = { item ->
                    _statusMessage.value = "Successfully uploaded ${item.title}"
                    currentTopicId?.let { loadTopic(it) }
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
                    currentTopicId?.let { id ->
                        _topic.value = repository.getTopicById(id)
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

    fun addQuestion(
        questionText: String,
        marks: Int,
        importance: Importance,
        status: String
    ) {
        val currentTopic = _topic.value ?: return
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = repository.addQuestion(
                subjectId = currentTopic.subjectId,
                unitId = currentTopic.unitId,
                topicId = currentTopic.id,
                questionText = questionText,
                marks = marks,
                importance = importance,
                status = status
            )
            result.fold(
                onSuccess = { _ ->
                    _statusMessage.value = "Added question successfully"
                    currentTopicId?.let { loadTopic(it) }
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Failed to add question."
                }
            )
        }
    }

    fun updateQuestion(question: QuestionItem) {
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = repository.updateQuestion(question)
            result.fold(
                onSuccess = {
                    _statusMessage.value = "Updated question successfully"
                    currentTopicId?.let { loadTopic(it) }
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Failed to update question."
                }
            )
        }
    }

    fun deleteQuestion(questionId: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = repository.deleteQuestion(questionId)
            result.fold(
                onSuccess = {
                    _statusMessage.value = "Deleted question"
                    currentTopicId?.let { loadTopic(it) }
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Failed to delete question."
                }
            )
        }
    }

    fun addNote(title: String, content: String) {
        val currentTopic = _topic.value ?: return
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = repository.addNote(
                subjectId = currentTopic.subjectId,
                topicId = currentTopic.id,
                title = title,
                content = content
            )
            result.fold(
                onSuccess = { _ ->
                    _statusMessage.value = "Note saved successfully"
                    currentTopicId?.let { loadTopic(it) }
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Failed to save note."
                }
            )
        }
    }

    fun updateNote(note: NoteItem) {
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = repository.updateNote(note)
            result.fold(
                onSuccess = {
                    _statusMessage.value = "Note updated successfully"
                    currentTopicId?.let { loadTopic(it) }
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Failed to update note."
                }
            )
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            val result = repository.deleteNote(noteId)
            result.fold(
                onSuccess = {
                    _statusMessage.value = "Deleted note"
                    currentTopicId?.let { loadTopic(it) }
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Failed to delete note."
                }
            )
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _statusMessage.value = null
    }
}
