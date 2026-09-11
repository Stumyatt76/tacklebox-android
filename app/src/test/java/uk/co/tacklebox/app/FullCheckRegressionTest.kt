/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.services.BiteWindow
import uk.co.tacklebox.app.services.BiteWindows
import uk.co.tacklebox.app.services.MarineHours
import uk.co.tacklebox.app.services.Tides
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Pure-logic regressions from the 11 September full check. Each pins a defect that was visible to an angler. */
class FullCheckRegressionTest {

    // --- #6 the date picker and the time of day ---------------------------------------------------------------

    /** Material's DatePicker reports UTC midnight; the picked day must be read as a calendar date, not an instant. */
    @Test fun `a picked date keeps the entered local time of day`() {
        val zone = ZoneId.of("America/New_York")
        val previous = LocalDateTime.of(2026, 9, 3, 14, 25).atZone(zone).toInstant().toEpochMilli()
        val pickedUtcMidnight = Instant.parse("2026-09-11T00:00:00Z").toEpochMilli()
        val combined = Instant.ofEpochMilli(CatchTiming.combine(pickedUtcMidnight, previous, zone)).atZone(zone)
        assertEquals(11, combined.dayOfMonth)   // used to read "10 Sep · 20:00" in New York
        assertEquals(9, combined.monthValue)
        assertEquals(LocalTime.of(14, 25), combined.toLocalTime())
    }

    @Test fun `the same day in the UK during summer time`() {
        val zone = ZoneId.of("Europe/London")
        val previous = LocalDateTime.of(2026, 9, 11, 6, 40).atZone(zone).toInstant().toEpochMilli()
        val combined = Instant.ofEpochMilli(CatchTiming.combine(Instant.parse("2026-09-05T00:00:00Z").toEpochMilli(), previous, zone)).atZone(zone)
        assertEquals(5, combined.dayOfMonth)
        assertEquals(LocalTime.of(6, 40), combined.toLocalTime())   // used to become 01:00
    }

    // --- #7 editing must not truncate -------------------------------------------------------------------------

    @Test fun `an untouched stepper leaves the stored value alone`() {
        assertEquals(2126.25, EditedMeasurement.resolve(2126.25, changed = false, entered = 2120.0)!!, 0.0)
        assertEquals(45.5, EditedMeasurement.resolve(45.5, changed = false, entered = 45.0)!!, 0.0)
        assertNull(EditedMeasurement.resolve(null, changed = false, entered = 0.0))
    }

    @Test fun `a moved stepper writes the new value, and zero clears it`() {
        assertEquals(2500.0, EditedMeasurement.resolve(2126.25, changed = true, entered = 2500.0)!!, 0.0)
        assertNull(EditedMeasurement.resolve(2126.25, changed = true, entered = 0.0))
    }

    // --- #12 windows straddling midnight ----------------------------------------------------------------------

    private val morning = BiteWindow("Moonrise", LocalTime.of(6, 10), LocalTime.of(7, 10), false)
    private val afternoon = BiteWindow("Moon overhead", LocalTime.of(13, 0), LocalTime.of(15, 0), true)
    private val overnight = BiteWindow("Moon underfoot", LocalTime.of(23, 30), LocalTime.of(1, 30), true)
    private val day = listOf(morning, afternoon, overnight)   // sorted by start, as Astronomy returns them

    @Test fun `a window straddling midnight is active on both sides of it`() {
        assertTrue(overnight.wrapsMidnight)
        assertTrue(overnight.contains(LocalTime.of(23, 45)))
        assertTrue(overnight.contains(LocalTime.of(0, 30)))
        assertTrue(!overnight.contains(LocalTime.of(12, 0)))
        assertTrue(!morning.wrapsMidnight)
    }

    @Test fun `late in the evening the straddling window is the next one, not "windows have passed"`() {
        assertEquals("Moon underfoot", BiteWindows.next(day, LocalTime.of(23, 45))?.label)   // happening now
        assertEquals("Moon underfoot", BiteWindows.next(day, LocalTime.of(20, 0))?.label)    // still to come
        assertEquals("Moon underfoot", BiteWindows.next(day, LocalTime.of(0, 30))?.label)    // just after midnight
    }

    @Test fun `earlier in the day the ordinary windows still come first`() {
        assertEquals("Moonrise", BiteWindows.next(day, LocalTime.of(5, 0))?.label)
        assertEquals("Moonrise", BiteWindows.next(day, LocalTime.of(6, 30))?.label)       // in progress
        assertEquals("Moon overhead", BiteWindows.next(day, LocalTime.of(8, 0))?.label)
        assertNull(BiteWindows.next(listOf(morning, afternoon), LocalTime.of(16, 0)))
    }

    // --- #23 the year summary's picks --------------------------------------------------------------------------

    private fun fish(id: Long, grams: Double?, bait: String?) =
        CatchRow(Catch(id = id, weightGrams = grams, bait = bait), null, null, null)

    @Test fun `a missing bait can never be the top bait`() {
        val rows = listOf(fish(1, 100.0, null), fish(2, 200.0, null), fish(3, 300.0, ""), fish(4, 400.0, "Sweetcorn"))
        assertEquals("Sweetcorn", SeasonSummary.topBait(rows))
        assertNull(SeasonSummary.topBait(listOf(fish(1, 100.0, null), fish(2, 100.0, "  "))))
    }

    @Test fun `only a weighed catch can be the biggest`() {
        assertNull(SeasonSummary.biggest(listOf(fish(1, null, "Boilie"), fish(2, null, "Worm"))))
        assertEquals(2L, SeasonSummary.biggest(listOf(fish(1, null, null), fish(2, 5000.0, null), fish(3, 4000.0, null)))?.item?.id)
    }

    // --- #8 locale-independent request URLs --------------------------------------------------------------------

    @Test fun `the WorldTides request uses a decimal point whatever the device locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val url = Tides.worldTidesUrl(50.15, -5.07, "key")
            assertTrue(url, url.contains("lat=50.15&lon=-5.07"))
            assertTrue(url, !url.contains(","))
        } finally { Locale.setDefault(saved) }
    }

    // --- #14 sea state from now ------------------------------------------------------------------------------------

    @Test fun `the sea-state list starts at the current hour`() {
        val times = (0 until 48).map { "2026-09-11T%02d:00".format(it % 24).let { t -> if (it < 24) t else t.replace("-11T", "-12T") } }
        assertEquals(16, MarineHours.firstFromNow(times, LocalDateTime.of(2026, 9, 11, 16, 20)))
        assertEquals(0, MarineHours.firstFromNow(times, LocalDateTime.of(2026, 9, 10, 9, 0)))     // series is all ahead
        assertEquals(0, MarineHours.firstFromNow(times, LocalDateTime.of(2026, 9, 14, 9, 0)))     // series is all behind: show something
        assertEquals(0, MarineHours.firstFromNow(emptyList()))
    }
}
