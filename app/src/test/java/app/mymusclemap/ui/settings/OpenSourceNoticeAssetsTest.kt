package app.mymusclemap.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenSourceNoticeAssetsTest {
    @Test
    fun packagedBodyMusclesNoticeAndLicenseMatchTheUpstreamTexts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val documents = OpenSourceNoticeAssets.read { name ->
            context.assets.open(name).bufferedReader().use { it.readText() }
        }
        checkNotNull(documents)
        val (notice, license) = documents
        assertTrue(notice.contains("Copyright 2024 Ivan Vulović"))
        assertTrue(notice.contains("https://github.com/vulovix/body-muscles"))
        assertTrue(license.contains("Apache License"))
        assertTrue(license.contains("Version 2.0, January 2004"))
        assertTrue(license.contains("END OF TERMS AND CONDITIONS"))
        assertTrue(license.contains("Copyright 2024 Ivan Vulović"))
    }
}
