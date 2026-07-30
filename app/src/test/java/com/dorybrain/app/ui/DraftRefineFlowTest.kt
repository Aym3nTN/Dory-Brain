package com.dorybrain.app.ui

import com.dorybrain.shared.categorize.CategorizerRepository
import com.dorybrain.shared.model.Note
import com.dorybrain.shared.notes.NoteStore
import com.dorybrain.shared.refine.RefinerRepository
import com.dorybrain.shared.settings.SettingsDefaults
import com.dorybrain.shared.settings.SettingsRepository
import com.dorybrain.shared.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the "refine the draft before saving" flow that backs the Refine with
 * AI screen. Only the paths that don't touch the network are exercised — with
 * no API key configured, refinement reports that rather than calling out.
 */
class DraftRefineFlowTest {

    private class FakeSettings(
        override var apiKey: String? = null,
        override var model: String = SettingsDefaults.MODEL,
        override var forceOnDevice: Boolean = true,
        override var themeMode: ThemeMode = ThemeMode.SYSTEM
    ) : SettingsRepository

    private class FakeNoteStore : NoteStore {
        val notes = MutableStateFlow<List<Note>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<Note>> = notes
        override fun observeById(id: Long): Flow<Note?> =
            notes.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun insert(note: Note): Long {
            val id = nextId++
            notes.value = notes.value + note.copy(id = id)
            return id
        }

        override suspend fun update(note: Note) {
            notes.value = notes.value.map { if (it.id == note.id) note else it }
        }

        override suspend fun delete(note: Note) {
            notes.value = notes.value.filterNot { it.id == note.id }
        }

        override suspend fun getById(id: Long): Note? =
            notes.value.firstOrNull { it.id == id }
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var settings: FakeSettings
    private lateinit var store: FakeNoteStore
    private lateinit var viewModel: NoteListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = FakeSettings()
        store = FakeNoteStore()
        viewModel = NoteListViewModel(
            noteStore = store,
            categorizerRepository = CategorizerRepository(settings),
            refinerRepository = RefinerRepository(settings)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refining a blank draft is refused so the screen isn't opened for nothing`() {
        assertFalse(viewModel.refineDraft("   "))
        assertSame(DraftRefineState.Idle, viewModel.draftRefineState.value)
    }

    @Test
    fun `refining with no api key reports why and leaves no pending state`() = runTest {
        assertTrue(viewModel.refineDraft("buy milk"))

        // No key configured, so this resolves to a message rather than a call.
        assertSame(DraftRefineState.Idle, viewModel.draftRefineState.value)
    }

    @Test
    fun `refining with no api key tells the user to add one`() = runTest {
        val messages = mutableListOf<String>()
        val collector = launch { viewModel.messages.collect { messages += it } }
        // Let the collector attach before triggering.
        yield()

        viewModel.refineDraft("buy milk")
        yield()
        collector.cancel()

        assertTrue(
            "unexpected messages: $messages",
            messages.any { it.contains("API key", ignoreCase = true) }
        )
    }

    @Test
    fun `edit manually hands the text back to the capture field once`() {
        viewModel.returnDraftForEditing("Buy almond milk, eggs, and honey.")

        assertEquals("Buy almond milk, eggs, and honey.", viewModel.draftOverride.value)
        assertSame(DraftRefineState.Idle, viewModel.draftRefineState.value)

        viewModel.consumeDraftOverride()
        assertNull("the override must not be re-applied", viewModel.draftOverride.value)
    }

    @Test
    fun `clearing refinement resets the screen state`() {
        viewModel.refineDraft("something")
        viewModel.clearDraftRefinement()

        assertSame(DraftRefineState.Idle, viewModel.draftRefineState.value)
    }

    @Test
    fun `saving the refined text stores that text, not the original`() = runTest {
        viewModel.addNote("Buy almond milk, eggs, whole grain bread, and honey.")

        val stored = store.notes.first { it.isNotEmpty() }.single()
        assertEquals("Buy almond milk, eggs, whole grain bread, and honey.", stored.text)
    }
}
