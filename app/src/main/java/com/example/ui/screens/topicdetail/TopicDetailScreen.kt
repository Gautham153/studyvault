package com.example.ui.screens.topicdetail

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.MaterialItem
import com.example.data.model.NoteItem
import com.example.data.model.QuestionItem
import com.example.data.model.TopicStatus
import com.example.ui.components.CardWithSubtleBorder
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import androidx.compose.ui.graphics.Color
import com.example.ui.screens.questions.AddEditQuestionDialog
import com.example.ui.theme.BackgroundNavy
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDetailScreen(
    topicId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopicDetailViewModel = viewModel()
) {
    LaunchedEffect(topicId) {
        viewModel.loadTopic(topicId)
    }

    val topic by viewModel.topic.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val materials by viewModel.materials.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<NoteItem?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteItem?>(null) }

    var materialToDelete by remember { mutableStateOf<MaterialItem?>(null) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }
    var questionToEdit by remember { mutableStateOf<QuestionItem?>(null) }
    var questionToDelete by remember { mutableStateOf<QuestionItem?>(null) }

    val context = LocalContext.current

    // System Document Picker for selecting study files (zero broad storage permissions)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileResult = FileUtils.readFileFromUri(context, uri)
            fileResult.fold(
                onSuccess = { fileInfo ->
                    viewModel.uploadMaterial(
                        filename = fileInfo.fileName,
                        mimeType = fileInfo.mimeType,
                        bytes = fileInfo.bytes
                    )
                },
                onFailure = { error ->
                    Toast.makeText(context, error.message ?: "Failed to read file.", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val supportedMimeTypes = remember {
        arrayOf(
            "application/pdf",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip",
            "text/*",
            "image/*",
            "*/*"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNavy)
            .testTag("topic_detail_screen")
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = topic?.title ?: "Topic Detail",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundNavy
            )
        )

        topic?.let { currentTopic ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Topic Header and Status Pill
                item {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentTopic.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )

                            // Clickable status badge to cycle status
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val nextStatus = when (currentTopic.status) {
                                            TopicStatus.COMPLETED -> TopicStatus.NEEDS_REVISION
                                            TopicStatus.NEEDS_REVISION -> TopicStatus.LEARNING
                                            TopicStatus.LEARNING -> TopicStatus.NOT_STARTED
                                            TopicStatus.NOT_STARTED -> TopicStatus.COMPLETED
                                        }
                                        viewModel.updateStatus(nextStatus)
                                    }
                                    .testTag("status_toggle_badge")
                            ) {
                                StatusBadge(status = currentTopic.status)
                            }
                        }
                    }
                }

                // 2x2 Metadata grid: Difficulty, Importance, Last Studied, Next Revision
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetadataTile(
                                icon = Icons.Default.Speed,
                                iconColor = PrimaryBlueLight,
                                label = "Difficulty",
                                value = currentTopic.difficulty.name.lowercase().replaceFirstChar { it.uppercase() },
                                modifier = Modifier.weight(1f)
                            )
                            MetadataTile(
                                icon = Icons.Default.Star,
                                iconColor = StatusAmber,
                                label = "Importance",
                                value = currentTopic.importance.name.lowercase().replaceFirstChar { it.uppercase() },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetadataTile(
                                icon = Icons.Default.CalendarToday,
                                iconColor = TextSecondary,
                                label = "Last Studied",
                                value = currentTopic.lastStudiedDate,
                                modifier = Modifier.weight(1f)
                            )
                            MetadataTile(
                                icon = Icons.Default.EventRepeat,
                                iconColor = StatusGreen,
                                label = "Next Revision",
                                value = currentTopic.nextRevisionDate,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Summary Counters Row: live counts from repository-backed lists
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CountStatCard(
                            label = "Materials",
                            count = materials.size,
                            modifier = Modifier.weight(1f)
                        )
                        CountStatCard(
                            label = "Questions",
                            count = questions.size,
                            modifier = Modifier.weight(1f)
                        )
                        CountStatCard(
                            label = "My Notes",
                            count = notes.size,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Section: Related Files (live, repository-backed)
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "Related Files (${materials.size})")
                        Button(
                            onClick = { documentPickerLauncher.launch(supportedMimeTypes) },
                            enabled = !isUploading,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("add_topic_material_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add File", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isUploading) {
                    item {
                        CardWithSubtleBorder(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upload_progress_card"),
                            backgroundColor = SurfaceDark,
                            borderColor = PrimaryBlueLight
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Uploading file to secure storage...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = PrimaryBlueLight,
                                        strokeWidth = 2.dp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { uploadProgress ?: 0.5f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = PrimaryBlueLight,
                                    trackColor = SurfaceElevated
                                )
                            }
                        }
                    }
                }

                if (materials.isEmpty() && !isUploading) {
                    item {
                        CardWithSubtleBorder(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_materials_card"),
                            backgroundColor = SurfaceDark
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No files for this topic yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Add File' to attach PDFs, notes, or slides up to 25 MB.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(materials, key = { it.id }) { material ->
                        RelatedMaterialRow(
                            material = material,
                            onOpen = { viewModel.openMaterial(context, material) },
                            onDelete = { materialToDelete = material }
                        )
                    }
                }

                // Section: Questions (live, repository-backed)
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "Questions (${questions.size})")
                        Button(
                            onClick = { showAddQuestionDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueLight),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("add_topic_question_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = BackgroundNavy
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Question", color = BackgroundNavy, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (questions.isEmpty()) {
                    item {
                        CardWithSubtleBorder(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_questions_card"),
                            backgroundColor = SurfaceDark
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No questions for this topic yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Add Question' to record exam or practice questions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(questions, key = { it.id }) { question ->
                        TopicQuestionRow(
                            question = question,
                            onEdit = { questionToEdit = question },
                            onDelete = { questionToDelete = question }
                        )
                    }
                }

                // Section: My Notes
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "My Notes (${notes.size})")
                        Button(
                            onClick = { showAddNoteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueLight),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("add_note_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = BackgroundNavy)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Note", color = BackgroundNavy, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                statusMessage?.let { msg ->
                    item {
                        CardWithSubtleBorder(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = SurfaceDark,
                            borderColor = StatusGreen
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "✓ $msg", style = MaterialTheme.typography.bodySmall, color = StatusGreen, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                errorMessage?.let { err ->
                    item {
                        CardWithSubtleBorder(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.clearMessages() },
                            backgroundColor = SurfaceDark,
                            borderColor = StatusRed
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "✕ $err", style = MaterialTheme.typography.bodySmall, color = StatusRed, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (notes.isEmpty()) {
                    item {
                        CardWithSubtleBorder(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("empty_notes_card"),
                            backgroundColor = SurfaceDark
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No notes added for this topic yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Add Note' to jot down key concepts, summaries, or formulas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(notes, key = { it.id }) { note ->
                        CardWithSubtleBorder(
                            modifier = Modifier.fillMaxWidth().testTag("note_card_${note.id}")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = note.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row {
                                        IconButton(
                                            onClick = { noteToEdit = note },
                                            modifier = Modifier.testTag("edit_note_${note.id}").size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Note",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { noteToDelete = note },
                                            modifier = Modifier.testTag("delete_note_${note.id}").size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Note",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                if (note.content.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = note.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 4,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showAddNoteDialog) {
        AddEditNoteDialog(
            onDismiss = { showAddNoteDialog = false },
            onSave = { title, content ->
                viewModel.addNote(title, content)
                showAddNoteDialog = false
            }
        )
    }

    noteToEdit?.let { note ->
        AddEditNoteDialog(
            initialNote = note,
            onDismiss = { noteToEdit = null },
            onSave = { title, content ->
                viewModel.updateNote(note.copy(title = title, content = content))
                noteToEdit = null
            }
        )
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = {
                Text(text = "Delete Note?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "Are you sure you want to delete '${note.title}'? This cannot be undone.", color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNote(note.id)
                        noteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Delete material confirmation
    materialToDelete?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { materialToDelete = null },
            title = {
                Text(
                    text = "Delete Study Material?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${toDelete.title}'? This will permanently remove the file from secure storage.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMaterial(toDelete)
                        materialToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { materialToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showAddQuestionDialog) {
        AddEditQuestionDialog(
            subjectId = topic?.subjectId ?: "",
            onDismiss = { showAddQuestionDialog = false },
            onSave = { text, marks, importance, status ->
                viewModel.addQuestion(
                    questionText = text,
                    marks = marks,
                    importance = importance,
                    status = status
                )
                showAddQuestionDialog = false
            }
        )
    }

    questionToEdit?.let { q ->
        AddEditQuestionDialog(
            subjectId = topic?.subjectId ?: "",
            initialQuestion = q,
            onDismiss = { questionToEdit = null },
            onSave = { text, marks, importance, status ->
                viewModel.updateQuestion(
                    q.copy(
                        questionText = text,
                        marks = marks,
                        importance = importance,
                        status = status
                    )
                )
                questionToEdit = null
            }
        )
    }

    questionToDelete?.let { q ->
        AlertDialog(
            onDismissRequest = { questionToDelete = null },
            title = {
                Text(
                    text = "Delete Question?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this question? This cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQuestion(q.id)
                        questionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { questionToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun AddEditNoteDialog(
    initialNote: NoteItem? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialNote == null) "Add Note" else "Edit Note",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlueLight,
                        unfocusedBorderColor = SurfaceElevated,
                        focusedLabelColor = PrimaryBlueLight,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note Content") },
                    minLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlueLight,
                        unfocusedBorderColor = SurfaceElevated,
                        focusedLabelColor = PrimaryBlueLight,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Text(text = it, color = StatusRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedTitle = title.trim()
                    if (trimmedTitle.isBlank()) {
                        error = "Note title cannot be blank."
                        return@Button
                    }
                    onSave(trimmedTitle, content.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueLight)
            ) {
                Text("Save", color = BackgroundNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark
    )
}


@Composable
private fun MetadataTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    CardWithSubtleBorder(
        modifier = modifier,
        backgroundColor = SurfaceDark
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CountStatCard(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    CardWithSubtleBorder(
        modifier = modifier,
        backgroundColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun RelatedMaterialRow(
    material: MaterialItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("topic_material_card_${material.id}"),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = material.fileType,
                    tint = PrimaryBlueLight,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = material.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "${material.fileType} • ${material.fileSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = onOpen,
                modifier = Modifier.testTag("open_topic_material_${material.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Open or Download",
                    tint = PrimaryBlueLight,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_topic_material_${material.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TopicQuestionRow(
    question: QuestionItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("topic_question_card_${question.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = question.questionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .testTag("edit_topic_question_${question.id}")
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .testTag("delete_topic_question_${question.id}")
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${question.marks} marks",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryBlueLight,
                    fontWeight = FontWeight.Medium
                )
                Text(text = "•", color = TextMuted)
                val topicStatus = when (question.status.lowercase()) {
                    "completed", "prepared" -> TopicStatus.COMPLETED
                    "learning", "in_progress" -> TopicStatus.LEARNING
                    "needs_revision" -> TopicStatus.NEEDS_REVISION
                    else -> TopicStatus.NOT_STARTED
                }
                StatusBadge(status = topicStatus)
            }
        }
    }
}
