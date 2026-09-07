/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.tacklebox.app.data.MIGRATION_1_2
import uk.co.tacklebox.app.data.TackleboxDatabase

/**
 * The first migration test in this repo.
 *
 * Room was on version 1 with `exportSchema = false` and no migrations, so the first entity change would have shipped
 * a crash-on-upgrade to every existing tester, and no migration test was even possible. Schema export is on now, and
 * adding `Catch.notes` is the change that proves the path works.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TackleboxDatabase::class.java
    )

    private val name = "migration-test.db"

    @Test
    fun migrate1To2_keepsExistingCatchesAndDefaultsTheNote() {
        // A version-1 database holding a catch, exactly as a current tester's device would.
        helper.createDatabase(name, 1).apply {
            execSQL(
                """INSERT INTO Catch (id, speciesId, weightGrams, lengthCm, returned, photoUri, rig, bait, caughtAt, sessionId, waterId)
                   VALUES (1, 7, 2400.0, NULL, 1, NULL, 'Method feeder', 'Sweetcorn', 1757000000000, NULL, 2)"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)

        db.query("SELECT id, weightGrams, bait, waterId, notes FROM Catch").use { cursor ->
            assertEquals("the existing catch must survive the upgrade", 1, cursor.count)
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(0))
            assertEquals(2400.0, cursor.getDouble(1), 0.01)
            assertEquals("Sweetcorn", cursor.getString(2))
            assertEquals(2L, cursor.getLong(3))
            // NOT NULL with a default, so old rows arrive valid without a backfill pass.
            assertEquals("", cursor.getString(4))
        }
    }

    @Test
    fun migrate1To2_leavesOtherTablesAlone() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO Water (id, name, type, region, disciplines, swimNotes) VALUES (1, 'Alder Mere', 'LAKE', 'Oxfordshire', 'COARSE', 'Reeds at dusk.')")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)

        db.query("SELECT name, swimNotes FROM Water").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Alder Mere", cursor.getString(0))
            assertEquals("Reeds at dusk.", cursor.getString(1))
        }
    }
}
