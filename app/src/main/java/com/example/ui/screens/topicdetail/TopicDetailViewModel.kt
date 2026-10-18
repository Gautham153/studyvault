package com.example.ui.screens.topicdetail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Importance
import com.example.data.model.MaterialItem
import com.example.data.model.NoteItem
import com.example.data.model.QuestionItem
import com.example.data.model.RevisionEvent
import com.example.data.model.TopicModel
import com.example.data.model.TopicStatus
import com.example.data.model.displayLabel
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

    // Live materials for this topic, sourced exclusively from the material repository.
    // No cached relatedMaterials fallback: an empty repository list stays empty, so deleted
    // files never reappear from stale embedded topic data.
    val materials: StateFlow<List<MaterialItem>> = combine(
        _topic,
        materialRepository.materials,
        _loadedTopicId
    ) { currentTop, allMats, loadedId ->
        val tId = currentTop?.id
        if (tId != null && loadedId == tId) {
            allMats.filter { it.topicId == tId }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isUpdatingStatus = MutableStateFlow(false)
    val isUpdatingStatus: StateFlow<Boolean> = _isUpdatingStatus.asStateFlow()

    private val _isMarkingRevised = MutableStateFlow(false)
    val isMarkingRevised: StateFlow<Boolean> = _isMarkingRevised.asStateFlow()

    private val _revisionEvents = MutableStateFlow<List<RevisionEvent>>(emptyList())
    val revisionEvents: StateFlow<List<RevisionEvent>> = _revisionEvents.asStateFlow()

    // Questions and notes for this topic, loaded explicitly from repository queries rather
    // than embedded topic snapshots, so CRUD results (in any repository implementation) are
    // always reflected and deletions cannot resurrect stale embedded copies.
    private val _questions = MutableStateFlow<List<QuestionItem>>(emptyList())
    val questions: StateFlow<List<QuestionItem>> = _questions.asStateFlow()

    private val _notes = MutableStateFlow<List<NoteItem>>(emptyList())
    val notes: StateFlow<List<NoteItem>> = _notes.asStateFlow()

    private val _questionsLoading = MutableStateFlow(false)
    val questionsLoading: StateFlow<Boolean> = _questionsLoading.asStateFlow()

    init {
        viewModelScope.launch {
            repository.subjects.collect { _ ->
                currentTopicId?.let { id ->
                    _topic.value = repository.getTopicById(id)
                    refreshQuestions(id)
                    refreshNotes(id)
                    refreshRevisionEvents(id)
                }
            }
        }
    }

    private fun refreshQuestions(topicId: String) {
        val requestedTopicId = topicId
        viewModelScope.launch {
            try {
                val list = repository.getQuestionsForTopic(topicId)
                if (currentTopicId == requestedTopicId) {
                    _questions.value = list
                }
            } catch (e: Exception) {
                if (currentTopicId == requestedTopicId) {
                    _errorMessage.value = e.message ?: "Failed to load questions."
                }
            }
        }
    }

    private fun refreshNotes(topicId: String) {
        val requestedTopicId = topicId
        viewModelScope.launch {
            try {
                val list = repository.getNotesForTopic(topicId)
                if (currentTopicId == requestedTopicId) {
                    _notes.value = list
                }
            } catch (e: Exception) {
                if (currentTopicId == requestedTopicId) {
                    _errorMessage.value = e.message ?: "Failed to load notes."
                }
            }
        }
    }

    fun loadTopic(topicId: String) {
        if (currentTopicId != topicId) {
            // Resource changed: reset load tracking and messages so state from the
            // previously viewed topic is never shown for this one.
            _loadedTopicId.value = null
            _statusMessage.value = null
            _errorMessage.value = null
            _questions.value = emptyList()
            _notes.value = emptyList()
            _revisionEvents.value = emptyList()
        }
        currentTopicId = topicId
        _topic.value = repository.getTopicById(topicId)
        refreshQuestions(topicId)
        refreshNotes(topicId)
        refreshRevisionEvents(topicId)
        val subjectId = _topic.value?.subjectId
        if (subjectId != null) {
            val requestedTopicId = topicId
            viewModelScope.launch {
                val result = materialRepository.loadMaterials(subjectId)
                // Mark loaded only on a genuine, current success for this topic.
                if (result.getOrNull() != null && currentTopicId == requestedTopicId) {
                    _loadedTopicId.value = requestedTopicId
                }
            }
        }
    }

    fun updateStatus(newStatus: TopicStatus) {
        val current = _topic.value ?: return
        if (_isUpdatingStatus.value) return
        val requestedTopicId = current.id
        if (current.status == newStatus) return

        _isUpdatingStatus.value = true
        _errorMessage.value = null
        _statusMessage.value = null
        viewModelScope.launch {
            val result = repository.updateTopicStatus(requestedTopicId, newStatus)
            result.fold(
                onSuccess = {
                    if (currentTopicId == requestedTopicId) {
                        _statusMessage.value = "Status updated to ${newStatus.displayLabel}"
                        _topic.value = repository.getTopicById(requestedTopicId)
                    }
                },
                onFailure = { err ->
                    if (currentTopicId == requestedTopicId) {
                        _errorMessage.value = err.message ?: "Failed to update status."
                        _topic.value = repository.getTopicById(requestedTopicId)
                    }
                }
            )
            _isUpdatingStatus.value = false
        }
    }

    fun markRevised(notes: String? = null) {
        val current = _topic.value ?: return
        if (_isMarkingRevised.value) return
        val requestedTopicId = current.id

        _isMarkingRevised.value = true
        _errorMessage.value = null
        _statusMessage.value = null
        viewModelScope.launch {
            val result = repository.markTopicRevised(requestedTopicId, notes)
            result.fold(
                onSuccess = { event ->
                    if (currentTopicId == requestedTopicId) {
                        _statusMessage.value = "Revision #${event.revisionNumber} recorded"
                        _topic.value = repository.getTopicById(requestedTopicId)
                        refreshRevisionEvents(requestedTopicId)
                    }
                },
                onFailure = { err ->
                    if (currentTopicId == requestedTopicId) {
                        _errorMessage.value = err.message ?: "Failed to record revision."
                    }
                }
            )
            _isMarkingRevised.value = false
        }
    }

    private fun refreshRevisionEvents(topicId: String) {
        if (currentTopicId != topicId) return
        val events = try {
            repository.getRevisionEventsForTopic(topicId)
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Failed to load revision history."
            emptyList()
        }
        if (currentTopicId == topicId) {
            _revisionEvents.value = events
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
        if (_isSaving.value) return // guard against duplicate submissions
        if (questionText.isBlank()) {
            _errorMessage.value = "Question text cannot be blank."
            return
        }
        if (marks <= 0) {
            _errorMessage.value = "Marks must be a positive number."
            return
        }
        _isSaving.value = true
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
            _isSaving.value = false
        }
    }

    fun updateQuestion(question: QuestionItem) {
        if (_isSaving.value) return // guard against duplicate submissions
        if (question.questionText.isBlank()) {
            _errorMessage.value = "Question text cannot be blank."
            return
        }
        if (question.marks <= 0) {
            _errorMessage.value = "Marks must be a positive number."
            return
        }
        _isSaving.value = true
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
            _isSaving.value = false
        }
    }

    fun deleteQuestion(questionId: String) {
        if (_isSaving.value) return // guard against duplicate submissions
        _isSaving.value = true
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
            _isSaving.value = false
        }
    }

    fun addNote(title: String, content: String) {
        val currentTopic = _topic.value ?: return
        if (_isSaving.value) return // guard against duplicate submissions
        if (title.isBlank()) {
            _errorMessage.value = "Note title cannot be blank."
            return
        }
        _isSaving.value = true
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
            _isSaving.value = false
        }
    }

    fun updateNote(note: NoteItem) {
        if (_isSaving.value) return // guard against duplicate submissions
        if (note.title.isBlank()) {
            _errorMessage.value = "Note title cannot be blank."
            return
        }
        _isSaving.value = true
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
            _isSaving.value = false
        }
    }

    fun deleteNote(noteId: String) {
        if (_isSaving.value) return // guard against duplicate submissions
        _isSaving.value = true
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
            _isSaving.value = false
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _statusMessage.value = null
    }
}
