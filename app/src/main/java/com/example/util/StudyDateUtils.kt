package com.example.util

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date helpers for exam planning and revision tracking.
 *
 * Deliberately avoids java.time (minSdk 24) and always compares calendar dates in the
 * device local time zone so an exam stored as "Oct 20, 2026" is treated as that date,
 * independent of the time of day or daylight-saving transitions.
 */
object StudyDateUtils {

    const val ISO_DATE_PATTERN = "yyyy-MM-dd"

    private val examDatePatterns = listOf(
        "MMM d, yyyy",
        "MMM dd, yyyy",
        "MMMM d, yyyy",
        "MMMM dd, yyyy",
        "yyyy-MM-dd",
        "d MMM yyyy",
        "d MMMM yyyy",
        "MMM d yyyy"
    )

    private val isoDateTimePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss"
    )

    private fun pattern(format: String, timeZone: TimeZone = TimeZone.getDefault()): SimpleDateFormat {
        val sdf = SimpleDateFormat(format, Locale.US)
        sdf.timeZone = timeZone
        sdf.isLenient = false
        return sdf
    }

    private fun parseWithPatterns(input: String, patterns: List<String>, timeZone: TimeZone): Date? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        for (p in patterns) {
            val position = ParsePosition(0)
            val parsed = pattern(p, timeZone).parse(trimmed, position)
            if (parsed != null && position.index > 0) return parsed
        }
        return null
    }

    private fun truncateToLocalDay(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    /** Stable calendar-day index; safe across DST and year boundaries. */
    private fun dayIndex(date: Date): Long {
        val cal = Calendar.getInstance()
        cal.time = date
        return cal.get(Calendar.YEAR).toLong() * 372L + cal.get(Calendar.DAY_OF_YEAR).toLong()
    }

    /** Parses a user-entered or stored exam date into an ISO "yyyy-MM-dd" string, or null when invalid. */
    fun parseExamDateToIso(input: String?, timeZone: TimeZone = TimeZone.getDefault()): String? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()
        val parsed = if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(trimmed)) {
            parseWithPatterns(trimmed, listOf(ISO_DATE_PATTERN), timeZone)
        } else {
            parseWithPatterns(trimmed, examDatePatterns, timeZone)
        } ?: return null
        return pattern(ISO_DATE_PATTERN, timeZone).format(truncateToLocalDay(parsed))
    }

    fun isValidExamDateInput(input: String?): Boolean = parseExamDateToIso(input) != null

    /** Reformats any stored/timestamp value into a readable "Oct 7, 2026" label. */
    fun toDisplayDate(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val trimmed = input.trim()
        val parsed = parseWithPatterns(trimmed, examDatePatterns, TimeZone.getDefault())
            ?: parseWithPatterns(trimmed, isoDateTimePatterns, TimeZone.getTimeZone("UTC"))
            ?: parseWithPatterns(trimmed, isoDateTimePatterns, TimeZone.getDefault())
            ?: return trimmed
        return pattern("MMM d, yyyy").format(parsed)
    }

    /** Parses an ISO date (yyyy-MM-dd) to a local-midnight Date. */
    private fun parseIsoDate(iso: String): Date? {
        if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(iso.trim())) return null
        val parsed = parseWithPatterns(iso.trim(), listOf(ISO_DATE_PATTERN), TimeZone.getDefault()) ?: return null
        return truncateToLocalDay(parsed)
    }

    fun todayIso(now: Long = System.currentTimeMillis()): String {
        return pattern(ISO_DATE_PATTERN).format(truncateToLocalDay(Date(now)))
    }

    /**
     * Signed day difference between the given ISO date and today:
     * 0 for today, positive for future dates, negative for past dates.
     */
    fun daysFromToday(iso: String?, now: Long = System.currentTimeMillis()): Int? {
        val target = iso?.let { parseIsoDate(it) } ?: return null
        val today = truncateToLocalDay(Date(now))
        return (dayIndex(target) - dayIndex(today)).toInt()
    }

    fun isPastDate(iso: String?, now: Long = System.currentTimeMillis()): Boolean {
        val days = daysFromToday(iso, now) ?: return false
        return days < 0
    }

    /**
     * Signed day difference between two ISO calendar dates (to - from): 0 for the same day,
     * positive when [toIso] is later, negative when earlier. Returns null when either date is
     * missing or unparseable, so callers never treat an unknown date as "today".
     */
    fun daysBetweenIso(fromIso: String?, toIso: String?): Int? {
        val from = fromIso?.let { parseIsoDate(it) } ?: return null
        val to = toIso?.let { parseIsoDate(it) } ?: return null
        return (dayIndex(to) - dayIndex(from)).toInt()
    }

    /** "Today", "Tomorrow", "in 5 days", "3 days ago", or "Date unavailable" when unknown. */
    fun describeDaysUntil(days: Int?): String {
        if (days == null) return "Date unavailable"
        return when {
            days == 0 -> "Today"
            days == 1 -> "Tomorrow"
            days > 1 -> "in $days days"
            days == -1 -> "Yesterday"
            else -> "${-days} days ago"
        }
    }

    /** Extracts the date portion of an ISO timestamp (e.g. "2026-10-07T09:30:00Z" -> "2026-10-07"). */
    fun isoDatePortion(timestamp: String?): String? {
        if (timestamp.isNullOrBlank()) return null
        val match = Regex("""(\d{4}-\d{2}-\d{2})""").find(timestamp) ?: return null
        return match.groupValues[1]
    }

    fun nowIsoTimestamp(): String = pattern("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC")).format(Date())
}
