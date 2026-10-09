package app.mymusclemap.ui.exercises

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mymusclemap.domain.exercise.StarterExerciseNames

@Composable
fun starterExerciseLabel(storedName: String): String {
    val resId = StarterExerciseNames.resId(storedName) ?: return storedName
    return stringResource(resId)
}

@Composable
fun joinedStarterNames(storedNames: List<String>): String {
    val labels = ArrayList<String>(storedNames.size)
    for (name in storedNames) {
        labels.add(starterExerciseLabel(name))
    }
    return labels.joinToString(" · ")
}
