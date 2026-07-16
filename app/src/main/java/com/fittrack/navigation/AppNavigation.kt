package com.fittrack.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fittrack.presentation.screens.analytics.AnalyticsScreen
import com.fittrack.presentation.screens.exercise.ExerciseBrowserScreen
import com.fittrack.presentation.screens.home.HomeScreen
import com.fittrack.presentation.screens.nutrition.NutritionScreen
import com.fittrack.presentation.screens.workout.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home      : Screen("home",      "Home",    Icons.Filled.Home)
    object Workout   : Screen("workout",   "Treinos", Icons.Filled.FitnessCenter)
    object Nutrition : Screen("nutrition", "Nutrição",Icons.Filled.Restaurant)
    object Analytics : Screen("analytics", "Análise", Icons.Filled.BarChart)
}

object Routes {
    const val HOME      = "home"
    const val WORKOUT   = "workout"
    const val NUTRITION = "nutrition"
    const val ANALYTICS = "analytics"

    // Nested workout-creation graph
    const val WORKOUT_FLOW          = "workout_flow"
    const val CREATE_WORKOUT_FORM   = "create_workout_form?workoutId={workoutId}"
    const val EXERCISE_BROWSER      = "exercise_browser_nested"

    // Standalone screens
    const val WORKOUT_DETAIL  = "workout_detail/{workoutId}"
    const val ACTIVE_WORKOUT  = "active_workout/{workoutId}"

    fun createWorkout(workoutId: Long? = null) =
        if (workoutId != null) "workout_flow/create_workout_form?workoutId=$workoutId"
        else "workout_flow"

    fun workoutDetail(workoutId: Long) = "workout_detail/$workoutId"
    fun activeWorkout(workoutId: Long) = "active_workout/$workoutId"
}

val bottomNavItems = listOf(Screen.Home, Screen.Workout, Screen.Nutrition, Screen.Analytics)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.HOME, Routes.WORKOUT, Routes.NUTRITION, Routes.ANALYTICS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon     = { Icon(screen.icon, contentDescription = screen.label) },
                            label    = { Text(screen.label) },
                            selected = navBackStackEntry?.destination?.hierarchy
                                ?.any { it.route == screen.route } == true,
                            onClick  = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Routes.HOME,
            modifier         = Modifier.padding(innerPadding),
            enterTransition  = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
            exitTransition   = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) },
            popExitTransition  = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) }
        ) {

            composable(Routes.HOME) {
                HomeScreen(
                    onStartWorkout  = { navController.navigate(Routes.WORKOUT) },
                    onViewAnalytics = { navController.navigate(Routes.ANALYTICS) }
                )
            }

            composable(Routes.WORKOUT) {
                WorkoutScreen(
                    onCreateWorkout = { navController.navigate(Routes.WORKOUT_FLOW) },
                    onWorkoutClick  = { id -> navController.navigate(Routes.workoutDetail(id)) }
                )
            }

            // -- Nested graph: CreateWorkout + ExerciseBrowser share the same ViewModel --
            navigation(
                startDestination = "create_workout_form",
                route            = Routes.WORKOUT_FLOW
            ) {
                composable(
                    route     = "create_workout_form?workoutId={workoutId}",
                    arguments = listOf(navArgument("workoutId") {
                        type         = NavType.LongType
                        defaultValue = -1L
                    })
                ) { entry ->
                    // ViewModel scoped to the WHOLE nested graph - survives navigation to ExerciseBrowser
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(Routes.WORKOUT_FLOW)
                    }
                    val vm: WorkoutViewModel = hiltViewModel(parentEntry)

                    CreateWorkoutScreen(
                        onNavigateBack    = { navController.popBackStack(Routes.WORKOUT, inclusive = false) },
                        onBrowseExercises = { navController.navigate(Routes.EXERCISE_BROWSER) },
                        vm                = vm
                    )
                }

                composable(Routes.EXERCISE_BROWSER) { entry ->
                    // Same ViewModel instance as CreateWorkoutScreen
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(Routes.WORKOUT_FLOW)
                    }
                    val workoutVm: WorkoutViewModel = hiltViewModel(parentEntry)

                    ExerciseBrowserScreen(
                        onNavigateBack     = { navController.popBackStack() },
                        onExerciseSelected = { navController.popBackStack() },
                        workoutVm          = workoutVm
                    )
                }
            }

            composable(
                route     = Routes.WORKOUT_DETAIL,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
                WorkoutDetailScreen(
                    workoutId      = workoutId,
                    onNavigateBack = { navController.popBackStack() },
                    onEditWorkout  = { navController.navigate("workout_flow?workoutId=$workoutId") },
                    onStartWorkout = { navController.navigate(Routes.activeWorkout(workoutId)) }
                )
            }

            composable(
                route     = Routes.ACTIVE_WORKOUT,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
                ActiveWorkoutScreen(
                    workoutId = workoutId,
                    onFinish  = {
                        navController.navigate(Routes.WORKOUT) {
                            popUpTo(Routes.HOME)
                        }
                    }
                )
            }

            composable(Routes.NUTRITION) { NutritionScreen() }
            composable(Routes.ANALYTICS) { AnalyticsScreen() }
        }
    }
}
