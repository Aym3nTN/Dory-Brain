package com.dorybrain.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dorybrain.app.DoryBrainApp
import com.dorybrain.app.ui.screens.ComposeThoughtScreen
import com.dorybrain.app.ui.screens.HomeScreen
import com.dorybrain.app.ui.screens.NoteDetailScreen
import com.dorybrain.app.ui.screens.NotesScreen
import com.dorybrain.app.ui.screens.RefineDraftScreen
import com.dorybrain.app.ui.screens.SettingsScreen
import com.dorybrain.app.ui.theme.DoryBrainAndroidTheme

private object Routes {
    const val HOME = "home"
    const val NOTES = "notes"
    const val SETTINGS = "settings"
    const val COMPOSE = "compose?dictate={dictate}"
    const val REFINE_DRAFT = "refineDraft"
    const val DETAIL = "note/{noteId}"

    fun compose(startDictation: Boolean = false) = "compose?dictate=$startDictation"
    fun detail(noteId: Long) = "note/$noteId"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Filled.Home),
    BottomTab(Routes.NOTES, "Notes", Icons.Outlined.Description),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DoryBrainApp
        val noteFactory = NoteListViewModel.Factory(
            noteStore = app.noteStore,
            categorizerRepository = app.categorizerRepository,
            refinerRepository = app.refinerRepository
        )
        val settingsFactory = SettingsViewModel.Factory(
            settings = app.settingsStore,
            connectionTester = app.connectionTester
        )

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)
            val themeMode by settingsViewModel.themeMode.collectAsState()

            DoryBrainAndroidTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DoryBrainApp(
                        noteViewModel = viewModel(factory = noteFactory),
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun DoryBrainApp(
    noteViewModel: NoteListViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                BottomBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(160)) }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = noteViewModel,
                    onCompose = { navController.navigate(Routes.compose()) },
                    onDictate = { navController.navigate(Routes.compose(startDictation = true)) },
                    onOpenBucket = { category ->
                        noteViewModel.setCategoryFilter(category)
                        navController.navigate(Routes.NOTES)
                    }
                )
            }

            composable(Routes.NOTES) {
                NotesScreen(
                    viewModel = noteViewModel,
                    onOpenNote = { noteId -> navController.navigate(Routes.detail(noteId)) },
                    onCompose = { navController.navigate(Routes.compose()) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = settingsViewModel)
            }

            composable(
                route = Routes.COMPOSE,
                arguments = listOf(
                    navArgument("dictate") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                ComposeThoughtScreen(
                    viewModel = noteViewModel,
                    startDictation = entry.arguments?.getBoolean("dictate") ?: false,
                    onDone = { navController.popBackStack() },
                    onRefine = { navController.navigate(Routes.REFINE_DRAFT) }
                )
            }

            composable(Routes.REFINE_DRAFT) {
                RefineDraftScreen(
                    viewModel = noteViewModel,
                    // Saving hands back to the compose screen, which is
                    // already watching for the categorized result and pops
                    // itself once the bucket has been shown.
                    onSaved = { navController.popBackStack() },
                    onEditManually = { navController.popBackStack() },
                    onBack = {
                        noteViewModel.clearDraftRefinement()
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType })
            ) { entry ->
                NoteDetailScreen(
                    viewModel = noteViewModel,
                    noteId = entry.arguments?.getLong("noteId") ?: -1L,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        bottomTabs.forEach { tab ->
            val selected = navController.currentDestination?.hierarchy
                ?.any { it.route == tab.route } == true || currentRoute == tab.route

            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != tab.route) {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
