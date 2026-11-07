package app.mymusclemap.domain.entitlement

import app.mymusclemap.data.preferences.InstallationIdStore
import app.mymusclemap.data.preferences.PrivacyNoticeStore
import app.mymusclemap.data.preferences.ProDiscoverySyncPermissionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ProDiscoveryPrivacyTest {
    @Test
    fun aMaterialPolicyVersionShowsOnceUntilAcknowledged() {
        val directory = Files.createTempDirectory("privacy-notice").toFile()
        val store = PrivacyNoticeStore(directory.resolve("notice.txt"))
        assertTrue(PrivacyPolicyNotice.due(store.acknowledgedVersion()))
        store.acknowledge(PrivacyPolicyNotice.CURRENT_VERSION)
        assertFalse(PrivacyPolicyNotice.due(store.acknowledgedVersion()))
        assertTrue(PrivacyPolicyNotice.due(PrivacyPolicyNotice.CURRENT_VERSION - 1))
    }

    @Test
    fun aNewAccountDoesNotAllowActivityUpload() {
        val file = Files.createTempDirectory("sync-default").toFile().resolve("missing.txt")
        val permission = ProDiscoverySyncPermissionStore(file).read()
        assertEquals(ProDiscoverySyncChoice.UNSET, permission.choice)
        assertFalse(permission.allowsUpload)
        assertTrue(permission.toUi().prompt)
        assertEquals(ProDiscoverySyncDeletion.NONE, permission.deletion)
    }

    @Test
    fun decliningStopsUploadWithoutClaimingServerDeletion() {
        val store = ProDiscoverySyncPermissionStore(
            Files.createTempDirectory("sync-decline").toFile().resolve("account-a.txt")
        )
        store.write(
            ProDiscoverySyncPermission(
                choice = ProDiscoverySyncChoice.DISABLED,
                deletion = ProDiscoverySyncDeletion.FAILED
            )
        )
        val loaded = store.read()
        assertFalse(loaded.allowsUpload)
        assertFalse(loaded.toUi().prompt)
        assertTrue(loaded.toUi().deletionFailed)
        assertFalse(loaded.toUi().enabled)
    }

    @Test
    fun anotherAccountDoesNotInheritTheFirstAccountsChoice() {
        val directory = Files.createTempDirectory("sync-accounts").toFile()
        val first = ProDiscoverySyncPermissionStore(directory.resolve("pro_discovery_sync_a.txt"))
        val second = ProDiscoverySyncPermissionStore(directory.resolve("pro_discovery_sync_b.txt"))
        first.write(
            ProDiscoverySyncPermission(
                choice = ProDiscoverySyncChoice.ENABLED,
                generation = 4
            )
        )
        assertTrue(first.read().allowsUpload)
        assertFalse(second.read().allowsUpload)
        assertEquals(ProDiscoverySyncChoice.UNSET, second.read().choice)
    }

    @Test
    fun aReinstallKeepsANewInstallationIdFromErasingTheOldOneLocally() {
        val directory = Files.createTempDirectory("installation").toFile()
        val first = InstallationIdStore(directory.resolve("id.txt")).getOrCreate()
        val again = InstallationIdStore(directory.resolve("id.txt")).getOrCreate()
        val reinstall = InstallationIdStore(directory.resolve("other.txt")).getOrCreate()
        assertEquals(first, again)
        assertNotEquals(first, reinstall)
    }
}
