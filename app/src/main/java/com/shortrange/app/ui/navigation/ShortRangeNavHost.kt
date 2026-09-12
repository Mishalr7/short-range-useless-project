package com.shortrange.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shortrange.app.model.FaultType
import com.shortrange.app.ui.screens.ActiveCallScreen
import com.shortrange.app.ui.screens.CalibrationScreen
import com.shortrange.app.ui.screens.CommunicationLostScreen
import com.shortrange.app.ui.screens.ConnectingScreen
import com.shortrange.app.ui.screens.CreateSessionScreen
import com.shortrange.app.ui.screens.HomeScreen
import com.shortrange.app.ui.screens.JoinSessionScreen
import com.shortrange.app.ui.screens.SplashScreen

@Composable
fun ShortRangeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onTimeout = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onCreateSessionClick = { navController.navigate(Screen.CreateSession.route) },
                onJoinSessionClick = { navController.navigate(Screen.JoinSession.route) }
            )
        }

        composable(Screen.CreateSession.route) {
            CreateSessionScreen(
                onBackClick = { navController.popBackStack() },
                onParticipantJoined = { navController.navigate(Screen.ProximityCalibration.route) }
            )
        }

        composable(Screen.JoinSession.route) {
            JoinSessionScreen(
                onBackClick = { navController.popBackStack() },
                onJoinSuccess = { navController.navigate(Screen.ProximityCalibration.route) }
            )
        }

        composable(Screen.ProximityCalibration.route) {
            CalibrationScreen(
                onBackClick = { navController.popBackStack() },
                onEstablishVoiceChannel = { navController.navigate(Screen.Connecting.route) }
            )
        }

        composable(Screen.Connecting.route) {
            ConnectingScreen(
                onBackClick = { navController.popBackStack() },
                onConnected = {
                    navController.navigate(Screen.ActiveCall.route) {
                        popUpTo(Screen.Connecting.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ActiveCall.route) {
            ActiveCallScreen(
                onEndCallClick = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onFaultOccurred = { fault ->
                    navController.navigate(Screen.CommunicationLost.createRoute(fault.name))
                }
            )
        }

        composable(
            route = Screen.CommunicationLost.route,
            arguments = listOf(navArgument("faultCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val faultCodeName = backStackEntry.arguments?.getString("faultCode")
            val faultType = try {
                FaultType.valueOf(faultCodeName ?: FaultType.PROXIMITY_FAULT_04.name)
            } catch (e: Exception) {
                FaultType.PROXIMITY_FAULT_04
            }

            CommunicationLostScreen(
                faultType = faultType,
                onReturnHomeClick = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }
    }
}
