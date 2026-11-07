package eu.strictworkout.account;

import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "strict.identity.marker-hmac-keys=",
                "strict.identity.marker-hmac-active-key=",
                "strict.google.client-id=test-client.apps.googleusercontent.com"
        }
)
@Import(ScriptedIdentityVerifierConfig.class)
@Testcontainers
class MissingMarkerKeyIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private DeletedAccountPolicy policy;

    @Autowired
    private AccountDeletionService deletion;

    @Autowired
    private ScriptedIdentityVerifier identities;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aMissingSecretKeepsLegacyProtectionAndDoesNotReenableBenefits() {
        UUID legacyUser = insertUser("legacy-subject");
        jdbc.update(
                """
                insert into account_deletion_marker (
                    subject_hash, deleted_at, founder_used, pro_discovery_used, welcome_back_used, early_adopter_used
                ) values (?, now(), true, true, true, true)
                """,
                GoogleSubjectHash.of("legacy-subject")
        );
        assertTrue(policy.founderUsed(legacyUser));
        assertTrue(policy.proDiscoveryUsed(legacyUser));
        assertTrue(policy.welcomeBackUsed(legacyUser));
        assertTrue(policy.skipEarlyAdopter("legacy-subject"));

        UUID fresh = insertUser("fresh-subject");
        assertFalse(policy.founderUsed(fresh));
        assertFalse(policy.skipEarlyAdopter("fresh-subject"));

        jdbc.update("insert into marker_key_verifier (key_id, verifier) values (1, 'missing-secret')");
        assertTrue(policy.founderUsed(fresh));
        assertTrue(policy.proDiscoveryUsed(fresh));
        assertTrue(policy.welcomeBackUsed(fresh));
        assertTrue(policy.skipEarlyAdopter("fresh-subject"));

        identities.accept("token-fresh", "fresh-subject", "fresh@example.com", true);
        AccountDeletionException failure = assertThrows(
                AccountDeletionException.class,
                () -> deletion.delete("token-fresh", true, null)
        );
        assertEquals("MARKER_UNAVAILABLE", failure.errorCode());
        assertEquals(1, count("select count(*) from app_user where id = ?", fresh));
        assertEquals(1, count("select count(*) from account_deletion_marker where subject_hash = ?", GoogleSubjectHash.of("legacy-subject")));
    }

    private UUID insertUser(String subject) {
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("insert into app_user (id, created_at, updated_at) values (?, ?, ?)", userId, now, now);
        jdbc.update(
                """
                insert into external_identity (
                    id, user_id, provider, provider_subject, email, email_verified, created_at, updated_at
                ) values (?, ?, 'GOOGLE', ?, null, true, ?, ?)
                """,
                UUID.randomUUID(),
                userId,
                subject,
                now,
                now
        );
        return userId;
    }

    private int count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
