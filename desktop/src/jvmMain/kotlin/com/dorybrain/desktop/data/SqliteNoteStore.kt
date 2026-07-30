package com.dorybrain.desktop.data

import com.dorybrain.shared.model.Category
import com.dorybrain.shared.model.Note
import com.dorybrain.shared.notes.NoteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

/**
 * SQLite-backed [NoteStore] for desktop, standing in for Room.
 *
 * A single JDBC connection is shared behind a mutex, and writes bump a
 * revision counter that the observe* flows key off — that's what gives the
 * UI the same "re-emits after every change" behaviour Room provides.
 */
class SqliteNoteStore(databaseFile: Path) : NoteStore {

    private val connection: Connection =
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.toAbsolutePath()}")

    private val lock = Mutex()
    private val revision = MutableStateFlow(0L)

    init {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    text TEXT NOT NULL,
                    category TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    isCategorizing INTEGER NOT NULL DEFAULT 0,
                    isAutoCategorized INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
        }
    }

    override fun observeAll(): Flow<List<Note>> =
        revision.map { queryAll() }.flowOn(Dispatchers.IO)

    override fun observeById(id: Long): Flow<Note?> =
        revision.map { getById(id) }.flowOn(Dispatchers.IO)

    override suspend fun insert(note: Note): Long = write { conn ->
        conn.prepareStatement(
            "INSERT INTO notes (text, category, createdAt, isCategorizing, isAutoCategorized) " +
                "VALUES (?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        ).use { statement ->
            statement.setString(1, note.text)
            statement.setString(2, note.category.name)
            statement.setLong(3, note.createdAt)
            statement.setInt(4, if (note.isCategorizing) 1 else 0)
            statement.setInt(5, if (note.isAutoCategorized) 1 else 0)
            statement.executeUpdate()
            statement.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
        }
    }

    override suspend fun update(note: Note) {
        write { conn ->
            conn.prepareStatement(
                "UPDATE notes SET text = ?, category = ?, createdAt = ?, " +
                    "isCategorizing = ?, isAutoCategorized = ? WHERE id = ?"
            ).use { statement ->
                statement.setString(1, note.text)
                statement.setString(2, note.category.name)
                statement.setLong(3, note.createdAt)
                statement.setInt(4, if (note.isCategorizing) 1 else 0)
                statement.setInt(5, if (note.isAutoCategorized) 1 else 0)
                statement.setLong(6, note.id)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun delete(note: Note) {
        write { conn ->
            conn.prepareStatement("DELETE FROM notes WHERE id = ?").use { statement ->
                statement.setLong(1, note.id)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun getById(id: Long): Note? = read { conn ->
        conn.prepareStatement("SELECT * FROM notes WHERE id = ?").use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toNote() else null
            }
        }
    }

    private suspend fun queryAll(): List<Note> = read { conn ->
        conn.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM notes ORDER BY createdAt DESC").use { rows ->
                buildList { while (rows.next()) add(rows.toNote()) }
            }
        }
    }

    private suspend fun <T> read(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) { lock.withLock { block(connection) } }

    /** Same as [read], but nudges observers once the write lands. */
    private suspend fun <T> write(block: (Connection) -> T): T {
        val result = read(block)
        revision.value += 1
        return result
    }

    private fun ResultSet.toNote() = Note(
        id = getLong("id"),
        text = getString("text"),
        category = Category.fromName(getString("category")),
        createdAt = getLong("createdAt"),
        isCategorizing = getInt("isCategorizing") == 1,
        isAutoCategorized = getInt("isAutoCategorized") == 1
    )
}
