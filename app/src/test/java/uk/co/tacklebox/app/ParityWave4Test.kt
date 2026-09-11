/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.*
import org.junit.Test
import uk.co.tacklebox.app.services.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Waters, rivers, tides and bite-window rules ported in parity wave 4. */
class ParityWave4Test {
    @Test fun `live formatting matches the iOS number and distance styles`() {
        assertEquals("3.2 km", LiveFormat.distance(3.24)); assertEquals("14 km", LiveFormat.distance(14.4))
        assertEquals("0.45", LiveFormat.number(0.45)); assertEquals("12", LiveFormat.number(12.0)); assertEquals("12.3", LiveFormat.number(12.34, 1))
        assertEquals("Level 0.45 m", LiveFormat.gaugeSummary(gauge(level=RiverMeasurement(0.45, "m"))))
        assertEquals("Flow 12.3 m3/s", LiveFormat.gaugeSummary(gauge(flow=RiverMeasurement(12.34, "m3/s"))))
        assertEquals("Latest reading unavailable", LiveFormat.gaugeSummary(gauge()))
        assertEquals("Trend unavailable", LiveFormat.trendTitle(RiverTrend.UNKNOWN)); assertEquals("Rising", LiveFormat.trendTitle(RiverTrend.RISING))
        assertNull(LiveFormat.measurement(null)); assertEquals("1.2 m", LiveFormat.measurement(RiverMeasurement(1.2, "m")))
    }

    @Test fun `updated-ago reads in named units`() {
        val now = Instant.parse("2026-09-11T12:00:00Z")
        assertEquals("just now", LiveFormat.relativeAgo(now.minusSeconds(30), now))
        assertEquals("1 minute ago", LiveFormat.relativeAgo(now.minusSeconds(60), now))
        assertEquals("2 hours ago", LiveFormat.relativeAgo(now.minusSeconds(2 * 3600 + 60), now))
        assertEquals("3 days ago", LiveFormat.relativeAgo(now.minusSeconds(3 * 86400), now))
    }

    @Test fun `the sea state comes from the current hour and knows when it is inland`() {
        val times = (0 until 48).map { "2026-09-11T%02d:00".format(it % 24).let { t -> if (it < 24) t else t.replace("-11T", "-12T") } }
        val hourly = MarineHourly(times, waveHeight = times.indices.map { it * 0.1 }, waveDirection = times.indices.map { 225.0 }, wavePeriod = times.indices.map { 5.0 }, seaTemperature = times.indices.map { 14.2 })
        val now = SeaState.now(hourly, LocalDateTime.of(2026, 9, 11, 16, 20))
        assertEquals(1.6, now.waveHeight!!, 0.001); assertEquals("SW", compassPoint(now.waveDirection!!)); assertEquals(14.2, now.seaTemperature!!, 0.0)
        assertEquals(25, SeaState.next24(hourly, LocalDateTime.of(2026, 9, 11, 16, 20)).size)
        assertTrue(SeaState.isCoastal(hourly)); assertFalse(SeaState.isCoastal(null)); assertFalse(SeaState.isCoastal(MarineHourly(times, waveHeight = times.map { null })))
    }

    @Test fun `heights and temperatures on the sea state follow the unit setting`() {
        assertEquals("1.2 m", 1.2.metres(uk.co.tacklebox.app.data.UnitSystem.METRIC)); assertEquals("3.9 ft", 1.2.metres(uk.co.tacklebox.app.data.UnitSystem.IMPERIAL))
        assertEquals("14.2°C", 14.2.celsius(uk.co.tacklebox.app.data.UnitSystem.METRIC)); assertEquals("57.6°F", 14.2.celsius(uk.co.tacklebox.app.data.UnitSystem.IMPERIAL))
    }

    @Test fun `solar noon sits between sunrise and sunset`() {
        val day = Astronomy.calculate(LocalDate.of(2026, 6, 21), 52.36, -1.17, ZoneId.of("Europe/London"))
        assertNotNull(day.solarNoon)
        assertTrue(day.solarNoon!! > day.sunrise && day.solarNoon!! < day.sunset)
        assertEquals(13, day.solarNoon!!.hour)
    }

    @Test fun `the matched-catches context line follows the busiest band`() {
        val sunrise = LocalTime.of(6, 12); val sunset = LocalTime.of(19, 40)
        assertTrue(PersonalInsight.contextLine(1, sunrise, sunset).startsWith("Your catches cluster around dawn; the selected day's sunrise is "))
        assertTrue(PersonalInsight.contextLine(5, sunrise, sunset).startsWith("Your catches lean toward dusk and night; the selected day's sunset is "))
        assertEquals("Compare that pattern with the selected day's highlighted feeding periods.", PersonalInsight.contextLine(2, sunrise, sunset))
        assertEquals("${LocalTime.of(4, 0).hm()}–${LocalTime.of(8, 0).hm()}", PersonalInsight.bandLabel(1))
        assertEquals("${LocalTime.of(20, 0).hm()}–${LocalTime.of(0, 0).hm()}", PersonalInsight.bandLabel(5))
    }

    private fun gauge(level:RiverMeasurement?=null, flow:RiverMeasurement?=null) =
        RiverGauge("g", "Gauge", null, level, flow, RiverTrend.STEADY, null, 1.0, "Environment Agency", emptyList(), null)
}
