package hu.laca.weighttracker.ui.navigation

import hu.laca.weighttracker.R

object AppRoutes {
    const val OVERVIEW = "dashboard"
    const val WORKOUT = "workout"
    const val JOURNAL = "history"
    const val SETTINGS = "settings"
    const val EXERCISES = "exercises"
    const val EXERCISE_EDITOR = "exercise_editor"
    const val EXERCISE_EDITOR_PATTERN = "exercise_editor?exerciseId={exerciseId}"
}

data class RootTab(
    val route: String,
    val labelRes: Int
)

data class InternalNavigation(
    val targetRoute: String,
    val backTarget: String,
    val shouldPush: Boolean
)

object AppNavigation {
    val rootTabs: List<RootTab> = listOf(
        RootTab(AppRoutes.OVERVIEW, R.string.nav_dashboard),
        RootTab(AppRoutes.WORKOUT, R.string.nav_workout),
        RootTab(AppRoutes.JOURNAL, R.string.nav_journal)
    )

    private val rootRoutes: Set<String> = rootTabs.map { it.route }.toSet()

    fun canonicalRoute(route: String?): String? {
        return route?.substringBefore("?")
    }

    fun isRootDestination(route: String?): Boolean {
        return canonicalRoute(route) in rootRoutes
    }

    fun showsBottomBar(route: String?): Boolean {
        return isRootDestination(route)
    }

    fun isSettingsBottomDestination(): Boolean {
        return rootTabs.any { it.route == AppRoutes.SETTINGS }
    }

    fun catalogEntryPoint(): String {
        return AppRoutes.WORKOUT
    }

    fun settingsContainsCatalog(): Boolean {
        return false
    }

    fun openSettings(fromRoute: String, currentRoute: String? = fromRoute): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.SETTINGS,
            backTarget = canonicalRoute(fromRoute) ?: AppRoutes.OVERVIEW,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.SETTINGS)
        )
    }

    fun openCatalog(currentRoute: String?): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.EXERCISES,
            backTarget = AppRoutes.WORKOUT,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.EXERCISES)
        )
    }

    fun shouldNavigate(currentRoute: String?, targetRoute: String): Boolean {
        return canonicalRoute(currentRoute) != canonicalRoute(targetRoute)
    }

    fun backFromRootTab(currentRoute: String?): String? {
        val current = canonicalRoute(currentRoute) ?: return null
        if (current == AppRoutes.OVERVIEW || current !in rootRoutes) {
            return null
        }
        return AppRoutes.OVERVIEW
    }
}
