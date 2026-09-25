package app.mymusclemap.ui.navigation

import app.mymusclemap.R

object AppRoutes {
    const val OVERVIEW = "dashboard"
    const val JOURNAL = "history"
    const val SETTINGS = "settings"
    const val EXERCISES = "exercises"
    const val EXERCISE_EDITOR = "exercise_editor"
    const val EXERCISE_EDITOR_PATTERN = "exercise_editor?exerciseId={exerciseId}"
    const val TEMPLATES = "templates"
    const val TEMPLATE_EDITOR = "template_editor"
    const val TEMPLATE_EDITOR_PATTERN = "template_editor?templateId={templateId}"
    const val ACTIVE_WORKOUT = "active_workout"
    const val ACTIVE_WORKOUT_PATTERN = "active_workout?sessionId={sessionId}"
    const val WORKOUT_DETAIL = "workout_detail"
    const val WORKOUT_DETAIL_PATTERN = "workout_detail?sessionId={sessionId}"
    const val WEIGHT_DETAILS = "weight_details"
    const val WORKOUT_IMPORT = "workout_import"
    const val WORKOUT_COMPLETE = "workout_complete"
    const val WORKOUT_COMPLETE_PATTERN =
        "workout_complete?exercises={exercises}&completedSets={completedSets}&durationMillis={durationMillis}"
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
        RootTab(AppRoutes.JOURNAL, R.string.nav_journal)
    )

    private val rootRoutes: Set<String> = rootTabs.map { it.route }.toSet()

    private val bottomBarRoutes: Set<String> = setOf(
        AppRoutes.OVERVIEW,
        AppRoutes.JOURNAL,
        AppRoutes.EXERCISES,
        AppRoutes.TEMPLATES
    )

    fun canonicalRoute(route: String?): String? {
        return route?.substringBefore("?")
    }

    fun isRootDestination(route: String?): Boolean {
        return canonicalRoute(route) in rootRoutes
    }

    fun showsBottomBar(route: String?): Boolean {
        return canonicalRoute(route) in bottomBarRoutes
    }

    fun isSettingsBottomDestination(): Boolean {
        return rootTabs.any { it.route == AppRoutes.SETTINGS }
    }

    fun catalogEntryPoint(): String {
        return AppRoutes.OVERVIEW
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
            backTarget = AppRoutes.OVERVIEW,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.EXERCISES)
        )
    }

    fun openActiveWorkout(currentRoute: String?): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.ACTIVE_WORKOUT,
            backTarget = AppRoutes.OVERVIEW,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.ACTIVE_WORKOUT)
        )
    }

    fun openWeightDetails(currentRoute: String?): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.WEIGHT_DETAILS,
            backTarget = AppRoutes.OVERVIEW,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.WEIGHT_DETAILS)
        )
    }

    fun openWorkoutImport(currentRoute: String?): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.WORKOUT_IMPORT,
            backTarget = AppRoutes.JOURNAL,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.WORKOUT_IMPORT)
        )
    }

    fun openWorkoutDetail(currentRoute: String?): InternalNavigation {
        val back = when (canonicalRoute(currentRoute)) {
            AppRoutes.OVERVIEW, AppRoutes.JOURNAL -> canonicalRoute(currentRoute)!!
            else -> AppRoutes.JOURNAL
        }
        return InternalNavigation(
            targetRoute = AppRoutes.WORKOUT_DETAIL,
            backTarget = back,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.WORKOUT_DETAIL)
        )
    }

    fun openTemplates(currentRoute: String?): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.TEMPLATES,
            backTarget = AppRoutes.OVERVIEW,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.TEMPLATES)
        )
    }

    fun openWorkoutComplete(currentRoute: String?): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.WORKOUT_COMPLETE,
            backTarget = AppRoutes.OVERVIEW,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.WORKOUT_COMPLETE)
        )
    }

    fun leaveWorkoutComplete(): String {
        return AppRoutes.OVERVIEW
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
