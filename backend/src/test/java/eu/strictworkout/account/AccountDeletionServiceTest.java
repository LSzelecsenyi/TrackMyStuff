package eu.strictworkout.account;

import eu.strictworkout.identity.IdentityProvider;
import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.UnverifiedIdentityException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountDeletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private final FakeStore store = new FakeStore();
    private final ScriptedIdentityVerifier identities = new ScriptedIdentityVerifier();
    private final AccountDeletionService service = new AccountDeletionService(
            identities,
            store,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void confirmationAndRecentAuthenticationAreRequired() {
        identities.accept("fresh", "subject-a", "a@example.com", true, NOW);
        assertThrows(AccountDeletionException.class, () -> service.delete("fresh", false, null));
        assertFalse(store.userDeleted);

        identities.accept("stale", "subject-a", "a@example.com", true, NOW.minus(Duration.ofMinutes(11)));
        AccountDeletionException stale = assertThrows(
                AccountDeletionException.class,
                () -> service.delete("stale", true, null)
        );
        assertEquals("REAUTHENTICATION_REQUIRED", stale.errorCode());

        identities.accept("future", "subject-a", "a@example.com", true, NOW.plus(Duration.ofMinutes(3)));
        assertEquals(
                "REAUTHENTICATION_REQUIRED",
                assertThrows(AccountDeletionException.class, () -> service.delete("future", true, null)).errorCode()
        );
        assertFalse(store.userDeleted);
    }

    @Test
    void theSessionSubjectMustMatchAndAnotherAccountIsNotDeleted() {
        identities.accept("token-b", "subject-b", "b@example.com", true, NOW);
        store.sessionSubject = "subject-a";
        AccountDeletionException mismatch = assertThrows(
                AccountDeletionException.class,
                () -> service.delete("token-b", true, UUID.randomUUID())
        );
        assertEquals("ACCOUNT_MISMATCH", mismatch.errorCode());
        assertFalse(store.userDeleted);
    }

    @Test
    void aMissingAccountWithAMarkerIsSuccessAndARepeatedDeleteDoesNotRunTwice() {
        identities.accept("gone", "subject-a", "a@example.com", true, NOW);
        store.userId = null;
        store.marker = true;
        service.delete("gone", true, null);
        assertFalse(store.userDeleted);

        store.userId = UUID.randomUUID();
        store.marker = false;
        store.usage = new AccountDeletionStore.Usage(true, true, false, true);
        service.delete("gone", true, null);
        assertTrue(store.userDeleted);
        assertTrue(store.claimsBlocked);
        assertEquals(store.usage, store.savedUsage);
        service.delete("gone", true, null);
        assertEquals(1, store.deletes);
    }

    @Test
    void aFailedMarkerInsertDoesNotReportSuccess() {
        identities.accept("fresh", "subject-a", "a@example.com", true, NOW);
        store.userId = UUID.randomUUID();
        store.failMarker = true;
        assertThrows(IllegalStateException.class, () -> service.delete("fresh", true, null));
        assertFalse(store.userDeleted);
    }

    @Test
    void anUnknownTokenIsRejected() {
        assertThrows(UnverifiedIdentityException.class, () -> service.delete("missing", true, null));
    }

    @Test
    void subjectHashDoesNotKeepTheSubject() {
        String hash = GoogleSubjectHash.of("subject-a");
        assertEquals(64, hash.length());
        assertEquals(hash, GoogleSubjectHash.of("subject-a"));
        assertFalse(hash.contains("subject-a"));
        assertFalse(hash.equals(GoogleSubjectHash.of("subject-b")));
    }

    private static final class FakeStore implements AccountDeletionStore {
        UUID userId = UUID.randomUUID();
        String sessionSubject = "subject-a";
        boolean marker;
        boolean userDeleted;
        boolean claimsBlocked;
        boolean failMarker;
        int deletes;
        Usage usage = new Usage(false, false, false, false);
        Usage savedUsage;

        @Override
        public UUID findGoogleUser(String subject) {
            return "subject-a".equals(subject) ? userId : null;
        }

        @Override
        public String googleSubject(UUID ignored) {
            return sessionSubject;
        }

        @Override
        public boolean lock(UUID ignored) {
            return userId != null;
        }

        @Override
        public Usage usage(UUID ignored) {
            return usage;
        }

        @Override
        public void blockSubscriptionClaims(UUID ignored, Instant now) {
            claimsBlocked = true;
        }

        @Override
        public void deleteAccountRows(UUID ignored) {
        }

        @Override
        public void insertMarker(String subjectHash, Instant deletedAt, Usage usage) {
            if (failMarker) {
                throw new IllegalStateException("marker");
            }
            savedUsage = usage;
            marker = true;
            userId = null;
        }

        @Override
        public void deleteUser(UUID ignored) {
            userDeleted = true;
            deletes++;
        }

        @Override
        public boolean marker(String subjectHash) {
            return marker;
        }

        @Override
        public boolean paidSubscriptionKnown(UUID userId) {
            return false;
        }
    }
}
