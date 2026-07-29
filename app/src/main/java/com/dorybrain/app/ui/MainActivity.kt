package com.dorybrain.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dorybrain.app.DoryBrainApp
import com.dorybrain.app.data.SettingsStore
import com.dorybrain.app.ui.screens.MainScreen
import com.dorybrain.app.ui.screens.SettingsScreen
import com.dorybrain.app.ui.theme.DoryBrainTheme

private const val ROUTE_MAIN = "main"
private const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DoryBrainApp
        val viewModelFactory = NoteListViewModel.Factory(
            noteDao = app.database.noteDao(),
            categorizerRepository = app.categorizerRepository
        )

        setContent {
            DoryBrainTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DoryBrainNavHost(
                        settingsStore = app.settingsStore,
                        viewModel = viewModel(factory = viewModelFactory)
                    )
                }
            }
        }
    }
}

@Composable
private fun DoryBrainNavHost(
    settingsStore: SettingsStore,
    viewModel: NoteListViewModel,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = ROUTE_MAIN) {
        composable(ROUTE_MAIN) {
            MainScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                settingsStore = settingsStore,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
