/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.*
import org.junit.Test
import uk.co.tacklebox.app.data.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Sessions, Insights and Year on the Water rules ported in parity wave 3. */
class ParityWave3Test {
    private val zone = ZoneId.of("Europe/London")
    private fun fish(id:Long, at:String, grams:Double?=1000.0, species:String?="Tench", rig:String?=null, conditions:ConditionsSnapshot?=null) =
        CatchRow(Catch(id=id, speciesId=species?.hashCode()?.toLong(), weightGrams=grams, caughtAt=Instant.parse(at), rig=rig), species?.let { Species(id=it.hashCode().toLong(), name=it, discipline=Discipline.COARSE) }, null, conditions)

    @Test fun `monthly counts keep the last twelve months with catches and flag the latest`() {
        val rows = (1..14).map { i -> val month = (i - 1) % 12 + 1; val year = if (i > 12) 2026 else 2025; fish(i.toLong(), "%d-%02d-10T08:00:00Z".format(year, month)) }
        val months = InsightRules.monthly(rows, zone)
        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2025, 3), months.first().start)
        assertEquals(YearMonth.of(2026, 2), months.last().start)
        assertTrue(months.last().isLatest); assertFalse(months.first().isLatest)
    }

    @Test fun `species rank by count then name with a share of the biggest`() {
        val rows = listOf(fish(1, "2026-01-01T08:00:00Z", species="Tench"), fish(2, "2026-01-02T08:00:00Z", species="Roach"), fish(3, "2026-01-03T08:00:00Z", species="Roach"), fish(4, "2026-01-04T08:00:00Z", species="Bream"), fish(5, "2026-01-05T08:00:00Z", species=null))
        val ranked = InsightRules.species(rows)
        assertEquals(listOf("Roach", "Bream", "Tench"), ranked.map { it.name })
        assertEquals(1f, ranked[0].proportion, 0f); assertEquals(0.5f, ranked[1].proportion, 0f)
        assertEquals(listOf("Mere" to 2, "Lea" to 1), InsightRules.waters(listOf("Mere", null, "Lea", "Mere")).map { it.name to it.count })
    }

    @Test fun `the best pattern needs three conditioned catches and picks modes and a four-degree band`() {
        fun c(temp:Double, wind:String?, trend:String?) = ConditionsSnapshot(catchId=0, airTempC=temp, windDirection=wind, pressureTrend=trend)
        assertNull(InsightRules.bestPattern(listOf(c(12.0, "SW", "Steady"), c(13.0, "SW", "Steady")), UnitSystem.METRIC))
        val pattern = InsightRules.bestPattern(listOf(c(12.0, "SW", "Steady"), c(13.5, "SW", "Rising"), c(15.9, "W", "Steady"), c(21.0, "SW", "Falling")), UnitSystem.METRIC)!!
        assertEquals("SW", pattern.wind); assertEquals("Steady", pattern.pressure); assertEquals("12–16°C", pattern.temperature)
        assertEquals("54–61°F", InsightRules.temperatureBand(12, UnitSystem.IMPERIAL))
        assertNull("no wind direction at all means no pattern", InsightRules.bestPattern(listOf(c(12.0, null, "Steady"), c(13.0, "", "Steady"), c(14.0, null, "Steady")), UnitSystem.METRIC))
        assertEquals("a tie goes to the first alphabetically", "N", InsightRules.mode(listOf("SW", "N", " SW ", "N")))
    }

    @Test fun `season hours are exact and formatted like iOS`() {
        val session = SessionRow(FishingSession(id=1, startAt=Instant.parse("2025-12-31T22:00:00Z"), endAt=Instant.parse("2026-01-01T03:30:00Z")), null, emptyList())
        assertEquals(3.5, SeasonRules.hours(listOf(session), 2026, ZoneId.of("UTC")), 0.001)
        assertEquals("3.5", SeasonRules.formattedHours(3.5)); assertEquals("12", SeasonRules.formattedHours(12.4))
        assertEquals("6.5 h", sessionDuration(Instant.parse("2026-06-01T05:00:00Z"), Instant.parse("2026-06-01T11:30:00Z")))
    }

    @Test fun `new personal bests are the all-time records set in the year`() {
        val rows = listOf(fish(1, "2025-06-01T08:00:00Z", grams=3000.0, species="Tench"), fish(2, "2026-06-01T08:00:00Z", grams=2000.0, species="Tench"), fish(3, "2026-07-01T08:00:00Z", grams=500.0, species="Roach"))
        assertEquals(listOf(3L), SeasonRules.newPersonalBests(rows, 2026).map { it.item.id })
        assertEquals(listOf(1L), SeasonRules.newPersonalBests(rows, 2025).map { it.item.id })
    }

    @Test fun `the season share text names the year and the best fish`() {
        val text = ShareCards.seasonText(ShareCards.Season(2026, "Tench", "2.13 kg", 4, "6.40 kg", 2, 3), 7)
        assertEquals("My 2026 on the water\n4 fish landed\n3 species\nBest · Tench 2.13 kg\n7 hours on the bank\n— logged with Tacklebox", text)
    }
}
