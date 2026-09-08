/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import uk.co.tacklebox.app.services.Astronomy
import uk.co.tacklebox.app.services.SolunarRating
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The solunar day (TB-P-05).
 *
 * The previous implementation did not merely present the day differently from iOS — it computed a different one.
 * Moon age came from `(epochDay + 4) % 29.53059`, an epoch about 19 days out, so on 2026-09-08 it reported a waxing
 * crescent for what was in fact a waning crescent. Moonrise was invented from that age rather than computed, and
 * both "major" windows were placed at the invented time and twelve hours later — so the headline feature of the
 * app was unrelated to the moon's actual position. These tests pin the properties that broke.
 */
class AstronomyTest {
    private val london = ZoneId.of("Europe/London")
    private val year = (0 until 365).map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()) }

    @Test fun `the whole rating scale is reachable across a year`() {
        val seen = year.map { Astronomy.calculate(it, zone = london).rating }.toSet()
        // The old scale advertised "/5" and could only ever return 3, 4 or 5 — it could not call a day poor.
        assertEquals(SolunarRating.entries.toSet(), seen)
    }

    /**
     * Solunar theory rates the extremes of illumination highest, so a new or full moon must always rate excellent
     * and a quarter must never rate above fair.
     *
     * Note the implication runs one way only. The eight phase *names* and the four rating bands are different
     * partitions of the same cycle, so an excellent day can legitimately be named gibbous — the bands are wider
     * than the name buckets at the extremes. Asserting the converse is what a first draft of this test did, and it
     * failed against correct behaviour.
     */
    @Test fun `the new and full moon rate best, the quarters never rate well`() {
        val days = year.map { Astronomy.calculate(it, zone = london) }
        val extremes = days.filter { it.moonPhase == "New Moon" || it.moonPhase == "Full Moon" }
        assertTrue("2026 should contain new and full moons", extremes.size >= 20)
        assertEquals(setOf(SolunarRating.EXCELLENT), extremes.map { it.rating }.toSet())

        val quarters = days.filter { it.moonPhase.endsWith("Quarter") }
        assertTrue("2026 should contain quarter moons", quarters.size >= 20)
        assertEquals(setOf(SolunarRating.FAIR, SolunarRating.POOR), quarters.map { it.rating }.toSet())
    }

    /**
     * The regression that started this. 2026-09-08 was a waning crescent; the old epoch reported a waxing crescent,
     * nearly the opposite point of the cycle.
     */
    @Test fun `moon phase matches the real cycle rather than the old offset epoch`() {
        val day = Astronomy.calculate(LocalDate.of(2026, 9, 8), zone = london)
        assertEquals("Waning Crescent", day.moonPhase)
    }

    @Test fun `moon phase advances through all eight names across a lunar month`() {
        val month = (0 until 30).map { Astronomy.calculate(LocalDate.of(2026, 3, 1).plusDays(it.toLong()), zone = london).moonPhase }
        assertEquals(8, month.toSet().size)
    }

    @Test fun `sunrise falls before sunset in the northern summer`() {
        val day = Astronomy.calculate(LocalDate.of(2026, 6, 21), latitude = 52.5, longitude = -1.5, zone = london)
        assertTrue(day.sunrise.isBefore(day.sunset))
    }

    @Test fun `latitude changes the day length`() {
        val date = LocalDate.of(2026, 6, 21)
        val south = Astronomy.calculate(date, latitude = 40.0, longitude = -1.5, zone = london)
        val north = Astronomy.calculate(date, latitude = 60.0, longitude = -1.5, zone = london)
        val southSeconds = south.sunset.toSecondOfDay() - south.sunrise.toSecondOfDay()
        val northSeconds = north.sunset.toSecondOfDay() - north.sunrise.toSecondOfDay()
        assertTrue("midsummer should be longer further north", northSeconds > southSeconds)
    }

    /** Majors are the moon overhead and underfoot; minors are moonrise and moonset — not dawn and dusk. */
    @Test fun `every day has two major windows drawn from the moon transits`() {
        year.forEach { date ->
            val day = Astronomy.calculate(date, zone = london)
            val majors = day.windows.filter { it.major }.map { it.label }
            assertEquals("wrong majors on $date", listOf("Moon overhead", "Moon underfoot"), majors.sorted())
            assertTrue(day.moonPhase.isNotBlank())
        }
    }

    /**
     * The moon rises roughly 50 minutes later each day, so a real ephemeris skips a rise on about one day a month
     * rather than producing one every day. A model that always has a moonrise is a model that invented it.
     */
    @Test fun `moonrise is computed, so it is occasionally absent`() {
        val risesInMarch = (0 until 30).map { Astronomy.calculate(LocalDate.of(2026, 3, 1).plusDays(it.toLong()), zone = london).moonrise }
        assertTrue("a real ephemeris skips a moonrise most months", risesInMarch.any { it == null })
        assertTrue("but should still find one on most days", risesInMarch.count { it != null } >= 25)
    }

    @Test fun `windows are ordered by start time`() {
        val day = Astronomy.calculate(LocalDate.of(2026, 5, 14), zone = london)
        assertEquals(day.windows.map { it.start }.sorted(), day.windows.map { it.start })
        assertNotNull(day.windows.firstOrNull())
    }
}
