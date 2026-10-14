package com.offline.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.offline.app.data.repository.DefaultPrayerRepository
import com.offline.app.data.repository.DefaultTaskRepository
import com.offline.app.data.repository.PrayerRepository
import com.offline.app.data.repository.TaskRepository
import com.offline.app.ui.screens.home.HomeScreen
import com.offline.app.ui.screens.prayer.PrayerScreen
import com.offline.app.ui.screens.todo.TodoScreen
import com.offline.app.ui.viewmodels.PrayerViewModel
import com.offline.app.ui.viewmodels.TodoViewModel

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/navigation/AppNavHost.kt
 *
 * Root navigation for the app: one Material 3 [Scaffold] with a
 * [NavigationBar] pinned to the bottom, hosting a [NavHost] that switches
 * between the three destinations declared in [Screen].
 *
 * Wiring details:
 *  - The repositories are injected by the caller (typically `MainActivity`, which
 *    owns the process-wide singletons). When none is supplied a default is built
 *    from the process-wide Room database with
 *    `DefaultTaskRepository.create(context)` / `DefaultPrayerRepository.create(context)`
 *    and cached with [remember], so the graph also works standalone. Either way
 *    every destination shares the same data layer instances. There is no DI
 *    framework and no network access anywhere in this tree.
 *  - Each destination obtains its own ViewModel with
 *    `viewModel(factory = ...)`; because the call happens inside a
 *    `composable { }` block, the ViewModel is scoped to that destination's
 *    back-stack entry and is cleared when the destination leaves the graph.
 *  - The bottom bar uses the standard single-level pattern
 *    (`popUpTo(startDestination) { saveState = true }`, `launchSingleTop`,
 *    `restoreState`) so switching tabs never grows the back stack.
 *  - Inset handling: the inner screens each own a `Scaffold` with a top app bar,
 *    so this outer Scaffold only reserves the bottom-bar height for the NavHost
 *    and then *consumes* that bottom inset. Without the consumption the inner
 *    Scaffolds would add the system navigation-bar inset a second time.
 *
 * @param modifier layout modifier applied to the root scaffold.
 * @param taskRepository the shared task repository, or null to build the default.
 * @param prayerRepository the shared prayer repository, or null to build the default.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    taskRepository: TaskRepository? = null,
    prayerRepository: PrayerRepository? = null
) {
    val context = LocalContext.current

    val defaultTaskRepository = remember(context) {
        DefaultTaskRepository.create(context)
    }
    val defaultPrayerRepository = remember(context) {
        DefaultPrayerRepository.create(context)
    }

    val resolvedTaskRepository = taskRepository ?: defaultTaskRepository
    val resolvedPrayerRepository = prayerRepository ?: defaultPrayerRepository

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val navigateTo: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Screen.bottomBarDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { navigateTo(destination.route) },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.arabicLabel
                            )
                        },
                        label = {
                            Text(text = destination.arabicLabel)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        val bottomPadding = innerPadding.calculateBottomPadding()

        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding)
                .consumeWindowInsets(PaddingValues(bottom = bottomPadding))
        ) {
            composable(route = Screen.Dashboard.route) {
                val prayerViewModel: PrayerViewModel = viewModel(
                    factory = PrayerViewModel.factory(resolvedPrayerRepository)
                )
                val todoViewModel: TodoViewModel = viewModel(
                    factory = TodoViewModel.factory(resolvedTaskRepository)
                )

                HomeScreen(
                    prayerViewModel = prayerViewModel,
                    todoViewModel = todoViewModel,
                    onNavigateToTasks = { navigateTo(Screen.Tasks.route) },
                    onNavigateToPrayerSchedule = { navigateTo(Screen.PrayerSchedule.route) }
                )
            }

            composable(route = Screen.Tasks.route) {
                val todoViewModel: TodoViewModel = viewModel(
                    factory = TodoViewModel.factory(resolvedTaskRepository)
                )

                TodoScreen(viewModel = todoViewModel)
            }

            composable(route = Screen.PrayerSchedule.route) {
                val prayerViewModel: PrayerViewModel = viewModel(
                    factory = PrayerViewModel.factory(resolvedPrayerRepository)
                )

                PrayerScreen(viewModel = prayerViewModel)
            }
        }
    }
}
