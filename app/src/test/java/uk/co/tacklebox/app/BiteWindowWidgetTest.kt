/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.tacklebox.app.services.Astronomy
import uk.co.tacklebox.app.widget.BiteWindowWidget
import java.time.LocalDate
import java.time.LocalTime

/**
 * The bite-window widget's logic. The widget needs no journal data — the windows are computed locally from a date
 * and a position — so what is worth testing is the countdown and the window it picks. Mirrors the iOS tests.
 */
class BiteWindowWidgetTest {
    private val day = Astronomy.calculate(LocalDate.of(2026, 6, 1), latitude = 52.36, longitude = -1.17)

    private fun next(now: LocalTime) = day.windows.firstOrNull { !it.end.isBefore(now) }

    @Test fun `the countdown reads in hours and minutes`() {
        assertEquals("in 1h 20m", BiteWindowWidget.countdown(LocalTime.of(6, 0), LocalTime.of(7, 20)))
        assertEquals("in 45m", BiteWindowWidget.countdown(LocalTime.of(6, 0), LocalTime.of(6, 45)))
        assertEquals("in 2h 0m", BiteWindowWidget.countdown(LocalTime.of(6, 0), LocalTime.of(8, 0)))
    }

    /** A window already under way says so, rather than showing a negative or a stale countdown. */
    @Test fun `a window already started reads as happening now`() {
        assertEquals("Happening now", BiteWindowWidget.countdown(LocalTime.of(7, 0), LocalTime.of(6, 30)))
        assertEquals("Happening now", BiteWindowWidget.countdown(LocalTime.of(7, 0), LocalTime.of(7, 0)))
    }

    @Test fun `the next window is the first that has not finished`() {
        val chosen = next(LocalTime.MIDNIGHT)
        assertNotNull(chosen)
        day.windows.filter { it.start < chosen!!.start }.forEach {
            assertTrue("an earlier window must already have closed", it.end.isBefore(LocalTime.MIDNIGHT))
        }
    }

    /** An angler wants to know a window is happening now, not to be pointed past it. */
    @Test fun `a window in progress is still the next window`() {
        val first = day.windows.first()
        val midway = first.start.plusMinutes(java.time.Duration.between(first.start, first.end).toMinutes() / 2)
        assertEquals(first.label, next(midway)?.label)
    }

    @Test fun `after the last window there is none left today`() {
        val last = day.windows.maxByOrNull { it.end }!!
        // A window that wraps past midnight would make "after the last" meaningless, so guard the assumption.
        if (last.end.isBefore(LocalTime.of(23, 58))) {
            assertNull(next(last.end.plusMinutes(1)))
        }
    }

    @Test fun `every day offers windows, a rating and a named phase`() {
        (0 until 30).forEach { offset ->
            val d = Astronomy.calculate(LocalDate.of(2026, 1, 1).plusDays(offset.toLong()))
            assertTrue(d.windows.isNotEmpty())
            // The rating is a four-point word scale now, not "n/5" — see AstronomyTest for why (TB-P-05).
            assertTrue(d.rating.title.isNotBlank())
            assertTrue(d.moonPhase.isNotBlank())
        }
    }

    @Test fun `windows are ordered by start`() {
        val starts = day.windows.map { it.start }
        assertEquals(starts.sorted(), starts)
    }
}
