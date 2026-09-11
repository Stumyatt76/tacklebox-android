/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.app.Application
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

/** Repository rules from the 11 September full check: species by name, and what a reset keeps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class SpeciesAndResetTest {
    private fun database() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TackleboxDatabase::class.java)
        .allowMainThreadQueries().build()

    /** #9: a species added from the capture screen belongs to the first active discipline, not always Coarse. */
    @Test fun addedSpeciesTakesTheFirstActiveDiscipline() = runBlocking {
        val db = database()
        try {
            val repo = TackleboxRepository(db)
            val id = repo.addSpecies("Mackerel", listOf("SEA", "GAME"))
            assertEquals(Discipline.SEA, db.dao().speciesOnce().single { it.id == id }.discipline)
            val gudgeon = repo.addSpecies("Gudgeon")
            assertEquals(Discipline.COARSE, db.dao().speciesOnce().single { it.id == gudgeon }.discipline)
        } finally { db.close() }
    }

    /** #21: "tench", "Tench" and " TENCH " are one species; the existing id comes back and nothing is duplicated. */
    @Test fun addingAnExistingNameReusesItCaseInsensitively() = runBlocking {
        val db = database()
        try {
            val repo = TackleboxRepository(db)
            val first = repo.addSpecies("Tench", listOf("COARSE"))
            assertEquals(first, repo.addSpecies("tench", listOf("SEA")))
            assertEquals(first, repo.addSpecies("  TENCH ", listOf("SEA")))
            assertEquals(1, db.dao().speciesOnce().size)
            assertTrue(runCatching { repo.addSpecies("   ") }.isFailure)
        } finally { db.close() }
    }

    /** A known name also gains its canonical scientific name, as the seed catalogue would have given it. */
    @Test fun aKnownSpeciesGetsItsScientificName() = runBlocking {
        val db = database()
        try {
            val id = TackleboxRepository(db).addSpecies("Barbel", listOf("COARSE"))
            assertEquals("Barbus barbus", db.dao().speciesOnce().single { it.id == id }.scientificName)
        } finally { db.close() }
    }

    /** #20: the eleven starter presets come back after "Delete catches, waters & gear"; the angler's own do not. */
    @Test fun deleteAllRestoresTheStarterPresets() = runBlocking {
        val db = database()
        try {
            val repo = TackleboxRepository(db)
            repo.addPreset(TacklePreset(name = "Zig rig", kind = PresetKind.RIG))
            repo.deleteAllUserData()
            val presets = repo.presets.first()
            assertEquals(TackleboxRepository.seedPresets.map { it.name }.toSet(), presets.map { it.name }.toSet())
            assertEquals(11, presets.size)
            assertTrue(presets.none { it.name == "Zig rig" })
        } finally { db.close() }
    }

    /** #1/#2: the app's own photo files go with their rows; anything outside the store is never touched. */
    @Test fun deletingACatchRemovesItsStoredPhotoFilesOnly() = runBlocking {
        val db = database()
        val root = File(RuntimeEnvironment.getApplication().filesDir, "qa-root").apply { mkdirs() }
        val outside = File(RuntimeEnvironment.getApplication().cacheDir, "outside.jpg").apply { writeBytes(byteArrayOf(1)) }
        try {
            val repo = TackleboxRepository(db, root)
            val cover = File(File(root, "photos").apply { mkdirs() }, "cover.jpg").apply { writeBytes(byteArrayOf(1)) }
            val extra = File(File(root, "photos"), "extra.jpg").apply { writeBytes(byteArrayOf(2)) }
            val uris = listOf(cover, extra, outside).map { android.net.Uri.fromFile(it).toString() }
            val id = repo.addCatch(Catch(photoUri = uris[0]), null, uris)
            assertTrue(cover.exists() && extra.exists())
            repo.savePhotos(id, listOf(uris[0], uris[2]))
            assertTrue("a photo dropped from the catch is deleted", !extra.exists())
            assertTrue(cover.exists())
            repo.deleteCatch(id)
            assertTrue(!cover.exists())
            assertTrue("a file outside the store survives", outside.exists())
            assertTrue(repo.catches.first().isEmpty())
        } finally { db.close(); root.deleteRecursively(); outside.delete() }
    }

    @Test fun storeOwnershipIsLimitedToFilesUnderTheRoot() {
        val root = File(RuntimeEnvironment.getApplication().filesDir, "photos")
        assertTrue(PhotoStore.isOwned(android.net.Uri.fromFile(File(root, "a.jpg")).toString(), root))
        assertFalse(PhotoStore.isOwned(android.net.Uri.fromFile(File(root.parentFile, "a.jpg")).toString(), root))
        assertFalse(PhotoStore.isOwned("content://media/external/images/1", root))
    }
}
