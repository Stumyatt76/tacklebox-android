/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.tacklebox.app.data.*

@RunWith(AndroidJUnit4::class)
class PortableIdentityMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),TackleboxDatabase::class.java)
    @Test fun upgradeFromFivePreservesJournalAndAssignsStableUniqueIds() {
        val name="tacklebox-portable-migration.db"
        helper.createDatabase(name,5).apply {
            execSQL("INSERT INTO AppSettings(id,unitSystem,activeDisciplines,onboardingComplete,backupEnabled,speciesIdToken,worldTidesKey) VALUES(1,'METRIC','[\"COARSE\"]',1,0,'','')")
            execSQL("INSERT INTO FishingSession(id,waterId,startAt,endAt,notes) VALUES(1,NULL,1757000000000,1757003600000,'Preserve this session')")
            for(id in 1..2)execSQL("INSERT INTO Catch(id,speciesId,weightGrams,lengthCm,returned,photoUri,rig,bait,caughtAt,sessionId,waterId,notes) VALUES($id,NULL,2126.25,45.5,1,'content://qa/photo','Method feeder','Sweetcorn',1757001800000,1,NULL,'Preserve this catch')")
            close()
        }
        val db=helper.runMigrationsAndValidate(name,6,true,MIGRATION_5_6)
        db.query("SELECT portableID,notes,weightGrams,sessionId,photoUri FROM Catch ORDER BY id").use { rows->
            assertEquals(2,rows.count);val ids=mutableSetOf<String>()
            while(rows.moveToNext()) { assertTrue(rows.getString(0).matches(Regex("[a-f0-9]{32}")));ids.add(rows.getString(0));assertEquals("Preserve this catch",rows.getString(1));assertEquals(2126.25,rows.getDouble(2),0.0);assertEquals(1,rows.getInt(3));assertEquals("content://qa/photo",rows.getString(4)) }
            assertEquals(2,ids.size)
        }
        db.query("SELECT freeSessionsStarted FROM AppSettings").use { it.moveToFirst();assertEquals(0,it.getInt(0)) }
        db.query("SELECT isTrialSession,notes FROM FishingSession").use { it.moveToFirst();assertEquals(0,it.getInt(0));assertEquals("Preserve this session",it.getString(1)) }
        db.close()
    }
    @Test fun completeUpgradePathOneToSixValidates() {
        helper.createDatabase("tacklebox-all-migrations.db",1).close()
        helper.runMigrationsAndValidate("tacklebox-all-migrations.db",6,true,MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6).close()
    }
}
