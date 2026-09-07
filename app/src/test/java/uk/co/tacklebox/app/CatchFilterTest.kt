/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.tacklebox.app.data.*
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The filtering behind the catch list. Neither app had search or filter, so this is the first coverage of the
 * questions an angler actually asks of a journal. Kept in step with the iOS CatchFilterTests, case for case.
 */
class CatchFilterTest {
    private val now: Instant = Instant.ofEpochSecond(1_780_000_000)   // fixed clock, so period tests never drift

    private val tench = Species(id = 1, name = "Tench", discipline = Discipline.COARSE, scientificName = "Tinca tinca")
    private val carp = Species(id = 2, name = "Common carp", discipline = Discipline.COARSE, scientificName = "Cyprinus carpio")
    private val roach = Species(id = 3, name = "Roach", discipline = Discipline.COARSE, scientificName = "Rutilus rutilus")
    private val alder = Water(id = 1, name = "Alder Mere", type = WaterType.LAKE, region = "Oxfordshire")
    private val lea = Water(id = 2, name = "River Lea", type = WaterType.RIVER, region = "Hertfordshire")

    private fun row(id: Long, species: Species, water: Water?, grams: Double?, rig: String?, bait: String?, daysAgo: Long) =
        CatchRow(
            item = Catch(id = id, speciesId = species.id, weightGrams = grams, rig = rig, bait = bait,
                         waterId = water?.id, caughtAt = now.minus(daysAgo, ChronoUnit.DAYS)),
            species = species, water = water, conditions = null
        )

    private val smallCarp = row(1, carp, alder, 4_000.0, "Hair rig", "Boilie", 20)
    private val bigCarp = row(2, carp, alder, 8_108.0, "Ronnie rig", "Krill boilie", 3)
    private val tenchCatch = row(3, tench, alder, 2_126.0, "Method feeder", "Sweetcorn", 2)
    private val roachCatch = row(4, roach, lea, 397.0, "Float", "Maggot", 90)
    private val all = listOf(smallCarp, bigCarp, tenchCatch, roachCatch)

    private fun names(filter: CatchFilter) =
        filter.apply(all, now).mapNotNull { it.species?.name }.distinct().sorted()

    private fun matching(text: String) = names(CatchFilter(text = text))

    @Test fun `text matches species water rig and bait`() {
        assertEquals(listOf("Tench"), matching("tench"))
        assertEquals(listOf("Common carp", "Tench"), matching("alder"))
        assertEquals(listOf("Common carp"), matching("ronnie"))
        assertEquals(listOf("Tench"), matching("sweetcorn"))
    }

    @Test fun `text ignores case and surrounding space`() {
        assertEquals(listOf("Tench"), matching("  TENCH  "))
    }

    @Test fun `text also matches the scientific name`() {
        assertEquals(listOf("Tench"), matching("tinca"))
    }

    @Test fun `a term that matches nothing returns nothing`() {
        assertTrue(matching("marlin").isEmpty())
    }

    @Test fun `species and water narrow independently`() {
        assertEquals(listOf("Tench"), names(CatchFilter(speciesName = "Tench")))
        assertEquals(listOf("Roach"), names(CatchFilter(waterName = "River Lea")))
    }

    @Test fun `a minimum weight excludes lighter fish`() {
        assertEquals(listOf("Common carp"), names(CatchFilter(minimumGrams = 3_000.0)))
    }

    @Test fun `personal bests only keeps the heaviest of each species`() {
        val kept = CatchFilter(personalBestsOnly = true).apply(all, now).map { it.item.id }
        assertFalse("the lighter carp should drop out", kept.contains(smallCarp.item.id))
        assertTrue(kept.containsAll(listOf(bigCarp.item.id, tenchCatch.item.id, roachCatch.item.id)))
    }

    @Test fun `the period is measured from the supplied clock`() {
        val kept = CatchFilter(period = CatchFilter.Period.LAST_30_DAYS).apply(all, now)
        // Newest first: tench at 2 days, then both carp at 3 and 20 days. The 90-day-old roach is excluded.
        assertEquals(listOf("Tench", "Common carp", "Common carp"), kept.mapNotNull { it.species?.name })
        assertEquals(4, CatchFilter(period = CatchFilter.Period.ALL).apply(all, now).size)
    }

    @Test fun `clauses combine`() {
        val filter = CatchFilter(text = "boilie", speciesName = "Common carp", minimumGrams = 3_000.0)
        assertEquals(listOf("Common carp"), names(filter))
        assertTrue(names(filter.copy(minimumGrams = 20_000.0)).isEmpty())
    }

    @Test fun `results come back newest first`() {
        val ordered = CatchFilter().apply(all, now).map { it.item.caughtAt }
        assertEquals(ordered.sortedDescending(), ordered)
    }

    @Test fun `an empty filter is not active and keeps everything`() {
        val filter = CatchFilter()
        assertFalse(filter.isActive)
        assertTrue(filter.activeSummary().isEmpty())
        assertEquals(all.size, filter.apply(all, now).size)
    }

    @Test fun `whitespace only text does not count as a filter`() {
        assertTrue(CatchFilter(text = "   ").activeSummary().isEmpty())
    }

    @Test fun `the summary describes every active clause in the readers units`() {
        val filter = CatchFilter(
            text = "boilie", speciesName = "Common carp", waterName = "Alder Mere",
            period = CatchFilter.Period.THIS_YEAR, personalBestsOnly = true, minimumGrams = 5_000.0
        )
        assertTrue(filter.isActive)
        assertEquals(
            listOf("“boilie”", "Common carp", "Alder Mere", "This year", "Personal bests", "Over 5.00 kg"),
            filter.activeSummary(UnitSystem.METRIC)
        )
        assertEquals("Over 11 lb 0 oz", filter.activeSummary(UnitSystem.IMPERIAL).last())
    }

    @Test fun `an unweighed catch cannot hold a personal best`() {
        val unweighed = row(5, tench, alder, null, null, null, 1)
        val bests = CatchFilter.personalBests(listOf(unweighed))
        assertTrue(bests.isEmpty())
    }
}
