/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.co.tacklebox.app.services.NoaaPrediction
import uk.co.tacklebox.app.services.TideError
import uk.co.tacklebox.app.services.TideEvent
import uk.co.tacklebox.app.services.TideKind
import uk.co.tacklebox.app.services.TideResult
import uk.co.tacklebox.app.services.TideSource
import uk.co.tacklebox.app.services.Tides
import java.time.Instant

/**
 * Tide predictions (feature parity with iOS, 2026-09-08).
 *
 * Android had none: the screen named "Tides & sea" fetched wave height and period and nothing else, while the
 * Play listing promised coastal tides. These cover the parts that decide what an angler sees without going near
 * the network — which source is chosen for a position, and what "next" means once the day is half gone.
 */
class TidesTest {

    @Before fun clearCache() = Tides.clearCache()

    /** NOAA is free and needs no key, so inside its coverage it wins whether or not a WorldTides key exists. */
    @Test fun `the contiguous US is recognised`() {
        assertTrue("Chesapeake Bay", Tides.isContiguousUS(38.98, -76.48))
        assertTrue("San Diego", Tides.isContiguousUS(32.71, -117.17))
        assertTrue("Seattle", Tides.isContiguousUS(47.60, -122.33))
    }

    @Test fun `everywhere else is not, including US states outside the box`() {
        assertTrue("Cornwall", !Tides.isContiguousUS(50.15, -5.07))
        assertTrue("Anchorage is US but not contiguous", !Tides.isContiguousUS(61.22, -149.90))
        assertTrue("Honolulu is US but not contiguous", !Tides.isContiguousUS(21.31, -157.86))
    }

    /**
     * The check is a plain latitude/longitude rectangle, so it takes in most of southern Canada as well —
     * Vancouver and Toronto both fall inside it and would be sent to NOAA. In practice NOAA answers with the
     * nearest US station, which for a Great Lakes or Pacific-coast position is a real station a reasonable
     * distance away rather than nonsense.
     *
     * iOS draws exactly the same rectangle. Pinned rather than corrected, because the two apps agreeing matters
     * more than the edge; tightening it would have to be done on both, deliberately.
     */
    @Test fun `the box is a rectangle and takes in southern Canada too`() {
        assertTrue("Vancouver falls inside the box", Tides.isContiguousUS(49.28, -123.12))
        assertTrue("Toronto falls inside the box", Tides.isContiguousUS(43.65, -79.38))
        assertTrue("Winnipeg is north of the latitude bound", !Tides.isContiguousUS(49.90, -97.14))
        assertTrue("Halifax is east of the longitude bound", !Tides.isContiguousUS(44.65, -63.57))
    }

    /**
     * Outside NOAA coverage with no key, the honest answer is that predictions are not available here — not an
     * empty list, which would read as "no tides today".
     */
    @Test fun `no key outside NOAA coverage reports unavailable rather than empty`() = runTest {
        val error = runCatching { Tides.tides(50.15, -5.07, worldTidesKey = "") }.exceptionOrNull()
        assertTrue("expected NotAvailableHere, got $error", error is TideError.NotAvailableHere)
    }

    @Test fun `a blank key counts as no key`() = runTest {
        val error = runCatching { Tides.tides(50.15, -5.07, worldTidesKey = "   ") }.exceptionOrNull()
        assertTrue(error is TideError.NotAvailableHere)
    }

    private fun at(hour: Int, minute: Int = 0) =
        Instant.parse("2026-09-08T%02d:%02d:00Z".format(hour, minute))

    private val day = TideResult(
        listOf(
            TideEvent(at(3, 12), 1.2, TideKind.LOW),
            TideEvent(at(9, 28), 4.8, TideKind.HIGH),
            TideEvent(at(15, 41), 1.1, TideKind.LOW),
            TideEvent(at(21, 57), 4.9, TideKind.HIGH),
        ), TideSource.NOAA, cached = false)

    @Test fun `next tide is the first still to come`() {
        assertEquals(at(9, 28), day.next(at(6))?.time)
        assertEquals(TideKind.HIGH, day.next(at(6))?.kind)
        assertEquals(at(15, 41), day.next(at(10))?.time)
    }

    /** A tide happening exactly now has not passed. */
    @Test fun `a tide at this instant still counts as next`() {
        assertEquals(at(9, 28), day.next(at(9, 28))?.time)
    }

    /** After the last one the screen has to say so rather than silently showing the first of the day. */
    @Test fun `after the final tide there is no next`() {
        assertNull(day.next(at(23)))
    }

    /**
     * A real NOAA response, taken from the live API on 2026-09-08 for station 8575512, Annapolis (US Naval
     * Academy) — the nearest to a Chesapeake position. Written from the wire rather than from memory, because the
     * thing most likely to be wrong is my idea of the field names and units.
     */
    @Test fun `a real NOAA response parses, with feet converted to metres`() {
        val events = Tides.eventsFrom(listOf(
            NoaaPrediction("2026-09-08 03:41", "1.789", "H"),
            NoaaPrediction("2026-09-08 10:11", "0.828", "L"),
            NoaaPrediction("2026-09-08 15:23", "1.362", "H"),
            NoaaPrediction("2026-09-08 21:37", "0.422", "L")))

        assertEquals(4, events.size)
        assertEquals(listOf(TideKind.HIGH, TideKind.LOW, TideKind.HIGH, TideKind.LOW), events.map { it.kind })
        // units=english, so 1.789 ft is 0.545 m — the conversion is the easiest thing here to get silently wrong.
        assertEquals(0.545, events[0].heightMetres, 0.001)
        assertEquals(0.129, events[3].heightMetres, 0.001)
    }

    @Test fun `rows that are neither high nor low are dropped rather than guessed`() {
        val events = Tides.eventsFrom(listOf(
            NoaaPrediction("2026-09-08 03:41", "1.789", "H"),
            NoaaPrediction("2026-09-08 06:00", "1.100", null),
            NoaaPrediction(null, "1.200", "L"),
            NoaaPrediction("2026-09-08 09:00", "not a number", "L")))
        assertEquals(1, events.size)
        assertEquals(TideKind.HIGH, events.single().kind)
    }

    @Test fun `distance is measured in kilometres and is symmetric`() {
        // Portsmouth to Cherbourg, about 150 km across the Channel.
        val there = Tides.distanceKm(50.80, -1.09, 49.63, -1.62)
        val back = Tides.distanceKm(49.63, -1.62, 50.80, -1.09)
        assertEquals(there, back, 0.001)
        assertTrue("expected ~150 km, got $there", there in 130.0..170.0)
        assertEquals(0.0, Tides.distanceKm(50.80, -1.09, 50.80, -1.09), 0.001)
    }
}
