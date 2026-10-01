/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.tacklebox.app.data.TackleboxDatabase
import uk.co.tacklebox.app.data.TackleboxRepository

@RunWith(AndroidJUnit4::class)
class SessionNotesReviewTest {
    @Test
    fun delayedNotesSaveMustNotReopenAnEndedSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TackleboxDatabase::class.java).build()
        try {
            val repository = TackleboxRepository(db)
            repository.startSession(null)
            // Simulate a notes submission racing with an explicit end-session action.
            val rowBeforeDebounce = repository.openSession()!!
            repository.stopSession(rowBeforeDebounce.id)
            assertNull("End session must have succeeded", repository.openSession())
            // The notes API must preserve all other columns from the current database row.
            repository.saveSessionNotes(rowBeforeDebounce.id, "Last note before ending")
            assertEquals("Last note before ending", repository.sessions.first().single().item.notes)
            assertNull("Saving notes must not clear the already-saved end time", repository.openSession())
        } finally {
            db.close()
        }
    }
}
