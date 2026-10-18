package com.example.data.repository

import com.example.data.model.Difficulty
import com.example.data.model.ExamItem
import com.example.data.model.Importance
import com.example.data.model.RevisionEvent
import com.example.data.model.Subject
import com.example.data.model.TopicStatus
import com.example.util.StudyDateUtils

/**
 * Relative urgency bucket derived purely from a recommendation's numeric score.
 */
enum class PriorityLevel {
    HIGH,
    MEDIUM,
    LOW;

    val displayLabel: String
        get() = when (this) {
            HIGH -> "High Priority"
            MEDIUM -> "Medium Priority"
            LOW -> "Low Priority"
        }
}

/**
 * The concrete next step for a recommended topic: begin study, keep going, or revise.
 */
enum class StudyAction {
    STUDY,
    CONTINUE,
    REVISE;

    val displayLabel: String
        get() = when (this) {
            STUDY -> "Start"
            CONTINUE -> "Continue"
            REVISE -> "Revise"
        }
}

/**
 * Immutable, dependency-free view of everything the engine needs about a single topic.
 * Built from real topic data (status, importance, difficulty, revision history) plus the
 * nearest upcoming exam for its subject; never from placeholder fields.
 */
data class TopicStudySnapshot(
    val topicId: String,
    val subjectId: String,
    val unitId: String,
    val topicName: String,
    val subjectName: String,
    val unitName: String,
    val iconKey: String,
    val status: TopicStatus,
    val importance: Importance,
    val difficulty: Difficulty,
    val revisionCount: Int,
    val lastRevisedIsoDate: String?,
    val daysUntilExam: Int?,
    val examDateDisplay: String?,
    val examName: String?
)

/**
 * A scored, explained recommendation. [reasons] are short, human-readable strings derived from
 * the same data used for scoring so the UI never has to guess why a topic surfaced.
 */
data class StudyRecommendation(
    val topicId: String,
    val subjectId: String,
    val unitId: String,
    val topicName: String,
    val subjectName: String,
    val unitName: String,
    val iconKey: String,
    val status: TopicStatus,
    val importance: Importance,
    val score: Int,
    val level: PriorityLevel,
    val action: StudyAction,
    val reasons: List<String>,
    val estimatedMinutes: Int,
    val daysUntilExam: Int?,
    val examDateDisplay: String?,
    val examName: String?,
    val lastRevisedIsoDate: String?,
    val revisionCount: Int
)

/** Bounds for selecting a realistic Today's Plan (no persistence; recomputed on demand). */
data class TodayPlanConfig(
    val maxItems: Int = StudyPriorityEngine.DEFAULT_PLAN_MAX_ITEMS,
    val dailyMinutesTarget: Int? = null
)

/**
 * Deterministic Smart Study Priority Engine (Phase 7).
 *
 * Scoring is pure Kotlin with zero Compose/Supabase/clock dependencies: the same inputs always
 * produce the same scores and ordering. A topic's 0-100 score is the sum of four non-overlapping
 * components (max 100):
 *
 *  - Importance       0-25  : HIGH 25, MEDIUM 15, LOW 8
 *  - Progress gap     0-25  : NOT_STARTED 25, LEARNING 18, NEEDS_REVISION 10, COMPLETED 0
 *  - Exam urgency     0-30  : nearest upcoming exam for the subject (today/tomorrow 30, <=3d 28,
 *                             <=7d 24, <=14d 16, <=30d 8, later 3, none 0)
 *  - Revision signal  0-20  : NEEDS_REVISION 20; otherwise an unrevised completed/learning topic
 *                             (14/10) or one not revised for >=10/>=21 days (6/12)
 */
object StudyPriorityEngine {

    const val DEFAULT_PLAN_MAX_ITEMS = 5

    // Component caps (kept explicit so tests can assert the bounds).
    const val MAX_IMPORTANCE = 25
    const val MAX_PROGRESS_GAP = 25
    const val MAX_EXAM_URGENCY = 30
    const val MAX_REVISION_SIGNAL = 20
    const val MAX_SCORE = MAX_IMPORTANCE + MAX_PROGRESS_GAP + MAX_EXAM_URGENCY + MAX_REVISION_SIGNAL

    fun buildSnapshots(
        subjects: List<Subject>,
        upcomingExams: List<ExamItem>,
        revisionEventsByTopic: Map<String, List<RevisionEvent>> = emptyMap(),
        todayIso: String = StudyDateUtils.todayIso()
    ): List<TopicStudySnapshot> {
        val nearestExamBySubject = nearestUpcomingExamBySubject(upcomingExams, todayIso)
        return subjects.flatMap { subject ->
            subject.units.flatMap { unit ->
                unit.topics.map { topic ->
                    val exam = nearestExamBySubject[subject.id]
                    // Revision history is the source of truth; the denormalized topic fields are
                    // only a fallback, matching how Phase 6 derives revision summaries.
                    val events = revisionEventsByTopic[topic.id].orEmpty()
                    val lastRevised = events.latestRevisionIso() ?: topic.lastRevisedAt
                    TopicStudySnapshot(
                        topicId = topic.id,
                        subjectId = subject.id,
                        unitId = unit.id,
                        topicName = topic.title,
                        subjectName = subject.name,
                        unitName = unit.title,
                        iconKey = subject.iconKey,
                        status = topic.status,
                        importance = topic.importance,
                        difficulty = topic.difficulty,
                        revisionCount = maxOf(topic.revisionCount, events.size),
                        lastRevisedIsoDate = StudyDateUtils.isoDatePortion(lastRevised),
                        daysUntilExam = exam?.first,
                        examDateDisplay = exam?.second?.examDate,
                        examName = exam?.second?.name?.takeIf { it.isNotBlank() }
                    )
                }
            }
        }
    }

