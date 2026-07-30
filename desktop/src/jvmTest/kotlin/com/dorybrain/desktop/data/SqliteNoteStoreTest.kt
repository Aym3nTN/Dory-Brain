package com.dorybrain.desktop.data

import com.dorybrain.shared.model.Category
import com.dorybrain.shared.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqliteNoteStoreTest {

    private fun newStore(): SqliteNoteStore {
        val dir = Files.createTempDirectory("dory-test")
        return SqliteNoteStore(dir.resolve("notes.db"))
    }

    private fun sampleNote(text: String = "buy milk", category: Category = Category.SHOPPING) =
        Note(
            text = text,
            category = category,
            createdAt = 1_700_000_000_000,
            isCategorizing = false,
            isAutoCategorized = true
        )

    @Test
    fun `insert assigns an id and the note is readable back`() = runTest {
        val store = newStore()

        val id = store.insert(sampleNote())
        assertTrue(id > 0, "expected a generated row id")

        val stored = store.getById(id)
        assertNotNull(stored)
        assertEquals("buy milk", stored.text)
        assertEquals(Category.SHOPPING, stored.category)
        assertEquals(1_700_000_000_000, stored.createdAt)
        assertTrue(stored.isAutoCategorized)
    }

    @Test
    fun `observeAll re-emits after a write`() = runTest {
        val store = newStore()
        assertEquals(0, store.observeAll().first().size)

        store.insert(sampleNote())
        assertEquals(1, store.observeAll().first().size)

        store.insert(sampleNote(text = "call dentist", category = Category.HEALTH))
        assertEquals(2, store.observeAll().first().size)
    }

    @Test
    fun `observeAll returns newest first`() = runTest {
        val store = newStore()
        store.insert(sampleNote(text = "older").copy(createdAt = 1_000))
        store.insert(sampleNote(text = "newer").copy(createdAt = 2_000))

        val texts = store.observeAll().first().map { it.text }
        assertEquals(listOf("newer", "older"), texts)
    }

    @Test
    fun `update persists changed fields`() = runTest {
        val store = newStore()
        val id = store.insert(sampleNote())

        val stored = store.getById(id)!!
        store.update(stored.copy(text = "buy oat milk", category = Category.PERSONAL, isAutoCategorized = false))

        val updated = store.getById(id)!!
        assertEquals("buy oat milk", updated.text)
        assertEquals(Category.PERSONAL, updated.category)
        assertEquals(false, updated.isAutoCategorized)
    }

    @Test
    fun `delete removes the row`() = runTest {
        val store = newStore()
        val id = store.insert(sampleNote())

        store.delete(store.getById(id)!!)

        assertNull(store.getById(id))
        assertEquals(0, store.observeAll().first().size)
    }

    @Test
    fun `data survives reopening the same file`() = runTest {
        val dir = Files.createTempDirectory("dory-persist")
        val file = dir.resolve("notes.db")

        SqliteNoteStore(file).insert(sampleNote(text = "remember the milk"))

        val reopened = SqliteNoteStore(file)
        val notes = reopened.observeAll().first()
        assertEquals(1, notes.size)
        assertEquals("remember the milk", notes.single().text)
    }

    @Test
    fun `unknown stored category falls back to OTHER rather than crashing`() = runTest {
        val store = newStore()
        val id = store.insert(sampleNote())

        // Simulate a row written by a future version with a bucket we don't know.
        val field = SqliteNoteStore::class.java.getDeclaredField("connection")
        field.isAccessible = true
        val connection = field.get(store) as java.sql.Connection
        connection.createStatement().use {
            it.executeUpdate("UPDATE notes SET category = 'QUANTUM' WHERE id = $id")
        }

        assertEquals(Category.OTHER, store.getById(id)!!.category)
    }
}
