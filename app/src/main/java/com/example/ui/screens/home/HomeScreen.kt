package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ExamItem
import com.example.data.model.MaterialItem
import com.example.data.model.RevisionEvent
import com.example.data.model.StudyItem
import com.example.data.model.Subject
import com.example.data.model.TopicStatus
import com.example.data.repository.StudyAction
import com.example.data.repository.StudyPlanning
import com.example.data.repository.StudyRecommendation
import com.example.ui.components.CardWithSubtleBorder
import com.example.ui.components.PriorityScoreBadge
import com.example.ui.components.ProgressBarWithPercent
import com.example.ui.components.SectionHeader
import com.example.ui.components.SemesterProgressCard
import com.example.ui.components.SubjectIconBadge
import com.example.ui.theme.BackgroundNavy
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

internal fun todayPlanItemKey(recommendation: StudyRecommendation): String =
    "plan_${recommendation.recommendationKey}"

internal fun revisionItemKey(item: StudyItem): String = "revision_${item.cardKey}"

internal fun todayPlanEmptyText(isLoading: Boolean, hasSubjects: Boolean, hasError: Boolean): String = when {
    isLoading && !hasSubjects -> "Loading your plan..."
    hasError -> "Plan unavailable — see the notice above."
    !hasSubjects -> "No study data yet — add subjects, topics, or important questions to build your plan."
    else -> "Your plan is clear — nothing flagged right now."
}

