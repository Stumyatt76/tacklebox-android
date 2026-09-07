/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.tacklebox.app.data.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Covers the hardcoded "conditions insight" sentence that was printed regardless of the user's data. */
class InsightTest {
    private fun row(hour:Int, pressure:Double?=null, wind:String?=null) = CatchRow(
        item = Catch(id = hour.toLong(), caughtAt = LocalDateTime.of(2026,6,1,hour,0).atZone(ZoneId.systemDefault()).toInstant()),
        species = null, water = null,
        conditions = pressure?.let { ConditionsSnapshot(catchId = hour.toLong(), pressureHpa = it, windDirection = wind) }
    )

    @Test fun `under three catches it asks for more rather than asserting a pattern`() {
        assertEquals("Log three or more catches to reveal weather patterns.", Insight.conditions(listOf(row(6), row(7))))
    }

    @Test fun `a dawn bias is reported`() {
        val text = Insight.conditions(listOf(row(5), row(6), row(7), row(14)))
        assertTrue(text, text.contains("at dawn"))
    }

    @Test fun `no dominant time of day is not reported as one`() {
        val text = Insight.conditions(listOf(row(5), row(13), row(19), row(2)))
        assertTrue(text, !text.contains("at dawn") && !text.contains("at dusk"))
    }

    @Test fun `settled high pressure is described as such`() {
        val text = Insight.conditions(listOf(row(6,1025.0), row(7,1024.0), row(8,1026.0)))
        assertTrue(text, text.contains("settled high pressure"))
    }

    @Test fun `low pressure is described as such`() {
        val text = Insight.conditions(listOf(row(6,1000.0), row(7,1001.0), row(8,999.0)))
        assertTrue(text, text.contains("low pressure"))
    }

    @Test fun `a repeated wind direction is reported and a one-off is not`() {
        val repeated = Insight.conditions(listOf(row(6,1015.0,"SW"), row(7,1014.0,"SW"), row(8,1016.0,"N")))
        assertTrue(repeated, repeated.contains("SW wind"))
        val oneOff = Insight.conditions(listOf(row(6,1015.0,"SW"), row(7,1014.0,"N"), row(8,1016.0,"E")))
        assertTrue(oneOff, !oneOff.contains("wind"))
    }

    @Test fun `catches with no weather do not invent one`() {
        val text = Insight.conditions(listOf(row(5), row(6), row(7)))
        assertTrue(text, !text.contains("hPa"))
    }
}
