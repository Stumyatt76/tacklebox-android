/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.co.tacklebox.app.data.PresetKind
import uk.co.tacklebox.app.data.TackleboxRepository

/**
 * The seeded tackle presets (TB-P-02).
 *
 * `seed()` never called `addPreset`, so a fresh Android install had none — and because `PresetField` renders its
 * chips only when presets exist, the rig and bait fields silently degraded to bare text entry while iOS offered a
 * chip grid. The list is asserted verbatim rather than by count: the point of the fix is that both apps open with
 * the *same* presets in the *same* order, and a count would pass while they drifted apart.
 */
class SeedPresetsTest {
    private val presets = TackleboxRepository.seedPresets

    @Test fun `seeds the same eleven presets as iOS, in order`() {
        assertEquals(
            listOf("Ronnie rig", "Hair rig", "Method feeder", "Waggler", "Ledger"),
            presets.filter { it.kind == PresetKind.RIG }.map { it.name })
        assertEquals(
            listOf("Boilie", "Sweetcorn", "Maggots", "Pellets", "Bread", "Worm"),
            presets.filter { it.kind == PresetKind.BAIT }.map { it.name })
    }

    @Test fun `every seeded preset has a kind and a name`() {
        assertEquals(11, presets.size)
        assertEquals(emptyList<String>(), presets.filter { it.name.isBlank() }.map { it.name })
        // Ids are left at 0 so Room assigns them; a hardcoded id would collide on insert.
        assertEquals(emptyList<Long>(), presets.map { it.id }.filter { it != 0L })
    }
}
