/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.tacklebox.app.data.*
import java.time.Instant

/**
 * The catch note, and the edit path that finally lets a saved catch be corrected. Both apps were append-only until
 * now: mistype a weight and the only remedy was to delete the catch and enter it again.
 */
class CatchNotesTest {
    private val carp = Species(id = 1, name = "Common carp", discipline = Discipline.COARSE)
    private val tench = Species(id = 2, name = "Tench", discipline = Discipline.COARSE)

    @Test fun `a new catch starts with no note`() {
        assertEquals("", Catch(speciesId = carp.id).notes)
    }

    @Test fun `an edit keeps the same row rather than creating another`() {
        val original = Catch(id = 7, speciesId = tench.id, weightGrams = 2_126.0, rig = "Method feeder")
        val edited = original.copy(
            speciesId = carp.id, weightGrams = 8_108.0, rig = "Ronnie rig", returned = false,
            notes = "Corrected — this was the mirror, not the tench."
        )
        assertEquals("the identity must survive an edit", original.id, edited.id)
        assertEquals(carp.id, edited.speciesId)
        assertEquals(8_108.0, edited.weightGrams!!, 0.01)
        assertEquals("Ronnie rig", edited.rig)
        assertTrue(!edited.returned)
        assertTrue(edited.notes.startsWith("Corrected"))
    }

    @Test fun `an edit leaves untouched fields alone`() {
        val original = Catch(id = 7, speciesId = tench.id, weightGrams = 2_126.0, bait = "Sweetcorn",
                             waterId = 3, sessionId = 4, caughtAt = Instant.ofEpochSecond(1_780_000_000))
        val edited = original.copy(notes = "Just adding the story.")
        assertEquals("Sweetcorn", edited.bait)
        assertEquals(3L, edited.waterId)
        assertEquals(4L, edited.sessionId)
        assertEquals(original.caughtAt, edited.caughtAt)
        assertEquals(original.weightGrams, edited.weightGrams)
    }

    /** Correcting a weight has to move the personal best with it, or the board keeps showing the wrong fish. */
    @Test fun `editing a weight moves the personal best`() {
        fun row(item: Catch) = CatchRow(item, carp, null, null)
        val smaller = Catch(id = 1, speciesId = carp.id, weightGrams = 4_000.0)
        var bigger = Catch(id = 2, speciesId = carp.id, weightGrams = 8_108.0)

        assertEquals(2L, CatchFilter.personalBests(listOf(row(smaller), row(bigger)))["Common carp"]?.item?.id)

        bigger = bigger.copy(weightGrams = 3_000.0)   // it was a mis-entry
        assertEquals(1L, CatchFilter.personalBests(listOf(row(smaller), row(bigger)))["Common carp"]?.item?.id)
    }

    @Test fun `clearing a weight leaves the catch unweighed rather than zero`() {
        val cleared = Catch(id = 1, speciesId = carp.id, weightGrams = 4_000.0).copy(weightGrams = null)
        assertNull(cleared.weightGrams)
    }

    @Test fun `a noted catch still matches the filter on its other fields`() {
        val row = CatchRow(
            Catch(id = 1, speciesId = carp.id, bait = "Sweetcorn", notes = "Bites dried up after the wind turned."),
            carp, null, null
        )
        assertEquals(1, CatchFilter(text = "sweetcorn").apply(listOf(row)).size)
        // The note itself is not searched — it is free text, and matching on it would surprise more than it helps.
        assertTrue(CatchFilter(text = "wind turned").apply(listOf(row)).isEmpty())
    }
}
