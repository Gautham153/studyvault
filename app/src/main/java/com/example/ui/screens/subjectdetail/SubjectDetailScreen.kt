package com.example.ui.screens.subjectdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Importance
import com.example.data.model.QuestionItem
import com.example.data.model.TopicStatus
import com.example.ui.components.StatusBadge
import com.example.ui.screens.questions.AddEditQuestionDialog
import com.example.ui.screens.questions.QuestionsViewModel
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.MaterialItem
import com.example.util.FileUtils
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Subject
import com.example.data.model.UnitModel
import com.example.ui.components.CardWithSubtleBorder
import com.example.ui.components.ProgressBarWithPercent
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubjectIconBadge
import com.example.ui.theme.BackgroundNavy
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(
    subjectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToUnitDetail: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    initialTabIndex: Int = 0,
    viewModel: SubjectDetailViewModel = viewModel()
) {
    LaunchedEffect(subjectId) {
        viewModel.loadSubject(subjectId)
    }

    val subject by viewModel.subject.collectAsState()
    val unitActionMessage by viewModel.unitActionMessage.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(initialTabIndex) }
    val tabTitles = listOf("Overview", "Materials", "Questions")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNavy)
            .testTag("subject_detail_screen")
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "${subject?.name ?: "Subject"} / Overview",
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

        subject?.let { sub ->
            val materials by viewModel.materials.collectAsState()

            // One shared questions state for this subject: the Overview counts and the
            // Questions tab read the same repository-backed data, so counts stay
            // consistent immediately after add/edit/delete operations.
            val questionsViewModel: QuestionsViewModel = viewModel(key = "questions_${sub.id}")
            val questionsState by questionsViewModel.uiState.collectAsState()
            LaunchedEffect(sub.id) {
                questionsViewModel.loadForSubject(sub.id)
            }

            // Hero card with subject summary
            SubjectHeroCard(
                subject = sub,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("subject_hero_card")
            )

            // Tabs row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = BackgroundNavy,
                contentColor = PrimaryBlueLight,
                divider = { Box(modifier = Modifier.height(1.dp).background(BorderSubtle)) },
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = PrimaryBlueLight,
                        height = 3.dp
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selectedTabIndex == index) TextPrimary else TextSecondary,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> SubjectOverviewTab(
                    subject = sub,
                    materialsCount = materials.size,
                    questionsCount = questionsState.questions.size,
                    onNavigateToUnit = { unitId -> onNavigateToUnitDetail(sub.id, unitId) },
                    onSelectMaterialsTab = { selectedTabIndex = 1 },
                    onSelectQuestionsTab = { selectedTabIndex = 2 },
                    onCreateUnit = viewModel::createUnit,
                    onRenameUnit = { unit, name, order -> viewModel.updateUnit(unit.id, name, order) },
                    onDeleteUnit = { unit -> viewModel.deleteUnit(unit.id) },
                    unitActionMessage = unitActionMessage,
                    onClearUnitActionMessage = viewModel::clearUnitActionMessage
                )
                1 -> SubjectMaterialsTab(subject = sub, viewModel = viewModel)
                2 -> SubjectQuestionsTab(subject = sub, viewModel = questionsViewModel)
            }
        }
    }
}

