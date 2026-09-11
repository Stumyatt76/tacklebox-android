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
import uk.co.tacklebox.app.data.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class CrossPlatformBackupTest {
    @Test fun actualIosExportRestoresAndReexportsWithoutLoss()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val payload=instrumentation.context.assets.open("native-ios.tacklebox").use { PhotoBackupFormat.decode(it.readBytes()) }
        val db=Room.inMemoryDatabaseBuilder(context,TackleboxDatabase::class.java).build()
        try {
            val repo=TackleboxRepository(db)
            assertEquals(4,PhotoBackup.restore(context,repo,payload,false))
            assertEquals(0,PhotoBackup.restore(context,repo,payload,false))
            val fish=repo.catches.first().single()
            assertEquals("Tench",fish.species?.name);assertEquals("Alder Mere",fish.water?.name)
            assertEquals(2126.25,fish.item.weightGrams!!,0.0);assertEquals(45.5,fish.item.lengthCm!!,0.0)
            assertEquals(2,fish.allPhotoUris.size);assertNull(fish.conditions?.pressureHpa)
            val state=AppState(loaded=true,settings=repo.settings.first(),species=repo.species.first(),waters=repo.waters.first(),sessions=repo.sessions.first(),catches=repo.catches.first(),gear=repo.gear.first(),presets=repo.presets.first())
            val exported=PhotoBackup.payload(context,state)
            assertEquals(payload.media,exported.media)
            assertEquals(payload.catches.single().references,exported.catches.single().references)
            assertEquals(payload.catches.single().id,exported.catches.single().id)
            assertEquals(0,state.settings.freeSessionsStarted)
            assertFalse(state.sessions.single().item.isTrialSession)
            val directory=File(context.filesDir,"Tacklebox-QA").apply { mkdirs() }
            File(directory,"native-android.tacklebox").writeBytes(PhotoBackupFormat.encode(exported))
        } finally { db.close() }
    }
}
