/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.services.*
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class AuditRegressionTest {
    private fun database() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TackleboxDatabase::class.java)
        .allowMainThreadQueries().build()
    private val journal = """{"app":"Tacklebox","schema":1,"waters":[{"name":"QA","type":"lake"}],"sessions":[{"id":7,"startAt":"2026-09-09T07:00:00Z","water":"QA","notes":"Dawn"}],"catches":[{"species":"Barbel","caughtAt":"2026-09-09T08:00:00Z","sessionId":7,"water":"QA","weightGrams":1000}]}"""
    private fun emptyVault() = JournalImport.ExistingVault(emptySet(), emptySet(), emptySet())

    @Test fun repeatedAndStalePlansReuseSessionsAndAttachNewCatch() = runBlocking {
        val db=database()
        try {
            val repo=TackleboxRepository(db)
            val plan=JournalImport.plan(journal, emptyVault())
            assertEquals(1,plan.newSessions)
            repo.applyImport(plan)
            val repeat=repo.applyImport(plan)
            assertEquals(0,repeat.catches); assertEquals(0,repeat.sessions)
            repo.applyImport(JournalImport.plan(journal.replace("08:00:00","09:00:00"),emptyVault()))
            val sessions=repo.sessions.first(); val fish=repo.catches.first()
            assertEquals(1,sessions.size); assertEquals(2,fish.size)
            assertTrue(fish.all { it.item.sessionId==sessions.single().item.id })
            val state=AppState(catches=fish,sessions=sessions,waters=repo.waters.first(),species=repo.species.first())
            assertEquals(0,JournalImport.plan(journal,JournalImport.ExistingVault.from(state)).newSessions)
        } finally { db.close() }
    }
    @Test fun sessionOnlyFilesAreNotRejectedAndDifferentNotesRemainDistinct() = runBlocking {
        val db=database()
        try {
            val plan=JournalImport.plan("""{"app":"Tacklebox","sessions":[{"id":1,"startAt":"2026-09-09T07:00:00Z","notes":"One"},{"id":2,"startAt":"2026-09-09T07:00:00Z","notes":"Two"}]}""",emptyVault())
            assertFalse(plan.isEmpty); assertEquals(2,plan.newSessions)
            TackleboxRepository(db).applyImport(plan)
            assertEquals(2,db.dao().sessions().first().size)
        } finally { db.close() }
    }
    @Test fun failingImportRollsBackWaterSessionSpeciesAndCatch() = runBlocking {
        val db=database()
        try {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER qa_fail BEFORE INSERT ON `Catch` BEGIN SELECT RAISE(ABORT, 'QA failure'); END")
            val failure=runCatching { TackleboxRepository(db).applyImport(JournalImport.plan(journal,emptyVault())) }
            assertTrue(failure.isFailure)
            assertTrue(db.dao().watersOnce().isEmpty()); assertTrue(db.dao().sessions().first().isEmpty())
            assertTrue(db.dao().speciesOnce().isEmpty()); assertTrue(db.dao().catches().first().isEmpty())
        } finally { db.close() }
    }
    @Test fun deleteAllRemovesConditionsIncludingExistingOrphans() = runBlocking {
        val db=database()
        try {
            val repo=TackleboxRepository(db)
            repo.addCatch(Catch(weightGrams=1000.0,photoUri="cover"),ConditionsSnapshot(catchId=0,airTempC=12.0),listOf("cover","extra"))
            db.dao().addConditions(ConditionsSnapshot(catchId=999,airTempC=99.0))
            repo.deleteAllUserData()
            assertTrue(repo.catches.first().isEmpty())
            for(table in listOf("ConditionsSnapshot","CatchPhoto","Catch","FishingSession","Water","GearItem")) {
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); assertEquals(table,0,it.getInt(0)) }
            }
            // The angler's presets go too, but the eleven starters are put back so the capture screen keeps its chips.
            assertEquals(TackleboxRepository.seedPresets.map { it.name }.toSet(), db.dao().presetsOnce().map { it.name }.toSet())
        } finally { db.close() }
    }
    @Test fun failedPhotoInsertionDoesNotLeaveCatchOrConditions() = runBlocking {
        val db=database()
        try {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER qa_fail_photo BEFORE INSERT ON CatchPhoto BEGIN SELECT RAISE(ABORT, 'QA failure'); END")
            val failure=runCatching { TackleboxRepository(db).addCatch(Catch(),ConditionsSnapshot(catchId=0),listOf("cover","extra")) }
            assertTrue(failure.isFailure); assertTrue(db.dao().catches().first().isEmpty())
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM ConditionsSnapshot").use { it.moveToFirst(); assertEquals(0,it.getInt(0)) }
        } finally { db.close() }
    }
    @Test fun startupRepairsKnownWrongSpeciesWithoutDeletingCatch() = runBlocking {
        val db=database()
        try {
            val id=db.dao().addSpecies(Species(name="Barbel",discipline=Discipline.COARSE,scientificName="Anthochaera carunculata",referencePhotoUrl="bird"))
            db.dao().addCatch(Catch(speciesId=id))
            TackleboxRepository(db).repairSpeciesMetadata()
            val species=db.dao().speciesOnce().single()
            assertEquals("Barbus barbus",species.scientificName); assertNull(species.referencePhotoUrl)
            assertEquals(1,db.dao().catches().first().size)
        } finally { db.close() }
    }
    private fun taxon(name:String,common:String,group:String="Actinopterygii",active:Boolean=true) = Taxon(
        name=name,iconicTaxonName=group,isActive=active,preferredCommonName=common,wikipediaSummary=null,defaultPhoto=null)
    @Test fun barbelLookupRejectsBirdAndChoosesExactFish() {
        val response=TaxaResponse(listOf(taxon("Anthochaera carunculata","Red Wattlebird","Aves"),taxon("Barbus barbus","Barbel")))
        assertEquals("Barbus barbus",SpeciesLookup.validatedInfo(response,"Barbel")?.scientificName)
    }
    @Test fun wrongInactiveAndAmbiguousTaxaAreRejected() {
        assertNull(SpeciesLookup.validatedInfo(TaxaResponse(listOf(taxon("Cyprinus carpio","Carp"))),"Barbel"))
        assertNull(SpeciesLookup.validatedInfo(TaxaResponse(listOf(taxon("Barbus barbus","Barbel",active=false))),"Barbel"))
        assertNull(SpeciesLookup.validatedInfo(TaxaResponse(listOf(taxon("Fish a","Custom"),taxon("Fish b","Custom"))),"Custom"))
    }
    @Test fun historicalAndFutureCatchesNeverReceiveCurrentWeather() {
        val now=Instant.parse("2026-09-09T12:00:00Z")
        assertTrue(CapturePolicy.canStampCurrentWeather(now,now))
        assertTrue(CapturePolicy.canStampCurrentWeather(now.minusSeconds(900),now))
        assertFalse(CapturePolicy.canStampCurrentWeather(now.minusSeconds(901),now))
        assertFalse(CapturePolicy.canStampCurrentWeather(now.plusSeconds(1),now))
    }
    @Test fun deniedLocationReturnsNoFabricatedPosition() = runBlocking {
        assertNull(DeviceLocation.current(RuntimeEnvironment.getApplication()))
    }
    private fun session(start:String,end:String?) = SessionRow(FishingSession(startAt=Instant.parse(start),endAt=end?.let(Instant::parse)),null,emptyList())
    @Test fun annualHoursExcludeOtherYearsAndClampBoundarySessions() {
        val sessions=listOf(session("2025-06-01T10:00:00Z","2025-06-01T14:00:00Z"),
            session("2025-12-31T23:00:00Z","2026-01-01T02:00:00Z"),
            session("2026-12-31T23:00:00Z","2027-01-01T02:00:00Z"),
            session("2026-08-01T10:00:00Z",null))
        assertEquals(3L,SeasonMetrics.hours(sessions,2026,ZoneId.of("UTC")))
    }
    @Test fun annualHoursUseSelectedTimeZoneAndIgnoreInvalidDuration() {
        val rows=listOf(session("2026-01-01T00:00:00Z","2026-01-01T02:00:00Z"),session("2026-04-01T12:00:00Z","2026-04-01T10:00:00Z"))
        assertEquals(0L,SeasonMetrics.hours(rows,2026,ZoneId.of("America/Los_Angeles")))
        assertEquals(2L,SeasonMetrics.hours(rows,2025,ZoneId.of("America/Los_Angeles")))
    }
    @Test fun riverStationNetworkFailureIsNotAnEmptySuccessfulResult() = runBlocking {
        val api=object:RiverStationsApi {
            override suspend fun stations(url:String):EAStationsResponse { throw IOException("offline") }
            override suspend fun readings(url:String):EAReadingsResponse { throw AssertionError("must not fetch") }
            override suspend fun usgs(url:String):UsgsResponse { throw AssertionError("must not fetch") }
        }
        val failure=runCatching { Rivers.environmentAgency(52.36,-1.17,api) }.exceptionOrNull()
        assertTrue(failure is IOException)
    }
    @Test fun successfulEmptyStationResponseRemainsAnEmptyResult() = runBlocking {
        val api=object:RiverStationsApi {
            override suspend fun stations(url:String)=EAStationsResponse(emptyList())
            override suspend fun readings(url:String):EAReadingsResponse { throw AssertionError("must not fetch") }
            override suspend fun usgs(url:String):UsgsResponse { throw AssertionError("must not fetch") }
        }
        assertTrue(Rivers.environmentAgency(52.36,-1.17,api).isEmpty())
    }
}