@Composable
fun HomeScreen(
    onNavigateToSubjectDetail: (String) -> Unit,
    onNavigateToSubjectQuestions: (String) -> Unit = {},
    onNavigateToTopicDetail: (String) -> Unit,
    onNavigateToSubjectsTab: () -> Unit,
    onNavigateToStudyTab: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val overallProgress by viewModel.overallProgress.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val todayPlan by viewModel.todayStudyPlan.collectAsState()
    val allRecommendations by viewModel.allStudyRecommendations.collectAsState()
    val upcomingExams by viewModel.upcomingExams.collectAsState()
    val recentRevisions by viewModel.recentRevisions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val revisionPriorities = allRecommendations
        .filter { it.status == TopicStatus.NEEDS_REVISION || it.action == StudyAction.REVISE }
        .take(3)
        .map { StudyPlanning.recommendationToStudyItem(it) }
    val recentMaterials = viewModel.getRecentlyAddedMaterials()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNavy)
            .testTag("home_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Greeting Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Good evening,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${overallProgress.studentName} 👋",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Profile Avatar Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, BorderSubtle, CircleShape)
                        .clickable(onClick = onOpenProfile)
                        .testTag("profile_avatar_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = PrimaryBlueLight,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        errorMessage?.let { error ->
            item {
                CardWithSubtleBorder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("configuration_error_banner"),
                    backgroundColor = SurfaceDark,
                    borderColor = StatusAmber
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Cloud Persistence Notice",
                            style = MaterialTheme.typography.titleSmall,
                            color = StatusAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Main Progress Card: Semester Preparation
        item {
            SemesterProgressCard(
                progressPercent = overallProgress.overallPercentage,
                subjectsCount = overallProgress.subjectsCount,
                examsCount = overallProgress.upcomingExamsCount,
                totalTopics = overallProgress.totalTopics,
                completedTopics = overallProgress.completedTopics,
                inProgressTopics = overallProgress.inProgressTopics,
                modifier = Modifier.testTag("semester_progress_card")
            )
        }

        if (isLoading && subjects.isEmpty()) {
            item {
                LoadingHint()
            }
        }

        // TODAY'S PLAN Section
        item {
            SectionHeader(
                title = "Today's Plan",
                actionText = "See all",
                onActionClick = onNavigateToStudyTab
            )
        }

        actionError?.let { error ->
            item {
                EmptyHint(
                    text = "Couldn't update topic: $error",
                    modifier = Modifier.testTag("today_plan_action_error")
                )
            }
        }

        if (todayPlan.isEmpty()) {
            item {
                EmptyHint(
                    text = todayPlanEmptyText(
                        isLoading = isLoading,
                        hasSubjects = subjects.isNotEmpty(),
                        hasError = errorMessage != null
                    ),
                    modifier = Modifier.testTag("today_plan_empty")
                )
            }
        }

        items(todayPlan, key = { todayPlanItemKey(it) }) { recommendation ->
            TodayPlanItemCard(
                recommendation = recommendation,
                onOpenTopic = { onNavigateToTopicDetail(recommendation.topicId) },
                onOpenQuestions = { onNavigateToSubjectQuestions(recommendation.subjectId) },
                onStart = { viewModel.startTopic(recommendation.topicId) },
                onComplete = { viewModel.completeTopic(recommendation.topicId) },
                onRevise = { viewModel.reviseTopic(recommendation.topicId) }
            )
        }

        // UPCOMING EXAMS Section
        item {
            SectionHeader(
                title = "Upcoming Exams",
                actionText = if (upcomingExams.isNotEmpty()) "See all" else null,
                onActionClick = onNavigateToSubjectsTab
            )
        }

        if (upcomingExams.isEmpty()) {
            item {
                EmptyHint(
                    text = "No upcoming exams scheduled.",
                    modifier = Modifier.testTag("upcoming_exams_empty")
                )
            }
        }

        items(upcomingExams.take(3)) { exam ->
            UpcomingExamCard(
                exam = exam,
                onClick = { onNavigateToSubjectDetail(exam.subjectId) }
            )
        }

        // REVISION PRIORITIES Section
        item {
            SectionHeader(
                title = "Revision Priorities",
                actionText = if (revisionPriorities.isNotEmpty()) "See all" else null,
                onActionClick = onNavigateToStudyTab
            )
        }

        if (revisionPriorities.isEmpty()) {
            item {
                EmptyHint(
                    text = "No topics flagged for revision.",
                    modifier = Modifier.testTag("revision_priorities_empty")
                )
            }
        }

        items(revisionPriorities, key = { revisionItemKey(it) }) { studyItem ->
            StudyTodayItemCard(
                item = studyItem,
                onClick = {
                    if (studyItem.isQuestion) {
                        onNavigateToSubjectQuestions(studyItem.subjectId)
                    } else {
                        onNavigateToTopicDetail(studyItem.topicId)
                    }
                }
            )
        }

        // RECENT REVISIONS Section
        item {
            SectionHeader(
                title = "Recent Revisions",
                actionText = if (recentRevisions.isNotEmpty()) "See all" else null,
                onActionClick = onNavigateToStudyTab
            )
        }

        if (recentRevisions.isEmpty()) {
            item {
                EmptyHint(
                    text = "No revisions recorded yet.",
                    modifier = Modifier.testTag("recent_revisions_empty")
                )
            }
        }

        items(recentRevisions.take(3), key = { it.id }) { revision ->
            RevisionEventCard(
                event = revision,
                onClick = { onNavigateToTopicDetail(revision.topicId) }
            )
        }

        // SUBJECT PROGRESS Section
        item {
            SectionHeader(
                title = "Subject Progress",
                actionText = "See all",
                onActionClick = onNavigateToSubjectsTab
            )
        }

        items(subjects.take(3)) { subject ->
            SubjectProgressRowCard(
                subject = subject,
                onClick = { onNavigateToSubjectDetail(subject.id) }
            )
        }

        // RECENTLY ADDED Section
        item {
            SectionHeader(
                title = "Recently Added"
            )
        }

        items(recentMaterials) { material ->
            RecentlyAddedCard(material = material)
        }
    }
}

