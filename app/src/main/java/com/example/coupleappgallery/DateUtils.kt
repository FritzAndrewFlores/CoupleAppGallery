package com.example.coupleappgallery

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Data holder for calculated anniversary milestones.
 */
data class AnniversaryMilestone(
    val title: String,
    val daysRemaining: Long,
    val isToday: Boolean,
    val targetDateMillis: Long = 0L
)

/**
 * Breakdown of time together into years, remaining months, and days.
 */
data class TimeTogetherBreakdown(
    val years: Int,
    val months: Int,
    val days: Int,
    val totalMonths: Int,
    val totalDays: Long
)

/**
 * Utility functions for date calculations and day-count countdowns.
 * Uses Calendar-based midnight normalization to prevent time-of-day truncation bugs
 * (e.g. integer division on millisecond differences undercounting calendar days).
 */
object DateUtils {

    /**
     * Calculates the signed difference in calendar days between [targetDateMillis] and [referenceDateMillis]
     * (defaults to now). Supports any epoch timestamp including pre-1970 dates (< 0L).
     *
     * @return Positive if [targetDateMillis] is in the future (+1 = tomorrow).
     *         Negative if [targetDateMillis] is in the past (-1 = yesterday).
     *         0 if both dates fall on the same calendar day in [timeZone] (or if [targetDateMillis] == 0L).
     */
    fun calculateDaysDifference(
        targetDateMillis: Long,
        referenceDateMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        if (targetDateMillis == 0L) return 0L

        val targetCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            timeInMillis = targetDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val refCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            timeInMillis = referenceDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = targetCal.timeInMillis - refCal.timeInMillis
        // Double division with roundToLong correctly accounts for 23h/25h Daylight Saving Time transitions
        return (diffMillis.toDouble() / 86_400_000.0).roundToLong()
    }

    /**
     * Formats days difference for countdowns and special days (e.g. "5 days until", "1 day until", "Today is the day! 🎉").
     */
    fun formatCountdownDays(days: Long, hasDate: Boolean = true): String {
        if (!hasDate) return ""
        return when {
            days > 1 -> "$days days until"
            days == 1L -> "1 day until"
            days == 0L -> "Today is the day! 🎉"
            days == -1L -> "1 day since"
            else -> "${abs(days)} days since"
        }
    }

    /**
     * Formats days difference for album folders (e.g. "5 days to go! ⏳", "1 day to go! ⏳", "Today! 🎉", "1 day ago", "5 days ago").
     */
    fun formatFolderDays(days: Long, hasDate: Boolean): String {
        if (!hasDate) return ""
        return when {
            days > 1 -> "$days days to go! ⏳"
            days == 1L -> "1 day to go! ⏳"
            days == 0L -> "Today! 🎉"
            days == -1L -> "1 day ago"
            else -> "${abs(days)} days ago"
        }
    }

    /**
     * Returns English ordinal string (e.g. 1st, 2nd, 3rd, 4th, 11th, 21st).
     */
    fun getOrdinal(n: Int): String {
        val absN = abs(n)
        val mod100 = absN % 100
        if (mod100 in 11..13) return "${n}th"
        return when (absN % 10) {
            1 -> "${n}st"
            2 -> "${n}nd"
            3 -> "${n}rd"
            else -> "${n}th"
        }
    }

    /**
     * Calculates the number of days together since [anniversaryMillis].
     * Day 1 is the anniversary date itself.
     * Returns 0 if [anniversaryMillis] is 0L.
     * Returns a negative difference if [anniversaryMillis] is set in the future.
     */
    fun calculateDaysTogether(
        anniversaryMillis: Long,
        referenceDateMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        if (anniversaryMillis == 0L) return 0L
        val diff = calculateDaysDifference(referenceDateMillis, anniversaryMillis, timeZone)
        return if (diff >= 0) diff + 1 else diff
    }

