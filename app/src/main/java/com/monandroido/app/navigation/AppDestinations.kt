package com.monandroido.app.navigation

sealed class AppDestination(val route: String) {
    data object Home : AppDestination("home")
    data object Profiles : AppDestination("profiles")
    data object Benchmark : AppDestination("benchmark")
    data object Settings : AppDestination("settings")
    data object ProfileEditor : AppDestination("profile_editor?profileId={profileId}") {
        fun route(profileId: Long?) = if (profileId == null) {
            "profile_editor"
        } else {
            "profile_editor?profileId=$profileId"
        }
    }
}
