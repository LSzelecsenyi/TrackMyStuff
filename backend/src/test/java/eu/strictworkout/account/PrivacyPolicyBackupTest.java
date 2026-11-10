package eu.strictworkout.account;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyPolicyBackupTest {

    @Test
    void hostedPoliciesDescribeTheOperationalBackupAndDoNotPromiseImmediateRemoval() throws Exception {
        String english = read("privacy-en.html");
        String hungarian = read("privacy-hu.html");

        assertTrue(english.contains("Effective date: 9 November 2026."));
        assertTrue(hungarian.contains("Hatályos: 2026. november 9."));
        for (String policy : new String[] {english, hungarian}) {
            assertTrue(policy.contains("eu-frankfurt-1"));
            assertFalse(policy.contains("not running"));
            assertFalse(policy.contains("nem fut"));
            assertFalse(policy.contains("is planned"));
            assertFalse(policy.contains("tervezve van"));
            assertFalse(policy.contains("nincs telepítve"));
        }
        assertTrue(english.contains("Deleting an account does not erase that account from a backup already made"));
        assertTrue(english.contains("This is not a promise that every database copy disappears after 20 days."));
        assertTrue(hungarian.contains("A fiók törlése nem törli a fiókot a már elkészült mentésből"));
        assertTrue(hungarian.contains("Ez nem ígéri, hogy minden adatbázis-másolat 20 nap után eltűnik."));
    }

    private static String read(String name) throws Exception {
        return new ClassPathResource(name).getContentAsString(StandardCharsets.UTF_8);
    }
}
