package com.shortrange.app.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Home : Screen("home")
    data object CreateSession : Screen("create_session")
    data object JoinSession : Screen("join_session")
    data object ProximityCalibration : Screen("calibration")
    data object Connecting : Screen("connecting")
    data object ActiveCall : Screen("active_call")
    data object CommunicationLost : Screen("communication_lost/{faultCode}") {
        fun createRoute(faultCode: String) = "communication_lost/$faultCode"
    }
}
