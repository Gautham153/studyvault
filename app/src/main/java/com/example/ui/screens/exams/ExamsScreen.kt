package com.example.ui.screens.exams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ExamItem
import com.example.data.model.Subject
import com.example.ui.components.CardWithSubtleBorder
import com.example.ui.components.ProgressBarWithPercent
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubjectIconBadge
import com.example.ui.theme.BackgroundNavy
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.StudyDateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    onNavigateToSubjectDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamsViewModel = viewModel()
) {
    val upcomingExams by viewModel.upcomingExams.collectAsState()
    val pastExams by viewModel.pastExams.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val formError by viewModel.formError.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<ExamItem?>(null) }
    var selectedExam by remember { mutableStateOf<ExamItem?>(null) }
    var examPendingDeletion by remember { mutableStateOf<ExamItem?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundNavy
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(BackgroundNavy)
                .padding(horizontal = 16.dp)
                .testTag("exams_screen")
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exams",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            viewModel.clearFormError()
                            showAddDialog = true
                        },
                        modifier = Modifier.testTag("add_exam_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Exam",
                            tint = PrimaryBlueLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item { SectionHeader(title = "Upcoming Exams") }
                if (upcomingExams.isEmpty()) {
                    item {
                        EmptyExamState(
                            text = "No upcoming exams scheduled. Tap + to plan one.",
                            testTag = "upcoming_exams_empty"
                        )
                    }
                } else {
                    items(upcomingExams, key = { "upcoming_${it.id}" }) { exam ->
                        ExamCard(
                            exam = exam,
                            onClick = { selectedExam = exam }
                        )
                    }
                }

                if (pastExams.isNotEmpty()) {
                    item { SectionHeader(title = "Past Exams") }
                    items(pastExams, key = { "past_${it.id}" }) { exam ->
                        ExamCard(
                            exam = exam,
                            onClick = { selectedExam = exam }
                        )
                    }
                }
            }
        }

        selectedExam?.let { exam ->
            ModalBottomSheet(
                onDismissRequest = { selectedExam = null },
                sheetState = sheetState,
                containerColor = SurfaceDark
            ) {
                ExamDetailSheet(
                    exam = exam,
                    onOpenSubject = {
                        val subId = exam.subjectId
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            selectedExam = null
                            onNavigateToSubjectDetail(subId)
                        }
                    },
                    onEdit = {
                        viewModel.clearFormError()
                        selectedExam = null
                        editingExam = exam
                    },
                    onDelete = {
                        selectedExam = null
                        examPendingDeletion = exam
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        ExamFormDialog(
            initialExam = null,
            availableSubjects = subjects,
            isSaving = isSaving,
            formError = formError,
            onDismiss = {
                viewModel.clearFormError()
                showAddDialog = false
            },
            onSave = { subjectId, name, date, venue, description ->
                viewModel.createExam(subjectId, name, date, venue, description) {
                    showAddDialog = false
                }
            }
        )
    }

    editingExam?.let { exam ->
        ExamFormDialog(
            initialExam = exam,
            availableSubjects = subjects,
            isSaving = isSaving,
            formError = formError,
            onDismiss = {
                viewModel.clearFormError()
                editingExam = null
            },
            onSave = { subjectId, name, date, venue, description ->
                viewModel.updateExam(exam.id, subjectId, name, date, venue, description) {
                    editingExam = null
                }
            }
        )
    }

    examPendingDeletion?.let { exam ->
        AlertDialog(
            onDismissRequest = { examPendingDeletion = null },
            title = {
                Text(
                    text = "Delete exam?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This removes ${exam.subjectName} exam scheduled on ${exam.examDate}.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExam(exam.id) { examPendingDeletion = null }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_delete_exam")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { examPendingDeletion = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun EmptyExamState(text: String, testTag: String) {
    CardWithSubtleBorder(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .testTag(testTag),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

@Composable
private fun ExamDetailSheet(
    exam: ExamItem,
    onOpenSubject: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubjectIconBadge(iconKey = exam.iconKey, size = 44.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = exam.name.ifBlank { exam.subjectName },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${exam.subjectName} • ${exam.code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = StudyDateUtils.describeDaysUntil(exam.daysRemaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusAmber,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = PrimaryBlueLight,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Exam Date: ${exam.examDate}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = StatusGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Venue: ${exam.venue}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        if (!exam.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = exam.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Preparation Status (${exam.preparationPercent}%)",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProgressBarWithPercent(percent = exam.preparationPercent, height = 8.dp)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onOpenSubject,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text(text = "Open Subject Syllabus", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .testTag("edit_exam_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit", color = TextPrimary)
            }
            Button(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .testTag("delete_exam_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete", color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ExamCard(
    exam: ExamItem,
    onClick: () -> Unit
) {
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_card_${exam.id}"),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectIconBadge(iconKey = exam.iconKey, size = 42.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = exam.name.ifBlank { exam.subjectName },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${exam.subjectName} • ${exam.examDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = StudyDateUtils.describeDaysUntil(exam.daysRemaining),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Preparation",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = "${exam.preparationPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            ProgressBarWithPercent(
                percent = exam.preparationPercent,
                height = 5.dp
            )
        }
    }
}

@Composable
private fun ExamFormDialog(
    initialExam: ExamItem?,
    availableSubjects: List<Subject>,
    isSaving: Boolean,
    formError: String?,
    onDismiss: () -> Unit,
    onSave: (subjectId: String, name: String, date: String, venue: String, description: String?) -> Unit
) {
    var subjectId by remember(initialExam) {
        mutableStateOf(initialExam?.subjectId ?: availableSubjects.firstOrNull()?.id.orEmpty())
    }
    var examName by remember(initialExam) { mutableStateOf(initialExam?.name.orEmpty()) }
    var examDate by remember(initialExam) {
        mutableStateOf(
            initialExam?.examDate ?: StudyDateUtils.toDisplayDate(StudyDateUtils.todayIso())
        )
    }
    var venue by remember(initialExam) {
        mutableStateOf(initialExam?.venue?.takeIf { it != "Venue not set" }.orEmpty())
    }
    var description by remember(initialExam) { mutableStateOf(initialExam?.description.orEmpty()) }

    val selectedSubject = availableSubjects.find { it.id == subjectId }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = if (initialExam == null) "Schedule Exam" else "Edit Exam",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (availableSubjects.isEmpty()) {
                    Text(
                        text = "Create a subject first, then schedule its exam.",
                        color = StatusAmber,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "Subject",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableSubjects.forEach { sub ->
                            Button(
                                onClick = { subjectId = sub.id },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (subjectId == sub.id) PrimaryBlue else SurfaceElevated
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("exam_subject_chip_${sub.id}")
                            ) {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                    if (selectedSubject != null) {
                        Text(
                            text = "Selected: ${selectedSubject.name} (${selectedSubject.code})",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                OutlinedTextField(
                    value = examName,
                    onValueChange = { examName = it },
                    label = { Text("Exam Name") },
                    placeholder = { Text("e.g. Mid Term") },
                    singleLine = true,
                    modifier = Modifier.testTag("exam_name_field"),
                    colors = examFieldColors()
                )

                OutlinedTextField(
                    value = examDate,
                    onValueChange = { examDate = it },
                    label = { Text("Exam Date") },
                    placeholder = { Text("e.g. Nov 15, 2026") },
                    singleLine = true,
                    modifier = Modifier.testTag("exam_date_field"),
                    colors = examFieldColors()
                )

                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("Venue (optional)") },
                    placeholder = { Text("e.g. Main Hall B") },
                    singleLine = true,
                    modifier = Modifier.testTag("exam_venue_field"),
                    colors = examFieldColors()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.testTag("exam_notes_field"),
                    colors = examFieldColors()
                )

                if (formError != null) {
                    Text(
                        text = formError,
                        color = StatusAmber,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("exam_form_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(subjectId, examName, examDate, venue, description.ifBlank { null })
                },
                enabled = !isSaving && availableSubjects.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_exam_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary
                    )
                } else {
                    Text("Save Exam")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isSaving) onDismiss() }) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun examFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SurfaceDark,
    unfocusedContainerColor = SurfaceDark,
    focusedBorderColor = PrimaryBlueLight,
    unfocusedBorderColor = BorderSubtle,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
