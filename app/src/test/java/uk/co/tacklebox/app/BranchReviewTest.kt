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
import uk.co.tacklebox.app.services.BiteWindow
import java.io.File
import java.time.LocalTime

/** The fixes from the branch review of the QA commits: photo file ownership, routes and the live card. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class BranchReviewTest {
    private fun database() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TackleboxDatabase::class.java).allowMainThreadQueries().build()
    private fun root() = File(RuntimeEnvironment.getApplication().filesDir, "review-root-" + System.nanoTime()).apply { File(this, "photos").mkdirs(); File(this, "backup-media").mkdirs() }
    private fun file(root:File, dir:String, name:String) = File(File(root, dir), name).apply { writeBytes(byteArrayOf(1)) }
    private fun uri(f:File) = Uri.fromFile(f).toString()

    /** H2: a file two catches share survives the first delete and goes with the last reference. */
    @Test fun `a photo file shared by two catches is only deleted with its last reference`() = runBlocking {
        val db = database(); val root = root()
        try {
            val repo = TackleboxRepository(db, root)
            val shared = file(root, "backup-media", "shared.jpg")
            val a = repo.addCatch(Catch(photoUri = uri(shared)), null, listOf(uri(shared)))
            val b = repo.addCatch(Catch(photoUri = uri(shared)), null, listOf(uri(shared)))
            repo.deleteCatch(a)
            assertTrue("still named by catch b", shared.exists())
            repo.deleteCatch(b)
            assertFalse(shared.exists())
        } finally { db.close(); root.deleteRecursively() }
    }

    /** H3: inside a transaction nothing is deleted; the caller gets the orphans to reclaim after the commit. */
    @Test fun `savePhotosInTransaction reports orphans without touching the disk`() = runBlocking {
        val db = database(); val root = root()
        try {
            val repo = TackleboxRepository(db, root)
            val cover = file(root, "photos", "cover.jpg"); val extra = file(root, "photos", "extra.jpg")
            val id = repo.addCatch(Catch(photoUri = uri(cover)), null, listOf(uri(cover), uri(extra)))
            val orphans = repo.transaction { repo.savePhotosInTransaction(id, listOf(uri(cover))) }
            assertEquals(setOf(uri(extra)), orphans)
            assertTrue("deletion is the caller's, after the commit", extra.exists())
            repo.deletePhotoFiles(orphans)
            assertFalse(extra.exists())
        } finally { db.close(); root.deleteRecursively() }
    }

    /** M3: a replaced cover is reclaimed — the old cover is read before the row is rewritten. */
    @Test fun `editing a catch reclaims the cover it replaced`() = runBlocking {
        val db = database(); val root = root()
        try {
            val repo = TackleboxRepository(db, root)
            val old = file(root, "photos", "old.jpg"); val new = file(root, "photos", "new.jpg")
            val id = repo.addCatch(Catch(photoUri = uri(old)), null, listOf(uri(old)))
            val row = repo.catches.first().single { it.item.id == id }.item
            repo.updateCatch(row.copy(photoUri = uri(new)), listOf(uri(new)))
            assertFalse("the replaced cover is gone", old.exists()); assertTrue(new.exists())
            assertEquals(uri(new), repo.catches.first().single().item.photoUri)
        } finally { db.close(); root.deleteRecursively() }
    }

    /** L6/H2: only files under photos/ and backup-media/ are ever deleted, whatever a row happens to name. */
    @Test fun `deletion never reaches outside the two photo directories`() = runBlocking {
        val db = database(); val root = root()
        try {
            val repo = TackleboxRepository(db, root)
            val elsewhere = File(root, "state.bin").apply { writeBytes(byteArrayOf(1)) }
            val id = repo.addCatch(Catch(photoUri = uri(elsewhere)), null, listOf(uri(elsewhere)))
            repo.deleteCatch(id)
            assertTrue(elsewhere.exists())
            assertFalse(PhotoStore.isAppPhoto(uri(elsewhere), root)); assertTrue(PhotoStore.isAppPhoto(uri(File(File(root, "photos"), "x.jpg")), root))
        } finally { db.close(); root.deleteRecursively() }
    }

    /** M4: the sweep removes old files nothing names and leaves fresh ones and referenced ones. */
    @Test fun `the orphan sweep keeps referenced and fresh files`() {
        val context = RuntimeEnvironment.getApplication()
        val dir = PhotoStore.directory(context)
        val now = System.currentTimeMillis()
        val oldOrphan = File(dir, "old-orphan.jpg").apply { writeBytes(byteArrayOf(1)); setLastModified(now - 48L * 3600 * 1000) }
        val oldKept = File(dir, "old-kept.jpg").apply { writeBytes(byteArrayOf(1)); setLastModified(now - 48L * 3600 * 1000) }
        val fresh = File(dir, "fresh.jpg").apply { writeBytes(byteArrayOf(1)); setLastModified(now) }
        try {
            assertEquals(1, PhotoStore.sweep(context, setOf(uri(oldKept)), now = now))
            assertFalse(oldOrphan.exists()); assertTrue(oldKept.exists()); assertTrue(fresh.exists())
        } finally { listOf(oldOrphan, oldKept, fresh).forEach { it.delete() } }
    }

    /** M1: only known destinations are navigated to; `adb --es tacklebox.route bogus` must not crash the launch. */
    @Test fun `a requested route is accepted only from the whitelist`() {
        assertEquals("sessions", MainActivity.requestedRoute("sessions"))
        assertEquals("data-services", MainActivity.requestedRoute("data-services"))
        assertNull(MainActivity.requestedRoute("bogus")); assertNull(MainActivity.requestedRoute(null))
    }

    /** L1: inside a window that started before midnight the card says "Happening now", not "in 23h". */
    @Test fun `a wrapping window in progress reads as happening now`() {
        val overnight = BiteWindow("Moon underfoot", LocalTime.of(23, 30), LocalTime.of(1, 30), true)
        assertEquals("Happening now", windowCountdown(LocalTime.of(0, 30), overnight))
        assertEquals("in 3h 0m", windowCountdown(LocalTime.of(20, 30), overnight))
    }

    /** M5: a grandfathering once granted is kept even when the install time no longer qualifies. */
    @Test fun `a remembered grandfathering survives a reinstall`() {
        assertTrue(UnlimitedStore.grandfathered(remembered = true, computedNow = false))
        assertTrue(UnlimitedStore.grandfathered(remembered = false, computedNow = true))
        assertFalse(UnlimitedStore.grandfathered(remembered = false, computedNow = false))
    }
}
