/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.co.tacklebox.app.data.TackleboxRepository

/**
 * The sample waters offered on the last onboarding page (TB-P-06, TB-P-03).
 *
 * Both apps now ask the same question there, so both must answer it the same way. Asserted by name rather than by
 * count, for the same reason as the presets: a count keeps passing while the two platforms drift apart, and these
 * names appear in the committed App Store screenshots.
 */
class SampleWatersTest {
    private val waters = TackleboxRepository.sampleWaters

    @Test fun `offers the same two waters as iOS, in order`() {
        assertEquals(listOf("Alder Mere", "River Lea"), waters.map { it.name })
        assertEquals(listOf("Oxfordshire", "Hertfordshire"), waters.map { it.region })
    }

    @Test fun `sample waters carry swim notes and no assigned ids`() {
        assertEquals(emptyList<String>(), waters.filter { it.swimNotes.isBlank() }.map { it.name })
        // Ids stay 0 so Room assigns them; a hardcoded id would collide on insert.
        assertEquals(emptyList<Long>(), waters.map { it.id }.filter { it != 0L })
    }
}
