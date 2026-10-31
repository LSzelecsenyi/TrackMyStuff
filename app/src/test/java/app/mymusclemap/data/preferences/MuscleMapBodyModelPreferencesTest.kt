package app.mymusclemap.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.musclemap.MuscleMapBodyModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MuscleMapBodyModelPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun missingPreferenceStaysMaleAndAChoiceSurvivesANewStore() = runBlocking {
        val first = MuscleMapBodyModelPreferences(context)
        first.load()
        assertNull(first.storedOrNull())
        assertEquals(MuscleMapBodyModel.MALE, first.current())
        assertEquals(MuscleMapBodyModel.MALE, DisplayedMuscleMapBodyModel.current)
        first.write(MuscleMapBodyModel.FEMALE)
        val second = MuscleMapBodyModelPreferences(context)
        second.load()
        assertEquals(MuscleMapBodyModel.FEMALE, second.storedOrNull())
        assertEquals(MuscleMapBodyModel.FEMALE, second.current())
        assertEquals(MuscleMapBodyModel.FEMALE, DisplayedMuscleMapBodyModel.current)
        second.write(MuscleMapBodyModel.MALE)
        assertEquals(MuscleMapBodyModel.MALE, MuscleMapBodyModelPreferences(context).read())
        assertEquals(MuscleMapBodyModel.MALE, DisplayedMuscleMapBodyModel.current)
    }

    @Test
    fun unknownStoredValueAndAnOlderBackupRestoreMale() {
        assertNull(MuscleMapBodyModel.fromStored("other"))
        assertNull(MuscleMapBodyModel.fromStored(null))
        assertEquals(MuscleMapBodyModel.MALE, MuscleMapBodyModelBackup.decode(emptyMap()))
        assertEquals(
            MuscleMapBodyModel.MALE,
            MuscleMapBodyModelBackup.decode(mapOf(MuscleMapBodyModelPreferences.SETTINGS_KEY to "other"))
        )
        assertEquals(
            MuscleMapBodyModel.FEMALE,
            MuscleMapBodyModelBackup.decode(
                mapOf(MuscleMapBodyModelPreferences.SETTINGS_KEY to "FEMALE")
            )
        )
    }
}
