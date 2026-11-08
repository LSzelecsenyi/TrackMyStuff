package eu.strictworkout.account;

import eu.strictworkout.identity.ExternalIdentityVerifier;
import eu.strictworkout.identity.IdentityProvider;
import eu.strictworkout.identity.UnverifiedIdentityException;
import eu.strictworkout.identity.VerifiedExternalIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountDeletionService {

    static final Duration MAX_TOKEN_AGE = Duration.ofMinutes(10);
    static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(2);

    private final ExternalIdentityVerifier identities;
    private final AccountDeletionStore store;
    private final OneTimeMarkers markers;
    private final Clock clock;

    public AccountDeletionService(
            ExternalIdentityVerifier identities,
            AccountDeletionStore store,
            Clock clock,
            OneTimeMarkers markers
    ) {
        this.identities = identities;
        this.store = store;
        this.clock = clock;
        this.markers = markers;
    }

    /**
     * Deletes the account named by the verified Google token.
     * Used by the public web page, which has no Strict session.
     * When {@code sessionUserId} is present, that session's Google subject must be the same token.
     * A missing user with an existing marker is success. The caller must not treat a thrown
     * exception as success: this method is one transaction, and a failure rolls the work back.
     */
    @Transactional
    public void delete(String idToken, boolean confirmed, UUID sessionUserId) {
        if (!confirmed) {
            throw confirmationRequired();
        }
        VerifiedExternalIdentity identity = verified(idToken);
        requireRecent(identity);
        if (sessionUserId != null) {
            String sessionSubject = store.googleSubject(sessionUserId);
            if (sessionSubject == null || !sessionSubject.equals(identity.subject())) {
                throw new AccountDeletionException(
                        AccountDeletionException.Code.ACCOUNT_MISMATCH,
                        "This Google account is not the signed-in Strict account."
                );
            }
        }
        if (markers.untrustworthy()) {
            throw unavailable();
        }
        UUID userId = store.findGoogleUser(identity.subject());
        if (userId == null) {
            if (markers.present(identity.subject())) {
                return;
            }
            throw notFound();
        }
        if (!store.lock(userId)) {
            if (markers.present(identity.subject())) {
                return;
            }
            throw notFound();
        }
        erase(userId, identity.subject());
    }

    /**
     * Deletes the account identified by the authenticated Strict session.
     * The body cannot name another account. A Google ID token is not required.
     */
    @Transactional
    public void deleteSignedIn(boolean confirmed, UUID sessionUserId) {
        if (!confirmed) {
            throw confirmationRequired();
        }
        if (sessionUserId == null) {
            throw notFound();
        }
        if (markers.untrustworthy()) {
            throw unavailable();
        }
        String subject = store.googleSubject(sessionUserId);
        if (!store.lock(sessionUserId)) {
            if (subject != null && markers.present(subject)) {
                return;
            }
            throw notFound();
        }
        if (subject == null || subject.isBlank()) {
            throw notFound();
        }
        erase(sessionUserId, subject);
    }

    private void erase(UUID userId, String googleSubject) {
        markers.requireWritable();
        AccountDeletionStore.Usage usage = store.usage(userId);
        Instant now = clock.instant();
        store.detachSubscriptions(userId, now);
        store.deleteAccountRows(userId);
        markers.write(googleSubject, now, usage);
        store.deleteUser(userId);
    }

    @Transactional(readOnly = true)
    public Preview preview(String idToken) {
        VerifiedExternalIdentity identity = verified(idToken);
        requireRecent(identity);
        UUID userId = store.findGoogleUser(identity.subject());
        if (userId == null) {
            return new Preview(false, false);
        }
        return new Preview(true, store.paidSubscriptionKnown(userId));
    }

    private VerifiedExternalIdentity verified(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new UnverifiedIdentityException();
        }
        VerifiedExternalIdentity identity = identities.verify(idToken);
        if (identity.provider() != IdentityProvider.GOOGLE || identity.subject() == null || identity.subject().isBlank()) {
            throw new UnverifiedIdentityException();
        }
        return identity;
    }

    private static AccountDeletionException confirmationRequired() {
        return new AccountDeletionException(
                AccountDeletionException.Code.CONFIRMATION_REQUIRED,
                "Confirm account deletion before it can run."
        );
    }

    private static AccountDeletionException notFound() {
        return new AccountDeletionException(
                AccountDeletionException.Code.NOT_FOUND,
                "No Strict account exists for this sign-in."
        );
    }

    private static AccountDeletionException unavailable() {
        return new AccountDeletionException(
                AccountDeletionException.Code.MARKER_UNAVAILABLE,
                "Account deletion is unavailable until the marker key is configured and matches its stored checksum."
        );
    }

    private void requireRecent(VerifiedExternalIdentity identity) {
        Instant issued = identity.issuedAt();
        Instant now = clock.instant();
        if (issued == null || issued.isBefore(now.minus(MAX_TOKEN_AGE)) || issued.isAfter(now.plus(MAX_FUTURE_SKEW))) {
            throw new AccountDeletionException(
                    AccountDeletionException.Code.REAUTHENTICATION_REQUIRED,
                    "Sign in with Google again to delete this account."
            );
        }
    }

    public record Preview(boolean accountFound, boolean paidSubscriptionKnown) {
    }
}
