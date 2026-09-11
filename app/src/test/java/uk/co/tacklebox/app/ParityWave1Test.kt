/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.co.tacklebox.app.data.*
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The data and capture rules ported from iOS in parity wave 1. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class ParityWave1Test {
    private fun database() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TackleboxDatabase::class.java)
        .allowMainThreadQueries().build()

    // --- "Caught at": date and time, never in the future --------------------------------------------------------

    @Test fun `the time picker keeps the day and changes only the clock`() {
        val zone = ZoneId.of("Europe/London")
        val previous = LocalDateTime.of(2026, 9, 3, 14, 25).atZone(zone).toInstant().toEpochMilli()
        val moved = Instant.ofEpochMilli(CatchTiming.withTime(previous, 6, 40, zone)).atZone(zone)
        assertEquals(3, moved.dayOfMonth); assertEquals(LocalTime.of(6, 40), moved.toLocalTime())
    }

    @Test fun `a picked date and time are clamped to now, as the iOS picker is bounded`() {
        val now = 1_000_000L
        assertEquals(now, CatchTiming.clampToNow(now + 60_000, now))
        assertEquals(now - 1, CatchTiming.clampToNow(now - 1, now))
    }

    @Test fun `the picker opens on the catch's own calendar day`() {
        val zone = ZoneId.of("America/New_York")
        val late = LocalDateTime.of(2026, 9, 3, 23, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(Instant.parse("2026-09-03T00:00:00Z").toEpochMilli(), CatchTiming.utcMidnightOf(late, zone))
    }

    // --- Add Species: discipline chosen, existing names reused -------------------------------------------------

    @Test fun `an added species takes the chosen discipline and is reused by name`() = runBlocking {
        val db = database()
        try {
            val repo = TackleboxRepository(db)
            val id = repo.addSpecies("Wels catfish", listOf("COARSE"), Discipline.PREDATOR)
            assertEquals(Discipline.PREDATOR, db.dao().speciesOnce().single { it.id == id }.discipline)
            assertEquals(id, repo.addSpecies("wels catfish", listOf("COARSE"), Discipline.SEA))
        } finally { db.close() }
    }

    // --- Reset to a fresh vault ---------------------------------------------------------------------------------

    @Test fun `a reset empties the vault, re-seeds species and presets, resets settings and replays onboarding`() = runBlocking {
        val db = database()
        val root = File(RuntimeEnvironment.getApplication().filesDir, "reset-root").apply { mkdirs() }
        try {
            val repo = TackleboxRepository(db, root)
            repo.seed(samples = true)
            repo.saveSettings(repo.settings.first().copy(unitSystem = UnitSystem.IMPERIAL, activeDisciplines = listOf("SEA"), freeSessionsStarted = 2, speciesIdToken = "t"))
            repo.addSpecies("Zander", listOf("PREDATOR"))
            repo.addPreset(TacklePreset(name = "Zig rig", kind = PresetKind.RIG))
            repo.addGear(GearItem(name = "Rod", category = GearCategory.ROD))
            val photo = File(File(root, "photos").apply { mkdirs() }, "cover.jpg").apply { writeBytes(byteArrayOf(1)) }
            val id = repo.addCatch(Catch(photoUri = android.net.Uri.fromFile(photo).toString()), ConditionsSnapshot(catchId = 0, airTempC = 9.0), listOf(android.net.Uri.fromFile(photo).toString()))
            repo.addSession(FishingSession(waterId = null))
            assertTrue(id > 0)

            repo.resetVault()

            assertTrue(repo.catches.first().isEmpty()); assertTrue(repo.sessions.first().isEmpty())
            assertTrue(repo.waters.first().isEmpty()); assertTrue(repo.gear.first().isEmpty())
            assertEquals(TackleboxRepository.seedSpecies.map { it.name }.toSet(), repo.species.first().map { it.name }.toSet())
            assertEquals(TackleboxRepository.seedPresets.map { it.name }.toSet(), repo.presets.first().map { it.name }.toSet())
            val settings = repo.settings.first()
            assertFalse("onboarding replays", settings.onboardingComplete)
            assertEquals("units return to the default", repo.defaults().unitSystem, settings.unitSystem)
            assertEquals(Discipline.entries.map { it.name }, settings.activeDisciplines)
            assertEquals("the free-session count is the one thing kept", 2, settings.freeSessionsStarted)
            assertFalse("photo files go with their rows", photo.exists())
        } finally { db.close(); root.deleteRecursively() }
    }

    @Test fun `the reset confirmation quotes live counts in the iOS wording`() {
        val state = AppState(true, catches = listOf(CatchRow(Catch(id = 1), null, null, null)), sessions = emptyList(),
            waters = listOf(Water(id = 1, name = "Mere", type = WaterType.LAKE, region = "")), gear = emptyList())
        assertEquals("Delete 1 catches and their photos, 0 sessions, 1 waters, 0 gear items, your presets and any species you added? Units and disciplines return to defaults. Export your journal first if you want to keep a copy; JSON does not include photos. This cannot be undone.", resetSummary(state))
    }

    // --- Export and import copy ---------------------------------------------------------------------------------

    @Test fun `the export reports hasPhoto rather than a device path`() {
        val row = CatchRow(Catch(id = 1, photoUri = "file:///data/user/0/x/photos/a.jpg"), null, null, null)
        @Suppress("UNCHECKED_CAST") val catches = JournalExport.payload(AppState(true, catches = listOf(row)))["catches"] as List<Map<String, Any?>>
        assertEquals(true, catches.single()["hasPhoto"])
        assertFalse(catches.single().containsKey("photoUri"))
        assertEquals(1, catches.single()["photoCount"])
    }

    @Test fun `the import summary lists counts in the iOS order and wording`() {
        val plan = JournalImport.Plan(newWaters = 2, newSpecies = 1, newSessions = 3, newGear = 0, newPresets = 4, duplicateCatches = 1)
        assertEquals("This adds 0 new catches, 2 new waters, 3 new sessions, 0 new gear items, 4 new presets, 1 new species. 1 catch is already in your vault and will be skipped. Nothing you already have is changed or removed. Photos are not included in an export, so imported catches arrive without them.", importSummary(plan))
        assertEquals("This adds 0 new catches, 0 new sessions, 0 new gear items, 0 new presets. Nothing you already have is changed or removed. Photos are not included in an export, so imported catches arrive without them.", importSummary(JournalImport.Plan()))
    }

    // --- Display names and units ------------------------------------------------------------------------------

    @Test fun `water types and subtitles use the iOS display names`() {
        assertEquals("Shore / beach", WaterType.SHORE.title); assertEquals("Day ticket", WaterType.DAY_TICKET.title); assertEquals("Lake", WaterType.LAKE.title)
        assertEquals("Day ticket · Kent", Water(name = "A", type = WaterType.DAY_TICKET, region = " Kent ").subtitle)
        assertEquals("River", Water(name = "A", type = WaterType.RIVER, region = "").subtitle)
        assertEquals("Terminal tackle", GearCategory.TERMINAL.title)
    }

    @Test fun `sea heights and temperatures follow the unit setting`() {
        assertEquals("1.2 m", 1.2.metres(UnitSystem.METRIC)); assertEquals("3.9 ft", 1.2.metres(UnitSystem.IMPERIAL))
        assertEquals("14.2 °C", 14.2.celsius(UnitSystem.METRIC)); assertEquals("57.6 °F", 14.2.celsius(UnitSystem.IMPERIAL))
    }

    @Test fun `a session that is already running is refused in the iOS words`() = runBlocking {
        val db = database()
        try {
            val repo = TackleboxRepository(db)
            repo.startSession(null, unlimited = true)
            val error = runCatching { repo.startSession(null, unlimited = true) }.exceptionOrNull()
            assertEquals("A session is already running.", error?.message)
        } finally { db.close() }
    }
}
