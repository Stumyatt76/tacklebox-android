/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.tacklebox.app.data.*

/**
 * Multiple photos per catch. One photo and no gallery was the biggest thing the app under-used — anglers take
 * three or four shots of a good fish.
 *
 * `Catch.photoUri` stays the cover and the extras live in `CatchPhoto`, so existing records need no backfill and
 * the cover is never stored twice. Kept in step with the iOS design.
 */
class CatchPhotoTest {
    private val carp = Species(id = 1, name = "Common carp", discipline = Discipline.COARSE)

    private fun row(cover: String?, extras: List<CatchPhoto>) =
        CatchRow(Catch(id = 1, speciesId = carp.id, photoUri = cover), carp, null, null, extras)

    @Test fun `a catch with no photo has none`() {
        assertTrue(row(null, emptyList()).allPhotoUris.isEmpty())
    }

    @Test fun `a lone cover reads back as a single photo`() {
        assertEquals(listOf("a"), row("a", emptyList()).allPhotoUris)
    }

    @Test fun `extras follow the cover in their stored order`() {
        val extras = listOf(
            CatchPhoto(id = 2, catchId = 1, uri = "c", order = 1),
            CatchPhoto(id = 1, catchId = 1, uri = "b", order = 0)
        )
        assertEquals(listOf("a", "b", "c"), row("a", extras).allPhotoUris)
    }

    /** Order must come from the column, not from whatever order Room happens to return the rows in. */
    @Test fun `order survives a shuffled relationship`() {
        val extras = listOf(
            CatchPhoto(id = 9, catchId = 1, uri = "d", order = 2),
            CatchPhoto(id = 8, catchId = 1, uri = "b", order = 0),
            CatchPhoto(id = 7, catchId = 1, uri = "c", order = 1)
        )
        assertEquals(listOf("a", "b", "c", "d"), row("a", extras).allPhotoUris)
    }

    /**
     * A catch with extras but no cover should not be possible — savePhotos always writes the first image as the
     * cover — but if it ever happened, the photos are shown rather than hidden. Never lose sight of a user's photo
     * because of an inconsistent row.
     */
    @Test fun `extras still show when the cover is somehow missing`() {
        val extras = listOf(
            CatchPhoto(id = 2, catchId = 1, uri = "c", order = 1),
            CatchPhoto(id = 1, catchId = 1, uri = "b", order = 0)
        )
        assertEquals(listOf("b", "c"), row(null, extras).allPhotoUris)
    }

    @Test fun `the cover is never duplicated among the extras`() {
        val extras = listOf(CatchPhoto(id = 1, catchId = 1, uri = "b", order = 0))
        val all = row("a", extras).allPhotoUris
        assertEquals(all.size, all.distinct().size)
        assertEquals("a", all.first())
    }
}
