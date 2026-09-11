/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.services.*
import java.time.Instant
import java.time.LocalDate

/** The Vault, catch detail, catches list, species record and species-ID rules ported in parity wave 2. */
class ParityWave2Test {
    private fun fish(id:Long, grams:Double?, at:String, species:Species?=Species(id=1,name="Tench",discipline=Discipline.COARSE), water:Water?=null, sessionId:Long?=null) =
        CatchRow(Catch(id=id, speciesId=species?.id, weightGrams=grams, caughtAt=Instant.parse(at), sessionId=sessionId), species, water, null)

    @Test fun `the progression flags every catch that set a new record at the time`() {
        val rows = listOf(fish(1, 2000.0, "2026-03-01T08:00:00Z"), fish(2, 1500.0, "2026-04-01T08:00:00Z"), fish(3, 2500.0, "2026-05-01T08:00:00Z"), fish(4, null, "2026-06-01T08:00:00Z"))
        val progression = SpeciesProgressionRules.progression(rows.shuffled())
        assertEquals(listOf(1L, 2L, 3L), progression.map { it.row.item.id })
        assertEquals(listOf(true, false, true), progression.map { it.isPB })
        assertEquals(listOf(2000.0, 2000.0, 2500.0), SpeciesProgressionRules.recordLine(rows).map { it.second })
        assertTrue(SpeciesProgressionRules.progression(listOf(fish(9, null, "2026-06-01T08:00:00Z"))).isEmpty())
    }

    @Test fun `a catch resolves its water through its session when it has none of its own`() {
        val mere = Water(id=7, name="Alder Mere", type=WaterType.SYNDICATE, region="Oxfordshire")
        val session = SessionRow(FishingSession(id=3, waterId=7), mere, emptyList())
        val state = AppState(true, sessions=listOf(session))
        assertEquals("Alder Mere", state.resolvedWater(fish(1, null, "2026-06-01T08:00:00Z", sessionId=3))?.name)
        val own = Water(id=8, name="River Lea", type=WaterType.RIVER, region="")
        assertEquals("River Lea", state.resolvedWater(fish(2, null, "2026-06-01T08:00:00Z", water=own, sessionId=3))?.name)
        assertNull(state.resolvedWater(fish(3, null, "2026-06-01T08:00:00Z")))
    }

    @Test fun `the share card caption and text read as the iOS card does`() {
        assertEquals("Water not recorded · " + LocalDate.of(2026, 6, 1).pretty(), ShareCards.catchCaption(null, LocalDate.of(2026, 6, 1)))
        assertEquals("Alder Mere · " + LocalDate.of(2026, 6, 1).pretty(), ShareCards.catchCaption("Alder Mere", LocalDate.of(2026, 6, 1)))
        val text = ShareCards.catchText(fish(1, 2126.0, "2026-06-01T07:00:00Z"), "Alder Mere", UnitSystem.METRIC)
        assertEquals("Tench\n2.13 kg\nat Alder Mere\n2026-06-01\n— logged with Tacklebox", text)
    }

    @Test fun `species-ID failures map to the iOS messages`() {
        val http = HttpException(Response.error<Any>(401, "".toResponseBody("text/plain".toMediaType())))
        assertEquals(SpeciesId.UNAUTHORIZED, SpeciesId.describe(http))
        assertEquals(SpeciesId.UNREACHABLE, SpeciesId.describe(HttpException(Response.error<Any>(500, "".toResponseBody("text/plain".toMediaType())))))
        assertEquals(SpeciesId.OFFLINE, SpeciesId.describe(java.net.UnknownHostException("api.inaturalist.org")))
        assertEquals(SpeciesId.UNREACHABLE, SpeciesId.describe(java.net.SocketTimeoutException()))
    }

    @Test fun `suggestions keep the top five with thumbnails and clamped scores`() {
        val result = VisionResult((1..7).map { i -> VisionScore(score=(i*10).toDouble(), taxon=VisionTaxon(name="Sp $i", commonName=if(i==1)"One" else null, defaultPhoto=VisionPhoto("https://x/$i.jpg"))) } + VisionScore(score=50.0, taxon=VisionTaxon(name="")))
        val suggestions = SpeciesId.suggestionsFrom(result)
        assertEquals(5, suggestions.size)
        assertEquals("Sp 7", suggestions.first().scientificName)
        assertEquals(70.0, suggestions.first().score, 0.0)
        assertEquals("https://x/7.jpg", suggestions.first().thumbnailUrl)
        assertEquals(100.0, SpeciesId.suggestionsFrom(VisionResult(listOf(VisionScore(score=140.0, taxon=VisionTaxon(name="Sp"))))).single().score, 0.0)
        assertEquals("One", SpeciesId.suggestionsFrom(VisionResult(listOf(VisionScore(score=1.0, taxon=VisionTaxon(name="Sp 1", commonName="One"))))).single().displayName)
    }

    @Test fun `lengths print in the angler's units`() {
        assertEquals("45 cm", 45.0.length(UnitSystem.METRIC)); assertEquals("17.7 in", 45.0.length(UnitSystem.IMPERIAL))
    }
}
