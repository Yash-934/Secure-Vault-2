package com.example.ui.navigation

sealed class NavRoutes(val route: String) {
    object Lock : NavRoutes("lock")
    object Calculator : NavRoutes("calculator")
    object Notes : NavRoutes("notes")
    object Dashboard : NavRoutes("dashboard")
    object Settings : NavRoutes("settings")
    object IntruderLogs : NavRoutes("intruder_logs")
    object About : NavRoutes("about")
    object Help : NavRoutes("help")
    object PasswordManager : NavRoutes("password_manager")
    object EncryptionInspector : NavRoutes("encryption_inspector")
    object MediaViewer : NavRoutes("media_viewer/{itemId}") {
        fun createRoute(itemId: Long): String = "media_viewer/$itemId"
    }
}
