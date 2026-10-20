package com.example.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.AuthState
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.auth.ForgotPasswordScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.SignUpScreen
import com.example.ui.screens.main.MainScaffold
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.screens.subjectdetail.SubjectDetailScreen
import com.example.ui.screens.topicdetail.TopicDetailScreen
import com.example.ui.screens.unitdetail.UnitDetailScreen

@Composable
fun StudyVaultNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()

    // Handle global session expiration or external sign-out
    LaunchedEffect(authState) {
        if (authState is AuthState.Unauthenticated) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != null &&
                currentRoute != Screen.Splash.route &&
                currentRoute != Screen.Login.route &&
                currentRoute != Screen.SignUp.route &&
                currentRoute != Screen.ForgotPassword.route
            ) {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                authState = authState,
                onNavigateToHome = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate(Screen.SignUp.route)
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                }
            )
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                viewModel = authViewModel,
                onSignUpSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Main.route) {
            MainScaffold(
                onNavigateToSubjectDetail = { subjectId ->
                    navController.navigate(Screen.SubjectDetail.createRoute(subjectId))
                },
                onNavigateToSubjectQuestions = { subjectId ->
                    navController.navigate(Screen.SubjectQuestions.createRoute(subjectId))
                },
                onNavigateToTopicDetail = { topicId ->
                    navController.navigate(Screen.TopicDetail.createRoute(topicId))
                },
                onOpenProfile = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(
            route = Screen.SubjectDetail.route,
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getString("subjectId") ?: "s_java"
            SubjectDetailScreen(
                subjectId = subjectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUnitDetail = { subId, unitId ->
                    navController.navigate(Screen.UnitDetail.createRoute(subId, unitId))
                }
            )
        }

        composable(
            route = Screen.SubjectQuestions.route,
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getString("subjectId") ?: ""
            SubjectDetailScreen(
                subjectId = subjectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUnitDetail = { subId, unitId ->
                    navController.navigate(Screen.UnitDetail.createRoute(subId, unitId))
                },
                initialTabIndex = 2
            )
        }

        composable(
            route = Screen.UnitDetail.route,
            arguments = listOf(
                navArgument("subjectId") { type = NavType.StringType },
                navArgument("unitId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getString("subjectId") ?: "s_java"
            val unitId = backStackEntry.arguments?.getString("unitId") ?: "u_java_2"
            UnitDetailScreen(
                subjectId = subjectId,
                unitId = unitId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTopicDetail = { topicId ->
                    navController.navigate(Screen.TopicDetail.createRoute(topicId))
                }
            )
        }

        composable(
            route = Screen.TopicDetail.route,
            arguments = listOf(navArgument("topicId") { type = NavType.StringType })
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getString("topicId") ?: "t_java_u2_1"
            TopicDetailScreen(
                topicId = topicId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            SettingsScreen(
                authViewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSignOutSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
