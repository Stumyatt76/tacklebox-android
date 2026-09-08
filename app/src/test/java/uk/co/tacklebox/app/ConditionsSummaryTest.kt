/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import uk.co.tacklebox.app.data.ConditionsSnapshot
import uk.co.tacklebox.app.data.UnitSystem

/**
 * The one-line conditions summary shown on the capture screen (TB-P-04).
 *
 * Android captured conditions silently at save time, so the angler could not see what was being stamped on the
 * fish, let alone retry a failed reading. The wording, order and units match iOS's `ConditionsMetrics.summary`.
 */
class ConditionsSummaryTest {
    private val full = ConditionsSnapshot(catchId = 1, airTempC = 13.7, windSpeedKph = 12.0,
        windDirection = "WSW", pressureHpa = 996.0, moonPhase = "Waning Crescent")

    @Test fun `metric reads in celsius, kilometres per hour and hectopascals`() {
        assertEquals("14°C  ·  WSW 12 km/h  ·  996 hPa  ·  Waning Crescent moon", full.summary(UnitSystem.METRIC))
    }

    @Test fun `imperial converts every unit, not just temperature`() {
        assertEquals("57°F  ·  WSW 7 mph  ·  29.41 inHg  ·  Waning Crescent moon", full.summary(UnitSystem.IMPERIAL))
    }

    /**
     * A weather call can fail while the moon phase still succeeds, because the moon is computed on-device. The
     * summary has to degrade to what is known rather than print "null" or an empty gap.
     */
    @Test fun `a partial reading shows only what was captured`() {
        val moonOnly = ConditionsSnapshot(catchId = 1, moonPhase = "Full Moon")
        assertEquals("Full Moon moon", moonOnly.summary(UnitSystem.METRIC))
        assertFalse(moonOnly.summary(UnitSystem.METRIC).contains("null"))
    }

    @Test fun `an empty reading produces nothing rather than a row of separators`() {
        assertEquals("", ConditionsSnapshot(catchId = 1).summary(UnitSystem.METRIC))
    }
}
