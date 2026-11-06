package com.example.coupleappgallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateUtilsTest {

    private val tz = TimeZone.getTimeZone("UTC")

    private fun createMillis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long {
        return Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun sameDayDifferentTimes_returnsZeroDays() {
        val todayMorning = createMillis(2026, 11, 5, 8, 30)
        val todayNight = createMillis(2026, 11, 5, 23, 45)

        val diff = DateUtils.calculateDaysDifference(todayNight, todayMorning, tz)
        assertEquals(0L, diff)
        assertEquals("Today is the day! 🎉", DateUtils.formatCountdownDays(diff))
    }

    @Test
    fun tomorrowFewHoursAway_returnsOneDayUntil() {
        val todayNight = createMillis(2026, 11, 5, 23, 30)
        val tomorrowEarly = createMillis(2026, 11, 6, 1, 30)

        val diff = DateUtils.calculateDaysDifference(tomorrowEarly, todayNight, tz)
        assertEquals(1L, diff)
        assertEquals("1 day until", DateUtils.formatCountdownDays(diff))
        assertEquals("1 day to go! ⏳", DateUtils.formatFolderDays(diff, true))
    }

    @Test
    fun yesterdayFewHoursAgo_returnsOneDaySince() {
        val todayEarly = createMillis(2026, 11, 6, 1, 30)
        val yesterdayNight = createMillis(2026, 11, 5, 23, 30)

        val diff = DateUtils.calculateDaysDifference(yesterdayNight, todayEarly, tz)
        assertEquals(-1L, diff)
        assertEquals("1 day since", DateUtils.formatCountdownDays(diff))
        assertEquals("1 day ago", DateUtils.formatFolderDays(diff, true))
    }

    @Test
    fun futureMultipleDays_formatsPluralCorrectly() {
        val today = createMillis(2026, 11, 5, 12, 0)
        val future = createMillis(2026, 11, 10, 12, 0)

        val diff = DateUtils.calculateDaysDifference(future, today, tz)
        assertEquals(5L, diff)
        assertEquals("5 days until", DateUtils.formatCountdownDays(diff))
        assertEquals("5 days to go! ⏳", DateUtils.formatFolderDays(diff, true))
    }

    @Test
    fun pastMultipleDays_formatsPluralCorrectly() {
        val today = createMillis(2026, 11, 10, 12, 0)
        val past = createMillis(2026, 11, 5, 12, 0)

        val diff = DateUtils.calculateDaysDifference(past, today, tz)
        assertEquals(-5L, diff)
        assertEquals("5 days since", DateUtils.formatCountdownDays(diff))
        assertEquals("5 days ago", DateUtils.formatFolderDays(diff, true))
    }

    @Test
    fun pre1970HistoricalDate_computesNegativeDaysSince() {
        val pre1970 = createMillis(1969, 7, 20, 12, 0)
        val ref = createMillis(1970, 1, 1, 12, 0)

        assertTrue(pre1970 < 0L)
        val diff = DateUtils.calculateDaysDifference(pre1970, ref, tz)
        assertEquals(-165L, diff)
        assertEquals("165 days since", DateUtils.formatCountdownDays(diff))
    }

    @Test
    fun formatDays_whenNoDate_returnsEmptyString() {
        assertEquals("", DateUtils.formatFolderDays(0L, hasDate = false))
        assertEquals("", DateUtils.formatFolderDays(5L, hasDate = false))
        assertEquals("", DateUtils.formatCountdownDays(0L, hasDate = false))
    }

    @Test
    fun getOrdinal_formatsEnglishOrdinalsCorrectly() {
        assertEquals("1st", DateUtils.getOrdinal(1))
        assertEquals("2nd", DateUtils.getOrdinal(2))
        assertEquals("3rd", DateUtils.getOrdinal(3))
        assertEquals("4th", DateUtils.getOrdinal(4))
        assertEquals("11th", DateUtils.getOrdinal(11))
        assertEquals("12th", DateUtils.getOrdinal(12))
        assertEquals("13th", DateUtils.getOrdinal(13))
        assertEquals("21st", DateUtils.getOrdinal(21))
        assertEquals("22nd", DateUtils.getOrdinal(22))
        assertEquals("23rd", DateUtils.getOrdinal(23))
        assertEquals("100th", DateUtils.getOrdinal(100))
    }

    @Test
    fun calculateDaysTogether_todayIsDayOne() {
        val today = createMillis(2026, 11, 5, 12, 0)
        val anniversary = createMillis(2026, 11, 5, 8, 0)
        assertEquals(1L, DateUtils.calculateDaysTogether(anniversary, today, tz))
    }

    @Test
    fun calculateDaysTogether_yesterdayIsDayTwo() {
        val today = createMillis(2026, 11, 5, 12, 0)
        val anniversary = createMillis(2026, 11, 4, 18, 0)
        assertEquals(2L, DateUtils.calculateDaysTogether(anniversary, today, tz))
    }

    @Test
    fun calculateDaysTogether_zeroReturnsZero() {
        assertEquals(0L, DateUtils.calculateDaysTogether(0L))
    }

    @Test
    fun calculateNextAnnualAnniversary_startedToday_milestoneIsNextYearFirst() {
        val today = createMillis(2026, 11, 5, 12, 0)
        val milestone = DateUtils.calculateNextAnnualAnniversary(today, today, tz)
        org.junit.Assert.assertNotNull(milestone)
        assertEquals("1st Anniversary", milestone?.title)
        assertEquals(365L, milestone?.daysRemaining)
        assertEquals(false, milestone?.isToday)
    }

    @Test
    fun calculateNextAnnualAnniversary_startedExactlyOneYearAgo_isTodayFirst() {
        val today = createMillis(2026, 11, 5, 12, 0)
        val start = createMillis(2025, 11, 5, 9, 0)
        val milestone = DateUtils.calculateNextAnnualAnniversary(start, today, tz)
        org.junit.Assert.assertNotNull(milestone)
        assertEquals("Happy 1st Anniversary! 🎉", milestone?.title)
        assertEquals(0L, milestone?.daysRemaining)
        assertEquals(true, milestone?.isToday)
    }

    @Test
    fun calculateNextAnnualAnniversary_upcomingInFiveDays_returnsCountdown() {
        val today = createMillis(2026, 11, 5, 12, 0)
        val start = createMillis(2024, 11, 10, 9, 0)
        val milestone = DateUtils.calculateNextAnnualAnniversary(start, today, tz)
        org.junit.Assert.assertNotNull(milestone)
        assertEquals("2nd Anniversary", milestone?.title)
        assertEquals(5L, milestone?.daysRemaining)
        assertEquals(false, milestone?.isToday)
    }

    @Test
    fun calculateNextHundredDayMilestone_progressAndExactDay() {
        val m1 = DateUtils.calculateNextHundredDayMilestone(1L)
        assertEquals("Day 100", m1?.title)
        assertEquals(99L, m1?.daysRemaining)
        assertEquals(false, m1?.isToday)

        val m412 = DateUtils.calculateNextHundredDayMilestone(412L)
        assertEquals("Day 500", m412?.title)
        assertEquals(88L, m412?.daysRemaining)
        assertEquals(false, m412?.isToday)

        val m500 = DateUtils.calculateNextHundredDayMilestone(500L)
        assertEquals("Day 500 Milestone! 💖", m500?.title)
        assertEquals(0L, m500?.daysRemaining)
        assertEquals(true, m500?.isToday)
    }

    @Test
    fun calculateMonthsTogether_progressFromZeroToMultipleMonths() {
        val start = createMillis(2024, 3, 15, 12, 0)

        // Same day
        assertEquals(0, DateUtils.calculateMonthsTogether(start, createMillis(2024, 3, 15, 12, 0), tz))
        // 1 day before 1st month
        assertEquals(0, DateUtils.calculateMonthsTogether(start, createMillis(2024, 4, 14, 12, 0), tz))
        // Exactly 1 month
        assertEquals(1, DateUtils.calculateMonthsTogether(start, createMillis(2024, 4, 15, 12, 0), tz))
        // 14 months later
        assertEquals(14, DateUtils.calculateMonthsTogether(start, createMillis(2025, 5, 15, 12, 0), tz))
    }

    @Test
    fun calculateNextMonthsary_startedToday_isNextMonthFirst() {
        val start = createMillis(2026, 11, 5, 12, 0)
        val nextMonthsary = DateUtils.calculateNextMonthsary(start, start, tz)
        org.junit.Assert.assertNotNull(nextMonthsary)
        assertEquals("1st Monthsary", nextMonthsary?.title)
        assertEquals(30L, nextMonthsary?.daysRemaining)
        assertEquals(false, nextMonthsary?.isToday)
    }

    @Test
    fun calculateNextMonthsary_exactDayOfMonthsary_isToday() {
        val start = createMillis(2026, 3, 15, 9, 0)
        val today = createMillis(2026, 4, 15, 14, 0) // 1st monthsary day
        val nextMonthsary = DateUtils.calculateNextMonthsary(start, today, tz)
        org.junit.Assert.assertNotNull(nextMonthsary)
        assertEquals("Happy 1st Monthsary! 🥂", nextMonthsary?.title)
        assertEquals(0L, nextMonthsary?.daysRemaining)
        assertEquals(true, nextMonthsary?.isToday)
    }

    @Test
    fun calculateNextMonthsary_afterMonthsaryPasses_movesToNext() {
        val start = createMillis(2026, 3, 15, 9, 0)
        val today = createMillis(2026, 4, 18, 14, 0) // 3 days after 1st monthsary
        val nextMonthsary = DateUtils.calculateNextMonthsary(start, today, tz)
        org.junit.Assert.assertNotNull(nextMonthsary)
        assertEquals("2nd Monthsary", nextMonthsary?.title)
        assertEquals(27L, nextMonthsary?.daysRemaining)
        assertEquals(false, nextMonthsary?.isToday)
    }

    @Test
    fun calculateTimeTogetherBreakdown_calculatesAccurately() {
        val start = createMillis(2024, 1, 15, 0, 0)
        val ref = createMillis(2025, 3, 20, 0, 0)
        val breakdown = DateUtils.calculateTimeTogetherBreakdown(start, ref, tz)
        assertEquals(1, breakdown.years)
        assertEquals(2, breakdown.months)
        assertEquals(14, breakdown.totalMonths)
        assertEquals(5, breakdown.days)
    }
}
