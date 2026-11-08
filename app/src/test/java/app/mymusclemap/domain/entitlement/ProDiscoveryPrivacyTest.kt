package app.mymusclemap.domain.entitlement

import app.mymusclemap.data.preferences.PrivacyNoticeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ProDiscoveryPrivacyTest {
    @Test
    fun theNoticeIsInformationalAndANewVersionIsDueAgain() {
        val file = Files.createTempDirectory("strict-privacy").resolve("privacy_policy_notice.txt").toFile()
        val store = PrivacyNoticeStore(file)
        assertEquals(0, store.acknowledgedVersion())
        assertTrue(PrivacyPolicyNotice.due(store.acknowledgedVersion()))
        store.acknowledge(PrivacyPolicyNotice.CURRENT_VERSION)
        assertFalse(PrivacyPolicyNotice.due(store.acknowledgedVersion()))
        assertTrue(PrivacyPolicyNotice.due(PrivacyPolicyNotice.CURRENT_VERSION - 1))
        assertEquals(4, PrivacyPolicyNotice.CURRENT_VERSION)
    }
}
