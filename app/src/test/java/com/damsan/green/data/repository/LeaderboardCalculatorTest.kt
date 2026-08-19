package com.damsan.green.data.repository

import com.damsan.green.data.model.TrashReport
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardCalculatorTest {
    @Test
    fun `approved verified reports are totaled and sorted descending`() {
        val reports = listOf(
            verified("11A1", 10, 100L),
            verified("11A2", 20, 200L),
            verified("11A1", 15, 300L),
            verified("11A3", 99, 400L, status = "pending"),
            TrashReport(className = "11A4", points = 50, status = "approved")
        )

        val result = LeaderboardCalculator.calculate(reports)

        assertEquals(listOf("11A1", "11A2"), result.map { it.className })
        assertEquals(listOf(25, 20), result.map { it.totalPoints })
        assertEquals(listOf(1, 2), result.map { it.rank })
        assertEquals(2, result.first().reportCount)
        assertEquals(300L, result.first().lastActivity)
    }

    private fun verified(
        className: String,
        points: Int,
        timestamp: Long,
        status: String = "approved"
    ) = TrashReport(
        className = className,
        imageBeforeUrl = "before.jpg",
        imageAfterUrl = "after.jpg",
        trashType = "recyclable",
        points = points,
        status = status,
        timestamp = timestamp
    )
}
