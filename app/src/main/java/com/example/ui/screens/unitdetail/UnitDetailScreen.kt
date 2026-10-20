package com.example.ui.screens.unitdetail

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.MaterialItem
import com.example.data.model.QuestionItem
import com.example.data.model.TopicModel
import com.example.data.model.TopicStatus
import com.example.ui.components.CardWithSubtleBorder
import com.example.ui.components.ProgressBarWithPercent
import com.example.ui.components.StatusBadge
import com.example.ui.screens.questions.AddEditQuestionDialog
import com.example.ui.screens.questions.QuestionsViewModel
import com.example.util.FileUtils
import com.example.ui.theme.BackgroundNavy
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitDetailScreen(
    subjectId: String,
    unitId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTopicDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnitDetailViewModel = viewModel()
) {
    // Shared subject-level questions state; this screen filters it to the current unit so
    // counts and CRUD results stay consistent with the subject questions tab.
    val questionsViewModel: QuestionsViewModel = viewModel(key = "questions_$subjectId")

    LaunchedEffect(subjectId, unitId) {
        viewModel.loadUnit(subjectId, unitId)
        questionsViewModel.loadForSubject(subjectId)
    }

    val unit by viewModel.unit.collectAsState()
    val subject by viewModel.subject.collectAsState()
    val materials by viewModel.materials.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val questionsState by questionsViewModel.uiState.collectAsState()
    val unitQuestions = questionsState.questions.filter { it.unitId == unitId }

    var materialsExpanded by remember { mutableStateOf(false) }
    var questionsExpanded by remember { mutableStateOf(false) }
    var materialToDelete by remember { mutableStateOf<MaterialItem?>(null) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }
    var showAddTopicDialog by remember { mutableStateOf(false) }
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
            .testTag("unit_detail_screen")
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "${subject?.name ?: "Subject"} / Unit ${unit?.unitNumber ?: ""}",
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
            actions = {
                unit?.let { u ->
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .border(1.5.dp, PrimaryBlueLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${u.preparationPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryBlueLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundNavy
            )
        )

        unit?.let { currentUnit ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Unit Title and Progress Card
                item {
                    CardWithSubtleBorder(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = SurfaceDark
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = currentUnit.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Unit Preparation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${currentUnit.preparationPercent}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            ProgressBarWithPercent(
                                percent = currentUnit.preparationPercent,
                                height = 5.dp
                            )
                        }
                    }
                }

                // Section header: Topics
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Topics (${currentUnit.topics.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = { showAddTopicDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("add_unit_topic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Topic")
                        }
                    }
                }

                // Topic Items
                items(currentUnit.topics, key = { it.id }) { topic ->
                    TopicRowCard(
                        topic = topic,
                        onClick = { onNavigateToTopicDetail(topic.id) }
                    )
                }

                // Materials section: live repository-backed list with upload/open/delete
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    CardWithSubtleBorder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unit_materials_card"),
                        onClick = { materialsExpanded = !materialsExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = PrimaryBlueLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Materials (${materials.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = if (materialsExpanded) "Collapse materials" else "Expand materials",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (materialsExpanded) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Encrypted private storage (max 25 MB)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                                Button(
                                    onClick = { documentPickerLauncher.launch(supportedMimeTypes) },
                                    enabled = !isUploading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryBlue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("upload_unit_material_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Upload",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Upload",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (isUploading) {
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

                            statusMessage?.let { msg ->
                                CardWithSubtleBorder(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.clearMessages() },
                                    backgroundColor = SurfaceDark,
                                    borderColor = StatusGreen
                                ) {
                                    Text(
                                        text = "✓ $msg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusGreen,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            errorMessage?.let { err ->
                                CardWithSubtleBorder(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.clearMessages() },
                                    backgroundColor = SurfaceDark,
                                    borderColor = StatusRed
                                ) {
                                    Text(
                                        text = "✕ $err",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusRed,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            if (materials.isEmpty() && !isUploading) {
                                CardWithSubtleBorder(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("empty_unit_materials_card"),
                                    backgroundColor = SurfaceDark
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No materials in this unit yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Upload PDFs, notes, or slides up to 25 MB for this unit.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }

                            materials.forEach { item ->
                                CardWithSubtleBorder(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("unit_material_card_${item.id}"),
                                    onClick = { viewModel.openMaterial(context, item) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
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
                                                contentDescription = item.fileType,
                                                tint = PrimaryBlueLight,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${item.fileType} • ${item.fileSize} • ${item.uploadDate}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.openMaterial(context, item) },
                                            modifier = Modifier.testTag("open_unit_material_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Open or Download",
                                                tint = PrimaryBlueLight,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { materialToDelete = item },
                                            modifier = Modifier.testTag("delete_unit_material_${item.id}")
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
                        }
                    }
                }

                // Questions section: live repository-backed list with add/edit/delete
                item {
                    CardWithSubtleBorder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unit_questions_card"),
                        onClick = { questionsExpanded = !questionsExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = StatusAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Important Questions (${unitQuestions.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = if (questionsExpanded) "Collapse questions" else "Expand questions",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (questionsExpanded) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Questions for this unit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                                Button(
                                    onClick = { showAddQuestionDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("add_unit_question_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Question")
                                }
                            }

                            if (unitQuestions.isEmpty()) {
                                CardWithSubtleBorder(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("empty_unit_questions_card"),
                                    backgroundColor = SurfaceDark
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No questions for this unit yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Add exam or practice questions for this unit.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }

                            unitQuestions.forEach { question ->
                                CardWithSubtleBorder(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("unit_question_card_${question.id}")
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
                                                    onClick = { questionToEdit = question },
                                                    modifier = Modifier
                                                        .testTag("edit_unit_question_${question.id}")
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
                                                    onClick = { questionToDelete = question },
                                                    modifier = Modifier
                                                        .testTag("delete_unit_question_${question.id}")
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
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
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
            subjectId = subjectId,
            onDismiss = { showAddQuestionDialog = false },
            onSave = { text, marks, importance, status ->
                questionsViewModel.addQuestion(
                    subjectId = subjectId,
                    unitId = unitId,
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
            subjectId = subjectId,
            initialQuestion = q,
            onDismiss = { questionToEdit = null },
            onSave = { text, marks, importance, status ->
                questionsViewModel.updateQuestion(
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
                        questionsViewModel.deleteQuestion(q.id)
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

    if (showAddTopicDialog) {
        AddTopicDialog(
            onDismiss = { showAddTopicDialog = false },
            onSave = { title ->
                viewModel.createTopic(title) { topicId ->
                    onNavigateToTopicDetail(topicId)
                }
                showAddTopicDialog = false
            }
        )
    }
}

@Composable
private fun AddTopicDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Topic",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Create a new topic in this unit.",
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        validationError = null
                    },
                    label = { Text("Topic title") },
                    singleLine = true,
                    isError = validationError != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_topic_title_field")
                )
                validationError?.let { message ->
                    Text(
                        text = message,
                        color = StatusRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("add_topic_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        validationError = "Topic title cannot be blank."
                    } else {
                        onSave(title)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.testTag("add_topic_confirm_button")
            ) {
                Text("Add", color = Color.White)
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
private fun TopicRowCard(
    topic: TopicModel,
    onClick: () -> Unit
) {
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("topic_row_${topic.id}"),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Topic status indicator dot
                val indicatorColor = when (topic.status) {
                    TopicStatus.COMPLETED -> StatusGreen
                    TopicStatus.LEARNING -> StatusAmber
                    TopicStatus.NEEDS_REVISION -> StatusRed
                    TopicStatus.NOT_STARTED -> TextMuted
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            StatusBadge(status = topic.status)
        }
    }
}
