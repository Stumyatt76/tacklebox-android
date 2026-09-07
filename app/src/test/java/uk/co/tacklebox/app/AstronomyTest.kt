/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.tacklebox.app.services.Astronomy
import java.time.LocalDate

/** Covers TB-A-16: operator precedence capped the day rating at 4 while both screens advertised "/5". */
class AstronomyTest {
    private val year = (0 until 365).map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()) }

    @Test fun `rating stays within one to five`() {
        year.forEach { date ->
            val rating = Astronomy.calculate(date).rating
            assertTrue("rating $rating out of range on $date", rating in 1..5)
        }
    }

    @Test fun `a five out of five day is reachable`() {
        assertTrue("no day in 2026 scored 5/5", year.any { Astronomy.calculate(it).rating == 5 })
    }

    @Test fun `sunrise falls before sunset in the northern summer`() {
        val day = Astronomy.calculate(LocalDate.of(2026, 6, 21), latitude = 52.5, longitude = -1.5)
        assertTrue(day.sunrise.isBefore(day.sunset))
    }

    @Test fun `latitude changes the day length`() {
        val date = LocalDate.of(2026, 6, 21)
        val south = Astronomy.calculate(date, latitude = 40.0, longitude = -1.5)
        val north = Astronomy.calculate(date, latitude = 60.0, longitude = -1.5)
        val southHours = south.sunset.toSecondOfDay() - south.sunrise.toSecondOfDay()
        val northHours = north.sunset.toSecondOfDay() - north.sunrise.toSecondOfDay()
        assertTrue("midsummer should be longer further north", northHours > southHours)
    }

    @Test fun `every day names a moon phase and four windows`() {
        year.forEach { date ->
            val day = Astronomy.calculate(date)
            assertTrue(day.moonPhase.isNotBlank())
            assertTrue(day.windows.size == 4)
        }
    }
}
