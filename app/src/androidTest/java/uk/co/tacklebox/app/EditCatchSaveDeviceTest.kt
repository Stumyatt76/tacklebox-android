/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.database.sqlite.SQLiteDatabase
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.co.tacklebox.app.data.Catch
import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.data.Discipline
import uk.co.tacklebox.app.data.Species
import uk.co.tacklebox.app.ui.TackleboxTheme

class EditCatchSaveDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedSaveKeepsDraftAndSuccessfulRetryClosesEditor() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TackleboxApp
        val repository = app.repository
        val item = Catch(speciesId = 1, notes = "Original notes")
        val id = repository.dao.addCatch(item)
        val species = Species(id = 1, name = "Tench", discipline = Discipline.COARSE)
        val state = AppState(loaded = true, species = listOf(species),
            catches = listOf(CatchRow(item.copy(id = id), species, null, null)))
        val database = SQLiteDatabase.openDatabase(app.getDatabasePath("tacklebox.db").path, null, 0)
        val viewModels = ViewModelStore()
        lateinit var navigation: NavHostController
        try {
            database.execSQL("CREATE TRIGGER qa_reject_catch_update BEFORE UPDATE ON Catch " +
                "WHEN OLD.id = $id BEGIN SELECT RAISE(ABORT, 'Simulated storage failure'); END")
            compose.setContent {
                val nav = rememberNavController()
                navigation = nav
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<MainViewModel>(
                    viewModelStoreOwner = object : androidx.lifecycle.ViewModelStoreOwner {
                        override val viewModelStore = viewModels
                    },
                    factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app),
                )
                TackleboxTheme {
                    NavHost(nav, startDestination = "detail") {
                        composable("detail") { Text("Saved catch") }
                        composable("edit") { EditCatch(state, viewModel, id, nav) }
                    }
                    LaunchedEffect(Unit) { nav.navigate("edit") }
                }
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("editNotes"))
            compose.onNodeWithTag("editNotes").performTextReplacement("Keep this draft")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("saveEdit"))
            compose.onNodeWithTag("saveEdit").performScrollTo().performClick()
            compose.waitForIdle()
            compose.waitUntil(10_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Saving…")).fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("editNotes").assertTextContains("Keep this draft")
            compose.runOnIdle { assertEquals("edit", navigation.currentDestination?.route) }
            assertEquals("Original notes", repository.catches.first().first { it.item.id == id }.item.notes)

            database.execSQL("DROP TRIGGER qa_reject_catch_update")
            compose.onNodeWithTag("saveEdit").performScrollTo().performClick()
            compose.waitUntil(10_000) { navigation.currentDestination?.route == "detail" }
            assertEquals("Keep this draft", repository.catches.first().first { it.item.id == id }.item.notes)
        } finally {
            database.execSQL("DROP TRIGGER IF EXISTS qa_reject_catch_update")
            database.close()
            compose.runOnIdle { viewModels.clear() }
            repository.deleteCatch(id)
        }
    }
}
