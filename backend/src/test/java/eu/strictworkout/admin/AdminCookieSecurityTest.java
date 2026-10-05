package eu.strictworkout.admin;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCookieSecurityTest {

    @Test
    void productionProfileForcesASecureCookieEvenWhenThePropertySaysFalse() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        AdminProperties insecureProperty = properties(false);

        assertTrue(insecureProperty.secureCookie(production));
    }

    @Test
    void omittedCookieSettingIsSecureAndOnlyExplicitFalseIsForHttpDevelopment() {
        MockEnvironment local = new MockEnvironment();

        assertTrue(properties(null).secureCookie(local));
        assertTrue(properties(true).secureCookie(local));
        assertFalse(properties(false).secureCookie(local));
    }

    private static AdminProperties properties(Boolean secure) {
        return new AdminProperties(Duration.ofHours(12), "", secure);
    }
}