    /**
     * Calculates the next annual anniversary milestone (e.g. 1st Anniversary, 2nd Anniversary).
     */
    fun calculateNextAnnualAnniversary(
        anniversaryMillis: Long,
        referenceDateMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): AnniversaryMilestone? {
        if (anniversaryMillis == 0L) return null

        val startCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            timeInMillis = anniversaryMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val refCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            timeInMillis = referenceDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the start date is in the future, the first milestone is the anniversary itself
        if (startCal.timeInMillis > refCal.timeInMillis) {
            val diff = calculateDaysDifference(startCal.timeInMillis, refCal.timeInMillis, timeZone)
            return AnniversaryMilestone(
                title = "Anniversary Day",
                daysRemaining = diff,
                isToday = false,
                targetDateMillis = startCal.timeInMillis
            )
        }

        val startYear = startCal.get(Calendar.YEAR)
        val annivMonth = startCal.get(Calendar.MONTH)
        val annivDay = startCal.get(Calendar.DAY_OF_MONTH)
        val refYear = refCal.get(Calendar.YEAR)

        val candidateCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            set(Calendar.YEAR, refYear)
            set(Calendar.MONTH, annivMonth)
            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, annivDay.coerceAtMost(maxDay))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var diff = calculateDaysDifference(candidateCal.timeInMillis, refCal.timeInMillis, timeZone)
        var milestoneYear = refYear

        // If candidate date is on or before the starting date (e.g. started today), or this year's date already passed
        if (candidateCal.timeInMillis <= startCal.timeInMillis || diff < 0) {
            milestoneYear = refYear + 1
            candidateCal.apply {
                set(Calendar.YEAR, milestoneYear)
                set(Calendar.MONTH, annivMonth)
                val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, annivDay.coerceAtMost(maxDay))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            diff = calculateDaysDifference(candidateCal.timeInMillis, refCal.timeInMillis, timeZone)
        }

        val yearsTogether = (milestoneYear - startYear).coerceAtLeast(1)
        val ordinalYears = getOrdinal(yearsTogether)
        val isToday = (diff == 0L)

        val title = if (isToday) {
            "Happy $ordinalYears Anniversary! 🎉"
        } else {
            "$ordinalYears Anniversary"
        }

