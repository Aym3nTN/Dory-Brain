package com.dorybrain.app

import android.app.Application
import com.dorybrain.app.data.AppDatabase
import com.dorybrain.app.data.SettingsStore
import com.dorybrain.app.data.categorize.CategorizerRepository
import com.dorybrain.app.data.refine.RefinerRepository

class DoryBrainApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val categorizerRepository: CategorizerRepository by lazy { CategorizerRepository(settingsStore) }
    val refinerRepository: RefinerRepository by lazy { RefinerRepository(settingsStore) }
}
