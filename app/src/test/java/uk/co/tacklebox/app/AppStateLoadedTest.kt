/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The placeholder state must not look like a finished read.
 *
 * `stateIn` needs a value before the flows emit, and that value was a default `AppState` — whose settings say
 * onboarding is incomplete. So an angler who had used the app for months was shown the onboarding flow for as long
 * as the read took: normally a blink, but six seconds after an install, when the Room migration runs first.
 * Tapping through it would have marked onboarding complete and could have seeded sample waters into a full vault.
 *
 * The gate is `loaded`, and it has to default to false — a default that defaulted to true would restore the bug
 * silently, so it is worth a test of its own rather than trusting the field's declaration.
 */
class AppStateLoadedTest {

    @Test fun `the placeholder state is not marked loaded`() {
        assertFalse("stateIn's placeholder must not claim the store has answered", AppState().loaded)
    }

    /** And the placeholder must still look un-onboarded, so the gate is the only thing standing in the way. */
    @Test fun `the placeholder still reports onboarding as incomplete`() {
        assertFalse(AppState().settings.onboardingComplete)
    }

    @Test fun `a state built from real values is marked loaded`() {
        assertTrue(AppState(loaded = true).loaded)
    }
}
