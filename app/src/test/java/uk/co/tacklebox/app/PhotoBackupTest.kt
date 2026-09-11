/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.co.tacklebox.app.data.*
import java.util.Base64
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class PhotoBackupTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun database()=Room.inMemoryDatabaseBuilder(context,TackleboxDatabase::class.java).allowMainThreadQueries().build()
    private fun fixture():BackupPayload {
        val photo=byteArrayOf(1,2,3,4);val sha=PhotoBackupFormat.digest(photo)
        return BackupPayload(disciplines=listOf("coarse"),
            species=listOf(BackupRecord("species-1",mapOf("name" to "Tench","discipline" to "coarse"))),
            waters=listOf(BackupRecord("water-1",mapOf("name" to "Alder Mere","type" to "lake"))),
            sessions=listOf(BackupRecord("session-1",mapOf("startAt" to "2026-06-01T06:00:00Z","endAt" to "2026-06-01T10:00:00Z","notes" to "Dawn"),mapOf("water" to "water-1"))),
            catches=listOf(BackupRecord("catch-1",mapOf("caughtAt" to "2026-06-01T07:00:00Z","weightGrams" to "2126.25","lengthCm" to "45.5","returned" to "true","notes" to "Reeds","airTempC" to "-2.5"),mapOf("species" to "species-1","water" to "water-1","session" to "session-1"),listOf(sha,sha))),
            media=mapOf(sha to Base64.getEncoder().encodeToString(photo)))
    }
    @Test fun restorePreservesPhotosDecimalsAndRelationships()=runBlocking {
        val db=database()
        try {
            val repo=TackleboxRepository(db);val p=PhotoBackupFormat.decode(PhotoBackupFormat.encode(fixture()))
            assertEquals(4,PhotoBackup.restore(context,repo,p,false))
            val fish=repo.catches.first().single()
            assertEquals("Tench",fish.species?.name);assertEquals("Alder Mere",fish.water?.name)
            assertEquals(2126.25,fish.item.weightGrams!!,0.0);assertEquals(45.5,fish.item.lengthCm!!,0.0)
            assertEquals(2,fish.allPhotoUris.size)
            fish.allPhotoUris.forEach { uri->assertArrayEquals(byteArrayOf(1,2,3,4),context.contentResolver.openInputStream(android.net.Uri.parse(uri))!!.use { it.readBytes() }) }
            assertNull(fish.conditions?.pressureHpa);assertEquals(-2.5,fish.conditions!!.airTempC!!,0.0)
            assertEquals(repo.sessions.first().single().item.id,fish.item.sessionId)
        } finally { db.close() }
    }
    @Test fun repeatKeepsEditsUntilReplacementSelected()=runBlocking {
        val db=database()
        try {
            val repo=TackleboxRepository(db);val p=fixture();PhotoBackup.restore(context,repo,p,false)
            repo.saveCatch(repo.catches.first().single().item.copy(notes="Edited"))
            assertEquals(0,PhotoBackup.restore(context,repo,p,false));assertEquals("Edited",repo.catches.first().single().item.notes)
            PhotoBackup.restore(context,repo,p,true)
            assertEquals("Reeds",repo.catches.first().single().item.notes);assertEquals(2,repo.catches.first().single().allPhotoUris.size)
        } finally { db.close() }
    }
    @Test fun databaseFailureRollsBackAllImportedRecordsAndPhotos()=runBlocking {
        val db=database()
        try {
            val repo=TackleboxRepository(db)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER qa_fail BEFORE INSERT ON `Catch` BEGIN SELECT RAISE(ABORT, 'QA failure'); END")
            val root=java.io.File(context.filesDir,"backup-media");val before=root.list().orEmpty().toSet()
            assertTrue(runCatching { PhotoBackup.restore(context,repo,fixture(),false) }.isFailure)
            assertTrue(repo.species.first().isEmpty());assertTrue(repo.waters.first().isEmpty());assertTrue(repo.sessions.first().isEmpty())
            assertEquals(before,root.list().orEmpty().toSet())
        } finally { db.close() }
    }
    @Test fun damagedPhotoAndMissingRelationshipAreRejected() {
        val p=fixture()
        assertTrue(runCatching { PhotoBackupFormat.validate(p.copy(media=p.media.mapValues { "CQ==" })) }.isFailure)
        assertTrue(runCatching { PhotoBackupFormat.validate(p.copy(catches=p.catches.map { it.copy(references=mapOf("water" to "missing")) })) }.isFailure)
    }
    @Test fun futureVersionAndChecksumAreRejected() {
        val text=PhotoBackupFormat.encode(fixture()).toString(Charsets.UTF_8)
        assertTrue(runCatching { PhotoBackupFormat.decode(text.replace("\"version\":1","\"version\":1.5").toByteArray()) }.isFailure)
        assertTrue(runCatching { PhotoBackupFormat.decode(text.replace("\"sha256\":\"","\"sha256\":\"bad").toByteArray()) }.isFailure)
    }
    @Test fun sessionDatesContainCatches() {
        val now=Instant.ofEpochSecond(100)
        assertNotNull(SessionRules.error(now,now.minusSeconds(1),emptyList(),now))
        assertNotNull(SessionRules.error(now,null,listOf(now.minusSeconds(1)),now))
        assertNull(SessionRules.error(now,null,listOf(now),now))
    }
}