        return AnniversaryMilestone(
            title = title,
            daysRemaining = diff,
            isToday = isToday,
            targetDateMillis = candidateCal.timeInMillis
        )
    }

    /**
     * Calculates the next hundred-day milestone (e.g. 100 Days, 500 Days, 1000 Days).
     */
    fun calculateNextHundredDayMilestone(
        daysTogether: Long
    ): AnniversaryMilestone? {
        if (daysTogether <= 0L) return null
        if (daysTogether % 100L == 0L) {
            return AnniversaryMilestone(
                title = "Day $daysTogether Milestone! 💖",
                daysRemaining = 0L,
                isToday = true
            )
        }
        val nextTarget = ((daysTogether / 100L) + 1L) * 100L
        val remaining = nextTarget - daysTogether
        return AnniversaryMilestone(
            title = "Day $nextTarget",
            daysRemaining = remaining,
            isToday = false
        )
    }

    /**
     * Calculates the total full calendar months together since [anniversaryMillis].
     * Returns 0 if [anniversaryMillis] is in the future or not set (0L).
     */
    fun calculateMonthsTogether(
        anniversaryMillis: Long,
        referenceDateMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        if (anniversaryMillis == 0L) return 0
        val startCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            timeInMillis = anniversaryMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val refCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            timeInMillis = referenceDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (refCal.before(startCal)) return 0

        val startYear = startCal.get(Calendar.YEAR)
        val startMonth = startCal.get(Calendar.MONTH)
        val startDay = startCal.get(Calendar.DAY_OF_MONTH)

        val refYear = refCal.get(Calendar.YEAR)
        val refMonth = refCal.get(Calendar.MONTH)
        val refDay = refCal.get(Calendar.DAY_OF_MONTH)

        var months = (refYear - startYear) * 12 + (refMonth - startMonth)
        val maxRefDay = refCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val effectiveStartDay = startDay.coerceAtMost(maxRefDay)
        if (refDay < effectiveStartDay) {
            months -= 1
        }
        return months.coerceAtLeast(0)
    }

    /**
     * Calculates the detailed duration breakdown (years, remaining months, days, total months, total days)
     * between [anniversaryMillis] and [referenceDateMillis].
     */
    fun calculateTimeTogetherBreakdown(
        anniversaryMillis: Long,
        referenceDateMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): TimeTogetherBreakdown {
        if (anniversaryMillis == 0L) return TimeTogetherBreakdown(0, 0, 0, 0, 0L)
        val totalDays = calculateDaysTogether(anniversaryMillis, referenceDateMillis, timeZone)
        val totalMonths = calculateMonthsTogether(anniversaryMillis, referenceDateMillis, timeZone)
        val years = totalMonths / 12
        val remainingMonths = totalMonths % 12

        val startCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            timeInMillis = anniversaryMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val refCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            timeInMillis = referenceDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDay = startCal.get(Calendar.DAY_OF_MONTH)
        val refDay = refCal.get(Calendar.DAY_OF_MONTH)

        val days = if (refDay >= startDay) {
            refDay - startDay
        } else {
            val prevMonthCal = (refCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            val daysInPrevMonth = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            daysInPrevMonth - startDay.coerceAtMost(daysInPrevMonth) + refDay
        }

        return TimeTogetherBreakdown(
            years = years,
            months = remainingMonths,
            days = days.coerceAtLeast(0),
            totalMonths = totalMonths,
            totalDays = totalDays
        )
    }

    /**
     * Calculates the next monthly anniversary (Monthsary) milestone.
     * Celebrates on the same day-of-month as the relationship start date every month.
     * Clamps gracefully if the target month has fewer days than the start day (e.g. 31st on April 30th).
     */
    fun calculateNextMonthsary(
        anniversaryMillis: Long,
        referenceDateMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): AnniversaryMilestone? {
        if (anniversaryMillis == 0L) return null

        val startCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            timeInMillis = anniversaryMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val refCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            timeInMillis = referenceDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (startCal.timeInMillis > refCal.timeInMillis) {
            val diff = calculateDaysDifference(startCal.timeInMillis, refCal.timeInMillis, timeZone)
            return AnniversaryMilestone(
                title = "Start Day",
                daysRemaining = diff,
                isToday = false,
                targetDateMillis = startCal.timeInMillis
            )
        }

        val startYear = startCal.get(Calendar.YEAR)
        val startMonth = startCal.get(Calendar.MONTH)
        val startDay = startCal.get(Calendar.DAY_OF_MONTH)

        val refYear = refCal.get(Calendar.YEAR)
        val refMonth = refCal.get(Calendar.MONTH)

        // Check candidate in current reference month
        val candidateCal = Calendar.getInstance(timeZone, Locale.getDefault()).apply {
            set(Calendar.YEAR, refYear)
            set(Calendar.MONTH, refMonth)
            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, startDay.coerceAtMost(maxDay))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var diff = calculateDaysDifference(candidateCal.timeInMillis, refCal.timeInMillis, timeZone)

        // If candidate date already passed, or is the exact start date (e.g. today is start day), move to next month
        if (candidateCal.timeInMillis <= startCal.timeInMillis || diff < 0) {
            candidateCal.add(Calendar.MONTH, 1)
            val maxDay = candidateCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            candidateCal.set(Calendar.DAY_OF_MONTH, startDay.coerceAtMost(maxDay))
            diff = calculateDaysDifference(candidateCal.timeInMillis, refCal.timeInMillis, timeZone)
        }

        val candYear = candidateCal.get(Calendar.YEAR)
        val candMonth = candidateCal.get(Calendar.MONTH)
        val monthsCount = (candYear - startYear) * 12 + (candMonth - startMonth)
        val ordinal = getOrdinal(monthsCount.coerceAtLeast(1))
        val isToday = (diff == 0L)

        val title = if (isToday) {
            "Happy $ordinal Monthsary! 🥂"
        } else {
            "$ordinal Monthsary"
        }

        return AnniversaryMilestone(
            title = title,
            daysRemaining = diff,
            isToday = isToday,
            targetDateMillis = candidateCal.timeInMillis
        )
    }
}
