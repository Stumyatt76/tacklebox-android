/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.co.tacklebox.app.services.HourlyWeather
import uk.co.tacklebox.app.services.PressureTrend

/**
 * Which way the glass is going (feature parity with iOS, 2026-09-08).
 *
 * Android carried a `pressureTrend` column and an importer that filled it, but nothing that ever computed one, so
 * the field only ever showed on journals imported from iOS. Three hours back, one hectopascal either way — the
 * same window and threshold as `ConditionsService`.
 */
class PressureTrendTest {
    private val hours = listOf(
        "2026-09-08T09:00", "2026-09-08T10:00", "2026-09-08T11:00", "2026-09-08T12:00", "2026-09-08T13:00")

    private fun series(vararg pressures: Double?) = HourlyWeather(hours, pressures.toList())

    @Test fun `a glass that has climbed a hectopascal in three hours is rising`() {
        assertEquals("Rising", PressureTrend.of(1006.0, series(1004.0, 1004.5, 1005.0, 1006.0, null), "2026-09-08T12:00"))
    }

    @Test fun `a falling glass is what an angler wants to know about`() {
        assertEquals("Falling", PressureTrend.of(1002.0, series(1005.0, 1004.0, 1003.0, 1002.0, null), "2026-09-08T12:00"))
    }

    @Test fun `less than a hectopascal either way is steady`() {
        assertEquals("Steady", PressureTrend.of(1004.5, series(1004.0, 1004.2, 1004.3, 1004.5, null), "2026-09-08T12:00"))
    }

    /** No series, no current reading, or an hour with a gap in it — all degrade to Steady rather than throwing. */
    @Test fun `missing data is steady rather than a crash`() {
        assertEquals("Steady", PressureTrend.of(null, series(1004.0), "2026-09-08T12:00"))
        assertEquals("Steady", PressureTrend.of(1004.0, null, "2026-09-08T12:00"))
        assertEquals("Steady", PressureTrend.of(1004.0, HourlyWeather(), "2026-09-08T12:00"))
        assertEquals("Steady", PressureTrend.of(1006.0, series(null, null, null, null, null), "2026-09-08T12:00"))
    }

    /** With no "current" timestamp it falls back to the end of the series rather than giving up. */
    @Test fun `an absent current timestamp uses the latest hour`() {
        assertEquals("Rising", PressureTrend.of(1008.0, series(1004.0, 1005.0, 1006.0, 1007.0), null))
    }
}
