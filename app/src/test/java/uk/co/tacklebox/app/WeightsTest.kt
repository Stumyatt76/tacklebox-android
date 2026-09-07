/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.tacklebox.app.data.UnitSystem

/** Covers TB-A-13: weight was entered in ounces but displayed as pounds-and-ounces. */
class WeightsTest {
    @Test fun `pounds and ounces convert to grams`() {
        assertEquals(453.59, Weights.fromPoundsAndOunces("1", "0")!!, 0.01)
        assertEquals(28.35, Weights.fromPoundsAndOunces("0", "1")!!, 0.01)
        assertEquals(1814.37, Weights.fromPoundsAndOunces("4", "0")!!, 0.01)
    }

    @Test fun `a blank weight stays unrecorded rather than becoming zero`() {
        assertNull(Weights.fromPoundsAndOunces("", ""))
    }

    @Test fun `a partly filled weight still counts`() {
        assertEquals(226.80, Weights.fromPoundsAndOunces("", "8")!!, 0.01)
        assertEquals(907.18, Weights.fromPoundsAndOunces("2", "")!!, 0.01)
    }

    @Test fun `entering four pounds reads back as four pounds`() {
        // The defect: typing 64 into a field labelled "oz" displayed "4 lb 0 oz", so input and output disagreed.
        val grams = Weights.fromPoundsAndOunces("4", "0")!!
        assertEquals("4 lb 0 oz", grams.weight(UnitSystem.IMPERIAL))
    }

    @Test fun `ounces carry into pounds instead of showing sixteen`() {
        val justUnderAPound = 15.6 * Weights.GRAMS_PER_OUNCE
        assertEquals("1 lb 0 oz", justUnderAPound.weight(UnitSystem.IMPERIAL))
    }

    @Test fun `metric switches to kilograms at a kilo`() {
        assertEquals("397 g", 397.0.weight(UnitSystem.METRIC))
        assertEquals("1.00 kg", 1000.0.weight(UnitSystem.METRIC))
        assertEquals("9.87 kg", 9866.0.weight(UnitSystem.METRIC))
    }

    @Test fun `round trip through pounds and ounces is stable`() {
        val (lb, oz) = Weights.toPoundsAndOunces(1814.37)
        assertEquals(4, lb)
        assertEquals(0, oz)
    }
}
