package com.damsan.green.data.repository

import com.damsan.green.data.model.ClassRanking
import com.damsan.green.data.model.TrashReport

object LeaderboardCalculator {
    fun calculate(reports: List<TrashReport>): List<ClassRanking> {
        return reports
            .asSequence()
            .filter { it.status == "approved" }
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
}
