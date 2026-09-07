/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.tacklebox.app.data.*
import java.time.Instant

/**
 * Import reads a journal back in. It is the only feature that writes bulk user data, so the two guarantees it
 * makes — never destroy anything, never import the same catch twice — are asserted directly.
 *
 * Mirrors the iOS JournalImportTests case for case, including the cross-platform file.
 */
class JournalImportTest {
    private val empty = JournalImport.ExistingVault(emptySet(), emptySet(), emptySet())

    private fun journal(catches: String = "", extra: String = "") = """
        {"app":"Tacklebox","schema":1,"units":"METRIC",
         "waters":[{"name":"Alder Mere","type":"LAKE","region":"Oxfordshire","swimNotes":"Reeds."}],
         "sessions":[{"id":1,"startAt":"2026-06-01T06:00:00Z","water":"Alder Mere","notes":""}],
         "gear":[],"presets":[],
         "catches":[${catches.ifBlank { defaultCatch }}]$extra}
    """.trimIndent()

    private val defaultCatch = """
        {"caughtAt":"2026-06-01T07:00:00Z","species":"Tench","scientificName":"Tinca tinca",
         "weightGrams":2126.0,"lengthCm":45.0,"returned":true,"water":"Alder Mere","sessionId":1,
         "rig":"Method feeder","bait":"Sweetcorn","notes":"Reeds at first light.",
         "conditions":{"airTempC":14.0,"windDirection":"SW","windSpeedKph":13.0,"pressureHpa":1016.0,"moonPhase":"Waxing crescent"}}
    """.trimIndent()

    // --- Validation ---------------------------------------------------------------------------------------------

    @Test(expected = JournalImport.Failure::class)
    fun `rubbish is rejected`() { JournalImport.plan("not json at all", empty) }

    @Test(expected = JournalImport.Failure::class)
    fun `someone elses json is rejected`() { JournalImport.plan("""{"app":"SomeOtherApp","catches":[]}""", empty) }

    @Test(expected = JournalImport.Failure::class)
    fun `a newer format is refused rather than misread`() {
        JournalImport.plan("""{"app":"Tacklebox","schema":99,"catches":[]}""", empty)
    }

    // --- Parsing ------------------------------------------------------------------------------------------------

    @Test fun `a journal parses into its parts`() {
        val plan = JournalImport.plan(journal(), empty)
        assertEquals(1, plan.newCatches)
        assertEquals(0, plan.duplicateCatches)
        assertEquals(1, plan.newWaters)
        assertEquals(1, plan.newSpecies)
        assertEquals(1, plan.sessions.size)

        val row = plan.catches.single()
        assertEquals("Tench", row.species)
        assertEquals(2126.0, row.weightGrams!!, 0.01)
        assertEquals(45.0, row.lengthCm!!, 0.01)
        assertEquals("Method feeder", row.rig)
        assertEquals("Reeds at first light.", row.notes)
        assertEquals(1, row.sessionId)
        assertEquals("SW", row.conditions?.windDirection)
        assertEquals(Instant.parse("2026-06-01T07:00:00Z"), row.caughtAt)
    }

    // --- Never import the same catch twice -----------------------------------------------------------------------

    @Test fun `a catch already in the vault is flagged and skipped`() {
        val existing = JournalImport.ExistingVault(
            catchFingerprints = setOf(JournalImport.fingerprint("Tench", Instant.parse("2026-06-01T07:00:00Z"))),
            waterNames = emptySet(), speciesNames = emptySet()
        )
        val plan = JournalImport.plan(journal(), existing)
        assertEquals(0, plan.newCatches)
        assertEquals(1, plan.duplicateCatches)
    }

    @Test fun `duplicates inside the file itself are collapsed`() {
        val plan = JournalImport.plan(journal(catches = "$defaultCatch,$defaultCatch"), empty)
        assertEquals(1, plan.newCatches)
        assertEquals(1, plan.duplicateCatches)
    }

    @Test fun `the fingerprint ignores case but not the instant`() {
        val a = JournalImport.fingerprint("Tench", Instant.ofEpochSecond(1000))
        val b = JournalImport.fingerprint("TENCH", Instant.ofEpochSecond(1000))
        val c = JournalImport.fingerprint("Tench", Instant.ofEpochSecond(1001))
        assertEquals(a, b)
        assertTrue(a != c)
    }

