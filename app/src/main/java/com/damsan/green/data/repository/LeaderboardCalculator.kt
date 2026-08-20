package com.damsan.green.data.repository

import com.damsan.green.data.model.ClassRanking
import com.damsan.green.data.model.TrashReport
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object LeaderboardCalculator {
    enum class Period { ALL_TIME, WEEK, MONTH }

    fun calculate(reports: List<TrashReport>, period: Period = Period.ALL_TIME, now: Long = System.currentTimeMillis()): List<ClassRanking> {
        return reports
            .asSequence()
            .filter { it.status == "approved" }
            .filter { report -> period == Period.ALL_TIME || isInPeriod(report.timestamp, period, now) }
            .filter { it.className.isNotBlank() && it.className != "Unknown" }
            .filter { report ->
                val legacy = report.imageBeforeUrl.isBlank() &&
                    report.imageAfterUrl.isBlank() &&
                    report.imageUrl.isNotBlank()
                val verified = report.imageBeforeUrl.isNotBlank() &&
                    report.imageAfterUrl.isNotBlank() &&
                    report.trashType.isNotBlank()
                legacy || verified
            }
            .groupBy { it.className }
            .map { (className, classReports) ->
                ClassRanking(
                    className = className,
                    totalPoints = classReports.sumOf { it.points.coerceAtLeast(0) },
                    reportCount = classReports.size,
                    lastActivity = classReports.maxOfOrNull { it.timestamp } ?: 0L
                )
            }
            .sortedWith(
                compareByDescending<ClassRanking> { it.totalPoints }
                    .thenByDescending { it.reportCount }
                    .thenBy { it.className }
            )
            .mapIndexed { index, ranking -> ranking.copy(rank = index + 1) }
            .toList()
    }

    fun periodLabel(period: Period, now: Long = System.currentTimeMillis()): String {
        if (period == Period.ALL_TIME) return "Tất cả thời gian"
        val start = when (period) {
            Period.WEEK -> mondayAtStart(now)
            Period.MONTH -> cycleStart(now)
            Period.ALL_TIME -> return "Tất cả thời gian"
        }
        val end = if (period == Period.WEEK) addDays(start, 5) else addDays(start, 27)
        val formatter = SimpleDateFormat("dd/MM", Locale.getDefault())
        return if (period == Period.WEEK) {
            "Tuần này · ${formatter.format(Date(start))} - ${formatter.format(Date(end))} (T2-T7)"
        } else {
            "Tháng thi đua · ${formatter.format(Date(start))} - ${formatter.format(Date(end))} (4 tuần)"
        }
    }

    private fun isInPeriod(timestamp: Long, period: Period, now: Long): Boolean {
        if (timestamp <= 0L) return false
        val weekStart = mondayAtStart(timestamp)
        val dayOfWeek = calendar(timestamp).get(Calendar.DAY_OF_WEEK)
        val isSunday = dayOfWeek == Calendar.SUNDAY
        return when (period) {
            Period.ALL_TIME -> true
            Period.WEEK -> !isSunday && weekStart == mondayAtStart(now)
            Period.MONTH -> !isSunday && weekStart in cycleStart(now)..addDays(cycleStart(now), 21)
        }
    }

    private fun calendar(timestamp: Long): Calendar = Calendar.getInstance().apply { timeInMillis = timestamp }

    private fun mondayAtStart(timestamp: Long): Long {
        val value = calendar(timestamp)
        value.set(Calendar.HOUR_OF_DAY, 0)
        value.set(Calendar.MINUTE, 0)
        value.set(Calendar.SECOND, 0)
        value.set(Calendar.MILLISECOND, 0)
        val distance = (value.get(Calendar.DAY_OF_WEEK) + 6) % 7
        value.add(Calendar.DAY_OF_MONTH, -distance)
        return value.timeInMillis
    }

    private fun cycleStart(timestamp: Long): Long {
        val date = mondayAtStart(timestamp)
        val year = calendar(date).get(Calendar.YEAR)
        val januaryFirst = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val janDay = calendar(januaryFirst).get(Calendar.DAY_OF_WEEK)
        val daysUntilMonday = (Calendar.MONDAY - janDay + 7) % 7
        val firstMonday = januaryFirst + daysUntilMonday.toLong() * 24L * 60L * 60L * 1000L
        val weeks = ((date - firstMonday) / (7L * 24L * 60L * 60L * 1000L)).coerceAtLeast(0L)
        return firstMonday + (weeks / 4L) * 28L * 24L * 60L * 60L * 1000L
    }

    private fun addDays(timestamp: Long, days: Int): Long = timestamp + days.toLong() * 24L * 60L * 60L * 1000L
}
