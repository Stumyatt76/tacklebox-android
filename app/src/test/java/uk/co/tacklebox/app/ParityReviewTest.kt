/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.app.Application
import android.net.Uri
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.co.tacklebox.app.data.*
import java.io.File
import java.time.Instant
import java.util.Base64

/** The fixes from the review of the parity waves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class ParityReviewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun database() = Room.inMemoryDatabaseBuilder(context, TackleboxDatabase::class.java).allowMainThreadQueries().build()

    /** H1: a session started from the free-session sheet begins at the catch's time, so the fish joins it. */
    @Test fun `a session started for a back-dated catch begins at the catch's time and takes the catch`() = runBlocking {
        val db = database()
        try {
            val repo = TackleboxRepository(db)
            val caughtAt = Instant.now().minusSeconds(3 * 60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
            repo.startSession(null, unlimited = false, startAt = caughtAt)
            val open = repo.openSession()!!
            assertEquals(caughtAt, open.startAt)
            assertTrue("the catch is not before the session it started", !caughtAt.isBefore(open.startAt))
            assertEquals(1, repo.settings.first().freeSessionsStarted)
            assertNull("the notes-only rules still hold", SessionRules.error(open.startAt, null, listOf(caughtAt)))
        } finally { db.close() }
    }

    @Test fun `a session can never start in the future`() = runBlocking {
        val db = database()
        try {
            val repo = TackleboxRepository(db)
            val before = Instant.now()
            repo.startSession(null, unlimited = true, startAt = Instant.now().plusSeconds(3600))
            assertFalse(repo.openSession()!!.startAt.isAfter(Instant.now())); assertFalse(repo.openSession()!!.startAt.isBefore(before))
        } finally { db.close() }
    }

    /** H2: a Keystore that refuses the write keeps the value in Room; a confirmed one blanks the column. */
    @Test fun `migration keeps a secret in Room when the vault refuses it`() = runBlocking {
        val db = database()
        try {
            val repo = TackleboxRepository(db)
            repo.saveSettings(AppSettings(speciesIdToken = "tok", worldTidesKey = "wt"))
            val refusing = object : SecretVault {
                private val values = HashMap<String, String>()
                override fun read(name: String) = values[name]
                override fun write(name: String, value: String?) { if (name == Secrets.WORLD_TIDES) throw IllegalStateException("keystore wedged"); if (value == null) values.remove(name) else values[name] = value }
            }
            val secrets = Secrets(refusing)
            secrets.migrateFrom(repo)
            val settings = repo.settings.first()
            assertEquals("the token reached the vault and left Room", "", settings.speciesIdToken); assertEquals("tok", secrets.speciesIdToken)
            assertEquals("the key stays in Room for the next launch", "wt", settings.worldTidesKey); assertEquals("", secrets.worldTidesKey)
        } finally { db.close() }
    }

    /** M2: the toast and its saver round-trip, and only a genuine record produces one. */
    @Test fun `the PB toast is produced only for a record and survives saving`() {
        assertNull(PBCelebration.forSave("Tench", 2000.0, null, UnitSystem.METRIC))
        assertNull(PBCelebration.forSave("Tench", 2000.0, 2000.0, UnitSystem.METRIC))
        val toast = PBCelebration.forSave("Tench", 2500.0, 2000.0, UnitSystem.METRIC)!!
        assertEquals("2.50 kg", toast.weight); assertEquals("500 g", toast.margin)
        assertEquals(toast, PBCelebrationSaver.restore(listOf<String?>("Tench", "2.50 kg", "500 g")))
        assertEquals(PBCelebration("Tench", "2.50 kg", null), PBCelebrationSaver.restore(listOf<String?>("Tench", "2.50 kg", null)))
        assertNull(PBCelebrationSaver.restore(emptyList<String?>()))
    }

    /** M1: restoring the same backup twice with Replace reclaims the first restore's cover file. */
    @Test fun `a replace-restore reclaims the previous cover`() = runBlocking {
        val db = database()
        try {
            val repo = TackleboxRepository(db, context.filesDir)
            val photo = byteArrayOf(1, 2, 3, 4); val sha = PhotoBackupFormat.digest(photo)
            val payload = BackupPayload(disciplines = listOf("coarse"),
                species = listOf(BackupRecord("species-1", mapOf("name" to "Tench", "discipline" to "coarse"))),
                catches = listOf(BackupRecord("catch-1", mapOf("caughtAt" to "2026-06-01T07:00:00Z", "returned" to "true"), mapOf("species" to "species-1"), listOf(sha))),
                media = mapOf(sha to Base64.getEncoder().encodeToString(photo)))
            PhotoBackup.restore(context, repo, payload, false)
            val first = repo.catches.first().single().item.photoUri!!
            PhotoBackup.restore(context, repo, payload, true)
            val second = repo.catches.first().single().item.photoUri!!
            assertNotEquals(first, second)
            assertFalse("the replaced cover is gone", File(Uri.parse(first).path!!).exists())
            assertTrue(File(Uri.parse(second).path!!).exists())
        } finally { db.close(); File(context.filesDir, "backup-media").deleteRecursively() }
    }

    /** M4: the delete-session copy, verbatim from iOS. */
    @Test fun `the delete-session copy matches iOS in the singular and the plural`() {
        assertEquals("Its 1 catch is kept in your vault, keeps its water and simply loses this session. The free-session allowance is not returned. This cannot be undone.", deleteSessionMessage(1))
        assertEquals("Its 3 catches are kept in your vault, keep their water and simply lose this session. The free-session allowance is not returned. This cannot be undone.", deleteSessionMessage(3))
    }
}
