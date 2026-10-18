package com.example.data.repository

import com.example.data.model.RevisionEvent
import com.example.data.model.StudyItem
import com.example.data.model.Subject
import com.example.data.model.TopicModel
import com.example.data.model.TopicStatus
import com.example.data.model.UnitModel
import com.example.util.StudyDateUtils
import kotlin.math.roundToInt

/** Topic counters derived from real topic data (never from fabricated placeholders). */
data class TopicCounts(
    val total: Int = 0,
    val completed: Int = 0,
    val inProgress: Int = 0,
    val notStarted: Int = 0,
    val needsRevision: Int = 0
)

/**
 * Pure derivation logic shared by the Supabase and in-memory repositories so progress,
 * priority items and revision summaries behave identically in both.
 */
object StudyPlanning {

    const val RECENT_REVISION_LIMIT = 5

    fun allTopics(subjects: List<Subject>): List<TopicModel> = subjects.flatMap { it.units }.flatMap { it.topics }

    /** Completed topics over total topics. Returns 0 when nothing is tracked (never a fake 100%). */
    fun preparationPercent(topics: List<TopicModel>): Int {
        if (topics.isEmpty()) return 0
        val completed = topics.count { it.status == TopicStatus.COMPLETED }
        return (completed * 100f / topics.size).roundToInt()
    }

    fun subjectsPreparationPercent(subjects: List<Subject>): Int = preparationPercent(allTopics(subjects))

    fun unitPercent(unit: UnitModel): Int = preparationPercent(unit.topics)

    fun topicCounts(topics: List<TopicModel>): TopicCounts = TopicCounts(
        total = topics.size,
        completed = topics.count { it.status == TopicStatus.COMPLETED },
        inProgress = topics.count { it.status == TopicStatus.LEARNING },
        notStarted = topics.count { it.status == TopicStatus.NOT_STARTED },
        needsRevision = topics.count { it.status == TopicStatus.NEEDS_REVISION }
    )

    fun priorityLevel(score: Int, status: TopicStatus): String = when {
        status == TopicStatus.NEEDS_REVISION || score >= 80 -> "High Priority"
        score >= 55 -> "Medium Priority"
        else -> "Low Priority"
    }

    /** Human status label based on the real topic status and revision history, not on a guess. */
    fun statusNote(status: TopicStatus, lastRevisedIsoDate: String?, todayIso: String = StudyDateUtils.todayIso()): String {
        val revisionSuffix = when {
            lastRevisedIsoDate == null -> "never revised"
            StudyDateUtils.isoDatePortion(lastRevisedIsoDate) == todayIso -> "revised today"
            else -> "last revised ${StudyDateUtils.toDisplayDate(lastRevisedIsoDate)}"
        }
        val base = when (status) {
            TopicStatus.COMPLETED -> "Completed"
            TopicStatus.LEARNING -> "In progress"
            TopicStatus.NEEDS_REVISION -> "Needs revision"
            TopicStatus.NOT_STARTED -> "Not started"
        }
        return "$base - $revisionSuffix"
    }

    /**
     * Topics that deserve attention: anything not fully completed, ordered by revision need,
     * then by the stored priority score. Completed topics are omitted unless [includeCompleted]
     * is set (used by the "All Topics" study filter).
     */
    fun buildPriorityStudyItems(
        subjects: List<Subject>,
        revisionEventsByTopic: Map<String, List<RevisionEvent>> = emptyMap(),
        todayIso: String = StudyDateUtils.todayIso(),
        includeCompleted: Boolean = false
    ): List<StudyItem> {
        return subjects
            .flatMap { subject -> subject.units.flatMap { unit -> unit.topics.map { Triple(subject, unit, it) } } }
            .map { (subject, unit, topic) ->
                val events = revisionEventsByTopic[topic.id].orEmpty()
                val lastRevised = events.lastRevisionIso() ?: topic.lastRevisedAt
                StudyItem(
                    topicId = topic.id,
                    subjectId = subject.id,
                    unitId = unit.id,
                    topicName = topic.title,
                    subjectName = subject.name,
                    unitName = unit.title,
                    priorityScore = topic.priorityScore,
                    priorityLevel = priorityLevel(topic.priorityScore, topic.status),
                    statusNote = statusNote(topic.status, lastRevised, todayIso),
                    topicStatus = topic.status,
                    revisionCount = maxOf(topic.revisionCount, events.size),
                    lastRevisedAt = lastRevised,
                    iconKey = subject.iconKey
                )
            }
            .filter { includeCompleted || it.topicStatus != TopicStatus.COMPLETED }
            .sortedWith(
                compareByDescending<StudyItem> { it.topicStatus == TopicStatus.NEEDS_REVISION }
                    .thenByDescending { it.priorityScore }
            )
    }

    /** Study items for topics that have at least one recorded revision, newest revision first. */
    fun buildRevisionStudyItems(
        subjects: List<Subject>,
        revisionEventsByTopic: Map<String, List<RevisionEvent>>
    ): List<StudyItem> {
        val lastRevisedByTopic = revisionEventsByTopic
            .filterValues { it.isNotEmpty() }
            .mapValues { (_, events) -> events.maxOf { it.revisedAt } }

        return buildPriorityStudyItems(subjects, revisionEventsByTopic, includeCompleted = true)
            .filter { lastRevisedByTopic.containsKey(it.topicId) }
            .sortedByDescending { lastRevisedByTopic[it.topicId] }
    }

    fun sortRevisionEventsNewestFirst(events: List<RevisionEvent>): List<RevisionEvent> =
        events.sortedWith(
            compareByDescending<RevisionEvent> { it.revisedAt }
                .thenByDescending { it.revisionNumber }
        )

    fun recentRevisions(events: List<RevisionEvent>, limit: Int = RECENT_REVISION_LIMIT): List<RevisionEvent> =
        sortRevisionEventsNewestFirst(events).take(limit)

    private fun List<RevisionEvent>.lastRevisionIso(): String? =
        maxOfOrNull { it.revisedAt } ?: firstOrNull()?.revisedAt
}