    // --- Never destroy anything ----------------------------------------------------------------------------------

    @Test fun `a water already present is not counted as new`() {
        val existing = JournalImport.ExistingVault(emptySet(), setOf("alder mere"), emptySet())
        assertEquals(0, JournalImport.plan(journal(), existing).newWaters)
    }

    @Test fun `a species already present is not counted as new`() {
        val existing = JournalImport.ExistingVault(emptySet(), emptySet(), setOf("tench"))
        assertEquals(0, JournalImport.plan(journal(), existing).newSpecies)
    }

    // --- Cross-platform -------------------------------------------------------------------------------------------

    /** An iOS export spells its enums in camelCase and sends whole-number weights. A journal has to cross. */
    @Test fun `an iOS shaped journal imports`() {
        val ios = """
            {"app":"Tacklebox","schema":1,"units":"METRIC",
             "waters":[{"name":"Alder Mere","type":"dayTicket","region":"Oxfordshire","swimNotes":""}],
             "sessions":[],"gear":[{"name":"Landing net","category":"terminal","notes":""}],
             "presets":[{"name":"Boilie","kind":"bait"}],
             "catches":[{"caughtAt":"2026-06-01T07:00:00.000Z","species":"Common Carp","weightGrams":8108,"returned":true}]}
        """.trimIndent()
        val plan = JournalImport.plan(ios, empty)
        assertEquals(1, plan.newCatches)
        assertEquals(8108.0, plan.catches.single().weightGrams!!, 0.01)
        // dayTicket has no Android equivalent, so it lands on LAKE rather than being dropped.
        assertEquals(WaterType.LAKE, plan.waters.single().type)
        assertEquals(GearCategory.OTHER, plan.gear.single().category)
        assertEquals(PresetKind.BAIT, plan.presets.single().kind)
    }

    @Test fun `unknown enum spellings fall back rather than failing`() {
        assertEquals(WaterType.SEA, JournalImport.waterType("shore"))
        assertEquals(WaterType.LAKE, JournalImport.waterType("DAY_TICKET"))
        assertEquals(WaterType.RIVER, JournalImport.waterType("RIVER"))
        assertEquals(WaterType.LAKE, JournalImport.waterType(null))
        assertEquals(PresetKind.RIG, JournalImport.presetKind(null))
    }

    // --- Robustness -----------------------------------------------------------------------------------------------

    @Test fun `a catch with no timestamp is skipped rather than failing the whole file`() {
        val plan = JournalImport.plan(journal(catches = """{"species":"Tench"},$defaultCatch"""), empty)
        assertEquals(1, plan.catches.size)
    }

    @Test fun `missing optional fields are tolerated`() {
        val bare = """{"app":"Tacklebox","schema":1,"catches":[{"caughtAt":"2026-06-01T07:00:00Z"}]}"""
        val plan = JournalImport.plan(bare, empty)
        val row = plan.catches.single()
        assertEquals(null, row.species)
        assertEquals(null, row.weightGrams)
        assertEquals("", row.notes)
        assertTrue("an unspecified catch is assumed returned", row.returned)
    }

    /**
     * Regression for a real defect: the export wrote `sessionId` on every catch but never published `id` on the
     * sessions, so no importer could reconnect the two. Found by round-tripping a genuine export into the iOS app.
     * An older file must still import — the catch is kept, it simply arrives without its session.
     */
    @Test fun `a session-id that matches nothing still imports the catch`() {
        val orphaned = """
            {"app":"Tacklebox","schema":1,
             "sessions":[{"startAt":"2026-06-01T06:00:00Z","water":"Alder Mere","notes":""}],
             "catches":[{"caughtAt":"2026-06-01T07:00:00Z","species":"Tench","sessionId":1}]}
        """.trimIndent()
        val plan = JournalImport.plan(orphaned, empty)
        assertEquals(1, plan.newCatches)
        assertEquals(null, plan.sessions.single().id)
        assertEquals(1, plan.catches.single().sessionId)   // preserved; the apply step simply finds no match
    }

    @Test fun `an empty journal reports itself as empty`() {
        val plan = JournalImport.plan("""{"app":"Tacklebox","schema":1,"catches":[]}""", empty)
        assertTrue(plan.isEmpty)
    }
}
