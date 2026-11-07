package eu.strictworkout.account;

import eu.strictworkout.identity.ExternalIdentityRepository;
import eu.strictworkout.identity.IdentityProvider;
import eu.strictworkout.identity.ReturningAccountPolicy;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Predicate;

@Service
public class DeletedAccountPolicy implements ReturningAccountPolicy {

    private final OneTimeMarkers markers;
    private final ExternalIdentityRepository identities;

    public DeletedAccountPolicy(OneTimeMarkers markers, ExternalIdentityRepository identities) {
        this.markers = markers;
        this.identities = identities;
    }

    @Override
    public boolean skipEarlyAdopter(String googleSubject) {
        if (googleSubject == null || googleSubject.isBlank()) {
            return false;
        }
        if (markers.untrustworthy()) {
            return true;
        }
        return markers.find(googleSubject).map(AccountDeletionStore.MarkerRow::earlyAdopter).orElse(false);
    }

    public boolean founderUsed(UUID userId) {
        return flag(userId, AccountDeletionStore.MarkerRow::founder);
    }

    public boolean proDiscoveryUsed(UUID userId) {
        return flag(userId, AccountDeletionStore.MarkerRow::proDiscovery);
    }

    public boolean welcomeBackUsed(UUID userId) {
        return flag(userId, AccountDeletionStore.MarkerRow::welcomeBack);
    }

    private boolean flag(UUID userId, Predicate<AccountDeletionStore.MarkerRow> used) {
        if (markers.untrustworthy()) {
            return true;
        }
        return identities.findByUser_IdAndProvider(userId, IdentityProvider.GOOGLE)
                .map(identity -> identity.getProviderSubject())
                .flatMap(markers::find)
                .map(used::test)
                .orElse(false);
    }
}
