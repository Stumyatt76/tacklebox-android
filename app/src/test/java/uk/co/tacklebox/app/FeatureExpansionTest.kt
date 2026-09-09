package uk.co.tacklebox.app

import android.app.Application
import android.net.Uri
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class FeatureExpansionTest {
    private fun database()=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),TackleboxDatabase::class.java).allowMainThreadQueries().build()
    @Test fun twoSessionsThenThirdBlockedEvenAfterResetOrStaleSettings()=runBlocking {
        val db=database()
        try {
            val repo=TackleboxRepository(db)
            repeat(2) { val id=repo.startSession(null);assertTrue(repo.openSession()!!.isTrialSession);repo.stopSession(id) }
            assertTrue(runCatching { repo.startSession(null) }.isFailure)
            assertEquals(2,repo.sessions.first().size)
            repo.deleteAllUserData();repo.saveSettings(AppSettings(freeSessionsStarted=0))
            assertEquals(2,repo.settings.first().freeSessionsStarted)
            assertTrue(runCatching { repo.startSession(null) }.isFailure)
            repo.startSession(null,true);assertEquals(1,repo.sessions.first().size)
        } finally { db.close() }
    }
    @Test fun failedStartDoesNotSpendAllowance()=runBlocking {
        val db=database()
        try {
            val repo=TackleboxRepository(db)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_session BEFORE INSERT ON FishingSession BEGIN SELECT RAISE(ABORT,'QA'); END")
            assertTrue(runCatching { repo.startSession(null) }.isFailure)
            assertEquals(0,repo.settings.first().freeSessionsStarted)
        } finally { db.close() }
    }
    @Test fun pkceMatchesRfc7636AndRejectsInvalidCallbacks() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",SpeciesOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertEquals("abc",SpeciesOAuth.code(Uri.parse("uk.co.tacklebox.app://oauth/inaturalist?code=abc&state=expected"),"expected"))
        for(text in listOf("uk.co.tacklebox.app://oauth/inaturalist?code=abc&state=wrong","uk.co.tacklebox.app://oauth/inaturalist?code=abc&state=expected&state=expected","uk.co.tacklebox.app://wrong/inaturalist?code=abc&state=expected","uk.co.tacklebox.app://oauth/inaturalist?error=access_denied&state=expected"))assertTrue(runCatching { SpeciesOAuth.code(Uri.parse(text),"expected") }.isFailure)
    }
    @Test fun forecastKeepsMissingValuesMissingAndRejectsMismatchedArrays() {
        val fixture="""{"daily":{"time":["2026-09-09"],"temperature_2m_min":[null],"temperature_2m_max":[18],"wind_speed_10m_max":[12],"precipitation_probability_max":[null]}}"""
        val result=TripForecastService.parse(fixture.toByteArray())
        assertNull(result.days[0].low);assertNull(result.days[0].rainChance);assertEquals(18.0,result.days[0].high!!,0.0)
        assertTrue(runCatching { TripForecastService.parse(fixture.replace("[18]","[]").toByteArray()) }.isFailure)
    }
}
