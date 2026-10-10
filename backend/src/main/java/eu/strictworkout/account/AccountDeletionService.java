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
    private final Clock clock;

    public AccountDeletionService(ExternalIdentityVerifier identities, AccountDeletionStore store, Clock clock) {
        this.identities = identities;
        this.store = store;
        this.clock = clock;
    }

    /**
     * Deletes the account named by the verified Google token.
     * When {@code sessionUserId} is present, that session's Google subject must be the same token.
     * A missing user with an existing marker is success. The caller must not treat a thrown
     * exception as success: this method is one transaction, and a failure rolls the work back.
     */
    @Transactional
    public void delete(String idToken, boolean confirmed, UUID sessionUserId) {
        if (!confirmed) {
            throw new AccountDeletionException(
                    AccountDeletionException.Code.CONFIRMATION_REQUIRED,
                    "Confirm account deletion before it can run."
            );
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
        String hash = GoogleSubjectHash.of(identity.subject());
        UUID userId = store.findGoogleUser(identity.subject());
        if (userId == null) {
            if (store.marker(hash)) {
                return;
            }
            throw new AccountDeletionException(
                    AccountDeletionException.Code.NOT_FOUND,
                    "No Strict account exists for this Google sign-in."
            );
        }
        if (!store.lock(userId)) {
            if (store.marker(hash)) {
                return;
            }
            throw new AccountDeletionException(
                    AccountDeletionException.Code.NOT_FOUND,
                    "No Strict account exists for this Google sign-in."
            );
        }
        AccountDeletionStore.Usage usage = store.usage(userId);
        Instant now = clock.instant();
        store.blockSubscriptionClaims(userId, now);
        store.deleteAccountRows(userId);
        store.insertMarker(hash, now, usage);
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
