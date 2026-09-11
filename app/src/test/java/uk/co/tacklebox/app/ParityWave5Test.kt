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
import uk.co.tacklebox.app.services.SpeciesId
import uk.co.tacklebox.app.services.Tides

/** My Tacklebox, data services and secret storage rules ported in parity wave 5. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class ParityWave5Test {
    @Test fun `gear performance ranks by count then name, case-insensitively`() {
        val ranked = GearRules.performance(listOf("Method feeder", "method feeder", " Waggler ", "Ledger", "Ledger", "", null))
        assertEquals(listOf("Ledger", "Method feeder", "Waggler"), ranked.map { it.name })
        assertEquals(listOf(2, 2, 1), ranked.map { it.count })
        assertEquals(1f, ranked[0].proportion, 0f); assertEquals(0.5f, ranked[2].proportion, 0f)
    }

    @Test fun `behind your PBs reads rig · bait, either alone, or nothing`() {
        fun row(rig:String?, bait:String?) = CatchRow(Catch(rig=rig, bait=bait), null, null, null)
        assertEquals("Ronnie rig · Boilie", GearRules.pbSummary(listOf(row("Ronnie rig", "Boilie"), row("Hair rig", "Boilie"), row("Ronnie rig", null))))
        assertEquals("Boilie", GearRules.pbSummary(listOf(row(null, "Boilie"))))
        assertNull(GearRules.pbSummary(listOf(row(null, " "))))
    }

    @Test fun `the WorldTides test classifies as iOS does`() {
        assertEquals(DataServiceTestResult.Invalid, Tides.classifyWorldTides(401, null))
        assertEquals(DataServiceTestResult.Connected, Tides.classifyWorldTides(200, """{"extremes":[{"dt":1,"height":1.2,"type":"High"}]}"""))
        assertEquals(DataServiceTestResult.Invalid, Tides.classifyWorldTides(200, """{"status":400,"error":"Invalid key"}"""))
        assertEquals(DataServiceTestResult.Invalid, Tides.classifyWorldTides(200, """{"extremes":[]}"""))
        assertEquals(DataServiceTestResult.Unreachable(Tides.WORLD_TIDES_UNREACHABLE), Tides.classifyWorldTides(503, null))
        assertEquals(DataServiceTestResult.Connected, SpeciesId.classifyINaturalist(200))
        assertEquals(DataServiceTestResult.Invalid, SpeciesId.classifyINaturalist(403))
        assertEquals(DataServiceTestResult.Unreachable(SpeciesId.INATURALIST_UNREACHABLE), SpeciesId.classifyINaturalist(500))
    }

    @Test fun `a stored secret reads Saved · tap Test until it is validated`() {
        val secrets = Secrets(MemorySecretVault())
        assertNull(secrets.state.value.worldTidesStatus)
        assertTrue(secrets.save(Secrets.WORLD_TIDES, " key "))
        assertEquals("key", secrets.worldTidesKey)
        assertEquals(DataServiceStatus.SAVED, secrets.state.value.worldTidesStatus)
        secrets.setStatus(Secrets.WORLD_TIDES, DataServiceStatus.CONNECTED)
        assertEquals(DataServiceStatus.CONNECTED, secrets.state.value.worldTidesStatus)
        secrets.save(Secrets.WORLD_TIDES, "another")
        assertEquals("a re-saved key is untested again", DataServiceStatus.SAVED, secrets.state.value.worldTidesStatus)
        secrets.save(Secrets.WORLD_TIDES, "")
        assertEquals("", secrets.worldTidesKey); assertNull(secrets.state.value.worldTidesStatus)
    }

    @Test fun `secrets left in the Room columns move into the vault once and the columns are blanked`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TackleboxDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = TackleboxRepository(db)
            repo.saveSettings(AppSettings(speciesIdToken="tok", worldTidesKey="wt"))
            val vault = MemorySecretVault(); vault.write(Secrets.SPECIES_ID, "already")
            val secrets = Secrets(vault)
            secrets.migrateFrom(repo)
            assertEquals("a value already in the vault wins", "already", secrets.speciesIdToken)
            assertEquals("wt", secrets.worldTidesKey)
            val settings = repo.settings.first()
            assertEquals("", settings.speciesIdToken); assertEquals("", settings.worldTidesKey)
        } finally { db.close() }
    }
}
