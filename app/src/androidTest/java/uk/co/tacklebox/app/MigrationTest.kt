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
import uk.co.tacklebox.app.data.MIGRATION_2_3
import uk.co.tacklebox.app.data.MIGRATION_3_4
import uk.co.tacklebox.app.data.MIGRATION_4_5
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

    /**
     * Extra photos. An existing catch keeps its single `photoUri` as the cover and simply has no rows in the new
     * table, so there is nothing to backfill and no chance of losing an image.
     */
    @Test
    fun migrate2To3_addsThePhotoTableAndKeepsTheExistingCover() {
        helper.createDatabase(name, 1).apply {
            execSQL(
                """INSERT INTO Catch (id, speciesId, weightGrams, lengthCm, returned, photoUri, rig, bait, caughtAt, sessionId, waterId)
                   VALUES (1, 7, 2400.0, NULL, 1, 'content://photos/42', NULL, NULL, 1757000000000, NULL, NULL)"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        db.query("SELECT photoUri FROM Catch").use { cursor ->
            cursor.moveToFirst()
            assertEquals("the cover photo must survive", "content://photos/42", cursor.getString(0))
        }
        db.query("SELECT count(*) FROM CatchPhoto").use { cursor ->
            cursor.moveToFirst()
            assertEquals("an upgraded catch starts with no extras", 0, cursor.getInt(0))
        }
    }

    /**
     * `WaterType.SEA` was replaced by iOS's `SHORE` and `BOAT` (TB-P-14). Room stores an enum by name, so a water
     * a tester had saved as a sea mark would no longer convert on read — the column is unchanged, the value in it
     * is not. Everything else about the water has to survive untouched.
     */
    @Test
    fun migrate3To4_rewritesSeaWatersAsShoreAndLeavesTheRestAlone() {
        helper.createDatabase(name, 1).apply {
            execSQL("""INSERT INTO Water (id, name, type, region, disciplines, swimNotes)
                       VALUES (1, 'Chesil Beach', 'SEA', 'Dorset', 'SEA', 'Fish the far end after dark.')""")
            execSQL("""INSERT INTO Water (id, name, type, region, disciplines, swimNotes)
                       VALUES (2, 'Alder Mere', 'LAKE', 'Oxfordshire', 'COARSE', '')""")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        db.query("SELECT name, type, region, swimNotes FROM Water ORDER BY id").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals("Chesil Beach", cursor.getString(0))
            assertEquals("a sea mark becomes a shore mark", "SHORE", cursor.getString(1))
            assertEquals("Dorset", cursor.getString(2))
            assertEquals("the swim note must survive", "Fish the far end after dark.", cursor.getString(3))
            cursor.moveToNext()
            assertEquals("every other water is untouched", "LAKE", cursor.getString(1))
        }
    }

    /**
     * The optional WorldTides key, which unlocks tide predictions outside the contiguous US. Additive with a
     * default, so nothing needs backfilling — but every other setting has to come through untouched, because a
     * tester's units and species-ID token live in the same row.
     */
    @Test
    fun migrate4To5_addsTheTideKeyAndKeepsEverySetting() {
        helper.createDatabase(name, 1).apply {
            execSQL("""INSERT INTO AppSettings (id, unitSystem, activeDisciplines, onboardingComplete, backupEnabled, speciesIdToken)
                       VALUES (1, 'IMPERIAL', 'COARSE|SEA', 1, 0, 'inat-token-abc')""")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        db.query("SELECT unitSystem, speciesIdToken, onboardingComplete, worldTidesKey FROM AppSettings").use { cursor ->
            cursor.moveToFirst()
            assertEquals("IMPERIAL", cursor.getString(0))
            assertEquals("the species token must survive", "inat-token-abc", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals("a new install has no tide key", "", cursor.getString(3))
        }
    }

    /** Straight from a version-1 install, skipping no steps. */
    @Test
    fun migrate1To3_runsBothStepsInSequence() {
        helper.createDatabase(name, 1).apply {
            execSQL(
                """INSERT INTO Catch (id, speciesId, weightGrams, lengthCm, returned, photoUri, rig, bait, caughtAt, sessionId, waterId)
                   VALUES (1, 7, 2400.0, NULL, 1, NULL, 'Method feeder', 'Sweetcorn', 1757000000000, NULL, 2)"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        db.query("SELECT bait, notes FROM Catch").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Sweetcorn", cursor.getString(0))
            assertEquals("", cursor.getString(1))
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
