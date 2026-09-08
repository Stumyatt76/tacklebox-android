/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue
import uk.co.tacklebox.app.services.RiverError
import uk.co.tacklebox.app.services.RiverReading
import uk.co.tacklebox.app.services.RiverTrend
import uk.co.tacklebox.app.services.Rivers
import java.time.Instant

/**
 * River gauges (feature parity with iOS, 2026-09-08).
 *
 * Android called one endpoint and printed bare numbers — no station name, no units, no trend, and no US coverage.
 * These cover the two decisions made before any network call, and the trend, which is the part an angler acts on.
 */
class RiversTest {

    @Before fun clearCache() = Rivers.clearCache()

    /**
     * Source selection is a pure decision made before any request, so it is tested as one.
     *
     * The first version of this called `Rivers.gauges` and asserted on the error. It passed locally only because
     * the calls failed quickly, and hung on CI until `runTest` gave up — a unit test that reaches the network is
     * a test of the network.
     */
    @Test fun `Great Britain goes to the Environment Agency and the contiguous US to USGS`() {
        assertEquals(Rivers.Source.EA, Rivers.source(52.63, 1.29))      // Norwich
        assertEquals(Rivers.Source.EA, Rivers.source(50.72, -3.53))     // Exeter
        assertEquals(Rivers.Source.USGS, Rivers.source(38.98, -76.48))  // Chesapeake
        assertEquals(Rivers.Source.USGS, Rivers.source(45.52, -122.68)) // Portland
    }

    @Test fun `somewhere with neither source is null rather than a guess`() {
        assertNull("Sydney", Rivers.source(-33.87, 151.21))
        assertNull("Reykjavik", Rivers.source(64.15, -21.94))
        assertNull("Cape Town", Rivers.source(-33.92, 18.42))
    }

    /** And that null is what turns into the message the angler sees. */
    @Test fun `no source means the area is reported as unavailable`() = runTest {
        val error = runCatching { Rivers.gauges(-33.87, 151.21) }.exceptionOrNull()
        assertTrue("expected UnavailableArea, got $error", error is RiverError.UnavailableArea)
    }

    private fun at(hoursAgo: Long) = Instant.parse("2026-09-08T12:00:00Z").minusSeconds(hoursAgo * 3600)

    @Test fun `a river climbing over three hours is rising`() {
        val readings = listOf(RiverReading(at(3), 1.00), RiverReading(at(2), 1.10), RiverReading(at(0), 1.30))
        assertEquals(RiverTrend.RISING, Rivers.trend(readings))
    }

    @Test fun `a river dropping over three hours is falling`() {
        val readings = listOf(RiverReading(at(3), 2.00), RiverReading(at(0), 1.60))
        assertEquals(RiverTrend.FALLING, Rivers.trend(readings))
    }

    /**
     * The threshold is one per cent of the earlier reading, so a big river moving a centimetre is steady. Without
     * that, a gauge sitting at four metres would flicker between rising and falling all day.
     */
    @Test fun `a centimetre on a four metre river is steady, not rising`() {
        val readings = listOf(RiverReading(at(3), 4.20), RiverReading(at(0), 4.21))
        assertEquals(RiverTrend.STEADY, Rivers.trend(readings))
    }

    /** But the same centimetre on a shallow stream is a real move, which is what the 0.01 floor protects. */
    @Test fun `the floor keeps small streams responsive`() {
        val readings = listOf(RiverReading(at(3), 0.10), RiverReading(at(0), 0.14))
        assertEquals(RiverTrend.RISING, Rivers.trend(readings))
    }

    @Test fun `one reading or none cannot show a trend`() {
        assertEquals(RiverTrend.UNKNOWN, Rivers.trend(emptyList()))
        assertEquals(RiverTrend.UNKNOWN, Rivers.trend(listOf(RiverReading(at(0), 1.0))))
    }
}