    /** All scored recommendations, including completed topics, in deterministic priority order. */
    fun buildRecommendations(
        subjects: List<Subject>,
        upcomingExams: List<ExamItem>,
        revisionEventsByTopic: Map<String, List<RevisionEvent>> = emptyMap(),
        todayIso: String = StudyDateUtils.todayIso()
    ): List<StudyRecommendation> =
        buildRecommendations(buildSnapshots(subjects, upcomingExams, revisionEventsByTopic, todayIso), todayIso)

    fun buildRecommendations(
        snapshots: List<TopicStudySnapshot>,
        todayIso: String = StudyDateUtils.todayIso()
    ): List<StudyRecommendation> =
        snapshots
            .map { snapshot -> score(snapshot, todayIso) }
            .sortedWith(recommendationOrder())

    /** Scores a single snapshot into a recommendation. Exposed for focused testing. */
    fun score(
        snapshot: TopicStudySnapshot,
        todayIso: String = StudyDateUtils.todayIso()
    ): StudyRecommendation {
        val revisionSignal = revisionSignalScore(snapshot, todayIso)
        val raw = importanceScore(snapshot.importance) +
            progressGapScore(snapshot.status) +
            examUrgencyScore(snapshot.daysUntilExam) +
            revisionSignal
        val score = raw.coerceIn(0, MAX_SCORE)

        return StudyRecommendation(
            topicId = snapshot.topicId,
            subjectId = snapshot.subjectId,
            unitId = snapshot.unitId,
            topicName = snapshot.topicName,
            subjectName = snapshot.subjectName,
            unitName = snapshot.unitName,
            iconKey = snapshot.iconKey,
            status = snapshot.status,
            importance = snapshot.importance,
            score = score,
            level = levelFor(score),
            action = actionFor(snapshot.status, revisionSignal),
            reasons = reasons(snapshot, todayIso, revisionSignal),
            estimatedMinutes = estimatedMinutes(snapshot, revisionSignal),
            daysUntilExam = snapshot.daysUntilExam,
            examDateDisplay = snapshot.examDateDisplay,
            examName = snapshot.examName,
            lastRevisedIsoDate = snapshot.lastRevisedIsoDate,
            revisionCount = snapshot.revisionCount
        )
    }

    /**
     * Selects a realistic Today's Plan: highest-priority topics first, excluding completed topics
     * that have no revision need, capped at [config]'s item count and (when set) an approximate
     * total-minutes budget. At least one topic is always returned when any exist.
     */
    fun selectTodayPlan(
        recommendations: List<StudyRecommendation>,
        config: TodayPlanConfig = TodayPlanConfig()
    ): List<StudyRecommendation> {
        val maxItems = config.maxItems.coerceAtLeast(0)
        if (maxItems == 0) return emptyList()

        val candidates = recommendations
            .filter { !(it.status == TopicStatus.COMPLETED && it.action != StudyAction.REVISE) }
            .sortedWith(recommendationOrder())
            .distinctBy { it.topicId }

        val target = config.dailyMinutesTarget
        if (target == null || target <= 0) return candidates.take(maxItems)

        val selected = mutableListOf<StudyRecommendation>()
        var minutes = 0
        for (candidate in candidates) {
            if (selected.size >= maxItems) break
            val projected = minutes + candidate.estimatedMinutes
            if (selected.isNotEmpty() && projected > target) continue
            selected.add(candidate)
            minutes = projected
        }
        return selected
    }

    // --- Scoring components ---

    private fun importanceScore(importance: Importance): Int = when (importance) {
        Importance.HIGH -> MAX_IMPORTANCE
        Importance.MEDIUM -> 15
        Importance.LOW -> 8
    }

    private fun progressGapScore(status: TopicStatus): Int = when (status) {
        TopicStatus.NOT_STARTED -> MAX_PROGRESS_GAP
        TopicStatus.LEARNING -> 18
        TopicStatus.NEEDS_REVISION -> 10
        TopicStatus.COMPLETED -> 0
    }

    private fun examUrgencyScore(daysUntilExam: Int?): Int {
        val days = daysUntilExam ?: return 0
        return when {
            days < 0 -> 0
            days <= 1 -> MAX_EXAM_URGENCY
            days <= 3 -> 28
            days <= 7 -> 24
            days <= 14 -> 16
            days <= 30 -> 8
            else -> 3
        }
    }

