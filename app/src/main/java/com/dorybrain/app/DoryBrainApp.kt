package com.dorybrain.app

import android.app.Application
import com.dorybrain.app.data.AppDatabase
import com.dorybrain.app.data.RoomNoteStore
import com.dorybrain.app.data.SettingsStore
import com.dorybrain.shared.categorize.CategorizerRepository
import com.dorybrain.shared.notes.NoteStore
import com.dorybrain.shared.nvidia.NvidiaConnectionTester
import com.dorybrain.shared.refine.RefinerRepository

class DoryBrainApp : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val noteStore: NoteStore by lazy { RoomNoteStore(database.noteDao()) }
    val categorizerRepository: CategorizerRepository by lazy { CategorizerRepository(settingsStore) }
    val refinerRepository: RefinerRepository by lazy { RefinerRepository(settingsStore) }
    val connectionTester: NvidiaConnectionTester by lazy { NvidiaConnectionTester(settingsStore) }
}