@Composable
private fun TodayPlanItemCard(
    recommendation: StudyRecommendation,
    onOpenTopic: () -> Unit,
    onOpenQuestions: () -> Unit,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onRevise: () -> Unit
) {
    val isQuestion = recommendation.isQuestion
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                if (isQuestion) "today_plan_card_q_${recommendation.questionId}"
                else "today_plan_card_${recommendation.topicId}"
            ),
        onClick = if (isQuestion) onOpenQuestions else onOpenTopic
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubjectIconBadge(iconKey = recommendation.iconKey, size = 42.dp)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recommendation.subjectName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = recommendation.topicName,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${recommendation.level.displayLabel} • ~${recommendation.estimatedMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                PriorityScoreBadge(score = recommendation.score)
            }

            if (recommendation.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = recommendation.reasons.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val primaryLabel = when (recommendation.action) {
                    StudyAction.STUDY -> "Start"
                    StudyAction.CONTINUE -> "Continue"
                    StudyAction.REVISE -> "Revise"
                    StudyAction.PRACTICE -> "Practice"
                }
                val primaryAction = when (recommendation.action) {
                    StudyAction.STUDY -> onStart
                    StudyAction.CONTINUE -> onOpenTopic
                    StudyAction.REVISE -> onRevise
                    StudyAction.PRACTICE -> onOpenQuestions
                }
                Button(
                    onClick = primaryAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlueLight,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag(
                        if (isQuestion) "today_plan_primary_q_${recommendation.questionId}"
                        else "today_plan_primary_${recommendation.topicId}"
                    )
                ) {
                    Text(text = primaryLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (!isQuestion && recommendation.status != TopicStatus.COMPLETED) {
                    OutlinedButton(
                        onClick = onComplete,
                        modifier = Modifier.testTag("today_plan_done_${recommendation.topicId}")
                    ) {
                        Text(text = "Mark done", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyTodayItemCard(
    item: StudyItem,
    onClick: () -> Unit
) {
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                if (item.isQuestion) "revision_priority_card_q_${item.questionId}"
                else "revision_priority_card_${item.topicId}"
            ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubjectIconBadge(
                iconKey = item.iconKey,
                size = 42.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.subjectName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.topicName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = when (item.topicStatus) {
                        TopicStatus.NEEDS_REVISION -> StatusRed
                        TopicStatus.LEARNING -> StatusAmber
                        TopicStatus.COMPLETED -> StatusGreen
                        TopicStatus.NOT_STARTED -> PrimaryBlueLight
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.statusNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Open topic",
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun SubjectProgressRowCard(
    subject: Subject,
    onClick: () -> Unit
) {
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subject_progress_row_${subject.id}"),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubjectIconBadge(iconKey = subject.iconKey, size = 38.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${subject.preparationPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                ProgressBarWithPercent(
                    percent = subject.preparationPercent,
                    height = 5.dp
                )
            }
        }
    }
}

@Composable
private fun LoadingHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("home_loading"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = PrimaryBlueLight,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Loading your study data...",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    )
}

@Composable
private fun UpcomingExamCard(
    exam: ExamItem,
    onClick: () -> Unit
) {
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_exam_card_${exam.id}"),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubjectIconBadge(iconKey = exam.iconKey, size = 42.dp)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exam.subjectName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = exam.name.ifBlank { exam.examDate },
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = exam.examDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val isToday = exam.daysRemaining == 0
                Text(
                    text = when {
                        isToday -> "Today"
                        else -> "${exam.daysRemaining}d"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (exam.daysRemaining <= 3) StatusRed else StatusAmber,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${exam.preparationPercent}% ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun RevisionEventCard(
    event: RevisionEvent,
    onClick: () -> Unit
) {
    CardWithSubtleBorder(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_revision_card_${event.id}"),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubjectIconBadge(iconKey = event.subjectName, size = 40.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.topicName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${event.subjectName} • Revision #${event.revisionNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Text(
                text = event.revisedAtDisplay,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun RecentlyAddedCard(
    material: MaterialItem
) {
    CardWithSubtleBorder(
        modifier = Modifier.fillMaxWidth()
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
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${material.fileType} • ${material.fileSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
