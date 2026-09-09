/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app
import uk.co.tacklebox.app.data.SessionRow
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
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
