package app.mymusclemap.ui.navigation

import app.mymusclemap.R

object AppRoutes {
    const val OVERVIEW = "dashboard"
    const val JOURNAL = "history"
    const val SETTINGS = "settings"
    const val FOUNDER_PROGRAM = "founder_program"
    const val HEALTH_CONNECT = "health_connect"
    const val HELP = "help"
    const val PRIVACY = "privacy"
    const val OPEN_SOURCE_LICENSES = "open_source_licenses"
    const val PRO_INFO = "pro_info"
    const val STATISTICS_GRAPH = "statistics_graph"
    const val STATISTICS = "statistics"
    const val STATISTICS_MUSCLES = "statistics_muscles"
    const val STATISTICS_REST = "statistics_rest"
    const val STATISTICS_EXERCISES = "statistics_exercises"
    const val STATISTICS_EXERCISE = "statistics_exercise"
    const val STATISTICS_EXERCISE_PATTERN = "statistics_exercise?exerciseId={exerciseId}"
    const val REPORTS_GRAPH = "reports_graph"
    const val REPORTS = "reports"
    const val REPORT_DETAIL = "report_detail"
    const val REPORT_DETAIL_PATTERN = "report_detail?kind={kind}&start={start}"
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
    const val BODY_PROGRESS_GRAPH = "body_progress_graph"
    const val BODY_MEASUREMENT = "body_measurement"
    const val BODY_MEASUREMENT_PATTERN = "body_measurement?type={type}"
    const val PROGRESS_PHOTOS = "progress_photos"
    const val PROGRESS_PHOTO = "progress_photo"
    const val PROGRESS_PHOTO_PATTERN = "progress_photo?photoId={photoId}"
    const val PROGRESS_PHOTO_COMPARE = "progress_photo_compare"
    const val PROGRESS_PHOTO_COMPARE_PATTERN = "progress_photo_compare?first={first}&second={second}"
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
        RootTab(AppRoutes.STATISTICS_GRAPH, R.string.statistics_title),
        RootTab(AppRoutes.JOURNAL, R.string.nav_journal)
    )

    private val rootRoutes: Set<String> = rootTabs.map { it.route }.toSet()

    private val bottomBarRoutes: Set<String> = setOf(
        AppRoutes.OVERVIEW,
        AppRoutes.JOURNAL,
        AppRoutes.STATISTICS,
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

    fun bottomTabRoute(route: String?): String? {
        return when (val canonical = canonicalRoute(route)) {
            AppRoutes.STATISTICS, AppRoutes.STATISTICS_GRAPH -> AppRoutes.STATISTICS
            else -> canonical
        }
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

    fun openHelp(currentRoute: String?): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.HELP,
            backTarget = AppRoutes.SETTINGS,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.HELP)
        )
    }

    fun openOpenSourceLicenses(currentRoute: String?): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.OPEN_SOURCE_LICENSES,
            backTarget = AppRoutes.SETTINGS,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.OPEN_SOURCE_LICENSES)
        )
    }

    fun openPrivacy(currentRoute: String?): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.PRIVACY,
            backTarget = AppRoutes.SETTINGS,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.PRIVACY)
        )
    }

    fun openProInfo(fromRoute: String, currentRoute: String? = fromRoute): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.PRO_INFO,
            backTarget = canonicalRoute(fromRoute) ?: AppRoutes.OVERVIEW,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.PRO_INFO)
        )
    }

    fun openStatistics(currentRoute: String?): InternalNavigation {
        return InternalNavigation(
            targetRoute = AppRoutes.STATISTICS_GRAPH,
            backTarget = AppRoutes.OVERVIEW,
            shouldPush = shouldNavigate(currentRoute, AppRoutes.STATISTICS_GRAPH)
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

    fun bodyMeasurementRoute(typeCode: String): String {
        return "${AppRoutes.BODY_MEASUREMENT}?type=$typeCode"
    }

    fun progressPhotoRoute(photoId: Long): String {
        return "${AppRoutes.PROGRESS_PHOTO}?photoId=$photoId"
    }

    fun progressPhotoCompareRoute(first: Long, second: Long): String {
        return "${AppRoutes.PROGRESS_PHOTO_COMPARE}?first=$first&second=$second"
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
        return when (canonicalRoute(currentRoute)) {
            AppRoutes.JOURNAL, AppRoutes.STATISTICS, AppRoutes.STATISTICS_GRAPH -> AppRoutes.OVERVIEW
            else -> null
        }
    }
}
