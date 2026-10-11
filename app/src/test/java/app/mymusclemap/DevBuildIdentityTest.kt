package app.mymusclemap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DevBuildIdentityTest {
    @Test
    fun devBuildStaysBesideProductionAndDoesNotChangeBackup() {
        val gradle = File("build.gradle.kts").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val devName = File("src/dev/res/values/strings.xml").readText()
        val devNameHu = File("src/dev/res/values-hu/strings.xml").readText()

        assertTrue(gradle.contains("applicationId = \"com.strictworkout.app\""))
        assertTrue(gradle.contains("applicationIdSuffix = \".debug\""))
        assertTrue(gradle.contains("applicationIdSuffix = \".dev\""))
        assertTrue(gradle.contains("https://api.strictworkout.eu"))
        assertTrue(gradle.contains("https://api-dev.strictworkout.eu"))
        assertTrue(gradle.contains("src/release/java"))
        assertFalse(gradle.contains("applicationId = \"com.strictworkout.app.dev\""))
        assertTrue(devName.contains(">Strict DEV<"))
        assertTrue(devNameHu.contains(">Strict DEV<"))
        assertTrue(manifest.contains("android:allowBackup=\"true\""))
        assertTrue(manifest.contains("@xml/backup_rules"))
        assertTrue(manifest.contains("@xml/data_extraction_rules"))
    }
}
