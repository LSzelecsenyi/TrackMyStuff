package eu.strictworkout.account;

import eu.strictworkout.identity.ExternalIdentityRepository;
import eu.strictworkout.identity.IdentityProvider;
import eu.strictworkout.identity.ReturningAccountPolicy;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeletedAccountPolicy implements ReturningAccountPolicy {

    private final AccountDeletionMarkerRepository markers;
    private final ExternalIdentityRepository identities;

    public DeletedAccountPolicy(AccountDeletionMarkerRepository markers, ExternalIdentityRepository identities) {
        this.markers = markers;
        this.identities = identities;
    }

    @Override
    public boolean skipEarlyAdopter(String googleSubject) {
        if (googleSubject == null || googleSubject.isBlank()) {
            return false;
        }
        return markers.existsById(GoogleSubjectHash.of(googleSubject));
    }

    public boolean founderUsed(UUID userId) {
        return flag(userId, AccountDeletionMarker::isFounderUsed);
    }

    public boolean proDiscoveryUsed(UUID userId) {
        return flag(userId, AccountDeletionMarker::isProDiscoveryUsed);
    }

    public boolean welcomeBackUsed(UUID userId) {
        return flag(userId, AccountDeletionMarker::isWelcomeBackUsed);
    }

    private boolean flag(UUID userId, java.util.function.Predicate<AccountDeletionMarker> used) {
        return identities.findByUser_IdAndProvider(userId, IdentityProvider.GOOGLE)
                .flatMap(identity -> markers.findById(GoogleSubjectHash.of(identity.getProviderSubject())))
                .map(used::test)
                .orElse(false);
    }
}
