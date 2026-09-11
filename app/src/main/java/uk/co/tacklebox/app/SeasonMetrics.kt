/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app
import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.data.SessionRow
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * The year summary's headline picks. Unweighed catches cannot be the biggest fish, and a missing rig or bait is
 * not a bait — "Top bait · Unrecorded" was the most common line on the card for anyone who logs quickly.
 */
object SeasonSummary {
    fun biggest(catches: List<CatchRow>): CatchRow? =
        catches.filter { it.item.weightGrams != null }.maxByOrNull { it.item.weightGrams!! }
    fun topBait(catches: List<CatchRow>): String? = top(catches.map { it.item.bait })
    fun topRig(catches: List<CatchRow>): String? = top(catches.map { it.item.rig })
    private fun top(values: List<String?>): String? =
        values.filterNotNull().map(String::trim).filter(String::isNotEmpty).groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}

object SeasonMetrics {
    /** Only the portion of a finished session inside the selected local-calendar year counts. */
    fun hours(sessions: List<SessionRow>, year: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val first = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant()
        val last = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant()
        return sessions.sumOf { row ->
            val end = row.item.endAt ?: return@sumOf 0L
            val start = maxOf(row.item.startAt, first)
            val finish = minOf(end, last)
            if (finish <= start) 0L else Duration.between(start, finish).seconds
        } / 3600
    }
}