@Composable
private fun SubjectHeroCard(
    subject: Subject,
    modifier: Modifier = Modifier
) {
    CardWithSubtleBorder(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = SurfaceDark
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubjectIconBadge(
                iconKey = subject.iconKey,
                size = 54.dp,
                containerColor = SurfaceElevated
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${subject.preparationPercent}% prepared",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${subject.unitsCount} Units • ${subject.topicsCount} Topics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SubjectOverviewTab(
    subject: Subject,
    materialsCount: Int,
    questionsCount: Int,
    onNavigateToUnit: (String) -> Unit,
    onSelectMaterialsTab: () -> Unit,
    onSelectQuestionsTab: () -> Unit,
    onCreateUnit: (String, Int?) -> Unit,
    onRenameUnit: (UnitModel, String, Int?) -> Unit,
    onDeleteUnit: (UnitModel) -> Unit,
    unitActionMessage: String?,
    onClearUnitActionMessage: () -> Unit
) {
    var showAddUnitDialog by remember { mutableStateOf(false) }
    var unitToRename by remember { mutableStateOf<UnitModel?>(null) }
    var unitToDelete by remember { mutableStateOf<UnitModel?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(title = "Unit Progress")
                TextButton(
                    onClick = { showAddUnitDialog = true },
                    modifier = Modifier.testTag("add_unit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = PrimaryBlueLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Unit", color = PrimaryBlueLight)
                }
            }
        }

        unitActionMessage?.let { msg ->
            item {
                CardWithSubtleBorder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClearUnitActionMessage() }
                        .testTag("unit_action_banner"),
                    backgroundColor = SurfaceDark,
                    borderColor = if (msg.contains("Failed", ignoreCase = true) ||
                        msg.contains("still has", ignoreCase = true)
                    ) StatusRed else StatusGreen
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (msg.contains("Failed", ignoreCase = true) ||
                            msg.contains("still has", ignoreCase = true)
                        ) StatusRed else StatusGreen,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // List of Units
        items(subject.units, key = { it.id }) { unit ->
            UnitProgressRow(
                unit = unit,
                onClick = { onNavigateToUnit(unit.id) },
                onEdit = { unitToRename = unit },
                onDelete = { unitToDelete = unit }
            )
        }

        if (subject.units.isEmpty()) {
            item {
                Text(
                    text = "No units yet. Tap \"Add Unit\" to create the first unit for this subject.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("empty_units_message")
                )
            }
        }

        // Needs Attention Card
        if (subject.needsAttentionTopics.isNotEmpty()) {
            item {
                NeedsAttentionCard(topics = subject.needsAttentionTopics)
            }
        }

        // Quick Links for Materials and Questions
        item {
            CardWithSubtleBorder(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("materials_count_card"),
                onClick = onSelectMaterialsTab
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
                            contentDescription = "Materials",
                            tint = PrimaryBlueLight,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Materials ($materialsCount)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open Materials",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        item {
            CardWithSubtleBorder(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("questions_count_card"),
                onClick = onSelectQuestionsTab
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
                            contentDescription = "Important Questions",
                            tint = StatusAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Important Questions ($questionsCount)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open Questions",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showAddUnitDialog) {
        UnitNameDialog(
            title = "Add Unit",
            initialName = "",
            initialOrder = (subject.units.maxOfOrNull { it.unitNumber } ?: 0) + 1,
            confirmLabel = "Add Unit",
            onDismiss = { showAddUnitDialog = false },
            onConfirm = { name, order ->
                onCreateUnit(name, order)
                showAddUnitDialog = false
            }
        )
    }

    unitToRename?.let { unit ->
        UnitNameDialog(
            title = "Rename Unit",
            initialName = unit.title,
            initialOrder = unit.unitNumber,
            confirmLabel = "Save",
            onDismiss = { unitToRename = null },
            onConfirm = { name, order ->
                onRenameUnit(unit, name, order)
                unitToRename = null
            }
        )
    }

    unitToDelete?.let { unit ->
        AlertDialog(
            onDismissRequest = { unitToDelete = null },
            title = {
                Text(
                    text = "Delete ${unit.title}?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "A unit can only be deleted when it has no topics or questions. " +
                        "Linked study materials keep their file but lose the unit link.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUnit(unit)
                        unitToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                    modifier = Modifier.testTag("confirm_delete_unit_${unit.id}")
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { unitToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun UnitNameDialog(
    title: String,
    initialName: String,
    initialOrder: Int?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int?) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var orderText by remember(initialOrder) { mutableStateOf(initialOrder?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Unit Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = PrimaryBlueLight,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = orderText,
                    onValueChange = { orderText = it.filter { c -> c.isDigit() } },
                    label = { Text("Order (optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = PrimaryBlueLight,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, orderText.toIntOrNull()) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun UnitProgressRow(
    unit: UnitModel,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unit_progress_row_${unit.unitNumber}"),
        onClick = onClick
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
                    text = "Unit ${unit.unitNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${unit.preparationPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (unit.preparationPercent == 100) StatusGreen else TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("unit_edit_${unit.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename unit",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("unit_delete_${unit.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete unit",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = unit.title,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProgressBarWithPercent(
                percent = unit.preparationPercent,
                height = 5.dp
            )
        }
    }
}

@Composable
private fun NeedsAttentionCard(
    topics: List<String>
) {
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("needs_attention_card"),
        backgroundColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Needs Attention",
                    tint = StatusAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Needs Attention",
                    style = MaterialTheme.typography.titleSmall,
                    color = StatusAmber,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            topics.forEach { topic ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(StatusAmber)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = topic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectMaterialsTab(
    subject: Subject,
    viewModel: SubjectDetailViewModel
) {
    val context = LocalContext.current
    val materials by viewModel.materials.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    var materialToDelete by remember { androidx.compose.runtime.mutableStateOf<MaterialItem?>(null) }

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

    // Supported document mime types
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("subject_materials_tab"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Upload Action Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Study Materials (${materials.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Encrypted private storage (max 25 MB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = { documentPickerLauncher.launch(supportedMimeTypes) },
                    enabled = !isUploading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("upload_material_button")
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
        }

        // Upload in-progress banner
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
                        if (uploadProgress != null) {
                            LinearProgressIndicator(
                                progress = { uploadProgress ?: 0.5f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = PrimaryBlueLight,
                                trackColor = SurfaceElevated
                            )
                        } else {
                            LinearProgressIndicator(
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
        }

        // Status / Error Banners
        statusMessage?.let { msg ->
            item {
                CardWithSubtleBorder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.clearMessages() },
                    backgroundColor = SurfaceDark,
                    borderColor = StatusGreen
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✓ $msg",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        errorMessage?.let { err ->
            item {
                CardWithSubtleBorder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.clearMessages() },
                    backgroundColor = SurfaceDark,
                    borderColor = StatusRed
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✕ $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Empty State
        if (materials.isEmpty() && !isUploading) {
            item {
                CardWithSubtleBorder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .testTag("empty_materials_card"),
                    backgroundColor = SurfaceDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No study materials yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Upload PDFs, notes, or slides up to 25 MB for this subject.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { documentPickerLauncher.launch(supportedMimeTypes) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload First Material")
                        }
                    }
                }
            }
        }

        // Materials List
        items(materials, key = { it.id }) { item ->
            CardWithSubtleBorder(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("material_card_${item.id}"),
                onClick = { viewModel.openMaterial(context, item) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = item.fileType,
                                tint = PrimaryBlueLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = item.fileType,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${item.fileType} • ${item.fileSize} • ${item.uploadDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    // Open / Download Button
                    IconButton(
                        onClick = { viewModel.openMaterial(context, item) },
                        modifier = Modifier.testTag("open_material_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Open or Download",
                            tint = PrimaryBlueLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Delete Button
                    IconButton(
                        onClick = { materialToDelete = item },
                        modifier = Modifier.testTag("delete_material_${item.id}")
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

    // Delete Confirmation Dialog
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
}

@Composable
private fun SubjectQuestionsTab(subject: Subject, viewModel: QuestionsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var questionToEdit by remember { mutableStateOf<QuestionItem?>(null) }
    var questionToDelete by remember { mutableStateOf<QuestionItem?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Important Questions (${uiState.questions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("add_question_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Question")
                }
            }
        }

        uiState.errorMessage?.let { err ->
            item {
                CardWithSubtleBorder(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SurfaceDark,
                    borderColor = StatusRed
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✕ $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        uiState.successMessage?.let { msg ->
            item {
                CardWithSubtleBorder(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SurfaceDark,
                    borderColor = StatusGreen
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✓ $msg",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlueLight)
                }
            }
        } else if (uiState.questions.isEmpty()) {
            item {
                CardWithSubtleBorder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .testTag("empty_questions_card"),
                    backgroundColor = SurfaceDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No questions added yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add exam or practice questions for this subject.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(uiState.questions, key = { it.id }) { item ->
                CardWithSubtleBorder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("question_card_${item.id}")
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
                                text = item.questionText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                IconButton(
                                    onClick = { questionToEdit = item },
                                    modifier = Modifier
                                        .testTag("edit_question_${item.id}")
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
                                    onClick = { questionToDelete = item },
                                    modifier = Modifier
                                        .testTag("delete_question_${item.id}")
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
                                text = "${item.marks} marks",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryBlueLight,
                                fontWeight = FontWeight.Medium
                            )
                            Text(text = "•", color = TextMuted)
                            Text(
                                text = item.unitName.ifBlank { subject.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(text = "•", color = TextMuted)
                            val topicStatus = when (item.status.lowercase()) {
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

    if (showAddDialog) {
        AddEditQuestionDialog(
            subjectId = subject.id,
            onDismiss = { showAddDialog = false },
            onSave = { text, marks, importance, status ->
                viewModel.addQuestion(
                    subjectId = subject.id,
                    questionText = text,
                    marks = marks,
                    importance = importance,
                    status = status
                )
                showAddDialog = false
            }
        )
    }

    questionToEdit?.let { q ->
        AddEditQuestionDialog(
            subjectId = subject.id,
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