    private fun revisionSignalScore(snapshot: TopicStudySnapshot, todayIso: String): Int {
        if (snapshot.status == TopicStatus.NEEDS_REVISION) return MAX_REVISION_SIGNAL

        val lastRevised = snapshot.lastRevisedIsoDate
        if (lastRevised == null) {
            return when (snapshot.status) {
                TopicStatus.COMPLETED -> 14
                TopicStatus.LEARNING -> 10
                else -> 0
            }
        }

        val idleDays = StudyDateUtils.daysBetweenIso(lastRevised, todayIso) ?: return 0
        return when {
            idleDays >= 21 -> 12
            idleDays >= 10 -> 6
            else -> 0
        }
    }

    private fun levelFor(score: Int): PriorityLevel = when {
        score >= 75 -> PriorityLevel.HIGH
        score >= 50 -> PriorityLevel.MEDIUM
        else -> PriorityLevel.LOW
    }

    private fun actionFor(status: TopicStatus, revisionSignal: Int): StudyAction = when (status) {
        TopicStatus.NEEDS_REVISION -> StudyAction.REVISE
        TopicStatus.NOT_STARTED -> StudyAction.STUDY
        TopicStatus.LEARNING -> StudyAction.CONTINUE
        TopicStatus.COMPLETED -> if (revisionSignal > 0) StudyAction.REVISE else StudyAction.CONTINUE
    }

    private fun estimatedMinutes(snapshot: TopicStudySnapshot, revisionSignal: Int): Int {
        val base = when (snapshot.status) {
            TopicStatus.NOT_STARTED -> 40
            TopicStatus.LEARNING -> 30
            TopicStatus.NEEDS_REVISION -> 25
            TopicStatus.COMPLETED -> 25
        }
        val difficultyAdjustment = when (snapshot.difficulty) {
            Difficulty.HARD -> 10
            Difficulty.MEDIUM -> 0
            Difficulty.EASY -> -5
        }
        val examAdjustment =
            if (revisionSignal > 0 && (snapshot.daysUntilExam ?: Int.MAX_VALUE) <= 3) 10 else 0
        return (base + difficultyAdjustment + examAdjustment).coerceAtLeast(10)
    }

    private fun reasons(
        snapshot: TopicStudySnapshot,
        todayIso: String,
        revisionSignal: Int
    ): List<String> {
        val reasons = mutableListOf<String>()

        when (snapshot.status) {
            TopicStatus.NEEDS_REVISION -> reasons += "Flagged for revision"
            TopicStatus.NOT_STARTED -> reasons += "Not started yet"
            TopicStatus.LEARNING -> reasons += "In progress"
            TopicStatus.COMPLETED -> if (revisionSignal > 0) reasons += "Completed, revision due"
        }

        if (snapshot.importance == Importance.HIGH) reasons += "High importance"

        val days = snapshot.daysUntilExam
        if (days != null) {
            val examLabel = snapshot.examName ?: "Exam"
            reasons += when {
                days <= 0 -> "$examLabel today"
                days == 1 -> "$examLabel tomorrow"
                else -> "$examLabel in $days days"
            }
        }

        if (snapshot.status != TopicStatus.NEEDS_REVISION && revisionSignal > 0) {
            val lastRevised = snapshot.lastRevisedIsoDate
            if (lastRevised == null) {
                reasons += "Never revised"
            } else {
                val idle = StudyDateUtils.daysBetweenIso(lastRevised, todayIso)
                if (idle != null && idle >= 0) reasons += "Not revised in $idle days"
            }
        }

        return reasons.distinct()
    }

    private fun nearestUpcomingExamBySubject(
        upcomingExams: List<ExamItem>,
        todayIso: String
    ): Map<String, Pair<Int, ExamItem>> {
        val result = mutableMapOf<String, Pair<Int, ExamItem>>()
        for (exam in upcomingExams) {
            val days = examDaysUntil(exam, todayIso) ?: continue
            if (days < 0) continue
            val existing = result[exam.subjectId]
            if (existing == null || days < existing.first) {
                result[exam.subjectId] = days to exam
            }
        }
        return result
    }

    private fun examDaysUntil(exam: ExamItem, todayIso: String): Int? {
        val iso = exam.isoDate.takeIf { it.isNotBlank() }
        if (iso != null) return StudyDateUtils.daysBetweenIso(todayIso, iso)
        return exam.daysRemaining
    }

    /**
     * Deterministic ordering: score desc, then exam urgency asc (no exam last), then importance
     * desc, then topic id asc. Guarantees a stable plan regardless of input iteration order.
     */
    fun recommendationOrder(): Comparator<StudyRecommendation> =
        compareByDescending<StudyRecommendation> { it.score }
            .thenBy { it.daysUntilExam ?: Int.MAX_VALUE }
            .thenByDescending { it.importance.ordinal }
            .thenBy { it.topicId }

    private fun List<RevisionEvent>.latestRevisionIso(): String? =
        maxOfOrNull { it.revisedAt } ?: firstOrNull()?.revisedAt
}
